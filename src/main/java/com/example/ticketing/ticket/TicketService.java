package com.example.ticketing.ticket;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.auth.UserRole;
import com.example.ticketing.category.Category;
import com.example.ticketing.category.CategoryRepository;
import com.example.ticketing.category.CategoryTeamMapping;
import com.example.ticketing.category.CategoryTeamMappingRepository;
import com.example.ticketing.department.Department;
import com.example.ticketing.department.DepartmentRepository;
import com.example.ticketing.team.Team;
import com.example.ticketing.team.TeamRepository;

import org.springframework.http.HttpStatus;

@Service
@Transactional
public class TicketService {
    private final TicketRepository ticketRepository;
    private final TicketAssignmentRepository ticketAssignmentRepository;
    private final TicketAuditRepository ticketAuditRepository;
    private final TicketCommentRepository ticketCommentRepository;
    private final TicketTimelineRepository ticketTimelineRepository;
    private final UserAccountRepository userAccountRepository;
    private final DepartmentRepository departmentRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryTeamMappingRepository categoryTeamMappingRepository;
    private final TeamRepository teamRepository;
    private final TicketNotificationService ticketNotificationService;

    public TicketService(
        TicketRepository ticketRepository,
        TicketAssignmentRepository ticketAssignmentRepository,
        TicketAuditRepository ticketAuditRepository,
        TicketCommentRepository ticketCommentRepository,
        TicketTimelineRepository ticketTimelineRepository,
        UserAccountRepository userAccountRepository,
        DepartmentRepository departmentRepository,
        CategoryRepository categoryRepository,
        CategoryTeamMappingRepository categoryTeamMappingRepository,
        TeamRepository teamRepository,
        TicketNotificationService ticketNotificationService
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketAssignmentRepository = ticketAssignmentRepository;
        this.ticketAuditRepository = ticketAuditRepository;
        this.ticketCommentRepository = ticketCommentRepository;
        this.ticketTimelineRepository = ticketTimelineRepository;
        this.userAccountRepository = userAccountRepository;
        this.departmentRepository = departmentRepository;
        this.categoryRepository = categoryRepository;
        this.categoryTeamMappingRepository = categoryTeamMappingRepository;
        this.teamRepository = teamRepository;
        this.ticketNotificationService = ticketNotificationService;
    }

    // ============================================================
    // TICKET CREATION
    // ============================================================
    
    /**
     * Tạo ticket mới.
     */
    public Ticket createTicket(
        Ticket ticket,
        String actorUsername,
        UserRole.Role actorRole
    ) {
        ticket.setTicketNumber(generateTicketNumber());
        ticket.setStatus(TicketTypes.TicketStatus.NEW);
        ticket.setRequesterUsername(actorUsername);

        // Lấy department từ requester
        UserAccount requester = userAccountRepository.findByUsername(actorUsername)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        ticket.setDepartment(requester.getDepartment());

        // Set SLA times dựa trên priority
        setSLATimes(ticket);

        // Auto-assign team dựa trên category (nếu có category)
        autoAssignTeam(ticket);

        // NHAN_VIEN không thể tự assign, để null
        if (actorRole == UserRole.Role.NHAN_VIEN) {
            ticket.setAssigneeName(null);
        } else {
            ticket.setAssigneeName(normalizeAssignee(ticket.getAssigneeName()));
        }

        Ticket created = ticketRepository.save(ticket);
        
        // Log timeline - ticket created
        logTicketTimeline(
            created,
            TicketTimeline.EventType.TICKET_CREATED,
            TicketTimeline.EventCategory.STATUS,
            "Ticket được tạo",
            actorRole,
            actorUsername
        );
        
        // Log audit
        logAudit(
            created.getId(),
            TicketTypes.AuditAction.CREATED,
            "ticket",
            null,
            created.getTicketNumber(),
            actorRole,
            actorUsername
        );
        
        // Gửi notification
        ticketNotificationService.notifyTicketCreated(created);
        
        return created;
    }
    
    /**
     * Log ticket timeline event.
     */
    private void logTicketTimeline(Ticket ticket, TicketTimeline.EventType eventType,
                                  TicketTimeline.EventCategory category, String title,
                                  UserRole.Role actorRole, String actorUsername) {
        TicketTimeline event = TicketTimeline.builder()
            .ticket(ticket)
            .eventType(eventType)
            .eventCategory(category)
            .title(title)
            .userActor(actorUsername, actorUsername, actorRole != null ? actorRole.name() : null)
            .build();
        ticketTimelineRepository.save(event);
    }
    
    /**
     * Auto-assign team dựa trên category.
     * Ưu tiên subcategory > parent category.
     */
    private void autoAssignTeam(Ticket ticket) {
        Long categoryId = null;
        
        // Ưu tiên subcategory
        if (ticket.getSubcategoryEntity() != null) {
            categoryId = ticket.getSubcategoryEntity().getId();
        } else if (ticket.getCategoryEntity() != null) {
            categoryId = ticket.getCategoryEntity().getId();
        }
        
        // Nếu có category, tìm team được recommend
        if (categoryId != null) {
            categoryTeamMappingRepository.findBestTeamForCategory(categoryId)
                .ifPresent(mapping -> {
                    ticket.setTeam(mapping.getTeam());
                });
        }
    }
    
    /**
     * Set SLA times dựa trên priority.
     * CRITICAL: 1h response, 4h resolution
     * HIGH: 2h response, 8h resolution
     * MEDIUM: 4h response, 24h resolution
     * LOW: 8h response, 72h resolution
     */
    private void setSLATimes(Ticket ticket) {
        TicketTypes.TicketPriority priority = ticket.getPriority();
        LocalDateTime now = LocalDateTime.now();
        
        TicketTypes.SLAPriority slaPriority = TicketTypes.SLAPriority.fromTicketPriority(priority);
        
        ticket.setSlaResponseAt(now.plusHours(slaPriority.getResponseHours()));
        ticket.setSlaResolutionAt(now.plusHours(slaPriority.getResolutionHours()));
    }

    // ============================================================
    // TICKET LISTING
    // ============================================================
    
    /**
     * Lấy tickets với department filter.
     */
    @Transactional(readOnly = true)
    public Page<Ticket> listTickets(
        String assigneeName,
        TicketTypes.TicketStatus status,
        String search,
        boolean excludeClosed,
        Pageable pageable,
        String userRole,
        Long userDepartmentId
    ) {
        String normalizedAssignee = normalizeAssignee(assigneeName);
        boolean hasSearch = search != null && !search.isBlank();
        boolean applyExcludeClosed = excludeClosed && status == null;

        if ("NHAN_VIEN".equals(userRole) || "TRUONG_PHONG".equals(userRole)) {
            // Use the same filtering logic as ADMIN/GIAM_DOC but without department restriction
        }

        if (normalizedAssignee != null && normalizedAssignee.equalsIgnoreCase("UNASSIGNED")) {
            if (hasSearch) {
                if (applyExcludeClosed) {
                    return ticketRepository.searchTicketsUnassignedExcludeStatus(
                        search.trim(),
                        TicketTypes.TicketStatus.CLOSED,
                        pageable
                    );
                }
                return ticketRepository.searchTicketsUnassigned(search.trim(), status, pageable);
            }
            if (status != null) {
                return ticketRepository.findByAssigneeNameIsNullOrAssigneeNameAndStatus("", status, pageable);
            }
            if (applyExcludeClosed) {
                return ticketRepository.findByAssigneeNameIsNullOrAssigneeNameAndStatusNot(
                    "",
                    TicketTypes.TicketStatus.CLOSED,
                    pageable
                );
            }
            return ticketRepository.findByAssigneeNameIsNullOrAssigneeName("", pageable);
        }

        if (hasSearch) {
            if (applyExcludeClosed) {
                return ticketRepository.searchTicketsExcludeStatus(
                    search.trim(),
                    normalizedAssignee,
                    TicketTypes.TicketStatus.CLOSED,
                    pageable
                );
            }
            return ticketRepository.searchTickets(search.trim(), status, normalizedAssignee, pageable);
        }

        if (normalizedAssignee != null && status != null) {
            return ticketRepository.findByAssigneeNameAndStatus(normalizedAssignee, status, pageable);
        }

        if (normalizedAssignee != null) {
            if (applyExcludeClosed) {
                return ticketRepository.findByAssigneeNameAndStatusNot(
                    normalizedAssignee,
                    TicketTypes.TicketStatus.CLOSED,
                    pageable
                );
            }
            return ticketRepository.findByAssigneeName(normalizedAssignee, pageable);
        }

        if (status != null) {
            return ticketRepository.findByStatus(status, pageable);
        }

        if (applyExcludeClosed) {
            return ticketRepository.findByStatusNot(TicketTypes.TicketStatus.CLOSED, pageable);
        }

        return ticketRepository.findAll(pageable);
    }
    
    @Transactional(readOnly = true)
    public Ticket getTicket(Long id) {
        return ticketRepository.findById(id)
            .orElseThrow(() -> new TicketNotFoundException(id));
    }

    // ============================================================
    // TICKET ASSIGNMENT
    // ============================================================
    
    /**
     * Assign ticket cho user/team.
     */
    public Ticket assignTicket(
        Long id,
        String newAssignee,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName
    ) {
        if (!canAssignTickets(actorRole, actorDepartmentId)) {
            throw new TicketRuleViolationException("You don't have permission to assign tickets.");
        }

        Ticket ticket = getTicket(id);
        String previousAssignee = ticket.getAssigneeName();
        
        // Validate transition: NEW -> ASSIGNED hoặc ASSIGNED -> ASSIGNED
        if (ticket.getStatus() == TicketTypes.TicketStatus.NEW) {
            if (!StatusTransitionValidator.isValidTransition(TicketTypes.TicketStatus.NEW, TicketTypes.TicketStatus.ASSIGNED)) {
                throw new TicketRuleViolationException("Cannot assign ticket from status: " + ticket.getStatus());
            }
            ticket.setStatus(TicketTypes.TicketStatus.ASSIGNED);
        }
        
        ticket.setAssigneeName(normalizeAssignee(newAssignee));

        // Also set the assignee entity for proper query support
        userAccountRepository.findByUsername(newAssignee).ifPresentOrElse(
            ticket::setAssignee,
            () -> { /* User not found, leave assignee as null */ }
        );

        TicketAssignment assignment = new TicketAssignment();
        assignment.setTicketId(ticket.getId());
        assignment.setPreviousAssignee(previousAssignee);
        assignment.setNewAssignee(newAssignee);
        assignment.setActorRole(actorRole.name());
        assignment.setActorName(actorName);
        ticketAssignmentRepository.save(assignment);

        logAudit(
            ticket.getId(),
            TicketTypes.AuditAction.ASSIGNEE_CHANGED,
            "assigneeName",
            previousAssignee,
            normalizeAssignee(newAssignee),
            actorRole,
            actorName
        );
        
        // Gửi notification
        ticketNotificationService.notifyTicketAssigned(ticket, actorName);

        return ticket;
    }
    
    /**
     * Unassign ticket (bỏ gán).
     */
    public Ticket unassignTicket(
        Long id,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName
    ) {
        if (!canAssignTickets(actorRole, actorDepartmentId)) {
            throw new TicketRuleViolationException("You don't have permission to unassign tickets.");
        }

        Ticket ticket = getTicket(id);
        String previousAssignee = ticket.getAssigneeName();
        
        ticket.setAssigneeName(null);
        ticket.setAssignee(null);

        TicketAssignment assignment = new TicketAssignment();
        assignment.setTicketId(ticket.getId());
        assignment.setPreviousAssignee(previousAssignee);
        assignment.setNewAssignee(null);
        assignment.setActorRole(actorRole.name());
        assignment.setActorName(actorName);
        ticketAssignmentRepository.save(assignment);

        logAudit(
            ticket.getId(),
            TicketTypes.AuditAction.UNASSIGNED,
            "assigneeName",
            previousAssignee,
            null,
            actorRole,
            actorName
        );

        return ticket;
    }

    private boolean canAssignTickets(UserRole.Role actorRole, Long actorDepartmentId) {
        return switch (actorRole) {
            case ADMIN, GIAM_DOC -> true;
            case TRUONG_PHONG -> {
                if (actorDepartmentId == null) {
                    yield false;
                }
                Department dept = departmentRepository.findById(actorDepartmentId).orElse(null);
                yield dept != null && "IT".equals(dept.getCode());
            }
            case NHAN_VIEN -> false;
        };
    }

    // ============================================================
    // STATUS MANAGEMENT - Phase 2.2
    // ============================================================
    
    /**
     * Cập nhật status với validation.
     */
    public Ticket updateStatus(
        Long id,
        TicketTypes.TicketStatus newStatus,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName,
        String actorUsername,
        String comment
    ) {
        Ticket ticket = getTicket(id);
        TicketTypes.TicketStatus currentStatus = ticket.getStatus();

        // NHAN_VIEN chỉ có thể đóng hoặc reopen ticket của mình
        if (actorRole == UserRole.Role.NHAN_VIEN) {
            if (!actorUsername.equalsIgnoreCase(ticket.getRequesterUsername())) {
                throw new TicketRuleViolationException("You can only modify your own tickets.");
            }
            
            // NHAN_VIEN chỉ có thể:
            // - CLOSE ticket đã RESOLVED
            // - REOPEN ticket đã CLOSED
            if (newStatus == TicketTypes.TicketStatus.CLOSED) {
                if (currentStatus != TicketTypes.TicketStatus.RESOLVED) {
                    throw new TicketRuleViolationException("You can only close resolved tickets.");
                }
            } else if (newStatus == TicketTypes.TicketStatus.REOPENED) {
                if (currentStatus != TicketTypes.TicketStatus.CLOSED) {
                    throw new TicketRuleViolationException("You can only reopen closed tickets.");
                }
            } else {
                throw new TicketRuleViolationException("You can only close or reopen your own tickets.");
            }
            
            return performStatusChange(ticket, currentStatus, newStatus, actorRole, actorName, comment);
        }

        // IT Staff và Admin: validate transition
        if (!StatusTransitionValidator.isValidTransition(currentStatus, newStatus)) {
            Set<TicketTypes.TicketStatus> validNext = StatusTransitionValidator.getValidNextStatuses(currentStatus);
            throw new TicketRuleViolationException(
                "Invalid status transition: " + currentStatus + " -> " + newStatus + 
                ". Valid transitions: " + validNext
            );
        }
        
        // CANCELLED chỉ có thể được set bởi Admin
        if (newStatus == TicketTypes.TicketStatus.CANCELLED && actorRole != UserRole.Role.ADMIN) {
            throw new TicketRuleViolationException("Only administrators can cancel tickets.");
        }

        return performStatusChange(ticket, currentStatus, newStatus, actorRole, actorName, comment);
    }
    
    /**
     * Thực hiện thay đổi status.
     */
    private Ticket performStatusChange(
        Ticket ticket,
        TicketTypes.TicketStatus oldStatus,
        TicketTypes.TicketStatus newStatus,
        UserRole.Role actorRole,
        String actorName,
        String comment
    ) {
        // Set timestamps dựa trên newStatus
        switch (newStatus) {
            case RESOLVED -> {
                ticket.setResolvedAt(LocalDateTime.now());
                // Gửi notification cho requester
                ticketNotificationService.notifyTicketResolved(ticket, actorName);
            }
            case CLOSED -> {
                ticket.setClosedAt(LocalDateTime.now());
                if (ticket.getResolvedAt() == null) {
                    ticket.setResolvedAt(ticket.getClosedAt());
                }
                ticketNotificationService.notifyTicketClosed(ticket, actorName);
            }
            case REOPENED -> {
                ticket.incrementReopen();
                ticket.setClosedAt(null);
                ticketNotificationService.notifyTicketReopened(ticket, actorName);
            }
            case IN_PROGRESS -> {
                // Set first response time nếu chưa có
                if (ticket.getFirstResponseAt() == null) {
                    ticket.setFirstResponseAt(LocalDateTime.now());
                }
            }
            case WAITING_FOR_USER -> {
                ticketNotificationService.notifyWaitingForUser(ticket, comment);
            }
            case ESCALATED -> {
                ticket.incrementEscalation();
                ticketNotificationService.notifyTicketEscalated(ticket, actorName, comment != null ? comment : "No reason provided");
            }
            default -> {
                // Các trường hợp khác không cần xử lý đặc biệt
            }
        }
        
        ticket.setStatus(newStatus);
        
        // Log audit
        logAudit(
            ticket.getId(),
            mapStatusToAuditAction(newStatus),
            "status",
            oldStatus.name(),
            newStatus.name(),
            actorRole,
            actorName
        );
        
        // Gửi notification
        ticketNotificationService.notifyStatusChanged(ticket, oldStatus, newStatus, actorName);
        
        return ticket;
    }
    
    /**
     * Map ticket status sang audit action.
     */
    private TicketTypes.AuditAction mapStatusToAuditAction(TicketTypes.TicketStatus status) {
        return switch (status) {
            case RESOLVED -> TicketTypes.AuditAction.RESOLVED;
            case CLOSED -> TicketTypes.AuditAction.CLOSED;
            case REOPENED -> TicketTypes.AuditAction.REOPENED;
            case CANCELLED -> TicketTypes.AuditAction.CANCELLED;
            case ESCALATED -> TicketTypes.AuditAction.ESCALATED;
            case WAITING_FOR_USER -> TicketTypes.AuditAction.WAITING_FOR_INFO;
            default -> TicketTypes.AuditAction.STATUS_CHANGED;
        };
    }
    
    /**
     * Bắt đầu xử lý ticket (chuyển sang IN_PROGRESS).
     */
    public Ticket startProgress(
        Long id,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName
    ) {
        if (!canModifyTicketStatus(actorRole, actorDepartmentId)) {
            throw new TicketRuleViolationException("You don't have permission to modify ticket status.");
        }
        
        Ticket ticket = getTicket(id);
        TicketTypes.TicketStatus currentStatus = ticket.getStatus();
        
        // Validate transition
        if (!StatusTransitionValidator.isValidTransition(currentStatus, TicketTypes.TicketStatus.IN_PROGRESS)) {
            throw new TicketRuleViolationException(
                "Cannot start progress from status: " + currentStatus + 
                ". Valid transitions: " + StatusTransitionValidator.getValidNextStatuses(currentStatus)
            );
        }
        
        return performStatusChange(ticket, currentStatus, TicketTypes.TicketStatus.IN_PROGRESS, actorRole, actorName, null);
    }
    
    /**
     * Đánh dấu cần thông tin từ user.
     */
    public Ticket requestUserInfo(
        Long id,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName,
        String message
    ) {
        if (!canModifyTicketStatus(actorRole, actorDepartmentId)) {
            throw new TicketRuleViolationException("You don't have permission to modify ticket status.");
        }
        
        Ticket ticket = getTicket(id);
        TicketTypes.TicketStatus currentStatus = ticket.getStatus();
        
        if (!StatusTransitionValidator.isValidTransition(currentStatus, TicketTypes.TicketStatus.WAITING_FOR_USER)) {
            throw new TicketRuleViolationException(
                "Cannot request info from status: " + currentStatus
            );
        }
        
        return performStatusChange(ticket, currentStatus, TicketTypes.TicketStatus.WAITING_FOR_USER, actorRole, actorName, message);
    }
    
    /**
     * User cung cấp thông tin (chuyển từ WAITING_FOR_USER về IN_PROGRESS).
     */
    public Ticket provideUserInfo(
        Long id,
        UserRole.Role actorRole,
        String actorName
    ) {
        Ticket ticket = getTicket(id);
        TicketTypes.TicketStatus currentStatus = ticket.getStatus();
        
        if (currentStatus != TicketTypes.TicketStatus.WAITING_FOR_USER) {
            throw new TicketRuleViolationException("Ticket is not waiting for user info.");
        }
        
        // Validate transition
        if (!StatusTransitionValidator.isValidTransition(currentStatus, TicketTypes.TicketStatus.IN_PROGRESS)) {
            throw new TicketRuleViolationException(
                "Cannot provide info from status: " + currentStatus
            );
        }
        
        ticket = performStatusChange(ticket, currentStatus, TicketTypes.TicketStatus.IN_PROGRESS, actorRole, actorName, null);
        
        // Notify IT staff
        ticketNotificationService.notifyInfoProvided(ticket, actorName);
        
        return ticket;
    }
    
    /**
     * Resolve ticket.
     */
    public Ticket resolveTicket(
        Long id,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName,
        String resolution
    ) {
        if (!canModifyTicketStatus(actorRole, actorDepartmentId)) {
            throw new TicketRuleViolationException("You don't have permission to resolve tickets.");
        }
        
        Ticket ticket = getTicket(id);
        TicketTypes.TicketStatus currentStatus = ticket.getStatus();
        
        if (!StatusTransitionValidator.isValidTransition(currentStatus, TicketTypes.TicketStatus.RESOLVED)) {
            throw new TicketRuleViolationException(
                "Cannot resolve from status: " + currentStatus
            );
        }
        
        // Add resolution as internal comment if provided
        if (resolution != null && !resolution.isBlank()) {
            TicketComment comment = new TicketComment();
            comment.setTicketId(ticket.getId());
            comment.setVisibility(TicketTypes.CommentVisibility.INTERNAL);
            comment.setBody("Resolution: " + resolution);
            comment.setActorRole(actorRole.name());
            comment.setActorName(actorName);
            ticketCommentRepository.save(comment);
        }
        
        return performStatusChange(ticket, currentStatus, TicketTypes.TicketStatus.RESOLVED, actorRole, actorName, null);
    }
    
    /**
     * Close ticket (user confirm resolution).
     */
    public Ticket closeTicket(
        Long id,
        String actorUsername,
        UserRole.Role actorRole
    ) {
        Ticket ticket = getTicket(id);
        
        // User chỉ có thể close ticket của mình
        if (actorRole == UserRole.Role.NHAN_VIEN && 
            !actorUsername.equalsIgnoreCase(ticket.getRequesterUsername())) {
            throw new TicketRuleViolationException("You can only close your own tickets.");
        }
        
        // Non-IT staff chỉ có thể close RESOLVED tickets
        if (actorRole == UserRole.Role.NHAN_VIEN && 
            ticket.getStatus() != TicketTypes.TicketStatus.RESOLVED) {
            throw new TicketRuleViolationException("You can only close resolved tickets.");
        }
        
        TicketTypes.TicketStatus currentStatus = ticket.getStatus();
        
        if (!StatusTransitionValidator.isValidTransition(currentStatus, TicketTypes.TicketStatus.CLOSED)) {
            throw new TicketRuleViolationException(
                "Cannot close from status: " + currentStatus
            );
        }
        
        return performStatusChange(ticket, currentStatus, TicketTypes.TicketStatus.CLOSED, actorRole, actorUsername, null);
    }
    
    /**
     * Reopen ticket.
     */
    public Ticket reopenTicket(
        Long id,
        String actorUsername,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName,
        String reason
    ) {
        Ticket ticket = getTicket(id);
        
        // User chỉ có thể reopen ticket của mình
        if (actorRole == UserRole.Role.NHAN_VIEN && 
            !actorUsername.equalsIgnoreCase(ticket.getRequesterUsername())) {
            throw new TicketRuleViolationException("You can only reopen your own tickets.");
        }
        
        TicketTypes.TicketStatus currentStatus = ticket.getStatus();
        
        if (!StatusTransitionValidator.isValidTransition(currentStatus, TicketTypes.TicketStatus.REOPENED)) {
            throw new TicketRuleViolationException(
                "Cannot reopen from status: " + currentStatus + 
                ". Only CLOSED tickets can be reopened."
            );
        }
        
        // Add comment if reason provided
        if (reason != null && !reason.isBlank()) {
            TicketComment comment = new TicketComment();
            comment.setTicketId(ticket.getId());
            comment.setVisibility(TicketTypes.CommentVisibility.PUBLIC);
            comment.setBody("Reopen reason: " + reason);
            comment.setActorRole(actorRole.name());
            comment.setActorName(actorName);
            ticketCommentRepository.save(comment);
        }
        
        return performStatusChange(ticket, currentStatus, TicketTypes.TicketStatus.REOPENED, actorRole, actorName, null);
    }
    
    /**
     * Escalate ticket.
     */
    public Ticket escalateTicket(
        Long id,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName,
        String reason
    ) {
        if (!canModifyTicketStatus(actorRole, actorDepartmentId)) {
            throw new TicketRuleViolationException("You don't have permission to escalate tickets.");
        }
        
        Ticket ticket = getTicket(id);
        TicketTypes.TicketStatus currentStatus = ticket.getStatus();
        
        if (!StatusTransitionValidator.isValidTransition(currentStatus, TicketTypes.TicketStatus.ESCALATED)) {
            throw new TicketRuleViolationException(
                "Cannot escalate from status: " + currentStatus
            );
        }
        
        return performStatusChange(ticket, currentStatus, TicketTypes.TicketStatus.ESCALATED, actorRole, actorName, reason);
    }
    
    /**
     * Cancel ticket (Admin only).
     */
    public Ticket cancelTicket(
        Long id,
        UserRole.Role actorRole,
        String actorName,
        String reason
    ) {
        if (actorRole != UserRole.Role.ADMIN) {
            throw new TicketRuleViolationException("Only administrators can cancel tickets.");
        }
        
        Ticket ticket = getTicket(id);
        TicketTypes.TicketStatus currentStatus = ticket.getStatus();
        
        if (!StatusTransitionValidator.isValidTransition(currentStatus, TicketTypes.TicketStatus.CANCELLED)) {
            throw new TicketRuleViolationException(
                "Cannot cancel from status: " + currentStatus
            );
        }
        
        // Add comment if reason provided
        if (reason != null && !reason.isBlank()) {
            TicketComment comment = new TicketComment();
            comment.setTicketId(ticket.getId());
            comment.setVisibility(TicketTypes.CommentVisibility.INTERNAL);
            comment.setBody("Cancellation reason: " + reason);
            comment.setActorRole(actorRole.name());
            comment.setActorName(actorName);
            ticketCommentRepository.save(comment);
        }
        
        return performStatusChange(ticket, currentStatus, TicketTypes.TicketStatus.CANCELLED, actorRole, actorName, null);
    }
    
    /**
     * Get valid next statuses cho một ticket.
     */
    @Transactional(readOnly = true)
    public Set<TicketTypes.TicketStatus> getValidNextStatuses(Long ticketId) {
        Ticket ticket = getTicket(ticketId);
        Set<TicketTypes.TicketStatus> valid = StatusTransitionValidator.getValidNextStatuses(ticket.getStatus());
        
        // Filter out CANCELLED nếu không phải Admin
        // Sẽ được filter ở controller level
        
        return valid;
    }

    private boolean canModifyTicketStatus(UserRole.Role actorRole, Long actorDepartmentId) {
        return switch (actorRole) {
            case ADMIN, GIAM_DOC -> true;
            case TRUONG_PHONG -> {
                if (actorDepartmentId == null) {
                    yield false;
                }
                Department dept = departmentRepository.findById(actorDepartmentId).orElse(null);
                yield dept != null && "IT".equals(dept.getCode());
            }
            case NHAN_VIEN -> {
                if (actorDepartmentId == null) {
                    yield false;
                }
                Department dept = departmentRepository.findById(actorDepartmentId).orElse(null);
                yield dept != null && "IT".equals(dept.getCode());
            }
        };
    }

    // ============================================================
    // PRIORITY MANAGEMENT
    // ============================================================
    
    public Ticket updatePriority(
        Long id,
        TicketTypes.TicketPriority newPriority,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName
    ) {
        if (actorRole == UserRole.Role.NHAN_VIEN) {
            throw new TicketRuleViolationException("You cannot change ticket priority.");
        }

        if (!canModifyTicketStatus(actorRole, actorDepartmentId)) {
            throw new TicketRuleViolationException("You don't have permission to change priority.");
        }

        Ticket ticket = getTicket(id);
        TicketTypes.TicketPriority currentPriority = ticket.getPriority();
        
        // Cập nhật SLA nếu priority thay đổi
        ticket.setPriority(newPriority);
        setSLATimes(ticket); // Recalculate SLA
        
        logAudit(
            ticket.getId(),
            TicketTypes.AuditAction.PRIORITY_CHANGED,
            "priority",
            currentPriority == null ? null : currentPriority.name(),
            newPriority.name(),
            actorRole,
            actorName
        );
        return ticket;
    }
    
    // ============================================================
    // CATEGORY MANAGEMENT
    // ============================================================
    
    /**
     * Cập nhật category và subcategory của ticket.
     * Tự động cập nhật team nếu có mapping.
     */
    public Ticket updateCategory(
        Long id,
        Long categoryId,
        Long subcategoryId,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName
    ) {
        if (!canModifyTicketStatus(actorRole, actorDepartmentId)) {
            throw new TicketRuleViolationException("You don't have permission to change ticket category.");
        }

        Ticket ticket = getTicket(id);
        
        // Validate subcategory belongs to category
        Category newCategory = null;
        Category newSubcategory = null;
        
        if (categoryId != null) {
            newCategory = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new TicketRuleViolationException("Category not found: " + categoryId));
            
            // Validate là top-level category
            if (newCategory.getParent() != null) {
                throw new TicketRuleViolationException("Category must be a top-level category, not a subcategory.");
            }
        }
        
        if (subcategoryId != null) {
            newSubcategory = categoryRepository.findById(subcategoryId)
                .orElseThrow(() -> new TicketRuleViolationException("Subcategory not found: " + subcategoryId));
            
            // Validate subcategory thuộc về category
            if (newCategory == null || !newSubcategory.getParent().getId().equals(newCategory.getId())) {
                throw new TicketRuleViolationException("Subcategory does not belong to the selected category.");
            }
        }
        
        // Log old values
        Long oldCategoryId = ticket.getCategoryId();
        String oldCategoryName = ticket.getCategoryName();
        Long oldSubcategoryId = ticket.getSubcategoryId();
        String oldSubcategoryName = ticket.getSubcategoryName();
        
        // Update category
        ticket.setCategoryEntity(newCategory);
        ticket.setSubcategoryEntity(newSubcategory);
        
        // Auto-update team nếu có category change và team chưa được set
        if (oldCategoryId == null || !oldCategoryId.equals(categoryId)) {
            autoAssignTeam(ticket);
        }
        
        // Log audit
        String oldCatStr = oldCategoryName != null ? oldCategoryName : "None";
        String newCatStr = newCategory != null ? newCategory.getName() : "None";
        String oldSubcatStr = oldSubcategoryName != null ? oldSubcategoryName : "None";
        String newSubcatStr = newSubcategory != null ? newSubcategory.getName() : "None";
        
        logAudit(
            ticket.getId(),
            TicketTypes.AuditAction.CATEGORY_CHANGED,
            "category",
            oldCatStr + " / " + oldSubcatStr,
            newCatStr + " / " + newSubcatStr,
            actorRole,
            actorName
        );
        
        return ticket;
    }

    // ============================================================
    // COMMENTS
    // ============================================================
    
    public TicketComment addComment(
        Long ticketId,
        TicketTypes.CommentVisibility visibility,
        String body,
        UserRole.Role actorRole,
        String actorName
    ) {
        if (visibility == TicketTypes.CommentVisibility.INTERNAL
            && actorRole == UserRole.Role.NHAN_VIEN) {
            throw new TicketRuleViolationException("Only IT staff can add internal comments.");
        }

        Ticket ticket = getTicket(ticketId);
        TicketComment comment = new TicketComment();
        comment.setTicketId(ticket.getId());
        comment.setVisibility(visibility);
        comment.setBody(body);
        comment.setActorRole(actorRole.name());
        comment.setActorName(actorName);
        TicketComment saved = ticketCommentRepository.save(comment);

        logAudit(
            ticket.getId(),
            TicketTypes.AuditAction.COMMENT_ADDED,
            "comment",
            null,
            buildCommentAuditValue(visibility, body),
            actorRole,
            actorName
        );
        
        // Gửi notification về comment mới
        ticketNotificationService.notifyCommentAdded(ticket, body, actorName, 
            visibility == TicketTypes.CommentVisibility.INTERNAL);
        
        return saved;
    }

    @Transactional(readOnly = true)
    public List<TicketComment> listComments(
        Long ticketId,
        UserRole.Role actorRole,
        TicketTypes.CommentVisibility visibility
    ) {
        getTicket(ticketId);
        if (actorRole == UserRole.Role.NHAN_VIEN) {
            return ticketCommentRepository.findByTicketIdAndVisibilityOrderByCreatedAtDesc(
                ticketId,
                TicketTypes.CommentVisibility.PUBLIC
            );
        }
        if (visibility != null) {
            return ticketCommentRepository.findByTicketIdAndVisibilityOrderByCreatedAtDesc(
                ticketId,
                visibility
            );
        }
        return ticketCommentRepository.findByTicketIdOrderByCreatedAtDesc(ticketId);
    }

    // ============================================================
    // AUDIT & ASSIGNMENTS
    // ============================================================
    
    @Transactional(readOnly = true)
    public List<TicketAssignment> listAssignments(Long ticketId) {
        getTicket(ticketId);
        return ticketAssignmentRepository.findByTicketIdOrderByCreatedAtDesc(ticketId);
    }
    
    @Transactional(readOnly = true)
    public List<TicketAudit> listAudit(Long ticketId) {
        getTicket(ticketId);
        return ticketAuditRepository.findByTicketIdOrderByCreatedAtDesc(ticketId);
    }
    
    @Transactional(readOnly = true)
    public List<Ticket> listUnassignedQueue() {
        return ticketRepository.findByAssigneeNameIsNullOrAssigneeName("", Pageable.unpaged()).stream()
            .filter(ticket -> ticket.getStatus() != TicketTypes.TicketStatus.CLOSED)
            .toList();
    }

    // ============================================================
    // REPORTS (Giữ nguyên từ code cũ)
    // ============================================================
    
    @Transactional(readOnly = true)
    public TicketDtos.TicketCountReport getTicketCounts(String requesterUsername) {
        long total = requesterUsername == null
            ? ticketRepository.count()
            : ticketRepository.countByRequesterUsername(requesterUsername);
        List<TicketDtos.TicketStatusCountResponse> byStatus = java.util.Arrays.stream(TicketTypes.TicketStatus.values())
            .map(status -> new TicketDtos.TicketStatusCountResponse(
                status,
                requesterUsername == null
                    ? ticketRepository.countByStatus(status)
                    : ticketRepository.countByRequesterUsernameAndStatus(requesterUsername, status)
            ))
            .toList();
        return new TicketDtos.TicketCountReport(total, byStatus);
    }

    @Transactional(readOnly = true)
    public List<TicketDtos.EngineerReportRow> buildEngineerReport(LocalDateTime from, LocalDateTime to) {
        Department itDept = departmentRepository.findByCode("IT").orElse(null);
        List<UserAccount> itStaff;
        if (itDept != null) {
            itStaff = userAccountRepository.findByDepartmentIdAndEnabledTrueOrderByUsernameAsc(itDept.getId());
        } else {
            itStaff = List.of();
        }

        List<TicketAssignment> assignments = ticketAssignmentRepository.findByCreatedAtBetween(from, to);
        List<Ticket> closedTickets = ticketRepository.findByClosedAtBetween(from, to);
        double days = Math.max(1.0, Duration.between(from, to).toHours() / 24.0);

        Map<String, Set<Long>> assignedTicketIds = assignments.stream()
            .filter(assignment -> assignment.getNewAssignee() != null && !assignment.getNewAssignee().isBlank())
            .collect(Collectors.groupingBy(
                TicketAssignment::getNewAssignee,
                Collectors.mapping(TicketAssignment::getTicketId, Collectors.toSet())
            ));

        Map<String, List<Ticket>> closedByEngineer = closedTickets.stream()
            .filter(ticket -> ticket.getAssigneeName() != null && !ticket.getAssigneeName().isBlank())
            .collect(Collectors.groupingBy(Ticket::getAssigneeName));

        return itStaff.stream()
            .map(engineer -> {
                String name = engineer.getUsername();
                Set<Long> assignedIds = assignedTicketIds.getOrDefault(name, Set.of());
                List<Ticket> closed = closedByEngineer.getOrDefault(name, List.of());
                long assignedCount = assignedIds.size();
                long completedCount = closed.size();
                double avgAssignedPerDay = assignedCount / days;
                double avgCompletedPerDay = completedCount / days;
                double avgCompletionHours = closed.stream()
                    .filter(ticket -> ticket.getCreatedAt() != null && ticket.getClosedAt() != null)
                    .mapToDouble(ticket -> Duration.between(ticket.getCreatedAt(), ticket.getClosedAt()).toMinutes() / 60.0)
                    .average()
                    .orElse(0);
                return new TicketDtos.EngineerReportRow(
                    name,
                    assignedCount,
                    completedCount,
                    avgAssignedPerDay,
                    avgCompletedPerDay,
                    avgCompletionHours
                );
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public List<TicketDtos.RequesterReportRow> buildRequesterReport(LocalDateTime from, LocalDateTime to) {
        List<UserAccount> requesters = userAccountRepository.findByRoleAndEnabledTrueOrderByUsernameAsc(UserRole.Role.NHAN_VIEN);
        List<Ticket> createdTickets = ticketRepository.findByCreatedAtBetween(from, to);
        List<Ticket> closedTickets = ticketRepository.findByClosedAtBetween(from, to);

        Map<String, Long> submittedCounts = createdTickets.stream()
            .filter(ticket -> ticket.getRequesterUsername() != null && !ticket.getRequesterUsername().isBlank())
            .collect(Collectors.groupingBy(Ticket::getRequesterUsername, Collectors.counting()));

        Map<String, List<Ticket>> closedByRequester = closedTickets.stream()
            .filter(ticket -> ticket.getRequesterUsername() != null && !ticket.getRequesterUsername().isBlank())
            .collect(Collectors.groupingBy(Ticket::getRequesterUsername));

        return requesters.stream()
            .map(requester -> {
                String name = requester.getUsername();
                long submitted = submittedCounts.getOrDefault(name, 0L);
                double avgCompletionHours = closedByRequester.getOrDefault(name, List.of()).stream()
                    .filter(ticket -> ticket.getCreatedAt() != null && ticket.getClosedAt() != null)
                    .mapToDouble(ticket -> Duration.between(ticket.getCreatedAt(), ticket.getClosedAt()).toMinutes() / 60.0)
                    .average()
                    .orElse(0);
                return new TicketDtos.RequesterReportRow(name, submitted, avgCompletionHours);
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public TicketDtos.DashboardSummary buildDashboardSummary(
        String requesterUsername,
        LocalDateTime from,
        LocalDateTime to
    ) {
        List<Ticket> tickets = ticketRepository.findAll();
        if (requesterUsername != null) {
            tickets = tickets.stream()
                .filter(ticket -> requesterUsername.equalsIgnoreCase(ticket.getRequesterUsername()))
                .toList();
        }
        LocalDateTime overdueThreshold = LocalDateTime.now().minusDays(7);
        long openCount = tickets.stream()
            .filter(ticket -> ticket.getStatus() != TicketTypes.TicketStatus.CLOSED)
            .count();
        long overdueCount = tickets.stream()
            .filter(ticket -> ticket.getStatus() != TicketTypes.TicketStatus.CLOSED)
            .filter(ticket -> ticket.getCreatedAt() != null && ticket.getCreatedAt().isBefore(overdueThreshold))
            .count();
        List<Ticket> closedInRange = tickets.stream()
            .filter(ticket -> ticket.getClosedAt() != null)
            .filter(ticket -> !ticket.getClosedAt().isBefore(from) && !ticket.getClosedAt().isAfter(to))
            .toList();
        long closedCount = closedInRange.size();
        double avgCompletionHours = closedInRange.stream()
            .filter(ticket -> ticket.getCreatedAt() != null)
            .mapToDouble(ticket -> Duration.between(ticket.getCreatedAt(), ticket.getClosedAt()).toMinutes() / 60.0)
            .average()
            .orElse(0);
        List<TicketDtos.TicketStatusCountResponse> openByStatus = tickets.stream()
            .filter(ticket -> ticket.getStatus() != TicketTypes.TicketStatus.CLOSED)
            .collect(Collectors.groupingBy(Ticket::getStatus, Collectors.counting()))
            .entrySet()
            .stream()
            .map(entry -> new TicketDtos.TicketStatusCountResponse(entry.getKey(), entry.getValue()))
            .toList();
        
        // Extended dashboard stats
        TicketDtos.DashboardSummary summary = new TicketDtos.DashboardSummary(
            openCount,
            closedCount,
            overdueCount,
            avgCompletionHours,
            openByStatus
        );
        
        // Count by specific statuses
        summary.setNewCount(tickets.stream().filter(t -> t.getStatus() == TicketTypes.TicketStatus.NEW).count());
        summary.setAssignedCount(tickets.stream().filter(t -> t.getStatus() == TicketTypes.TicketStatus.ASSIGNED).count());
        summary.setInProgressCount(tickets.stream().filter(t -> t.getStatus() == TicketTypes.TicketStatus.IN_PROGRESS).count());
        summary.setWaitingCount(tickets.stream().filter(t -> t.getStatus() == TicketTypes.TicketStatus.WAITING_FOR_USER).count());
        summary.setSlaBreachedCount(tickets.stream().filter(Ticket::isResolutionSLABreached).count());
        
        return summary;
    }

    @Transactional(readOnly = true)
    public List<TicketDtos.BacklogAgingRow> buildBacklogAging(LocalDateTime from, LocalDateTime to) {
        LocalDateTime now = LocalDateTime.now();
        List<Ticket> openTickets = ticketRepository.findAll().stream()
            .filter(ticket -> ticket.getStatus() != TicketTypes.TicketStatus.CLOSED)
            .filter(ticket -> ticket.getCreatedAt() != null)
            .filter(ticket -> !ticket.getCreatedAt().isBefore(from) && !ticket.getCreatedAt().isAfter(to))
            .toList();
        Map<TicketTypes.TicketStatus, List<Ticket>> grouped = openTickets.stream()
            .collect(Collectors.groupingBy(Ticket::getStatus));
        return grouped.entrySet().stream()
            .map(entry -> {
                List<Ticket> tickets = entry.getValue();
                double avgAgeHours = tickets.stream()
                    .mapToDouble(ticket -> Duration.between(ticket.getCreatedAt(), now).toMinutes() / 60.0)
                    .average()
                    .orElse(0);
                return new TicketDtos.BacklogAgingRow(entry.getKey(), tickets.size(), avgAgeHours);
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public List<TicketDtos.SlaBucketRow> buildSlaBuckets(LocalDateTime from, LocalDateTime to) {
        LocalDateTime now = LocalDateTime.now();
        List<Ticket> openTickets = ticketRepository.findAll().stream()
            .filter(ticket -> ticket.getStatus() != TicketTypes.TicketStatus.CLOSED)
            .filter(ticket -> ticket.getCreatedAt() != null)
            .filter(ticket -> !ticket.getCreatedAt().isBefore(from) && !ticket.getCreatedAt().isAfter(to))
            .toList();
        long bucket0to2 = openTickets.stream()
            .filter(ticket -> Duration.between(ticket.getCreatedAt(), now).toHours() <= 48)
            .count();
        long bucket3to7 = openTickets.stream()
            .filter(ticket -> {
                long hours = Duration.between(ticket.getCreatedAt(), now).toHours();
                return hours > 48 && hours <= 168;
            })
            .count();
        long bucket8plus = openTickets.stream()
            .filter(ticket -> Duration.between(ticket.getCreatedAt(), now).toHours() > 168)
            .count();
        return List.of(
            new TicketDtos.SlaBucketRow("0-2 days", bucket0to2),
            new TicketDtos.SlaBucketRow("3-7 days", bucket3to7),
            new TicketDtos.SlaBucketRow("8+ days", bucket8plus)
        );
    }

    // ============================================================
    // PRIVATE HELPERS
    // ============================================================
    
    private void logAudit(
        Long ticketId,
        TicketTypes.AuditAction action,
        String fieldName,
        String oldValue,
        String newValue,
        UserRole.Role actorRole,
        String actorName
    ) {
        TicketAudit audit = new TicketAudit();
        audit.setTicketId(ticketId);
        audit.setAction(action);
        audit.setFieldName(fieldName);
        audit.setOldValue(oldValue);
        audit.setNewValue(newValue);
        audit.setActorRole(actorRole.name());
        audit.setActorName(actorName);
        ticketAuditRepository.save(audit);
    }

    private String buildCommentAuditValue(TicketTypes.CommentVisibility visibility, String body) {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.length() > 400) {
            trimmed = trimmed.substring(0, 400) + "...";
        }
        return visibility.name() + ":" + trimmed;
    }

    private String normalizeAssignee(String assigneeName) {
        if (assigneeName == null) {
            return null;
        }
        String trimmed = assigneeName.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String generateTicketNumber() {
        return "TCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
            .toUpperCase();
    }
}

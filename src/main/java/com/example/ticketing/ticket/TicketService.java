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
import com.example.ticketing.department.Department;
import com.example.ticketing.department.DepartmentRepository;

import org.springframework.http.HttpStatus;

@Service
@Transactional
public class TicketService {
    private final TicketRepository ticketRepository;
    private final TicketAssignmentRepository ticketAssignmentRepository;
    private final TicketAuditRepository ticketAuditRepository;
    private final TicketCommentRepository ticketCommentRepository;
    private final UserAccountRepository userAccountRepository;
    private final DepartmentRepository departmentRepository;

    public TicketService(
        TicketRepository ticketRepository,
        TicketAssignmentRepository ticketAssignmentRepository,
        TicketAuditRepository ticketAuditRepository,
        TicketCommentRepository ticketCommentRepository,
        UserAccountRepository userAccountRepository,
        DepartmentRepository departmentRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketAssignmentRepository = ticketAssignmentRepository;
        this.ticketAuditRepository = ticketAuditRepository;
        this.ticketCommentRepository = ticketCommentRepository;
        this.userAccountRepository = userAccountRepository;
        this.departmentRepository = departmentRepository;
    }

    /**
     * Tạo ticket - auto-assign department từ requester
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

        // NHAN_VIEN không thể tự assign, để null
        if (actorRole == UserRole.Role.NHAN_VIEN) {
            ticket.setAssigneeName(null);
        } else {
            ticket.setAssigneeName(normalizeAssignee(ticket.getAssigneeName()));
        }

        Ticket created = ticketRepository.save(ticket);
        logAudit(
            created.getId(),
            TicketTypes.AuditAction.CREATED,
            "ticket",
            null,
            created.getTicketNumber(),
            actorRole,
            actorUsername
        );
        return created;
    }

    /**
     * Lấy tickets với department filter
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

        // NHAN_VIEN và TRUONG_PHONG thấy tất cả tickets (không filter theo department)
        // Department filter chỉ áp dụng cho ADMIN và GIAM_DOC
        if ("NHAN_VIEN".equals(userRole) || "TRUONG_PHONG".equals(userRole)) {
            // Use the same filtering logic as ADMIN/GIAM_DOC but without department restriction
            // Fall through to the common logic below
        }

        // GIAM_DOC và ADMIN thấy tất cả
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

    private Page<Ticket> listTicketsForNhanVien(
        TicketTypes.TicketStatus status,
        String search,
        boolean excludeClosed,
        Pageable pageable,
        Long departmentId,
        String assigneeName
    ) {
        if (departmentId == null) {
            return Page.empty();
        }

        String normalizedAssignee = normalizeAssignee(assigneeName);
        boolean isUnassignedFilter = assigneeName != null && assigneeName.equalsIgnoreCase("UNASSIGNED");
        boolean hasSearch = search != null && !search.isBlank();
        boolean applyExcludeClosed = excludeClosed && status == null;

        // Handle UNASSIGNED filter: show tickets where assigneeName is null or empty
        if (isUnassignedFilter) {
            if (hasSearch) {
                // Search within unassigned tickets
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                if (applyExcludeClosed) {
                    return ticketRepository.findByDepartmentIdAndStatusNot(departmentId, TicketTypes.TicketStatus.CLOSED, pageable);
                }
                return ticketRepository.findUnassignedByDepartmentIdAndStatus(departmentId, status, pageable);
            }
            if (status != null) {
                return ticketRepository.findUnassignedByDepartmentIdAndStatus(departmentId, status, pageable);
            }
            if (applyExcludeClosed) {
                return ticketRepository.findUnassignedByDepartmentIdAndStatusNot(departmentId, TicketTypes.TicketStatus.CLOSED, pageable);
            }
            return ticketRepository.findUnassignedByDepartmentId(departmentId, pageable);
        }

        if (hasSearch) {
            if (applyExcludeClosed) {
                return ticketRepository.searchTicketsByDepartmentExcludeStatus(
                    departmentId, search.trim(), normalizedAssignee, TicketTypes.TicketStatus.CLOSED, pageable);
            }
            return ticketRepository.searchTicketsByDepartment(
                departmentId, search.trim(), status, normalizedAssignee, pageable);
        }

        if (normalizedAssignee != null && status != null) {
            return ticketRepository.findByDepartmentIdAndAssigneeNameAndStatus(departmentId, normalizedAssignee, status, pageable);
        }

        if (normalizedAssignee != null) {
            return ticketRepository.findByDepartmentIdAndAssigneeName(departmentId, normalizedAssignee, pageable);
        }

        if (status != null) {
            return ticketRepository.findByDepartmentIdAndStatus(departmentId, status, pageable);
        }

        if (applyExcludeClosed) {
            return ticketRepository.findByDepartmentIdAndStatusNot(departmentId, TicketTypes.TicketStatus.CLOSED, pageable);
        }

        return ticketRepository.findByDepartmentId(departmentId, pageable);
    }

    private Page<Ticket> listTicketsForTruongPhong(
        TicketTypes.TicketStatus status,
        String search,
        boolean excludeClosed,
        Pageable pageable,
        Long departmentId,
        String assigneeName
    ) {
        if (departmentId == null) {
            return Page.empty();
        }

        String normalizedAssignee = normalizeAssignee(assigneeName);
        boolean isUnassignedFilter = assigneeName != null && assigneeName.equalsIgnoreCase("UNASSIGNED");
        boolean hasSearch = search != null && !search.isBlank();
        boolean applyExcludeClosed = excludeClosed && status == null;

        // Handle UNASSIGNED filter: show tickets where assigneeName is null or empty
        if (isUnassignedFilter) {
            if (hasSearch) {
                // Search within unassigned tickets
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                if (applyExcludeClosed) {
                    return ticketRepository.findByDepartmentIdAndStatusNot(departmentId, TicketTypes.TicketStatus.CLOSED, pageable);
                }
                return ticketRepository.findUnassignedByDepartmentIdAndStatus(departmentId, status, pageable);
            }
            if (status != null) {
                return ticketRepository.findUnassignedByDepartmentIdAndStatus(departmentId, status, pageable);
            }
            if (applyExcludeClosed) {
                return ticketRepository.findUnassignedByDepartmentIdAndStatusNot(departmentId, TicketTypes.TicketStatus.CLOSED, pageable);
            }
            return ticketRepository.findUnassignedByDepartmentId(departmentId, pageable);
        }

        if (hasSearch) {
            if (applyExcludeClosed) {
                return ticketRepository.searchTicketsByDepartmentExcludeStatus(
                    departmentId, search.trim(), normalizedAssignee, TicketTypes.TicketStatus.CLOSED, pageable);
            }
            return ticketRepository.searchTicketsByDepartment(
                departmentId, search.trim(), status, normalizedAssignee, pageable);
        }

        if (normalizedAssignee != null && status != null) {
            return ticketRepository.findByDepartmentIdAndAssigneeNameAndStatus(departmentId, normalizedAssignee, status, pageable);
        }

        if (normalizedAssignee != null) {
            return ticketRepository.findByDepartmentIdAndAssigneeName(departmentId, normalizedAssignee, pageable);
        }

        if (status != null) {
            return ticketRepository.findByDepartmentIdAndStatus(departmentId, status, pageable);
        }

        if (applyExcludeClosed) {
            return ticketRepository.findByDepartmentIdAndStatusNot(departmentId, TicketTypes.TicketStatus.CLOSED, pageable);
        }

        return ticketRepository.findByDepartmentId(departmentId, pageable);
    }

    @Transactional(readOnly = true)
    public Ticket getTicket(Long id) {
        return ticketRepository.findById(id)
            .orElseThrow(() -> new TicketNotFoundException(id));
    }

    /**
     * Assign ticket - chỉ TRUONG_PHONG (đặc biệt IT) và ADMIN/GIAM_DOC
     */
    public Ticket assignTicket(
        Long id,
        String newAssignee,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName
    ) {
        // Check permission: chỉ ADMIN, GIAM_DOC, hoặc TRUONG_PHONG IT mới có thể assign
        if (!canAssignTickets(actorRole, actorDepartmentId)) {
            throw new TicketRuleViolationException("You don't have permission to assign tickets.");
        }

        Ticket ticket = getTicket(id);
        String previousAssignee = ticket.getAssigneeName();
        String normalizedAssignee = normalizeAssignee(newAssignee);
        ticket.setAssigneeName(normalizedAssignee);

        // Tự động chuyển status sang IN_PROGRESS khi assign
        if (ticket.getStatus() == TicketTypes.TicketStatus.NEW) {
            ticket.setStatus(TicketTypes.TicketStatus.IN_PROGRESS);
        }

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
            normalizedAssignee,
            actorRole,
            actorName
        );

        return ticket;
    }

    /**
     * Kiểm tra có quyền assign tickets không
     */
    private boolean canAssignTickets(UserRole.Role actorRole, Long actorDepartmentId) {
        return switch (actorRole) {
            case ADMIN, GIAM_DOC -> true;
            case TRUONG_PHONG -> {
                // Chỉ TRUONG_PHONG IT mới có quyền assign
                if (actorDepartmentId == null) {
                    yield false;
                }
                Department dept = departmentRepository.findById(actorDepartmentId).orElse(null);
                yield dept != null && "IT".equals(dept.getCode());
            }
            case NHAN_VIEN -> false;
        };
    }

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
        // Lấy tất cả IT staff (NHAN_VIEN và TRUONG_PHONG trong IT department)
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
        // Lấy tất cả NHAN_VIEN
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
    public List<Ticket> listUnassignedQueue() {
        return ticketRepository.findByAssigneeNameIsNullOrAssigneeName("", Pageable.unpaged()).stream()
            .filter(ticket -> ticket.getStatus() != TicketTypes.TicketStatus.CLOSED)
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
        return new TicketDtos.DashboardSummary(
            openCount,
            closedCount,
            overdueCount,
            avgCompletionHours,
            openByStatus
        );
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

    @Transactional(readOnly = true)
    public List<TicketAssignment> listAssignments(Long ticketId) {
        getTicket(ticketId);
        return ticketAssignmentRepository.findByTicketIdOrderByCreatedAtDesc(ticketId);
    }

    public TicketComment addComment(
        Long ticketId,
        TicketTypes.CommentVisibility visibility,
        String body,
        UserRole.Role actorRole,
        String actorName
    ) {
        // NHAN_VIEN không thể add internal comment
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
        return saved;
    }

    @Transactional(readOnly = true)
    public List<TicketComment> listComments(
        Long ticketId,
        UserRole.Role actorRole,
        TicketTypes.CommentVisibility visibility
    ) {
        getTicket(ticketId);
        // NHAN_VIEN chỉ thấy PUBLIC comments
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

    public Ticket updateStatus(
        Long id,
        TicketTypes.TicketStatus newStatus,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName,
        String actorUsername
    ) {
        Ticket ticket = getTicket(id);
        TicketTypes.TicketStatus currentStatus = ticket.getStatus();

        if (currentStatus == TicketTypes.TicketStatus.CLOSED) {
            throw new TicketRuleViolationException("Closed tickets cannot be modified.");
        }

        // NHAN_VIEN chỉ có thể đóng ticket của mình
        if (actorRole == UserRole.Role.NHAN_VIEN) {
            if (!actorUsername.equalsIgnoreCase(ticket.getRequesterUsername())) {
                throw new TicketRuleViolationException("You can only close your own tickets.");
            }
            if (newStatus != TicketTypes.TicketStatus.CLOSED) {
                throw new TicketRuleViolationException("You can only close tickets.");
            }
            ticket.setClosedAt(LocalDateTime.now());
            ticket.setStatus(newStatus);
            logAudit(
                ticket.getId(),
                TicketTypes.AuditAction.STATUS_CHANGED,
                "status",
                currentStatus.name(),
                newStatus.name(),
                actorRole,
                actorName
            );
            return ticket;
        }

        // Chỉ IT staff (NHAN_VIEN in IT, TRUONG_PHONG IT) và ADMIN/GIAM_DOC mới có thể chuyển ticket từ NEW
        if (currentStatus == TicketTypes.TicketStatus.NEW) {
            if (!canModifyTicketStatus(actorRole, actorDepartmentId)) {
                throw new TicketRuleViolationException("You don't have permission to move tickets out of NEW status.");
            }
        }

        if (newStatus == TicketTypes.TicketStatus.CLOSED) {
            ticket.setClosedAt(LocalDateTime.now());
            if (ticket.getResolvedAt() == null) {
                ticket.setResolvedAt(ticket.getClosedAt());
            }
        }

        ticket.setStatus(newStatus);
        logAudit(
            ticket.getId(),
            TicketTypes.AuditAction.STATUS_CHANGED,
            "status",
            currentStatus.name(),
            newStatus.name(),
            actorRole,
            actorName
        );
        return ticket;
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

    public Ticket updatePriority(
        Long id,
        TicketTypes.TicketPriority newPriority,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName
    ) {
        // NHAN_VIEN không thể đổi priority
        if (actorRole == UserRole.Role.NHAN_VIEN) {
            throw new TicketRuleViolationException("You cannot change ticket priority.");
        }

        // Check permission
        if (!canModifyTicketStatus(actorRole, actorDepartmentId)) {
            throw new TicketRuleViolationException("You don't have permission to change priority.");
        }

        Ticket ticket = getTicket(id);
        TicketTypes.TicketPriority currentPriority = ticket.getPriority();
        ticket.setPriority(newPriority);
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

    @Transactional(readOnly = true)
    public List<TicketAudit> listAudit(Long ticketId) {
        getTicket(ticketId);
        return ticketAuditRepository.findByTicketIdOrderByCreatedAtDesc(ticketId);
    }

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

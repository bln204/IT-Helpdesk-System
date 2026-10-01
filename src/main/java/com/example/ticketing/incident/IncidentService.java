package com.example.ticketing.incident;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.team.Team;
import com.example.ticketing.team.TeamRepository;
import com.example.ticketing.ticket.Ticket;
import com.example.ticketing.ticket.TicketRepository;
import com.example.ticketing.ticket.TicketNotFoundException;
import com.example.ticketing.ticket.TicketTypes.TicketPriority;
import com.example.ticketing.sla.SlaPolicyService;

/**
 * Service cho Incident Management.
 */
@Service
@Transactional
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private final IncidentRepository incidentRepository;
    private final TicketIncidentLinkRepository linkRepository;
    private final IncidentTimelineRepository timelineRepository;
    private final TicketRepository ticketRepository;
    private final UserAccountRepository userAccountRepository;
    private final TeamRepository teamRepository;
    private final SlaPolicyService slaPolicyService;
    private final IncidentNotificationService incidentNotificationService;

    public IncidentService(
            IncidentRepository incidentRepository,
            TicketIncidentLinkRepository linkRepository,
            IncidentTimelineRepository timelineRepository,
            TicketRepository ticketRepository,
            UserAccountRepository userAccountRepository,
            TeamRepository teamRepository,
            SlaPolicyService slaPolicyService,
            IncidentNotificationService incidentNotificationService) {
        this.incidentRepository = incidentRepository;
        this.linkRepository = linkRepository;
        this.timelineRepository = timelineRepository;
        this.ticketRepository = ticketRepository;
        this.userAccountRepository = userAccountRepository;
        this.teamRepository = teamRepository;
        this.slaPolicyService = slaPolicyService;
        this.incidentNotificationService = incidentNotificationService;
    }

    // ==================== CRUD Operations ====================

    /**
     * Tạo incident mới.
     */
    public Incident createIncident(Incident incident, String createdBy) {
        log.info("Creating new incident: {}", incident.getTitle());

        // Generate incident number
        incident.setIncidentNumber(generateIncidentNumber());
        incident.setStatus(Incident.IncidentStatus.INVESTIGATING);
        incident.setCreatedBy(createdBy);
        
        // Set reportedByUsername nếu chưa có
        if (incident.getReportedByUsername() == null || incident.getReportedByUsername().isBlank()) {
            incident.setReportedByUsername(createdBy);
        }

        // Calculate SLA deadlines based on priority
        if (incident.getPriority() != null) {
            calculateAndSetSlaDeadlines(incident);
        }

        Incident saved = incidentRepository.save(incident);

        // Log timeline
        logTimeline(saved, IncidentTimeline.EventType.CREATED,
                "Incident được tạo", createdBy, null, null);

        // Gửi notification về incident mới
        incidentNotificationService.notifyIncidentCreated(saved);

        return saved;
    }
    
    /**
     * Tính và set SLA deadlines cho incident dựa trên priority.
     */
    private void calculateAndSetSlaDeadlines(Incident incident) {
        SlaPolicyService.SlaDeadline deadline = slaPolicyService.calculateSlaDeadlines(
                incident.getPriority(), 
                LocalDateTime.now()
        );
        
        incident.setResponseDeadline(deadline.getResponseDeadline());
        incident.setResolutionDeadline(deadline.getResolutionDeadline());
        incident.setSlaResponseMinutes(deadline.getResponseMinutes());
        incident.setSlaResolutionMinutes(deadline.getResolutionMinutes());
        incident.setSlaPolicyName("SLA Policy");
    }

    /**
     * Lấy incident theo ID.
     */
    @Transactional(readOnly = true)
    public Incident getIncidentById(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new IncidentNotFoundException(id));
    }

    /**
     * Lấy incident theo incident number.
     */
    @Transactional(readOnly = true)
    public Incident getIncidentByNumber(String incidentNumber) {
        return incidentRepository.findByIncidentNumber(incidentNumber)
                .orElseThrow(() -> new IncidentNotFoundException(incidentNumber));
    }

    /**
     * Cập nhật incident.
     */
    public Incident updateIncident(Long id, Incident updates, String updatedBy) {
        Incident existing = getIncidentById(id);

        existing.setTitle(updates.getTitle());
        existing.setDescription(updates.getDescription());
        existing.setPriority(updates.getPriority());
        existing.setImpactLevel(updates.getImpactLevel());
        existing.setAffectedUsers(updates.getAffectedUsers());
        existing.setRootCause(updates.getRootCause());
        existing.setWorkaround(updates.getWorkaround());
        existing.setResolution(updates.getResolution());
        existing.setUpdatedBy(updatedBy);

        return incidentRepository.save(existing);
    }

    /**
     * Xóa incident.
     * Chỉ ADMIN, TRUONG_PHONG IT, hoặc người tạo mới được xóa.
     */
    public void deleteIncident(Long id, String username, String userRole) {
        log.info("Delete incident: {} by user: {} ({})", id, username, userRole);
        
        Incident incident = getIncidentById(id);
        
        // Check permission: ADMIN, TRUONG_PHONG IT, hoặc người tạo
        boolean isCreator = incident.getCreatedBy() != null && 
                           incident.getCreatedBy().equals(username);
        boolean isAdmin = "ADMIN".equals(userRole);
        
        // TRUONG_PHONG chỉ xóa được nếu thuộc phòng IT
        boolean isTruongPhongIT = false;
        if ("TRUONG_PHONG".equals(userRole)) {
            UserAccount user = userAccountRepository.findByUsername(username).orElse(null);
            if (user != null && user.getDepartment() != null) {
                String deptName = user.getDepartment().getName().toUpperCase();
                if (deptName.contains("IT") || deptName.contains("INFORMATION")) {
                    isTruongPhongIT = true;
                }
            }
        }
        
        if (!isAdmin && !isCreator && !isTruongPhongIT) {
            throw new SecurityException("Bạn không có quyền xóa incident này");
        }
        
        incidentRepository.deleteById(id);
    }

    // ==================== Status Management ====================

    /**
     * Cập nhật trạng thái incident.
     */
    public Incident updateStatus(Long id, Incident.IncidentStatus newStatus, String actorName) {
        Incident incident = getIncidentById(id);
        Incident.IncidentStatus oldStatus = incident.getStatus();

        incident.setStatus(newStatus);

        // Set timestamps
        switch (newStatus) {
            case IDENTIFIED:
                incident.setIdentifiedAt(LocalDateTime.now());
                break;
            case RESOLVED:
                incident.setResolvedAt(LocalDateTime.now());
                break;
            case CLOSED:
                incident.setClosedAt(LocalDateTime.now());
                if (incident.getResolvedAt() == null) {
                    incident.setResolvedAt(LocalDateTime.now());
                }
                break;
            default:
                break;
        }

        Incident saved = incidentRepository.save(incident);

        // Log timeline
        logTimeline(saved, IncidentTimeline.EventType.STATUS_CHANGED,
                "Trạng thái thay đổi: " + oldStatus + " → " + newStatus,
                actorName, oldStatus.name(), newStatus.name());

        // Gửi notification
        incidentNotificationService.notifyStatusChanged(saved, oldStatus, newStatus, actorName);

        return saved;
    }

    /**
     * Assign incident cho user.
     */
    public Incident assignIncident(Long id, String assigneeUsername, String actorName) {
        Incident incident = getIncidentById(id);
        String oldAssignee = incident.getAssignedTo() != null ? 
                incident.getAssignedTo().getUsername() : null;

        UserAccount assignee = userAccountRepository.findByUsername(assigneeUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + assigneeUsername));

        incident.setAssignedTo(assignee);

        Incident saved = incidentRepository.save(incident);

        // Log timeline
        logTimeline(saved, IncidentTimeline.EventType.ASSIGNEE_CHANGED,
                "Người phụ trách thay đổi: " + oldAssignee + " → " + assigneeUsername,
                actorName, oldAssignee, assigneeUsername);

        // Gửi notification
        if (oldAssignee != null && !oldAssignee.equals(assigneeUsername)) {
            // Reassignment
            incidentNotificationService.notifyIncidentReassigned(saved, oldAssignee, assigneeUsername, actorName);
        } else {
            // New assignment
            incidentNotificationService.notifyIncidentAssigned(saved, actorName);
        }

        return saved;
    }

    /**
     * IT Staff nhận incident (Take Ownership).
     * - Assign cho user hiện tại
     * - Đổi status từ INVESTIGATING → IN_PROGRESS
     */
    public Incident takeOwnership(Long id, String username) {
        Incident incident = getIncidentById(id);

        // Get current user
        UserAccount user = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        
        Incident.IncidentStatus oldStatus = incident.getStatus();
        String oldAssignee = incident.getAssignedTo() != null ? 
                incident.getAssignedTo().getUsername() : null;

        // Assign to current user
        incident.setAssignedTo(user);
        incident.setStatus(Incident.IncidentStatus.IN_PROGRESS);

        Incident saved = incidentRepository.save(incident);

        // Log timeline
        logTimeline(saved, IncidentTimeline.EventType.ASSIGNEE_CHANGED,
                "Nhận incident: " + username + " → " + username,
                username, null, username);
        logTimeline(saved, IncidentTimeline.EventType.STATUS_CHANGED,
                "Trạng thái thay đổi: " + oldStatus + " → " + Incident.IncidentStatus.IN_PROGRESS,
                username, oldStatus.name(), Incident.IncidentStatus.IN_PROGRESS.name());

        // Gửi notification về việc nhận incident và đổi status
        incidentNotificationService.notifyIncidentAssigned(saved, username);
        incidentNotificationService.notifyStatusChanged(saved, oldStatus, Incident.IncidentStatus.IN_PROGRESS, username);

        return saved;
    }

    /**
     * IT Staff resolve incident.
     * - Đổi status thành RESOLVED
     * - Set resolvedAt
     * - Lưu resolution
     */
    public Incident resolveIncident(Long id, String resolution, String username) {
        Incident incident = getIncidentById(id);
        Incident.IncidentStatus oldStatus = incident.getStatus();

        incident.setStatus(Incident.IncidentStatus.RESOLVED);
        incident.setResolvedAt(LocalDateTime.now());
        if (resolution != null && !resolution.isBlank()) {
            incident.setResolution(resolution);
        }

        Incident saved = incidentRepository.save(incident);

        // Log timeline
        logTimeline(saved, IncidentTimeline.EventType.STATUS_CHANGED,
                "Trạng thái thay đổi: " + oldStatus + " → " + Incident.IncidentStatus.RESOLVED,
                username, oldStatus.name(), Incident.IncidentStatus.RESOLVED.name());
        if (resolution != null && !resolution.isBlank()) {
            logTimeline(saved, IncidentTimeline.EventType.NOTE_ADDED,
                    "Giải pháp được cung cấp",
                    username, null, resolution);
        }

        // Gửi notification
        incidentNotificationService.notifyIncidentResolved(saved, username);

        return saved;
    }

    /**
     * User xác nhận resolution → Close incident.
     */
    public Incident confirmResolution(Long id, String username) {
        Incident incident = getIncidentById(id);
        Incident.IncidentStatus oldStatus = incident.getStatus();

        incident.setStatus(Incident.IncidentStatus.CLOSED);
        incident.setClosedAt(LocalDateTime.now());

        Incident saved = incidentRepository.save(incident);

        // Log timeline
        logTimeline(saved, IncidentTimeline.EventType.STATUS_CHANGED,
                "Người dùng xác nhận giải pháp - Đóng incident",
                username, oldStatus.name(), Incident.IncidentStatus.CLOSED.name());

        // Gửi notification
        incidentNotificationService.notifyIncidentClosed(saved, username);

        return saved;
    }

    /**
     * User yêu cầu reopen incident.
     * - Đổi status từ RESOLVED → IN_PROGRESS
     * - Clear resolvedAt
     */
    public Incident reopenIncident(Long id, String reason, String username) {
        Incident incident = getIncidentById(id);
        Incident.IncidentStatus oldStatus = incident.getStatus();

        incident.setStatus(Incident.IncidentStatus.IN_PROGRESS);
        incident.setResolvedAt(null);

        Incident saved = incidentRepository.save(incident);

        // Log timeline
        logTimeline(saved, IncidentTimeline.EventType.STATUS_CHANGED,
                "Người dùng yêu cầu mở lại: " + (reason != null ? reason : "Không có lý do"),
                username, oldStatus.name(), Incident.IncidentStatus.IN_PROGRESS.name());

        // Gửi notification
        incidentNotificationService.notifyIncidentReopened(saved, username);

        return saved;
    }

    /**
     * Cập nhật priority của incident (với SLA recalculation).
     */
    public Incident updatePriority(Long id, TicketPriority newPriority, String actorName) {
        Incident incident = getIncidentById(id);
        TicketPriority oldPriority = incident.getPriority();

        incident.setPriority(newPriority);

        // Recalculate SLA deadlines based on new priority
        calculateAndSetSlaDeadlines(incident);

        Incident saved = incidentRepository.save(incident);

        // Log timeline
        logTimeline(saved, IncidentTimeline.EventType.STATUS_CHANGED,
                "Priority thay đổi: " + (oldPriority != null ? oldPriority : "N/A") + " → " + newPriority,
                actorName, oldPriority != null ? oldPriority.name() : null, newPriority.name());

        return saved;
    }

    /**
     * Assign incident cho team.
     */
    public Incident assignTeam(Long id, Long teamId, String actorName) {
        Incident incident = getIncidentById(id);
        Long oldTeamId = incident.getTeam() != null ? incident.getTeam().getId() : null;

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        incident.setTeam(team);

        Incident saved = incidentRepository.save(incident);

        logTimeline(saved, IncidentTimeline.EventType.ASSIGNEE_CHANGED,
                "Team phụ trách thay đổi: " + oldTeamId + " → " + teamId,
                actorName, String.valueOf(oldTeamId), String.valueOf(teamId));

        return saved;
    }

    // ==================== Ticket Linking ====================

    /**
     * Link ticket với incident.
     */
    public TicketIncidentLink linkTicket(Long incidentId, Long ticketId, String linkReason, String linkedBy) {
        Incident incident = getIncidentById(incidentId);
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        // Check if already linked
        if (linkRepository.existsByTicketIdAndIncidentId(ticketId, incidentId)) {
            throw new IllegalStateException("Ticket đã được link với incident này");
        }

        // Create link
        TicketIncidentLink link = new TicketIncidentLink(ticket, incident);
        link.setLinkReason(linkReason);
        link.setLinkedBy(linkedBy);
        TicketIncidentLink saved = linkRepository.save(link);

        // Update incident ticket count
        incident.setLinkedTicketCount((int) linkRepository.countByIncidentId(incidentId));
        incidentRepository.save(incident);

        // Update ticket with incident reference
        ticket.setIncidentId(incidentId);
        ticketRepository.save(ticket);

        // Log timeline
        logTimeline(incident, IncidentTimeline.EventType.TICKET_LINKED,
                "Link ticket: " + ticket.getTicketNumber(),
                linkedBy, null, ticket.getTicketNumber());

        return saved;
    }

    /**
     * Unlink ticket khỏi incident.
     */
    public void unlinkTicket(Long incidentId, Long ticketId, String unlinkedBy) {
        Incident incident = getIncidentById(incidentId);
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        TicketIncidentLink link = linkRepository.findByTicketIdAndIncidentId(ticketId, incidentId)
                .orElseThrow(() -> new IllegalStateException("Link not found"));

        linkRepository.delete(link);

        // Update incident ticket count
        incident.setLinkedTicketCount((int) linkRepository.countByIncidentId(incidentId));
        incidentRepository.save(incident);

        // Remove incident reference from ticket
        ticket.setIncidentId(null);
        ticketRepository.save(ticket);

        // Log timeline
        logTimeline(incident, IncidentTimeline.EventType.TICKET_UNLINKED,
                "Unlink ticket: " + ticket.getTicketNumber(),
                unlinkedBy, ticket.getTicketNumber(), null);
    }

    /**
     * Lấy tickets liên quan đến incident.
     */
    @Transactional(readOnly = true)
    public List<Ticket> getLinkedTickets(Long incidentId) {
        List<TicketIncidentLink> links = linkRepository.findByIncidentIdOrderByLinkedAtDesc(incidentId);
        return links.stream()
                .map(TicketIncidentLink::getTicket)
                .toList();
    }

    /**
     * Lấy incidents liên quan đến ticket.
     */
    @Transactional(readOnly = true)
    public List<Incident> getTicketIncidents(Long ticketId) {
        List<TicketIncidentLink> links = linkRepository.findByTicketIdOrderByLinkedAtDesc(ticketId);
        return links.stream()
                .map(TicketIncidentLink::getIncident)
                .toList();
    }

    // ==================== Timeline ====================

    /**
     * Lấy timeline của incident.
     */
    @Transactional(readOnly = true)
    public List<IncidentTimeline> getIncidentTimeline(Long incidentId) {
        return timelineRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId);
    }

    /**
     * Thêm note vào incident timeline.
     */
    public IncidentTimeline addNote(Long incidentId, String note, String actorName, String actorRole) {
        Incident incident = getIncidentById(incidentId);

        IncidentTimeline timeline = IncidentTimeline.builder()
                .incident(incident)
                .eventType(IncidentTimeline.EventType.NOTE_ADDED)
                .title("Ghi chú")
                .description(note)
                .actor(actorName, actorRole)
                .build();

        return timelineRepository.save(timeline);
    }

    // ==================== Query Methods ====================

    /**
     * Lấy tất cả incidents.
     */
    @Transactional(readOnly = true)
    public Page<Incident> getAllIncidents(Pageable pageable) {
        log.info("getAllIncidents called with pageable: {}", pageable);
        try {
            Page<Incident> result = incidentRepository.findAllByOrderByCreatedAtDesc(pageable);
            log.info("Found {} incidents", result.getTotalElements());
            return result;
        } catch (Exception e) {
            log.error("Error in getAllIncidents", e);
            throw e;
        }
    }

    /**
     * Tìm incidents theo trạng thái.
     */
    @Transactional(readOnly = true)
    public Page<Incident> getIncidentsByStatus(List<Incident.IncidentStatus> statuses, Pageable pageable) {
        return incidentRepository.findByStatusInOrderByCreatedAtDesc(statuses, pageable);
    }

    /**
     * Tìm incidents active (chưa đóng).
     */
    @Transactional(readOnly = true)
    public List<Incident> getActiveIncidents() {
        return incidentRepository.findActiveIncidents();
    }

    /**
     * Tìm kiếm incidents.
     */
    @Transactional(readOnly = true)
    public Page<Incident> searchIncidents(String search, Pageable pageable) {
        return incidentRepository.searchIncidents(search, pageable);
    }

    // ==================== Helper Methods ====================

    private String generateIncidentNumber() {
        String uuid = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "INC-" + uuid;
    }

    private void logTimeline(Incident incident, IncidentTimeline.EventType eventType,
                            String title, String actorName, String oldValue, String newValue) {
        IncidentTimeline timeline = IncidentTimeline.builder()
                .incident(incident)
                .eventType(eventType)
                .title(title)
                .actor(actorName, null)
                .oldValue(oldValue)
                .newValue(newValue)
                .build();

        timelineRepository.save(timeline);
    }

    // ==================== Exception ====================

    public static class IncidentNotFoundException extends RuntimeException {
        public IncidentNotFoundException(Long id) {
            super("Không tìm thấy Incident với ID: " + id);
        }

        public IncidentNotFoundException(String incidentNumber) {
            super("Không tìm thấy Incident với số: " + incidentNumber);
        }
    }
}

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

    public IncidentService(
            IncidentRepository incidentRepository,
            TicketIncidentLinkRepository linkRepository,
            IncidentTimelineRepository timelineRepository,
            TicketRepository ticketRepository,
            UserAccountRepository userAccountRepository,
            TeamRepository teamRepository) {
        this.incidentRepository = incidentRepository;
        this.linkRepository = linkRepository;
        this.timelineRepository = timelineRepository;
        this.ticketRepository = ticketRepository;
        this.userAccountRepository = userAccountRepository;
        this.teamRepository = teamRepository;
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

        Incident saved = incidentRepository.save(incident);

        // Log timeline
        logTimeline(saved, IncidentTimeline.EventType.CREATED,
                "Incident được tạo", createdBy, null, null);

        return saved;
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
     */
    public void deleteIncident(Long id) {
        log.info("Deleting incident: {}", id);
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
        return incidentRepository.findAllByOrderByCreatedAtDesc(pageable);
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

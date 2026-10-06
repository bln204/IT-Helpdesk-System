package com.example.ticketing.ticket;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.example.ticketing.auth.UserRole;
import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.authorization.ActorContextService;
import com.example.ticketing.authorization.TicketAuthorization;

/**
 * Service quản lý ticket timeline.
 *
 * <p>PHASE 5.1 (C-7): every read method now consults the canonical object-level
 * read policy through {@link TicketAuthorization#canReadTicket(ActorContext, Ticket)}.
 * The actor-aware overloads accept a username (canonical String principal) and the
 * read gate runs before any data is returned. The legacy no-actor overloads are
 * retained for internal callers that already do their own authorization (e.g. the
 * timeline writers).
 */
@Service
@Transactional
public class TicketTimelineService {

    private final TicketTimelineRepository timelineRepository;
    private final TicketRepository ticketRepository;
    private final ActorContextService actorContextService;
    private final TicketAuthorization ticketAuthorization;

    public TicketTimelineService(
            TicketTimelineRepository timelineRepository,
            TicketRepository ticketRepository,
            ActorContextService actorContextService,
            TicketAuthorization ticketAuthorization) {
        this.timelineRepository = timelineRepository;
        this.ticketRepository = ticketRepository;
        this.actorContextService = actorContextService;
        this.ticketAuthorization = ticketAuthorization;
    }

    // ============================================================
    // PHASE 5.1: C-7 read gate
    // ============================================================

    /**
     * Consult the canonical object-level read policy for a ticket id. Throws
     * {@code 404} when the ticket is missing and {@code 403} when the actor is
     * not authorized to read the ticket. On success, the resolved actor is returned.
     */
    @Transactional(readOnly = true)
    public ActorContext requireReadAccess(Long ticketId, ActorContext actor) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new com.example.ticketing.ticket.TicketNotFoundException(ticketId));
        if (!ticketAuthorization.canReadTicket(actor, ticket)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You don't have permission to read this ticket.");
        }
        return actor;
    }

    private void requireReadAccess(Long ticketId, String actorUsername) {
        ActorContext actor = actorContextService.fromUsername(actorUsername);
        requireReadAccess(ticketId, actor);
    }

    // ============================================================
    // TIMELINE OPERATIONS — actor-aware overloads (PHASE 5.1)
    // ============================================================

    @Transactional(readOnly = true)
    public List<TicketTimeline.TimelineResponse> getTicketTimeline(Long ticketId, String actorUsername) {
        requireReadAccess(ticketId, actorUsername);
        return timelineRepository.findByTicketIdOrderByCreatedAtDesc(ticketId).stream()
            .map(TicketTimeline.TimelineResponse::new)
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<TicketTimeline.TimelineResponse> getTicketTimeline(Long ticketId, Pageable pageable, String actorUsername) {
        requireReadAccess(ticketId, actorUsername);
        return timelineRepository.findByTicketId(ticketId, pageable)
            .map(TicketTimeline.TimelineResponse::new);
    }

    @Transactional(readOnly = true)
    public List<TicketTimeline.TimelineResponse> getTicketTimelineByCategory(
            Long ticketId, TicketTimeline.EventCategory category, String actorUsername) {
        requireReadAccess(ticketId, actorUsername);
        return timelineRepository.findByTicketIdAndCategory(ticketId, category).stream()
            .map(TicketTimeline.TimelineResponse::new)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<TicketTimeline.TimelineResponse> getTicketComments(Long ticketId, String actorUsername) {
        requireReadAccess(ticketId, actorUsername);
        return timelineRepository.findComments(ticketId).stream()
            .map(TicketTimeline.TimelineResponse::new)
            .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getTicketTimelineSummary(Long ticketId, String actorUsername) {
        requireReadAccess(ticketId, actorUsername);
        return timelineRepository.findByTicketIdOrderByCreatedAtDesc(ticketId).stream()
            .collect(Collectors.groupingBy(
                e -> e.getEventCategory().name(),
                Collectors.counting()
            ));
    }

    // ============================================================
    // TIMELINE OPERATIONS — legacy no-actor overloads (back-compat)
    //
    // Internal callers (e.g. the SLA scheduler) reach these and already do their
    // own authorization. The new controller endpoints use the actor-aware
    // overloads above.
    // ============================================================

    @Transactional(readOnly = true)
    public List<TicketTimeline.TimelineResponse> getTicketTimeline(Long ticketId) {
        return timelineRepository.findByTicketIdOrderByCreatedAtDesc(ticketId).stream()
            .map(TicketTimeline.TimelineResponse::new)
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<TicketTimeline.TimelineResponse> getTicketTimeline(Long ticketId, Pageable pageable) {
        return timelineRepository.findByTicketId(ticketId, pageable)
            .map(TicketTimeline.TimelineResponse::new);
    }

    @Transactional(readOnly = true)
    public List<TicketTimeline.TimelineResponse> getTicketTimelineByCategory(
            Long ticketId, TicketTimeline.EventCategory category) {
        return timelineRepository.findByTicketIdAndCategory(ticketId, category).stream()
            .map(TicketTimeline.TimelineResponse::new)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<TicketTimeline.TimelineResponse> getTicketComments(Long ticketId) {
        return timelineRepository.findComments(ticketId).stream()
            .map(TicketTimeline.TimelineResponse::new)
            .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getTicketTimelineSummary(Long ticketId) {
        return timelineRepository.findByTicketIdOrderByCreatedAtDesc(ticketId).stream()
            .collect(Collectors.groupingBy(
                e -> e.getEventCategory().name(),
                Collectors.counting()
            ));
    }
    
    // ============================================================
    // TIMELINE LOGGING
    // ============================================================
    
    /**
     * Log ticket created event.
     */
    public TicketTimeline logTicketCreated(Ticket ticket, String createdBy) {
        TicketTimeline event = TicketTimeline.forTicketCreated(ticket, createdBy);
        return timelineRepository.save(event);
    }
    
    /**
     * Log status change.
     */
    public TicketTimeline logStatusChange(Ticket ticket, String oldStatus, String newStatus,
                                         String actorName, UserRole.Role actorRole) {
        TicketTimeline event = TicketTimeline.forStatusChange(
            ticket, oldStatus, newStatus, actorName, 
            actorRole != null ? actorRole.name() : null
        );
        return timelineRepository.save(event);
    }
    
    /**
     * Log assignment.
     */
    public TicketTimeline logAssignment(Ticket ticket, String oldAssignee, String newAssignee,
                                       String actorName, UserRole.Role actorRole, boolean isAutoAssign) {
        TicketTimeline event = TicketTimeline.forAssignment(
            ticket, oldAssignee, newAssignee, actorName,
            actorRole != null ? actorRole.name() : null, isAutoAssign
        );
        return timelineRepository.save(event);
    }
    
    /**
     * Log unassignment.
     */
    public TicketTimeline logUnassignment(Ticket ticket, String previousAssignee,
                                         String actorName, UserRole.Role actorRole) {
        TicketTimeline event = TicketTimeline.builder()
            .ticket(ticket)
            .eventType(TicketTimeline.EventType.UNASSIGNED)
            .eventCategory(TicketTimeline.EventCategory.ASSIGNMENT)
            .title("Bỏ gán khỏi " + previousAssignee)
            .oldValue(previousAssignee)
            .newValue(null)
            .userActor(actorName, actorName, actorRole != null ? actorRole.name() : null)
            .build();
        return timelineRepository.save(event);
    }
    
    /**
     * Log comment.
     */
    public TicketTimeline logComment(Ticket ticket, boolean isInternal,
                                    String actorName, UserRole.Role actorRole) {
        TicketTimeline event = TicketTimeline.forComment(ticket, isInternal, actorName,
            actorRole != null ? actorRole.name() : null
        );
        return timelineRepository.save(event);
    }
    
    /**
     * Log SLA breach.
     */
    public TicketTimeline logSlaBreach(Ticket ticket, String slaType, int hoursElapsed) {
        TicketTimeline event = TicketTimeline.forSlaBreach(ticket, slaType, hoursElapsed);
        return timelineRepository.save(event);
    }
    
    /**
     * Log SLA warning.
     */
    public TicketTimeline logSlaWarning(Ticket ticket, String slaType, int hoursRemaining) {
        TicketTimeline event = TicketTimeline.forSlaWarning(ticket, slaType, hoursRemaining);
        return timelineRepository.save(event);
    }
    
    /**
     * Log category change.
     */
    public TicketTimeline logCategoryChange(Ticket ticket, String oldCategory, String newCategory,
                                           String actorName, UserRole.Role actorRole) {
        TicketTimeline event = TicketTimeline.forCategoryChange(
            ticket, oldCategory, newCategory, actorName,
            actorRole != null ? actorRole.name() : null
        );
        return timelineRepository.save(event);
    }
    
    /**
     * Log escalation.
     */
    public TicketTimeline logEscalation(Ticket ticket, String escalatedBy, String reason) {
        TicketTimeline event = TicketTimeline.forEscalation(ticket, escalatedBy, reason);
        return timelineRepository.save(event);
    }
    
    /**
     * Log priority change.
     */
    public TicketTimeline logPriorityChange(Ticket ticket, String oldPriority, String newPriority,
                                           String actorName, UserRole.Role actorRole) {
        TicketTimeline event = TicketTimeline.builder()
            .ticket(ticket)
            .eventType(TicketTimeline.EventType.PRIORITY_CHANGED)
            .eventCategory(TicketTimeline.EventCategory.PRIORITY)
            .title("Priority thay đổi: " + oldPriority + " → " + newPriority)
            .oldValue(oldPriority)
            .newValue(newPriority)
            .userActor(actorName, actorName, actorRole != null ? actorRole.name() : null)
            .build();
        return timelineRepository.save(event);
    }
    
    /**
     * Log first response sent.
     */
    public TicketTimeline logFirstResponse(Ticket ticket, String respondedBy) {
        TicketTimeline event = TicketTimeline.builder()
            .ticket(ticket)
            .eventType(TicketTimeline.EventType.FIRST_RESPONSE_SENT)
            .eventCategory(TicketTimeline.EventCategory.SLA)
            .title("Phản hồi đầu tiên được gửi")
            .description("Đã gửi phản hồi cho người yêu cầu")
            .userActor(respondedBy, respondedBy, null)
            .build();
        return timelineRepository.save(event);
    }
    
    /**
     * Log ticket closure.
     */
    public TicketTimeline logClosure(Ticket ticket, String closedBy, String resolution) {
        TicketTimeline event = TicketTimeline.builder()
            .ticket(ticket)
            .eventType(TicketTimeline.EventType.CLOSED)
            .eventCategory(TicketTimeline.EventCategory.CLOSURE)
            .title("Ticket được đóng")
            .description(resolution)
            .userActor(closedBy, closedBy, null)
            .build();
        return timelineRepository.save(event);
    }
    
    /**
     * Log ticket resolution.
     */
    public TicketTimeline logResolution(Ticket ticket, String resolvedBy) {
        TicketTimeline event = TicketTimeline.builder()
            .ticket(ticket)
            .eventType(TicketTimeline.EventType.RESOLVED)
            .eventCategory(TicketTimeline.EventCategory.STATUS)
            .title("Ticket được giải quyết")
            .description("Ticket đã được giải quyết và chờ xác nhận từ người yêu cầu")
            .userActor(resolvedBy, resolvedBy, null)
            .build();
        return timelineRepository.save(event);
    }
    
    /**
     * Log ticket reopening.
     */
    public TicketTimeline logReopening(Ticket ticket, String reopenedBy, String reason) {
        TicketTimeline event = TicketTimeline.builder()
            .ticket(ticket)
            .eventType(TicketTimeline.EventType.REOPENED)
            .eventCategory(TicketTimeline.EventCategory.STATUS)
            .title("Ticket được mở lại")
            .description(reason)
            .userActor(reopenedBy, reopenedBy, null)
            .build();
        return timelineRepository.save(event);
    }
    
    // ============================================================
    // UTILITY
    // ============================================================
    
    /**
     * Xóa timeline của một ticket (khi xóa ticket).
     */
    public void deleteTicketTimeline(Long ticketId) {
        timelineRepository.deleteByTicketId(ticketId);
    }
    
    /**
     * Đếm events trong timeline.
     */
    @Transactional(readOnly = true)
    public long countEvents(Long ticketId) {
        return timelineRepository.countByTicketId(ticketId);
    }
    
    /**
     * Đếm comments.
     */
    @Transactional(readOnly = true)
    public long countComments(Long ticketId) {
        return timelineRepository.countByTicketIdAndEventCategory(ticketId, TicketTimeline.EventCategory.COMMENT);
    }
}

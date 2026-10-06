package com.example.ticketing.ticket;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.authorization.ActorContextService;
import com.example.ticketing.authorization.TicketAuthorization;

/**
 * REST Controller cho Ticket Timeline operations.
 *
 * <p>PHASE 5.1 (C-7): every endpoint under {@code /api/tickets/{ticketId}/timeline}
 * consults the canonical object-level read policy before returning ticket-derived
 * timeline information. The controller now accepts an {@link Authentication} argument
 * and delegates the policy check to {@link TicketTimelineService}.
 */
@RestController
@RequestMapping("/api/tickets/{ticketId}/timeline")
public class TicketTimelineController {

    private final TicketTimelineService timelineService;
    private final ActorContextService actorContextService;
    private final TicketAuthorization ticketAuthorization;

    public TicketTimelineController(
            TicketTimelineService timelineService,
            ActorContextService actorContextService,
            TicketAuthorization ticketAuthorization) {
        this.timelineService = timelineService;
        this.actorContextService = actorContextService;
        this.ticketAuthorization = ticketAuthorization;
    }

    /**
     * Lấy full timeline của ticket.
     * GET /api/tickets/{ticketId}/timeline
     */
    @GetMapping
    public ResponseEntity<List<TicketTimeline.TimelineResponse>> getTimeline(
            @PathVariable Long ticketId,
            Authentication authentication) {
        ActorContext actor = requireReadAccess(ticketId, authentication);
        List<TicketTimeline.TimelineResponse> timeline = timelineService.getTicketTimeline(ticketId, actor.username());
        return ResponseEntity.ok(timeline);
    }

    /**
     * Lấy timeline với phân trang.
     * GET /api/tickets/{ticketId}/timeline/page?page=0&size=20
     */
    @GetMapping("/page")
    public ResponseEntity<Page<TicketTimeline.TimelineResponse>> getTimelinePage(
            @PathVariable Long ticketId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        ActorContext actor = requireReadAccess(ticketId, authentication);
        return ResponseEntity.ok(timelineService.getTicketTimeline(
            ticketId, PageRequest.of(page, size), actor.username()));
    }

    /**
     * Lấy timeline summary (số lượng events theo category).
     * GET /api/tickets/{ticketId}/timeline/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Long>> getTimelineSummary(
            @PathVariable Long ticketId,
            Authentication authentication) {
        requireReadAccess(ticketId, authentication);
        return ResponseEntity.ok(timelineService.getTicketTimelineSummary(ticketId));
    }

    /**
     * Lấy comments từ timeline.
     * GET /api/tickets/{ticketId}/timeline/comments
     */
    @GetMapping("/comments")
    public ResponseEntity<List<TicketTimeline.TimelineResponse>> getComments(
            @PathVariable Long ticketId,
            Authentication authentication) {
        ActorContext actor = requireReadAccess(ticketId, authentication);
        return ResponseEntity.ok(timelineService.getTicketComments(ticketId, actor.username()));
    }

    /**
     * Lấy timeline theo category.
     * GET /api/tickets/{ticketId}/timeline?category=STATUS
     */
    @GetMapping(params = "category")
    public ResponseEntity<List<TicketTimeline.TimelineResponse>> getTimelineByCategory(
            @PathVariable Long ticketId,
            @RequestParam String category,
            Authentication authentication) {
        ActorContext actor = requireReadAccess(ticketId, authentication);
        try {
            TicketTimeline.EventCategory eventCategory = TicketTimeline.EventCategory.valueOf(category.toUpperCase());
            List<TicketTimeline.TimelineResponse> timeline = timelineService.getTicketTimelineByCategory(
                ticketId, eventCategory, actor.username());
            return ResponseEntity.ok(timeline);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Đếm events.
     * GET /api/tickets/{ticketId}/timeline/count
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getEventCount(
            @PathVariable Long ticketId,
            Authentication authentication) {
        requireReadAccess(ticketId, authentication);
        long totalEvents = timelineService.countEvents(ticketId);
        long comments = timelineService.countComments(ticketId);
        return ResponseEntity.ok(Map.of(
            "totalEvents", totalEvents,
            "comments", comments
        ));
    }

    // ============================================================
    // C-7 gate
    // ============================================================

    private ActorContext requireReadAccess(Long ticketId, Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        ActorContext actor = actorContextService.fromAuthentication(authentication);
        // We delegate the existence + read policy to TicketTimelineService which
        // already resolves the parent ticket through TicketService.
        timelineService.requireReadAccess(ticketId, actor);
        return actor;
    }
}

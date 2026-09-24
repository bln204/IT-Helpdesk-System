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

/**
 * REST Controller cho Ticket Timeline operations.
 */
@RestController
@RequestMapping("/api/tickets/{ticketId}/timeline")
public class TicketTimelineController {
    
    private final TicketTimelineService timelineService;
    
    public TicketTimelineController(TicketTimelineService timelineService) {
        this.timelineService = timelineService;
    }
    
    /**
     * Lấy full timeline của ticket.
     * GET /api/tickets/{ticketId}/timeline
     */
    @GetMapping
    public ResponseEntity<List<TicketTimeline.TimelineResponse>> getTimeline(
            @PathVariable Long ticketId) {
        List<TicketTimeline.TimelineResponse> timeline = timelineService.getTicketTimeline(ticketId);
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
            @RequestParam(defaultValue = "20") int size) {
        Page<TicketTimeline.TimelineResponse> timeline = timelineService.getTicketTimeline(
            ticketId, PageRequest.of(page, size));
        return ResponseEntity.ok(timeline);
    }
    
    /**
     * Lấy timeline summary (số lượng events theo category).
     * GET /api/tickets/{ticketId}/timeline/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Long>> getTimelineSummary(
            @PathVariable Long ticketId) {
        Map<String, Long> summary = timelineService.getTicketTimelineSummary(ticketId);
        return ResponseEntity.ok(summary);
    }
    
    /**
     * Lấy comments từ timeline.
     * GET /api/tickets/{ticketId}/timeline/comments
     */
    @GetMapping("/comments")
    public ResponseEntity<List<TicketTimeline.TimelineResponse>> getComments(
            @PathVariable Long ticketId) {
        List<TicketTimeline.TimelineResponse> comments = timelineService.getTicketComments(ticketId);
        return ResponseEntity.ok(comments);
    }
    
    /**
     * Lấy timeline theo category.
     * GET /api/tickets/{ticketId}/timeline?category=STATUS
     */
    @GetMapping(params = "category")
    public ResponseEntity<List<TicketTimeline.TimelineResponse>> getTimelineByCategory(
            @PathVariable Long ticketId,
            @RequestParam String category) {
        try {
            TicketTimeline.EventCategory eventCategory = TicketTimeline.EventCategory.valueOf(category.toUpperCase());
            List<TicketTimeline.TimelineResponse> timeline = timelineService.getTicketTimelineByCategory(ticketId, eventCategory);
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
    public ResponseEntity<Map<String, Long>> getEventCount(@PathVariable Long ticketId) {
        long totalEvents = timelineService.countEvents(ticketId);
        long comments = timelineService.countComments(ticketId);
        return ResponseEntity.ok(Map.of(
            "totalEvents", totalEvents,
            "comments", comments
        ));
    }
}

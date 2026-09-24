package com.example.ticketing.dashboard;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.ticket.Ticket;
import com.example.ticketing.ticket.TicketRepository;
import com.example.ticketing.ticket.TicketTypes;
import com.example.ticketing.ticket.SlaSchedulerService;
import com.example.ticketing.team.TeamService;

/**
 * REST Controller cho IT Staff Dashboard.
 * Cung cấp dữ liệu tổng quan cho dashboard của IT staff.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    
    @Autowired
    private TicketRepository ticketRepository;
    
    @Autowired
    private UserAccountRepository userAccountRepository;
    
    @Autowired
    private TeamService teamService;
    
    @Autowired
    private SlaSchedulerService slaSchedulerService;
    
    /**
     * Lấy dashboard overview cho IT staff.
     * GET /api/dashboard/overview
     */
    @GetMapping("/overview")
    public ResponseEntity<DashboardOverview> getOverview(Authentication authentication) {
        String username = authentication.getName();
        UserAccount user = userAccountRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        DashboardOverview overview = new DashboardOverview();
        
        // Stats cho IT staff (tickets assigned to them)
        long myAssigned = ticketRepository.countByAssigneeIdAndStatusNotIn(user.getId(), username);
        
        long openTickets = ticketRepository.countOpenTickets();
        
        long unassigned = ticketRepository.countByStatus(TicketTypes.TicketStatus.NEW);
        
        long slaBreached = ticketRepository.countBySlaBreached();
        long responseSlaBreached = ticketRepository.countByResponseSlaBreached();
        
        overview.setMyAssignedTickets((int) myAssigned);
        overview.setOpenTickets((int) openTickets);
        overview.setUnassignedTickets((int) unassigned);
        overview.setSlaBreached((int) slaBreached);
        overview.setResponseSlaBreached((int) responseSlaBreached);
        
        return ResponseEntity.ok(overview);
    }
    
    /**
     * Lấy tickets được gán cho user hiện tại.
     * GET /api/dashboard/my-tickets
     */
    @GetMapping("/my-tickets")
    public ResponseEntity<List<Ticket>> getMyTickets(
            Authentication authentication,
            @RequestParam(defaultValue = "10") int limit) {
        String username = authentication.getName();
        UserAccount user = userAccountRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        List<Ticket> tickets = ticketRepository.findMyAssignedTickets(
            user.getId(),
            username,
            PageRequest.of(0, limit)
        ).getContent();
        
        return ResponseEntity.ok(tickets);
    }
    
    /**
     * Lấy tickets chưa được gán (unassigned).
     * GET /api/dashboard/unassigned-tickets
     */
    @GetMapping("/unassigned-tickets")
    public ResponseEntity<List<Ticket>> getUnassignedTickets(
            @RequestParam(defaultValue = "10") int limit) {
        List<Ticket> tickets = ticketRepository.findUnassignedOpenTickets(
            PageRequest.of(0, limit)
        ).getContent();
        return ResponseEntity.ok(tickets);
    }
    
    /**
     * Lấy tickets cần xử lý (priority cao, SLA warning/breach).
     * GET /api/dashboard/urgent-tickets
     */
    @GetMapping("/urgent-tickets")
    public ResponseEntity<List<Ticket>> getUrgentTickets(
            @RequestParam(defaultValue = "10") int limit) {
        List<Ticket> tickets = ticketRepository.findUrgentTickets(
            PageRequest.of(0, limit)
        ).getContent();
        return ResponseEntity.ok(tickets);
    }
    
    /**
     * Lấy team workload.
     * GET /api/dashboard/team-workload/{teamId}
     */
    @GetMapping("/team-workload/{teamId}")
    public ResponseEntity<List<TeamService.TeamMemberWorkload>> getTeamWorkload(
            @PathVariable Long teamId) {
        List<TeamService.TeamMemberWorkload> workload = teamService.getTeamWorkload(teamId);
        return ResponseEntity.ok(workload);
    }
    
    /**
     * Lấy SLA summary.
     * GET /api/dashboard/sla-summary
     */
    @GetMapping("/sla-summary")
    public ResponseEntity<SlaSummary> getSlaSummary() {
        SlaSummary summary = new SlaSummary();
        
        long openTickets = ticketRepository.countOpenTickets();
        long responseBreached = ticketRepository.countByResponseSlaBreached();
        long resolutionBreached = ticketRepository.countBySlaBreached();
        
        summary.setOpenTickets((int) openTickets);
        summary.setResponseSlaBreached((int) responseBreached);
        summary.setResolutionSlaBreached((int) resolutionBreached);
        
        if (openTickets > 0) {
            summary.setResponseSlaCompliance((int) (100 - (responseBreached * 100 / openTickets)));
            summary.setResolutionSlaCompliance((int) (100 - (resolutionBreached * 100 / openTickets)));
        } else {
            summary.setResponseSlaCompliance(100);
            summary.setResolutionSlaCompliance(100);
        }
        
        return ResponseEntity.ok(summary);
    }
    
    /**
     * Lấy tickets theo status.
     * GET /api/dashboard/tickets-by-status
     */
    @GetMapping("/tickets-by-status")
    public ResponseEntity<Map<String, Long>> getTicketsByStatus() {
        Map<String, Long> counts = Map.of(
            "new", ticketRepository.countByStatus(TicketTypes.TicketStatus.NEW),
            "assigned", ticketRepository.countByStatus(TicketTypes.TicketStatus.ASSIGNED),
            "inProgress", ticketRepository.countByStatus(TicketTypes.TicketStatus.IN_PROGRESS),
            "waitingForUser", ticketRepository.countByStatus(TicketTypes.TicketStatus.WAITING_FOR_USER),
            "escalated", ticketRepository.countByStatus(TicketTypes.TicketStatus.ESCALATED),
            "resolved", ticketRepository.countByStatus(TicketTypes.TicketStatus.RESOLVED),
            "closed", ticketRepository.countByStatus(TicketTypes.TicketStatus.CLOSED)
        );
        return ResponseEntity.ok(counts);
    }
    
    /**
     * Lấy tickets theo priority.
     * GET /api/dashboard/tickets-by-priority
     */
    @GetMapping("/tickets-by-priority")
    public ResponseEntity<Map<String, Long>> getTicketsByPriority() {
        Map<String, Long> counts = Map.of(
            "critical", ticketRepository.countByPriority(TicketTypes.TicketPriority.CRITICAL),
            "high", ticketRepository.countByPriority(TicketTypes.TicketPriority.HIGH),
            "medium", ticketRepository.countByPriority(TicketTypes.TicketPriority.MEDIUM),
            "low", ticketRepository.countByPriority(TicketTypes.TicketPriority.LOW)
        );
        return ResponseEntity.ok(counts);
    }
    
    /**
     * Lấy recent activity (timeline events).
     * GET /api/dashboard/recent-activity
     */
    @GetMapping("/recent-activity")
    public ResponseEntity<List<ActivityItem>> getRecentActivity(
            @RequestParam(defaultValue = "10") int limit) {
        List<Ticket> recentTickets = ticketRepository.findRecentTickets(
            PageRequest.of(0, limit)
        ).getContent();
        
        List<ActivityItem> activities = recentTickets.stream().map(ticket -> {
            ActivityItem item = new ActivityItem();
            item.setTicketId(ticket.getId());
            item.setTicketNumber(ticket.getTicketNumber());
            item.setTitle(ticket.getTitle());
            item.setStatus(ticket.getStatus().name());
            item.setPriority(ticket.getPriority().name());
            item.setCreatedAt(ticket.getCreatedAt());
            return item;
        }).toList();
        
        return ResponseEntity.ok(activities);
    }
    
    // ============ DTO Classes ============
    
    public static class DashboardOverview {
        private int myAssignedTickets;
        private int openTickets;
        private int unassignedTickets;
        private int slaBreached;
        private int responseSlaBreached;
        private int myTeamSize;
        private long teamTotalActive;
        
        // Getters & Setters
        public int getMyAssignedTickets() { return myAssignedTickets; }
        public void setMyAssignedTickets(int myAssignedTickets) { this.myAssignedTickets = myAssignedTickets; }
        public int getOpenTickets() { return openTickets; }
        public void setOpenTickets(int openTickets) { this.openTickets = openTickets; }
        public int getUnassignedTickets() { return unassignedTickets; }
        public void setUnassignedTickets(int unassignedTickets) { this.unassignedTickets = unassignedTickets; }
        public int getSlaBreached() { return slaBreached; }
        public void setSlaBreached(int slaBreached) { this.slaBreached = slaBreached; }
        public int getResponseSlaBreached() { return responseSlaBreached; }
        public void setResponseSlaBreached(int responseSlaBreached) { this.responseSlaBreached = responseSlaBreached; }
        public int getMyTeamSize() { return myTeamSize; }
        public void setMyTeamSize(int myTeamSize) { this.myTeamSize = myTeamSize; }
        public long getTeamTotalActive() { return teamTotalActive; }
        public void setTeamTotalActive(long teamTotalActive) { this.teamTotalActive = teamTotalActive; }
    }
    
    public static class SlaSummary {
        private int openTickets;
        private int responseSlaBreached;
        private int resolutionSlaBreached;
        private int responseSlaCompliance;
        private int resolutionSlaCompliance;
        
        // Getters & Setters
        public int getOpenTickets() { return openTickets; }
        public void setOpenTickets(int openTickets) { this.openTickets = openTickets; }
        public int getResponseSlaBreached() { return responseSlaBreached; }
        public void setResponseSlaBreached(int responseSlaBreached) { this.responseSlaBreached = responseSlaBreached; }
        public int getResolutionSlaBreached() { return resolutionSlaBreached; }
        public void setResolutionSlaBreached(int resolutionSlaBreached) { this.resolutionSlaBreached = resolutionSlaBreached; }
        public int getResponseSlaCompliance() { return responseSlaCompliance; }
        public void setResponseSlaCompliance(int responseSlaCompliance) { this.responseSlaCompliance = responseSlaCompliance; }
        public int getResolutionSlaCompliance() { return resolutionSlaCompliance; }
        public void setResolutionSlaCompliance(int resolutionSlaCompliance) { this.resolutionSlaCompliance = resolutionSlaCompliance; }
    }
    
    public static class ActivityItem {
        private Long ticketId;
        private String ticketNumber;
        private String title;
        private String status;
        private String priority;
        private LocalDateTime createdAt;
        
        // Getters & Setters
        public Long getTicketId() { return ticketId; }
        public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
        public String getTicketNumber() { return ticketNumber; }
        public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
}

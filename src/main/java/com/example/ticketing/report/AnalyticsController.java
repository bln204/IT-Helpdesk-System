package com.example.ticketing.report;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.ticketing.report.AnalyticsService.*;

/**
 * REST Controller cho Reporting & Analytics.
 */
@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);

    private final AnalyticsService analyticsService;
    private final ReportService reportService;

    public AnalyticsController(AnalyticsService analyticsService, ReportService reportService) {
        this.analyticsService = analyticsService;
        this.reportService = reportService;
    }

    // TEST ENDPOINT - No auth required
    @GetMapping("/test")
    public ResponseEntity<String> test() {
        log.info("TEST ENDPOINT HIT!");
        return ResponseEntity.ok("Analytics test OK");
    }

    // ==================== Dashboard ====================

    /**
     * Get dashboard metrics.
     * GET /api/analytics/dashboard
     */
    @GetMapping("/dashboard")
    // @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_GIAM_DOC', 'ROLE_TRUONG_PHONG', 'ROLE_NHAN_VIEN')")
    public ResponseEntity<DashboardMetrics> getDashboardMetrics() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        
        // Manual check for required role
        boolean hasRole = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || 
                          a.getAuthority().equals("ROLE_GIAM_DOC") ||
                          a.getAuthority().equals("ROLE_TRUONG_PHONG") ||
                          a.getAuthority().equals("ROLE_NHAN_VIEN"));
        
        if (!hasRole) {
            log.warn("User {} does not have required analytics role", auth.getName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        return ResponseEntity.ok(analyticsService.getDashboardMetrics());
    }

    // ==================== Ticket Analytics ====================

    /**
     * Get ticket volume by day.
     * GET /api/analytics/tickets/volume
     */
    @GetMapping("/tickets/volume")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_GIAM_DOC', 'ROLE_TRUONG_PHONG', 'ROLE_NHAN_VIEN')")
    public ResponseEntity<List<TimeSeriesData>> getTicketVolume(
            @RequestParam(defaultValue = "30") int days) {
        log.info("GET /api/analytics/tickets/volume - days: {}", days);
        return ResponseEntity.ok(analyticsService.getTicketVolumeByDay(days));
    }

    /**
     * Get tickets by category.
     * GET /api/analytics/tickets/by-category
     */
    @GetMapping("/tickets/by-category")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_GIAM_DOC', 'ROLE_TRUONG_PHONG', 'ROLE_NHAN_VIEN')")
    public ResponseEntity<List<CategoryData>> getTicketsByCategory() {
        log.info("GET /api/analytics/tickets/by-category");
        return ResponseEntity.ok(analyticsService.getTicketsByCategory());
    }

    /**
     * Get tickets by priority.
     * GET /api/analytics/tickets/by-priority
     */
    @GetMapping("/tickets/by-priority")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_GIAM_DOC', 'ROLE_TRUONG_PHONG', 'ROLE_NHAN_VIEN')")
    public ResponseEntity<List<PriorityData>> getTicketsByPriority() {
        log.info("GET /api/analytics/tickets/by-priority");
        return ResponseEntity.ok(analyticsService.getTicketsByPriority());
    }

    /**
     * Get top requesters.
     * GET /api/analytics/tickets/top-requesters
     */
    @GetMapping("/tickets/top-requesters")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_GIAM_DOC', 'ROLE_TRUONG_PHONG', 'ROLE_NHAN_VIEN')")
    public ResponseEntity<List<AgentData>> getTopRequesters(
            @RequestParam(defaultValue = "10") int limit) {
        log.info("GET /api/analytics/tickets/top-requesters - limit: {}", limit);
        return ResponseEntity.ok(analyticsService.getTopRequesters(limit));
    }

    // ==================== Asset Analytics ====================

    /**
     * Get asset health distribution.
     * GET /api/analytics/assets/health
     */
    @GetMapping("/assets/health")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_GIAM_DOC', 'ROLE_TRUONG_PHONG', 'ROLE_NHAN_VIEN')")
    public ResponseEntity<List<HealthDistribution>> getAssetHealthDistribution() {
        log.info("GET /api/analytics/assets/health");
        return ResponseEntity.ok(analyticsService.getAssetHealthDistribution());
    }

    /**
     * Get asset type distribution.
     * GET /api/analytics/assets/by-type
     */
    @GetMapping("/assets/by-type")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_GIAM_DOC', 'ROLE_TRUONG_PHONG', 'ROLE_NHAN_VIEN')")
    public ResponseEntity<List<CategoryData>> getAssetTypeDistribution() {
        log.info("GET /api/analytics/assets/by-type");
        return ResponseEntity.ok(analyticsService.getAssetTypeDistribution());
    }

    // ==================== Change Analytics ====================

    /**
     * Get change status distribution.
     * GET /api/analytics/changes/by-status
     */
    @GetMapping("/changes/by-status")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_GIAM_DOC', 'ROLE_TRUONG_PHONG', 'ROLE_NHAN_VIEN')")
    public ResponseEntity<List<ChangeStatusData>> getChangeStatusDistribution() {
        log.info("GET /api/analytics/changes/by-status");
        return ResponseEntity.ok(analyticsService.getChangeStatusDistribution());
    }

    /**
     * Get change type distribution.
     * GET /api/analytics/changes/by-type
     */
    @GetMapping("/changes/by-type")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_GIAM_DOC', 'ROLE_TRUONG_PHONG', 'ROLE_NHAN_VIEN')")
    public ResponseEntity<List<ChangeTypeData>> getChangeTypeDistribution() {
        log.info("GET /api/analytics/changes/by-type");
        return ResponseEntity.ok(analyticsService.getChangeTypeDistribution());
    }

    // ==================== Report Configs ====================

    /**
     * Get available reports.
     * GET /api/analytics/reports
     */
    @GetMapping("/reports")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_GIAM_DOC', 'ROLE_TRUONG_PHONG', 'ROLE_NHAN_VIEN')")
    public ResponseEntity<List<ReportConfig>> getReports() {
        log.info("GET /api/analytics/reports");
        return ResponseEntity.ok(reportService.getPublicReports());
    }

    /**
     * Get report by ID.
     * GET /api/analytics/reports/{id}
     */
    @GetMapping("/reports/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_GIAM_DOC', 'ROLE_TRUONG_PHONG', 'ROLE_NHAN_VIEN')")
    public ResponseEntity<ReportConfig> getReportById(@PathVariable Long id) {
        log.info("GET /api/analytics/reports/{}", id);
        return ResponseEntity.ok(reportService.getReportById(id));
    }

    // ==================== Exception Handlers ====================

    @ExceptionHandler(ReportService.ReportNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleNotFound(ReportService.ReportNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    // ==================== DTOs ====================

    public static class ErrorResponse {
        private String code;
        private String message;
        public ErrorResponse(String code, String message) {
            this.code = code;
            this.message = message;
        }
        public String getCode() { return code; }
        public String getMessage() { return message; }
    }
}

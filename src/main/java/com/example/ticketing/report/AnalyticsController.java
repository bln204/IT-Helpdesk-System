package com.example.ticketing.report;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import com.example.ticketing.authorization.ActorContextService;
import com.example.ticketing.authorization.ActorTiers;
import com.example.ticketing.authorization.ReportingAuthorization;

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
    private final ActorContextService actorContextService;
    private final ReportingAuthorization reporting;

    public AnalyticsController(AnalyticsService analyticsService, ReportService reportService,
                               ActorContextService actorContextService, ReportingAuthorization reporting) {
        this.analyticsService = analyticsService;
        this.reportService = reportService;
        this.actorContextService = actorContextService;
        this.reporting = reporting;
    }

    // ==================== Dashboard ====================

    /**
     * Get dashboard metrics.
     * GET /api/analytics/dashboard
     */
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardMetrics> getDashboardMetrics(Authentication authentication) {
        ActorTiers.require(reporting.canSeeCompanyStats(actorContextService.fromAuthentication(authentication)),
                "Bạn không có quyền xem số liệu phân tích.");
        return ResponseEntity.ok(analyticsService.getDashboardMetrics());
    }

    // ==================== Ticket Analytics ====================

    /**
     * Get ticket volume by day.
     * GET /api/analytics/tickets/volume
     */
    @GetMapping("/tickets/volume")
    public ResponseEntity<List<TimeSeriesData>> getTicketVolume(
            @RequestParam(defaultValue = "30") int days,
            Authentication authentication) {
        ActorTiers.require(reporting.canSeeCompanyStats(actorContextService.fromAuthentication(authentication)),
                "Bạn không có quyền xem số liệu phân tích.");
        log.info("GET /api/analytics/tickets/volume - days: {}", days);
        return ResponseEntity.ok(analyticsService.getTicketVolumeByDay(days));
    }

    /**
     * Get tickets by category.
     * GET /api/analytics/tickets/by-category
     */
    @GetMapping("/tickets/by-category")
    public ResponseEntity<List<CategoryData>> getTicketsByCategory(Authentication authentication) {
        ActorTiers.require(reporting.canSeeCompanyStats(actorContextService.fromAuthentication(authentication)),
                "Bạn không có quyền xem số liệu phân tích.");
        log.info("GET /api/analytics/tickets/by-category");
        return ResponseEntity.ok(analyticsService.getTicketsByCategory());
    }

    /**
     * Get tickets by priority.
     * GET /api/analytics/tickets/by-priority
     */
    @GetMapping("/tickets/by-priority")
    public ResponseEntity<List<PriorityData>> getTicketsByPriority(Authentication authentication) {
        ActorTiers.require(reporting.canSeeCompanyStats(actorContextService.fromAuthentication(authentication)),
                "Bạn không có quyền xem số liệu phân tích.");
        log.info("GET /api/analytics/tickets/by-priority");
        return ResponseEntity.ok(analyticsService.getTicketsByPriority());
    }

    /**
     * Get top requesters.
     * GET /api/analytics/tickets/top-requesters
     */
    @GetMapping("/tickets/top-requesters")
    public ResponseEntity<List<AgentData>> getTopRequesters(
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication) {
        ActorTiers.require(reporting.canSeeCompanyStats(actorContextService.fromAuthentication(authentication)),
                "Bạn không có quyền xem số liệu phân tích.");
        log.info("GET /api/analytics/tickets/top-requesters - limit: {}", limit);
        return ResponseEntity.ok(analyticsService.getTopRequesters(limit));
    }

    // ==================== Asset Analytics ====================

    /**
     * Get asset health distribution.
     * GET /api/analytics/assets/health
     */
    @GetMapping("/assets/health")
    public ResponseEntity<List<HealthDistribution>> getAssetHealthDistribution(Authentication authentication) {
        ActorTiers.require(reporting.canSeeCompanyStats(actorContextService.fromAuthentication(authentication)),
                "Bạn không có quyền xem số liệu phân tích.");
        log.info("GET /api/analytics/assets/health");
        return ResponseEntity.ok(analyticsService.getAssetHealthDistribution());
    }

    /**
     * Get asset type distribution.
     * GET /api/analytics/assets/by-type
     */
    @GetMapping("/assets/by-type")
    public ResponseEntity<List<CategoryData>> getAssetTypeDistribution(Authentication authentication) {
        ActorTiers.require(reporting.canSeeCompanyStats(actorContextService.fromAuthentication(authentication)),
                "Bạn không có quyền xem số liệu phân tích.");
        log.info("GET /api/analytics/assets/by-type");
        return ResponseEntity.ok(analyticsService.getAssetTypeDistribution());
    }

    // ==================== Change Analytics ====================

    /**
     * Get change status distribution.
     * GET /api/analytics/changes/by-status
     */
    @GetMapping("/changes/by-status")
    public ResponseEntity<List<ChangeStatusData>> getChangeStatusDistribution(Authentication authentication) {
        ActorTiers.require(reporting.canSeeCompanyStats(actorContextService.fromAuthentication(authentication)),
                "Bạn không có quyền xem số liệu phân tích.");
        log.info("GET /api/analytics/changes/by-status");
        return ResponseEntity.ok(analyticsService.getChangeStatusDistribution());
    }

    /**
     * Get change type distribution.
     * GET /api/analytics/changes/by-type
     */
    @GetMapping("/changes/by-type")
    public ResponseEntity<List<ChangeTypeData>> getChangeTypeDistribution(Authentication authentication) {
        ActorTiers.require(reporting.canSeeCompanyStats(actorContextService.fromAuthentication(authentication)),
                "Bạn không có quyền xem số liệu phân tích.");
        log.info("GET /api/analytics/changes/by-type");
        return ResponseEntity.ok(analyticsService.getChangeTypeDistribution());
    }

    // ==================== Report Configs ====================

    /**
     * Get available reports.
     * GET /api/analytics/reports
     */
    @GetMapping("/reports")
    public ResponseEntity<List<ReportConfig>> getReports(Authentication authentication) {
        ActorTiers.require(reporting.canSeeCompanyStats(actorContextService.fromAuthentication(authentication)),
                "Bạn không có quyền xem số liệu phân tích.");
        log.info("GET /api/analytics/reports");
        return ResponseEntity.ok(reportService.getPublicReports());
    }

    /**
     * Get report by ID.
     * GET /api/analytics/reports/{id}
     */
    @GetMapping("/reports/{id}")
    public ResponseEntity<ReportConfig> getReportById(@PathVariable Long id, Authentication authentication) {
        ActorTiers.require(reporting.canSeeCompanyStats(actorContextService.fromAuthentication(authentication)),
                "Bạn không có quyền xem số liệu phân tích.");
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

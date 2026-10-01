package com.example.ticketing.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.example.ticketing.report.ReportGenerationService.*;

/**
 * REST Controller for Report Generation and Export
 * 
 * Endpoints:
 * - POST /api/reports/tickets - Generate ticket report
 * - POST /api/reports/sla - Generate SLA compliance report
 * - POST /api/reports/assets - Generate asset report
 * - POST /api/reports/export - Export report to CSV/Excel/PDF
 */
@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final ReportGenerationService reportGenerationService;
    private final ReportExportService reportExportService;

    public ReportController(ReportGenerationService reportGenerationService, ReportExportService reportExportService) {
        this.reportGenerationService = reportGenerationService;
        this.reportExportService = reportExportService;
    }

    // ==================== Ticket Report ====================

    /**
     * Generate ticket report
     * POST /api/reports/tickets
     * 
     * @param startDate Start date (yyyy-MM-dd)
     * @param endDate End date (yyyy-MM-dd)
     * @param days Pre-defined range (7, 30, 90) - overrides dates if provided
     */
    @PostMapping("/tickets")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_GIAM_DOC', 'ROLE_TRUONG_PHONG', 'ROLE_NHAN_VIEN')")
    public ResponseEntity<TicketReport> generateTicketReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer days) {
        
        log.info("Generating ticket report - startDate: {}, endDate: {}, days: {}", startDate, endDate, days);
        
        LocalDateTime start = parseStartDate(startDate, days);
        LocalDateTime end = parseEndDate(endDate, days);
        
        TicketReport report = reportGenerationService.generateTicketReport(start, end);
        return ResponseEntity.ok(report);
    }

    // ==================== SLA Compliance Report ====================

    /**
     * Generate SLA compliance report
     * POST /api/reports/sla
     */
    @PostMapping("/sla")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_GIAM_DOC', 'ROLE_TRUONG_PHONG', 'ROLE_NHAN_VIEN')")
    public ResponseEntity<SLAComplianceReport> generateSlaReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer days) {
        
        log.info("Generating SLA compliance report - startDate: {}, endDate: {}, days: {}", startDate, endDate, days);
        
        LocalDateTime start = parseStartDate(startDate, days);
        LocalDateTime end = parseEndDate(endDate, days);
        
        SLAComplianceReport report = reportGenerationService.generateSlaComplianceReport(start, end);
        return ResponseEntity.ok(report);
    }

    // ==================== Asset Report ====================

    /**
     * Generate asset inventory report
     * POST /api/reports/assets
     */
    @PostMapping("/assets")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_GIAM_DOC', 'ROLE_TRUONG_PHONG', 'ROLE_NHAN_VIEN')")
    public ResponseEntity<AssetReport> generateAssetReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer days) {
        
        log.info("Generating asset report - startDate: {}, endDate: {}, days: {}", startDate, endDate, days);
        
        LocalDateTime start = parseStartDate(startDate, days);
        LocalDateTime end = parseEndDate(endDate, days);
        
        AssetReport report = reportGenerationService.generateAssetReport(start, end);
        return ResponseEntity.ok(report);
    }

    // ==================== Export Endpoints ====================

    /**
     * Export ticket report
     * GET /api/reports/tickets/export?format=csv|excel&startDate=...&endDate=...&days=...
     */
    @GetMapping("/tickets/export")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_GIAM_DOC', 'ROLE_TRUONG_PHONG', 'ROLE_NHAN_VIEN')")
    public ResponseEntity<byte[]> exportTicketReport(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer days) {
        
        log.info("Exporting ticket report - format: {}, startDate: {}, endDate: {}, days: {}", format, startDate, endDate, days);
        
        LocalDateTime start = parseStartDate(startDate, days);
        LocalDateTime end = parseEndDate(endDate, days);
        
        TicketReport report = reportGenerationService.generateTicketReport(start, end);
        
        String filename;
        byte[] content;
        String contentType;
        
        if ("excel".equalsIgnoreCase(format)) {
            content = reportExportService.exportTicketReportToExcel(report);
            filename = "ticket-report.xlsx";
            contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else {
            content = reportExportService.exportTicketReportToCsv(report);
            filename = "ticket-report.csv";
            contentType = "text/csv";
        }
        
        return buildExportResponse(content, filename, contentType);
    }

    /**
     * Export SLA compliance report
     * GET /api/reports/sla/export
     */
    @GetMapping("/sla/export")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_GIAM_DOC', 'ROLE_TRUONG_PHONG', 'ROLE_NHAN_VIEN')")
    public ResponseEntity<byte[]> exportSlaReport(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer days) {
        
        log.info("Exporting SLA report - format: {}, startDate: {}, endDate: {}, days: {}", format, startDate, endDate, days);
        
        LocalDateTime start = parseStartDate(startDate, days);
        LocalDateTime end = parseEndDate(endDate, days);
        
        SLAComplianceReport report = reportGenerationService.generateSlaComplianceReport(start, end);
        
        String filename;
        byte[] content;
        String contentType;
        
        if ("excel".equalsIgnoreCase(format)) {
            content = reportExportService.exportSlaReportToExcel(report);
            filename = "sla-compliance-report.xlsx";
            contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else {
            content = reportExportService.exportSlaReportToCsv(report);
            filename = "sla-compliance-report.csv";
            contentType = "text/csv";
        }
        
        return buildExportResponse(content, filename, contentType);
    }

    /**
     * Export asset report
     * GET /api/reports/assets/export
     */
    @GetMapping("/assets/export")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_GIAM_DOC', 'ROLE_TRUONG_PHONG', 'ROLE_NHAN_VIEN')")
    public ResponseEntity<byte[]> exportAssetReport(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer days) {
        
        log.info("Exporting asset report - format: {}, startDate: {}, endDate: {}, days: {}", format, startDate, endDate, days);
        
        LocalDateTime start = parseStartDate(startDate, days);
        LocalDateTime end = parseEndDate(endDate, days);
        
        AssetReport report = reportGenerationService.generateAssetReport(start, end);
        
        String filename;
        byte[] content;
        String contentType;
        
        if ("excel".equalsIgnoreCase(format)) {
            content = reportExportService.exportAssetReportToExcel(report);
            filename = "asset-inventory-report.xlsx";
            contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else {
            content = reportExportService.exportAssetReportToCsv(report);
            filename = "asset-inventory-report.csv";
            contentType = "text/csv";
        }
        
        return buildExportResponse(content, filename, contentType);
    }

    // ==================== Report Templates ====================

    /**
     * Get available report templates
     * GET /api/reports/templates
     */
    @GetMapping("/templates")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_GIAM_DOC', 'ROLE_TRUONG_PHONG', 'ROLE_NHAN_VIEN')")
    public ResponseEntity<List<ReportTemplate>> getReportTemplates() {
        List<ReportTemplate> templates = List.of(
            new ReportTemplate("TICKETS", "Ticket Report", "Overview of tickets with status, priority, and volume analysis", "ticket"),
            new ReportTemplate("SLA", "SLA Compliance Report", "Track SLA compliance rates and breach analysis", "sla"),
            new ReportTemplate("ASSETS", "Asset Inventory Report", "Complete asset inventory with health and status", "asset")
        );
        return ResponseEntity.ok(templates);
    }

    // ==================== Helper Methods ====================

    private LocalDateTime parseStartDate(String startDate, Integer days) {
        if (days != null && days > 0) {
            return LocalDateTime.now().minusDays(days).withHour(0).withMinute(0).withSecond(0);
        }
        if (startDate != null && !startDate.isEmpty()) {
            return LocalDate.parse(startDate).atStartOfDay();
        }
        // Default: last 30 days
        return LocalDateTime.now().minusDays(30).withHour(0).withMinute(0).withSecond(0);
    }

    private LocalDateTime parseEndDate(String endDate, Integer days) {
        if (days != null && days > 0) {
            return LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);
        }
        if (endDate != null && !endDate.isEmpty()) {
            return LocalDate.parse(endDate).atTime(LocalTime.MAX);
        }
        // Default: today
        return LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);
    }

    private ResponseEntity<byte[]> buildExportResponse(byte[] content, String filename, String contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(content.length);
        return ResponseEntity.ok()
                .headers(headers)
                .body(content);
    }

    // ==================== DTOs ====================

    public static class ReportTemplate {
        private String type;
        private String name;
        private String description;
        private String icon;

        public ReportTemplate(String type, String name, String description, String icon) {
            this.type = type;
            this.name = name;
            this.description = description;
            this.icon = icon;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getIcon() { return icon; }
        public void setIcon(String icon) { this.icon = icon; }
    }
}

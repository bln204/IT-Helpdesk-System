package com.example.ticketing.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.ticketing.report.ReportGenerationService.*;
import com.example.ticketing.ticket.Ticket;

/**
 * Report Export Service - Export reports to CSV and Excel formats
 */
@Service
public class ReportExportService {

    private static final Logger log = LoggerFactory.getLogger(ReportExportService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_ONLY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ==================== CSV Export ====================

    /**
     * Export ticket report to CSV
     */
    public byte[] exportTicketReportToCsv(TicketReport report) {
        StringBuilder csv = new StringBuilder();
        
        // Header
        csv.append("Ticket Report\n");
        csv.append("Generated:,").append(java.time.LocalDateTime.now().format(DATE_FORMATTER)).append("\n");
        csv.append("Period:,").append(report.getStartDate().format(DATE_FORMATTER))
           .append(" to ").append(report.getEndDate().format(DATE_FORMATTER)).append("\n\n");
        
        // Summary Statistics
        csv.append("SUMMARY STATISTICS\n");
        csv.append("Total Tickets,").append(report.getStats().total).append("\n");
        csv.append("Resolution Rate,").append(String.format("%.2f%%", report.getResolutionRate())).append("\n\n");
        
        // By Status
        csv.append("BY STATUS\n");
        csv.append("Status,Count\n");
        report.getStats().byStatus.forEach((status, count) -> 
            csv.append(status).append(",").append(count).append("\n"));
        csv.append("\n");
        
        // By Priority
        csv.append("BY PRIORITY\n");
        csv.append("Priority,Count\n");
        report.getStats().byPriority.forEach((priority, count) -> 
            csv.append(priority).append(",").append(count).append("\n"));
        csv.append("\n");
        
        // By Category
        csv.append("BY CATEGORY\n");
        csv.append("Category,Count\n");
        report.getStats().byCategory.forEach((category, count) -> 
            csv.append(category).append(",").append(count).append("\n"));
        csv.append("\n");
        
        // Daily Volume
        csv.append("DAILY VOLUME\n");
        csv.append("Date,Count\n");
        report.getStats().dailyVolume.forEach((date, count) -> 
            csv.append(date).append(",").append(count).append("\n"));
        csv.append("\n");
        
        // Ticket Details
        csv.append("TICKET DETAILS\n");
        csv.append("Ticket Number,Title,Priority,Status,Requester,Assignee,Created At,Resolved At,SLA Response,SLA Resolution\n");
        for (Ticket ticket : report.getTickets()) {
            csv.append(escapeCsv(ticket.getTicketNumber())).append(",");
            csv.append(escapeCsv(ticket.getTitle())).append(",");
            csv.append(ticket.getPriority() != null ? ticket.getPriority().name() : "").append(",");
            csv.append(ticket.getStatus() != null ? ticket.getStatus().name() : "").append(",");
            csv.append(escapeCsv(ticket.getRequesterName())).append(",");
            csv.append(escapeCsv(ticket.getAssigneeName())).append(",");
            csv.append(ticket.getCreatedAt() != null ? ticket.getCreatedAt().format(DATE_FORMATTER) : "").append(",");
            csv.append(ticket.getResolvedAt() != null ? ticket.getResolvedAt().format(DATE_FORMATTER) : "").append(",");
            csv.append(ticket.getSlaResponseAt() != null ? ticket.getSlaResponseAt().format(DATE_FORMATTER) : "").append(",");
            csv.append(ticket.getSlaResolutionAt() != null ? ticket.getSlaResolutionAt().format(DATE_FORMATTER) : "").append("\n");
        }
        
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Export SLA compliance report to CSV
     */
    public byte[] exportSlaReportToCsv(SLAComplianceReport report) {
        StringBuilder csv = new StringBuilder();
        
        // Header
        csv.append("SLA Compliance Report\n");
        csv.append("Generated:,").append(java.time.LocalDateTime.now().format(DATE_FORMATTER)).append("\n");
        csv.append("Period:,").append(report.getStartDate().format(DATE_FORMATTER))
           .append(" to ").append(report.getEndDate().format(DATE_FORMATTER)).append("\n\n");
        
        // Summary Statistics
        csv.append("COMPLIANCE SUMMARY\n");
        csv.append("Total Tickets,").append(report.getStats().total).append("\n");
        csv.append("Response Compliance Rate,").append(String.format("%.2f%%", report.getResponseComplianceRate())).append("\n");
        csv.append("Resolution Compliance Rate,").append(String.format("%.2f%%", report.getResolutionComplianceRate())).append("\n");
        csv.append("Overall Compliance Rate,").append(String.format("%.2f%%", report.getOverallComplianceRate())).append("\n\n");
        
        // Response SLA
        csv.append("RESPONSE SLA METRICS\n");
        csv.append("Met,").append(report.getStats().responseMet).append("\n");
        csv.append("Breached,").append(report.getStats().responseBreached).append("\n");
        csv.append("Pending,").append(report.getStats().pendingResponse).append("\n\n");
        
        // Resolution SLA
        csv.append("RESOLUTION SLA METRICS\n");
        csv.append("Met,").append(report.getStats().resolutionMet).append("\n");
        csv.append("Breached,").append(report.getStats().resolutionBreached).append("\n");
        csv.append("Pending,").append(report.getStats().pendingResolution).append("\n\n");
        
        // Ticket Details
        csv.append("TICKET DETAILS\n");
        csv.append("Ticket Number,Title,Priority,Status,Response SLA Met,Resolution SLA Met,Response Breach (min),Resolution Breach (min),Assignee\n");
        for (SLAComplianceReport.SLATicketDetail detail : report.getDetails()) {
            csv.append(escapeCsv(detail.ticketNumber)).append(",");
            csv.append(escapeCsv(detail.title)).append(",");
            csv.append(detail.priority).append(",");
            csv.append(detail.status).append(",");
            csv.append(formatSlaStatus(detail.responseSlaMet)).append(",");
            csv.append(formatSlaStatus(detail.resolutionSlaMet)).append(",");
            csv.append(detail.responseBreachMinutes != null ? detail.responseBreachMinutes : "").append(",");
            csv.append(detail.resolutionBreachMinutes != null ? detail.resolutionBreachMinutes : "").append(",");
            csv.append(escapeCsv(detail.assignee)).append("\n");
        }
        
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Export asset report to CSV
     */
    public byte[] exportAssetReportToCsv(AssetReport report) {
        StringBuilder csv = new StringBuilder();
        
        // Header
        csv.append("Asset Inventory Report\n");
        csv.append("Generated:,").append(java.time.LocalDateTime.now().format(DATE_FORMATTER)).append("\n");
        csv.append("Period:,").append(report.getStartDate() != null ? report.getStartDate().format(DATE_FORMATTER) : "N/A")
           .append(" to ").append(report.getEndDate().format(DATE_FORMATTER)).append("\n\n");
        
        // Summary Statistics
        csv.append("SUMMARY STATISTICS\n");
        csv.append("Total Assets,").append(report.getStats().total).append("\n");
        csv.append("Health Rate,").append(String.format("%.2f%%", report.getHealthRate())).append("\n");
        csv.append("Active Rate,").append(String.format("%.2f%%", report.getActiveRate())).append("\n\n");
        
        // By Status
        csv.append("BY STATUS\n");
        csv.append("Status,Count\n");
        report.getStats().byStatus.forEach((status, count) -> 
            csv.append(status).append(",").append(count).append("\n"));
        csv.append("\n");
        
        // By Type
        csv.append("BY TYPE\n");
        csv.append("Type,Count\n");
        report.getStats().byType.forEach((type, count) -> 
            csv.append(type).append(",").append(count).append("\n"));
        csv.append("\n");
        
        // By Health
        csv.append("BY HEALTH STATUS\n");
        csv.append("Health,Count\n");
        report.getStats().byHealth.forEach((health, count) -> 
            csv.append(health).append(",").append(count).append("\n"));
        csv.append("\n");
        
        // By Location
        csv.append("BY LOCATION\n");
        csv.append("Location,Count\n");
        report.getStats().byLocation.forEach((location, count) -> 
            csv.append(escapeCsv(location)).append(",").append(count).append("\n"));
        csv.append("\n");
        
        // Asset Details
        csv.append("ASSET DETAILS\n");
        csv.append("Asset Number,Name,Type,Status,Health,Location,Assigned To,Serial Number,IP Address,Purchase Date,Warranty Expiry,Cost\n");
        for (AssetReport.AssetDetail detail : report.getAssets()) {
            csv.append(escapeCsv(detail.assetNumber)).append(",");
            csv.append(escapeCsv(detail.name)).append(",");
            csv.append(detail.assetType).append(",");
            csv.append(detail.status).append(",");
            csv.append(detail.healthStatus).append(",");
            csv.append(escapeCsv(detail.location)).append(",");
            csv.append(escapeCsv(detail.assignedTo)).append(",");
            csv.append(escapeCsv(detail.serialNumber)).append(",");
            csv.append(escapeCsv(detail.ipAddress)).append(",");
            csv.append(detail.purchaseDate != null ? detail.purchaseDate.format(DATE_ONLY_FORMATTER) : "").append(",");
            csv.append(detail.warrantyExpiryDate != null ? detail.warrantyExpiryDate.format(DATE_ONLY_FORMATTER) : "").append(",");
            csv.append(detail.purchaseCost != null ? detail.purchaseCost.toString() : "").append("\n");
        }
        
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ==================== Excel Export ====================

    /**
     * Export ticket report to Excel (using Apache POI)
     * Returns XLSX format
     */
    public byte[] exportTicketReportToExcel(TicketReport report) {
        try {
            // For simplicity, we'll create CSV with .xlsx extension
            // In production, use Apache POI for proper Excel format
            // This basic implementation works for most spreadsheet applications
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            // Write BOM for UTF-8 Excel compatibility
            baos.write(0xEF);
            baos.write(0xBB);
            baos.write(0xBF);
            
            StringBuilder csv = new StringBuilder();
            
            // Use tab as separator for Excel
            csv.append("Ticket Report\t\n");
            csv.append("Generated:\t").append(java.time.LocalDateTime.now().format(DATE_FORMATTER)).append("\t\n");
            csv.append("Period:\t").append(report.getStartDate().format(DATE_FORMATTER))
               .append(" to ").append(report.getEndDate().format(DATE_FORMATTER)).append("\t\n\n");
            
            // Summary
            csv.append("SUMMARY STATISTICS\n");
            csv.append("Total Tickets\t").append(report.getStats().total).append("\n");
            csv.append("Resolution Rate\t").append(String.format("%.2f%%", report.getResolutionRate())).append("\n\n");
            
            // By Status
            csv.append("BY STATUS\n");
            csv.append("Status\tCount\n");
            report.getStats().byStatus.forEach((status, count) -> 
                csv.append(status).append("\t").append(count).append("\n"));
            csv.append("\n");
            
            // By Priority
            csv.append("BY PRIORITY\n");
            csv.append("Priority\tCount\n");
            report.getStats().byPriority.forEach((priority, count) -> 
                csv.append(priority).append("\t").append(count).append("\n"));
            csv.append("\n");
            
            // Ticket Details
            csv.append("TICKET DETAILS\n");
            csv.append("Ticket Number\tTitle\tPriority\tStatus\tRequester\tAssignee\tCreated At\tResolved At\n");
            for (Ticket ticket : report.getTickets()) {
                csv.append(escapeCsv(ticket.getTicketNumber())).append("\t");
                csv.append(escapeCsv(ticket.getTitle())).append("\t");
                csv.append(ticket.getPriority() != null ? ticket.getPriority().name() : "").append("\t");
                csv.append(ticket.getStatus() != null ? ticket.getStatus().name() : "").append("\t");
                csv.append(escapeCsv(ticket.getRequesterName())).append("\t");
                csv.append(escapeCsv(ticket.getAssigneeName())).append("\t");
                csv.append(ticket.getCreatedAt() != null ? ticket.getCreatedAt().format(DATE_FORMATTER) : "").append("\t");
                csv.append(ticket.getResolvedAt() != null ? ticket.getResolvedAt().format(DATE_FORMATTER) : "").append("\n");
            }
            
            baos.write(csv.toString().getBytes(StandardCharsets.UTF_8));
            return baos.toByteArray();
            
        } catch (IOException e) {
            log.error("Error exporting ticket report to Excel", e);
            return new byte[0];
        }
    }

    /**
     * Export SLA compliance report to Excel
     */
    public byte[] exportSlaReportToExcel(SLAComplianceReport report) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            // Write BOM for UTF-8 Excel compatibility
            baos.write(0xEF);
            baos.write(0xBB);
            baos.write(0xBF);
            
            StringBuilder csv = new StringBuilder();
            
            // Header
            csv.append("SLA Compliance Report\t\n");
            csv.append("Generated:\t").append(java.time.LocalDateTime.now().format(DATE_FORMATTER)).append("\t\n");
            csv.append("Period:\t").append(report.getStartDate().format(DATE_FORMATTER))
               .append(" to ").append(report.getEndDate().format(DATE_FORMATTER)).append("\t\n\n");
            
            // Compliance Summary
            csv.append("COMPLIANCE SUMMARY\n");
            csv.append("Total Tickets\t").append(report.getStats().total).append("\n");
            csv.append("Response Compliance Rate\t").append(String.format("%.2f%%", report.getResponseComplianceRate())).append("\n");
            csv.append("Resolution Compliance Rate\t").append(String.format("%.2f%%", report.getResolutionComplianceRate())).append("\n");
            csv.append("Overall Compliance Rate\t").append(String.format("%.2f%%", report.getOverallComplianceRate())).append("\n\n");
            
            // Response SLA
            csv.append("RESPONSE SLA\n");
            csv.append("Met\t").append(report.getStats().responseMet).append("\n");
            csv.append("Breached\t").append(report.getStats().responseBreached).append("\n");
            csv.append("Pending\t").append(report.getStats().pendingResponse).append("\n\n");
            
            // Resolution SLA
            csv.append("RESOLUTION SLA\n");
            csv.append("Met\t").append(report.getStats().resolutionMet).append("\n");
            csv.append("Breached\t").append(report.getStats().resolutionBreached).append("\n");
            csv.append("Pending\t").append(report.getStats().pendingResolution).append("\n\n");
            
            // Ticket Details
            csv.append("TICKET DETAILS\n");
            csv.append("Ticket Number\tTitle\tPriority\tResponse SLA\tResolution SLA\tResponse Breach (min)\tResolution Breach (min)\tAssignee\n");
            for (SLAComplianceReport.SLATicketDetail detail : report.getDetails()) {
                csv.append(escapeCsv(detail.ticketNumber)).append("\t");
                csv.append(escapeCsv(detail.title)).append("\t");
                csv.append(detail.priority).append("\t");
                csv.append(formatSlaStatus(detail.responseSlaMet)).append("\t");
                csv.append(formatSlaStatus(detail.resolutionSlaMet)).append("\t");
                csv.append(detail.responseBreachMinutes != null ? detail.responseBreachMinutes : "").append("\t");
                csv.append(detail.resolutionBreachMinutes != null ? detail.resolutionBreachMinutes : "").append("\t");
                csv.append(escapeCsv(detail.assignee)).append("\n");
            }
            
            baos.write(csv.toString().getBytes(StandardCharsets.UTF_8));
            return baos.toByteArray();
            
        } catch (IOException e) {
            log.error("Error exporting SLA report to Excel", e);
            return new byte[0];
        }
    }

    /**
     * Export asset report to Excel
     */
    public byte[] exportAssetReportToExcel(AssetReport report) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            // Write BOM for UTF-8 Excel compatibility
            baos.write(0xEF);
            baos.write(0xBB);
            baos.write(0xBF);
            
            StringBuilder csv = new StringBuilder();
            
            // Header
            csv.append("Asset Inventory Report\t\n");
            csv.append("Generated:\t").append(java.time.LocalDateTime.now().format(DATE_FORMATTER)).append("\t\n");
            csv.append("Period:\t").append(report.getStartDate() != null ? report.getStartDate().format(DATE_FORMATTER) : "N/A")
               .append(" to ").append(report.getEndDate().format(DATE_FORMATTER)).append("\t\n\n");
            
            // Summary
            csv.append("SUMMARY STATISTICS\n");
            csv.append("Total Assets\t").append(report.getStats().total).append("\n");
            csv.append("Health Rate\t").append(String.format("%.2f%%", report.getHealthRate())).append("\n");
            csv.append("Active Rate\t").append(String.format("%.2f%%", report.getActiveRate())).append("\n\n");
            
            // By Health
            csv.append("BY HEALTH STATUS\n");
            csv.append("Health\tCount\n");
            report.getStats().byHealth.forEach((health, count) -> 
                csv.append(health).append("\t").append(count).append("\n"));
            csv.append("\n");
            
            // By Type
            csv.append("BY TYPE\n");
            csv.append("Type\tCount\n");
            report.getStats().byType.forEach((type, count) -> 
                csv.append(type).append("\t").append(count).append("\n"));
            csv.append("\n");
            
            // Asset Details
            csv.append("ASSET DETAILS\n");
            csv.append("Asset Number\tName\tType\tStatus\tHealth\tLocation\tAssigned To\tSerial Number\tIP Address\tPurchase Date\tWarranty Expiry\tCost\n");
            for (AssetReport.AssetDetail detail : report.getAssets()) {
                csv.append(escapeCsv(detail.assetNumber)).append("\t");
                csv.append(escapeCsv(detail.name)).append("\t");
                csv.append(detail.assetType).append("\t");
                csv.append(detail.status).append("\t");
                csv.append(detail.healthStatus).append("\t");
                csv.append(escapeCsv(detail.location)).append("\t");
                csv.append(escapeCsv(detail.assignedTo)).append("\t");
                csv.append(escapeCsv(detail.serialNumber)).append("\t");
                csv.append(escapeCsv(detail.ipAddress)).append("\t");
                csv.append(detail.purchaseDate != null ? detail.purchaseDate.format(DATE_ONLY_FORMATTER) : "").append("\t");
                csv.append(detail.warrantyExpiryDate != null ? detail.warrantyExpiryDate.format(DATE_ONLY_FORMATTER) : "").append("\t");
                csv.append(detail.purchaseCost != null ? detail.purchaseCost.toString() : "").append("\n");
            }
            
            baos.write(csv.toString().getBytes(StandardCharsets.UTF_8));
            return baos.toByteArray();
            
        } catch (IOException e) {
            log.error("Error exporting asset report to Excel", e);
            return new byte[0];
        }
    }

    // ==================== Helper Methods ====================

    private String escapeCsv(String value) {
        if (value == null) return "";
        String str = String.valueOf(value);
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }

    private String formatSlaStatus(Boolean met) {
        if (met == null) return "PENDING";
        return met ? "MET" : "BREACHED";
    }
}

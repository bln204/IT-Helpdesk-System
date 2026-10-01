package com.example.ticketing.report;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.asset.Asset;
import com.example.ticketing.asset.AssetRepository;
import com.example.ticketing.ticket.Ticket;
import com.example.ticketing.ticket.TicketRepository;
import com.example.ticketing.ticket.TicketTypes.TicketStatus;

/**
 * Report Generation Service - Generate various report types
 * 
 * Supports:
 * - Ticket Reports (volume, status, priority)
 * - SLA Compliance Reports
 * - Asset Reports (inventory, health)
 */
@Service
@Transactional(readOnly = true)
public class ReportGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerationService.class);

    private final TicketRepository ticketRepository;
    private final AssetRepository assetRepository;

    public ReportGenerationService(TicketRepository ticketRepository, AssetRepository assetRepository) {
        this.ticketRepository = ticketRepository;
        this.assetRepository = assetRepository;
    }

    // ==================== Ticket Report ====================

    /**
     * Generate ticket report for date range.
     * 
     * @param startDate Start date
     * @param endDate End date
     * @return TicketReport containing tickets and statistics
     */
    public TicketReport generateTicketReport(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Generating ticket report from {} to {}", startDate, endDate);

        TicketReport report = new TicketReport();
        report.setStartDate(startDate);
        report.setEndDate(endDate);

        // Get tickets in date range
        List<Ticket> tickets = ticketRepository.findByCreatedAtBetween(startDate, endDate);
        
        // Calculate statistics
        TicketReport.TicketStats stats = new TicketReport.TicketStats();
        stats.total = tickets.size();
        stats.byStatus = new HashMap<>();
        stats.byPriority = new HashMap<>();
        stats.byCategory = new HashMap<>();
        stats.dailyVolume = new LinkedHashMap<>();

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (Ticket ticket : tickets) {
            // Count by status
            String status = ticket.getStatus() != null ? ticket.getStatus().name() : "UNKNOWN";
            stats.byStatus.merge(status, 1, Integer::sum);

            // Count by priority
            String priority = ticket.getPriority() != null ? ticket.getPriority().name() : "UNKNOWN";
            stats.byPriority.merge(priority, 1, Integer::sum);

            // Count by category
            String category = ticket.getCategory() != null ? ticket.getCategory().name() : "UNKNOWN";
            stats.byCategory.merge(category, 1, Integer::sum);

            // Daily volume
            String dateKey = ticket.getCreatedAt().format(dateFormatter);
            stats.dailyVolume.merge(dateKey, 1, Integer::sum);
        }

        // Sort daily volume by date
        Map<String, Integer> sortedDailyVolume = new LinkedHashMap<>();
        stats.dailyVolume.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEachOrdered(e -> sortedDailyVolume.put(e.getKey(), e.getValue()));
        stats.dailyVolume = sortedDailyVolume;

        report.setStats(stats);
        report.setTickets(tickets);

        // Calculate resolution rate
        int resolved = stats.byStatus.getOrDefault("RESOLVED", 0) + stats.byStatus.getOrDefault("CLOSED", 0);
        report.setResolutionRate(stats.total > 0 ? (double) resolved / stats.total * 100 : 0);

        return report;
    }

    // ==================== SLA Compliance Report ====================

    /**
     * Generate SLA compliance report.
     * 
     * @param startDate Start date
     * @param endDate End date
     * @return SLAComplianceReport with compliance metrics
     */
    public SLAComplianceReport generateSlaComplianceReport(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Generating SLA compliance report from {} to {}", startDate, endDate);

        SLAComplianceReport report = new SLAComplianceReport();
        report.setStartDate(startDate);
        report.setEndDate(endDate);

        List<Ticket> tickets = ticketRepository.findByCreatedAtBetween(startDate, endDate);
        
        SLAComplianceReport.SLAStats stats = new SLAComplianceReport.SLAStats();
        stats.total = tickets.size();
        stats.responseMet = 0;
        stats.responseBreached = 0;
        stats.resolutionMet = 0;
        stats.resolutionBreached = 0;
        stats.pendingResponse = 0;
        stats.pendingResolution = 0;

        List<SLAComplianceReport.SLATicketDetail> details = new ArrayList<>();

        for (Ticket ticket : tickets) {
            SLAComplianceReport.SLATicketDetail detail = new SLAComplianceReport.SLATicketDetail();
            detail.ticketNumber = ticket.getTicketNumber();
            detail.title = ticket.getTitle();
            detail.priority = ticket.getPriority() != null ? ticket.getPriority().name() : "UNKNOWN";
            detail.status = ticket.getStatus() != null ? ticket.getStatus().name() : "UNKNOWN";
            detail.createdAt = ticket.getCreatedAt();
            detail.slaResponseAt = ticket.getSlaResponseAt();
            detail.slaResolutionAt = ticket.getSlaResolutionAt();
            detail.firstResponseAt = ticket.getFirstResponseAt();
            detail.resolvedAt = ticket.getResolvedAt();
            detail.assignee = ticket.getAssigneeName();

            // Calculate response SLA
            if (ticket.getSlaResponseAt() != null) {
                if (ticket.getFirstResponseAt() != null) {
                    // Already responded
                    if (ticket.getFirstResponseAt().isBefore(ticket.getSlaResponseAt()) || 
                        ticket.getFirstResponseAt().isEqual(ticket.getSlaResponseAt())) {
                        detail.responseSlaMet = true;
                        stats.responseMet++;
                    } else {
                        detail.responseSlaMet = false;
                        detail.responseBreachMinutes = java.time.Duration.between(ticket.getSlaResponseAt(), ticket.getFirstResponseAt()).toMinutes();
                        stats.responseBreached++;
                    }
                } else if (!isTicketClosed(ticket)) {
                    // Pending response
                    detail.responseSlaMet = null; // In progress
                    stats.pendingResponse++;
                    if (LocalDateTime.now().isAfter(ticket.getSlaResponseAt())) {
                        detail.responseBreachMinutes = java.time.Duration.between(ticket.getSlaResponseAt(), LocalDateTime.now()).toMinutes();
                        stats.responseBreached++;
                    }
                }
            }

            // Calculate resolution SLA
            if (ticket.getSlaResolutionAt() != null) {
                if (ticket.getResolvedAt() != null || ticket.getStatus() == TicketStatus.CLOSED) {
                    // Already resolved
                    if (ticket.getResolvedAt().isBefore(ticket.getSlaResolutionAt()) || 
                        ticket.getResolvedAt().isEqual(ticket.getSlaResolutionAt())) {
                        detail.resolutionSlaMet = true;
                        stats.resolutionMet++;
                    } else {
                        detail.resolutionSlaMet = false;
                        detail.resolutionBreachMinutes = java.time.Duration.between(ticket.getSlaResolutionAt(), ticket.getResolvedAt()).toMinutes();
                        stats.resolutionBreached++;
                    }
                } else if (!isTicketClosed(ticket)) {
                    // Pending resolution
                    detail.resolutionSlaMet = null; // In progress
                    stats.pendingResolution++;
                    if (LocalDateTime.now().isAfter(ticket.getSlaResolutionAt())) {
                        detail.resolutionBreachMinutes = java.time.Duration.between(ticket.getSlaResolutionAt(), LocalDateTime.now()).toMinutes();
                        stats.resolutionBreached++;
                    }
                }
            }

            details.add(detail);
        }

        // Calculate compliance rates
        int resolvedTickets = stats.responseMet + stats.responseBreached;
        report.setResponseComplianceRate(resolvedTickets > 0 ? (double) stats.responseMet / resolvedTickets * 100 : 0);
        
        int resolvedResolution = stats.resolutionMet + stats.resolutionBreached;
        report.setResolutionComplianceRate(resolvedResolution > 0 ? (double) stats.resolutionMet / resolvedResolution * 100 : 0);

        // Overall compliance (average of response and resolution)
        report.setOverallComplianceRate((report.getResponseComplianceRate() + report.getResolutionComplianceRate()) / 2);

        report.setStats(stats);
        report.setDetails(details);

        return report;
    }

    // ==================== Asset Report ====================

    /**
     * Generate asset inventory report.
     * 
     * @param startDate Start date (optional, for filtering by purchase date)
     * @param endDate End date
     * @return AssetReport with inventory data
     */
    public AssetReport generateAssetReport(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Generating asset report from {} to {}", startDate, endDate);

        AssetReport report = new AssetReport();
        report.setStartDate(startDate);
        report.setEndDate(endDate);

        // Get all assets (for inventory)
        List<Asset> assets = assetRepository.findAll();
        
        AssetReport.AssetStats stats = new AssetReport.AssetStats();
        stats.total = assets.size();
        stats.byStatus = new HashMap<>();
        stats.byType = new HashMap<>();
        stats.byHealth = new HashMap<>();
        stats.byLocation = new HashMap<>();

        List<AssetReport.AssetDetail> details = new ArrayList<>();

        for (Asset asset : assets) {
            // Count by status
            String status = asset.getStatus() != null ? asset.getStatus().name() : "UNKNOWN";
            stats.byStatus.merge(status, 1, Integer::sum);

            // Count by type
            String type = asset.getAssetType() != null ? asset.getAssetType().name() : "UNKNOWN";
            stats.byType.merge(type, 1, Integer::sum);

            // Count by health
            String health = asset.getHealthStatus() != null ? asset.getHealthStatus().name() : "UNKNOWN";
            stats.byHealth.merge(health, 1, Integer::sum);

            // Count by location
            String location = asset.getLocation() != null ? asset.getLocation() : "Unassigned";
            stats.byLocation.merge(location, 1, Integer::sum);

            // Build detail
            AssetReport.AssetDetail detail = new AssetReport.AssetDetail();
            detail.id = asset.getId();
            detail.assetNumber = asset.getAssetNumber();
            detail.name = asset.getName();
            detail.assetType = type;
            detail.status = status;
            detail.healthStatus = health;
            detail.location = asset.getLocation();
            detail.assignedTo = asset.getAssignedTo();
            detail.serialNumber = asset.getSerialNumber();
            detail.ipAddress = asset.getIpAddress();
            detail.purchaseDate = asset.getPurchaseDate();
            detail.warrantyExpiryDate = asset.getWarrantyExpiryDate();
            if (asset.getPurchaseCost() != null) {
                detail.purchaseCost = asset.getPurchaseCost().doubleValue();
            }
            details.add(detail);
        }

        report.setStats(stats);
        report.setAssets(details);

        // Calculate health rate
        int healthy = stats.byHealth.getOrDefault("HEALTHY", 0);
        report.setHealthRate(stats.total > 0 ? (double) healthy / stats.total * 100 : 0);

        // Calculate active rate
        int active = stats.byStatus.getOrDefault("ACTIVE", 0);
        report.setActiveRate(stats.total > 0 ? (double) active / stats.total * 100 : 0);

        return report;
    }

    // ==================== Helper Methods ====================

    private boolean isTicketClosed(Ticket ticket) {
        TicketStatus status = ticket.getStatus();
        return status == TicketStatus.CLOSED || status == TicketStatus.RESOLVED || 
               status == TicketStatus.CANCELLED;
    }

    // ==================== Report DTOs ====================

    /**
     * Ticket Report DTO
     */
    public static class TicketReport {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private TicketStats stats;
        private List<Ticket> tickets;
        private double resolutionRate;

        // Getters and Setters
        public LocalDateTime getStartDate() { return startDate; }
        public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
        public LocalDateTime getEndDate() { return endDate; }
        public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
        public TicketStats getStats() { return stats; }
        public void setStats(TicketStats stats) { this.stats = stats; }
        public List<Ticket> getTickets() { return tickets; }
        public void setTickets(List<Ticket> tickets) { this.tickets = tickets; }
        public double getResolutionRate() { return resolutionRate; }
        public void setResolutionRate(double resolutionRate) { this.resolutionRate = resolutionRate; }

        public static class TicketStats {
            public int total;
            public Map<String, Integer> byStatus;
            public Map<String, Integer> byPriority;
            public Map<String, Integer> byCategory;
            public Map<String, Integer> dailyVolume;
        }
    }

    /**
     * SLA Compliance Report DTO
     */
    public static class SLAComplianceReport {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private SLAStats stats;
        private List<SLATicketDetail> details;
        private double responseComplianceRate;
        private double resolutionComplianceRate;
        private double overallComplianceRate;

        // Getters and Setters
        public LocalDateTime getStartDate() { return startDate; }
        public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
        public LocalDateTime getEndDate() { return endDate; }
        public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
        public SLAStats getStats() { return stats; }
        public void setStats(SLAStats stats) { this.stats = stats; }
        public List<SLATicketDetail> getDetails() { return details; }
        public void setDetails(List<SLATicketDetail> details) { this.details = details; }
        public double getResponseComplianceRate() { return responseComplianceRate; }
        public void setResponseComplianceRate(double responseComplianceRate) { this.responseComplianceRate = responseComplianceRate; }
        public double getResolutionComplianceRate() { return resolutionComplianceRate; }
        public void setResolutionComplianceRate(double resolutionComplianceRate) { this.resolutionComplianceRate = resolutionComplianceRate; }
        public double getOverallComplianceRate() { return overallComplianceRate; }
        public void setOverallComplianceRate(double overallComplianceRate) { this.overallComplianceRate = overallComplianceRate; }

        public static class SLAStats {
            public int total;
            public int responseMet;
            public int responseBreached;
            public int resolutionMet;
            public int resolutionBreached;
            public int pendingResponse;
            public int pendingResolution;
        }

        public static class SLATicketDetail {
            public String ticketNumber;
            public String title;
            public String priority;
            public String status;
            public LocalDateTime createdAt;
            public LocalDateTime slaResponseAt;
            public LocalDateTime slaResolutionAt;
            public LocalDateTime firstResponseAt;
            public LocalDateTime resolvedAt;
            public String assignee;
            public Boolean responseSlaMet; // true = met, false = breached, null = pending
            public Boolean resolutionSlaMet; // true = met, false = breached, null = pending
            public Long responseBreachMinutes;
            public Long resolutionBreachMinutes;
        }
    }

    /**
     * Asset Report DTO
     */
    public static class AssetReport {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private AssetStats stats;
        private List<AssetDetail> assets;
        private double healthRate;
        private double activeRate;

        // Getters and Setters
        public LocalDateTime getStartDate() { return startDate; }
        public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
        public LocalDateTime getEndDate() { return endDate; }
        public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
        public AssetStats getStats() { return stats; }
        public void setStats(AssetStats stats) { this.stats = stats; }
        public List<AssetDetail> getAssets() { return assets; }
        public void setAssets(List<AssetDetail> assets) { this.assets = assets; }
        public double getHealthRate() { return healthRate; }
        public void setHealthRate(double healthRate) { this.healthRate = healthRate; }
        public double getActiveRate() { return activeRate; }
        public void setActiveRate(double activeRate) { this.activeRate = activeRate; }

        public static class AssetStats {
            public int total;
            public Map<String, Integer> byStatus;
            public Map<String, Integer> byType;
            public Map<String, Integer> byHealth;
            public Map<String, Integer> byLocation;
        }

        public static class AssetDetail {
            public Long id;
            public String assetNumber;
            public String name;
            public String assetType;
            public String status;
            public String healthStatus;
            public String location;
            public String assignedTo;
            public String serialNumber;
            public String ipAddress;
            public java.time.LocalDate purchaseDate;
            public java.time.LocalDate warrantyExpiryDate;
            public Double purchaseCost;
        }
    }
}

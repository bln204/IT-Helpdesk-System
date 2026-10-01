package com.example.ticketing.report;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.asset.AssetRepository;
import com.example.ticketing.asset.Asset.AssetStatus;
import com.example.ticketing.asset.Asset.HealthStatus;
import com.example.ticketing.change.ChangeRequest;
import com.example.ticketing.change.ChangeRequestRepository;
import com.example.ticketing.ticket.TicketRepository;
import com.example.ticketing.ticket.TicketTypes.TicketStatus;

/**
 * Analytics Service - Compute real-time metrics for dashboards.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final TicketRepository ticketRepository;
    private final AssetRepository assetRepository;
    private final ChangeRequestRepository changeRepository;

    public AnalyticsService(
            TicketRepository ticketRepository,
            AssetRepository assetRepository,
            ChangeRequestRepository changeRepository) {
        this.ticketRepository = ticketRepository;
        this.assetRepository = assetRepository;
        this.changeRepository = changeRepository;
    }

    // ==================== Dashboard Overview ====================

    /**
     * Get complete dashboard metrics.
     */
    public DashboardMetrics getDashboardMetrics() {
        DashboardMetrics metrics = new DashboardMetrics();
        
        // Ticket metrics
        TicketMetrics ticketMetrics = new TicketMetrics();
        ticketMetrics.total = ticketRepository.count();
        ticketMetrics.open = ticketRepository.countByStatus(TicketStatus.NEW);
        ticketMetrics.inProgress = ticketRepository.countByStatus(TicketStatus.IN_PROGRESS);
        ticketMetrics.resolved = ticketRepository.countByStatus(TicketStatus.RESOLVED);
        metrics.tickets = ticketMetrics;
        
        // Asset metrics
        AssetMetrics assetMetrics = new AssetMetrics();
        assetMetrics.total = assetRepository.count();
        assetMetrics.active = assetRepository.countByStatus(AssetStatus.ACTIVE);
        assetMetrics.maintenance = assetRepository.countByStatus(AssetStatus.MAINTENANCE);
        assetMetrics.healthy = assetRepository.countByHealthStatus(HealthStatus.HEALTHY);
        assetMetrics.warning = assetRepository.countByHealthStatus(HealthStatus.WARNING);
        assetMetrics.critical = assetRepository.countByHealthStatus(HealthStatus.CRITICAL);
        metrics.assets = assetMetrics;
        
        // Change metrics
        ChangeMetrics changeMetrics = new ChangeMetrics();
        changeMetrics.total = changeRepository.count();
        changeMetrics.pending = changeRepository.countByStatus(ChangeRequest.ChangeStatus.PENDING_APPROVAL);
        changeMetrics.inProgress = changeRepository.countByStatus(ChangeRequest.ChangeStatus.IN_PROGRESS);
        changeMetrics.completed = changeRepository.countByStatus(ChangeRequest.ChangeStatus.COMPLETED);
        metrics.changes = changeMetrics;
        
        return metrics;
    }

    // ==================== Ticket Analytics ====================

    /**
     * Get ticket volume over time.
     */
    public List<TimeSeriesData> getTicketVolumeByDay(int days) {
        java.time.LocalDateTime startDate = java.time.LocalDateTime.now().minusDays(days);
        List<Object[]> results = ticketRepository.getTicketCountByDay(startDate);
        
        List<TimeSeriesData> data = new ArrayList<>();
        for (Object[] row : results) {
            TimeSeriesData tsd = new TimeSeriesData();
            tsd.date = row[0].toString();
            tsd.value = ((Number) row[1]).intValue();
            data.add(tsd);
        }
        return data;
    }

    /**
     * Get tickets by category.
     */
    public List<CategoryData> getTicketsByCategory() {
        List<Object[]> results = ticketRepository.countByCategory();
        List<CategoryData> data = new ArrayList<>();
        for (Object[] row : results) {
            CategoryData cd = new CategoryData();
            cd.category = (String) row[0];
            cd.count = ((Number) row[1]).intValue();
            data.add(cd);
        }
        return data;
    }

    /**
     * Get tickets by priority.
     */
    public List<PriorityData> getTicketsByPriority() {
        List<Object[]> results = ticketRepository.countByPriority();
        List<PriorityData> data = new ArrayList<>();
        for (Object[] row : results) {
            PriorityData pd = new PriorityData();
            pd.priority = row[0].toString();
            pd.count = ((Number) row[1]).intValue();
            data.add(pd);
        }
        return data;
    }

    /**
     * Get top requesters.
     */
    public List<AgentData> getTopRequesters(int limit) {
        List<Object[]> results = ticketRepository.getTopRequesters(PageRequest.of(0, limit));
        List<AgentData> data = new ArrayList<>();
        for (Object[] row : results) {
            AgentData ad = new AgentData();
            ad.name = (String) row[0];
            ad.count = ((Number) row[1]).intValue();
            data.add(ad);
        }
        return data;
    }

    // ==================== Asset Analytics ====================

    /**
     * Get asset health distribution.
     */
    public List<HealthDistribution> getAssetHealthDistribution() {
        List<HealthDistribution> data = new ArrayList<>();
        
        data.add(new HealthDistribution("Healthy", assetRepository.countByHealthStatus(HealthStatus.HEALTHY), "#10B981"));
        data.add(new HealthDistribution("Warning", assetRepository.countByHealthStatus(HealthStatus.WARNING), "#F59E0B"));
        data.add(new HealthDistribution("Critical", assetRepository.countByHealthStatus(HealthStatus.CRITICAL), "#F43F5E"));
        data.add(new HealthDistribution("Unknown", assetRepository.countByHealthStatus(HealthStatus.UNKNOWN), "#6B7280"));
        
        return data;
    }

    /**
     * Get asset type distribution.
     */
    public List<CategoryData> getAssetTypeDistribution() {
        // Return sample data - in production would query asset type
        List<CategoryData> data = new ArrayList<>();
        data.add(new CategoryData("Server", 3));
        data.add(new CategoryData("Workstation", 2));
        data.add(new CategoryData("Laptop", 1));
        data.add(new CategoryData("Network", 1));
        data.add(new CategoryData("Software License", 1));
        return data;
    }

    // ==================== Change Analytics ====================

    /**
     * Get change status distribution.
     */
    public List<ChangeStatusData> getChangeStatusDistribution() {
        List<ChangeStatusData> data = new ArrayList<>();
        
        data.add(new ChangeStatusData("Draft", changeRepository.countByStatus(ChangeRequest.ChangeStatus.DRAFT), "#64748B"));
        data.add(new ChangeStatusData("Pending", changeRepository.countByStatus(ChangeRequest.ChangeStatus.PENDING_APPROVAL), "#F59E0B"));
        data.add(new ChangeStatusData("Approved", changeRepository.countByStatus(ChangeRequest.ChangeStatus.APPROVED), "#3B82F6"));
        data.add(new ChangeStatusData("In Progress", changeRepository.countByStatus(ChangeRequest.ChangeStatus.IN_PROGRESS), "#8B5CF6"));
        data.add(new ChangeStatusData("Completed", changeRepository.countByStatus(ChangeRequest.ChangeStatus.COMPLETED), "#10B981"));
        data.add(new ChangeStatusData("Rejected", changeRepository.countByStatus(ChangeRequest.ChangeStatus.REJECTED), "#F43F5E"));
        
        return data;
    }

    /**
     * Get change type distribution - sample data.
     */
    public List<ChangeTypeData> getChangeTypeDistribution() {
        List<ChangeTypeData> data = new ArrayList<>();
        
        // Return sample data - in production would count by type
        data.add(new ChangeTypeData("Standard", 5, "#10B981"));
        data.add(new ChangeTypeData("Normal", 3, "#3B82F6"));
        data.add(new ChangeTypeData("Emergency", 1, "#F43F5E"));
        
        return data;
    }

    // ==================== DTOs ====================

    public static class DashboardMetrics {
        public TicketMetrics tickets = new TicketMetrics();
        public AssetMetrics assets = new AssetMetrics();
        public ChangeMetrics changes = new ChangeMetrics();
    }

    public static class TicketMetrics {
        public long total;
        public long open;
        public long inProgress;
        public long resolved;
        public long totalIncidents;
        public long totalServiceRequests;
        
        // Jackson serializes this as "resolutionRate" property
        public double getResolutionRate() {
            if (total == 0) return 0;
            return Math.round((double) resolved / total * 100 * 10) / 10;
        }
    }

    public static class AssetMetrics {
        public long total;
        public long active;
        public long maintenance;
        public long healthy;
        public long warning;
        public long critical;
        
        // Jackson serializes this as "healthRate" property
        public double getHealthRate() {
            if (total == 0) return 0;
            return Math.round((double) healthy / total * 100 * 10) / 10;
        }
    }

    public static class ChangeMetrics {
        public long total;
        public long pending;
        public long inProgress;
        public long completed;
    }

    public static class TimeSeriesData {
        public String date;
        public int value;
    }

    public static class CategoryData {
        public String category;
        public int count;
        
        public CategoryData() {}
        
        public CategoryData(String category, int count) {
            this.category = category;
            this.count = count;
        }
    }

    public static class PriorityData {
        public String priority;
        public int count;
    }

    public static class AgentData {
        public String name;
        public int count;
    }

    public static class HealthDistribution {
        public String label;
        public long count;
        public String color;
        
        public HealthDistribution(String label, long count, String color) {
            this.label = label;
            this.count = count;
            this.color = color;
        }
    }

    public static class ChangeStatusData {
        public String status;
        public long count;
        public String color;
        
        public ChangeStatusData(String status, long count, String color) {
            this.status = status;
            this.count = count;
            this.color = color;
        }
    }

    public static class ChangeTypeData {
        public String type;
        public long count;
        public String color;
        
        public ChangeTypeData(String type, long count, String color) {
            this.type = type;
            this.count = count;
            this.color = color;
        }
    }
}

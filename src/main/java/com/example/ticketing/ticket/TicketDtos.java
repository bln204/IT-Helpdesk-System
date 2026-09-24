package com.example.ticketing.ticket;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TicketDtos {
    private TicketDtos() {
    }

    // ============================================================
    // CREATE REQUEST - Hỗ trợ cả legacy (category enum) và new (category_id)
    // ============================================================
    public static class TicketCreateRequest {
        @NotBlank
        @Size(max = 160)
        private String title;

        @NotBlank
        private String description;

        @NotNull
        private TicketTypes.TicketPriority priority;

        // Legacy field - vẫn hỗ trợ cho tương thích ngược
        @NotNull
        private TicketTypes.TicketCategory category;

        // NEW: Category ID (thay thế category enum)
        private Long categoryId;
        
        // NEW: Subcategory ID
        private Long subcategoryId;

        @NotBlank
        @Size(max = 120)
        private String requesterName;

        @Email
        @Size(max = 160)
        private String requesterEmail;

        // Legacy field
        @Size(max = 120)
        private String assigneeName;

        // NEW: Assignee ID
        private Long assigneeId;
        
        // NEW: Team ID
        private Long teamId;

        // Getters & Setters
        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public TicketTypes.TicketPriority getPriority() {
            return priority;
        }

        public void setPriority(TicketTypes.TicketPriority priority) {
            this.priority = priority;
        }

        public TicketTypes.TicketCategory getCategory() {
            return category;
        }

        public void setCategory(TicketTypes.TicketCategory category) {
            this.category = category;
        }
        
        public Long getCategoryId() {
            return categoryId;
        }
        
        public void setCategoryId(Long categoryId) {
            this.categoryId = categoryId;
        }
        
        public Long getSubcategoryId() {
            return subcategoryId;
        }
        
        public void setSubcategoryId(Long subcategoryId) {
            this.subcategoryId = subcategoryId;
        }

        public String getRequesterName() {
            return requesterName;
        }

        public void setRequesterName(String requesterName) {
            this.requesterName = requesterName;
        }

        public String getRequesterEmail() {
            return requesterEmail;
        }

        public void setRequesterEmail(String requesterEmail) {
            this.requesterEmail = requesterEmail;
        }

        public String getAssigneeName() {
            return assigneeName;
        }

        public void setAssigneeName(String assigneeName) {
            this.assigneeName = assigneeName;
        }
        
        public Long getAssigneeId() {
            return assigneeId;
        }
        
        public void setAssigneeId(Long assigneeId) {
            this.assigneeId = assigneeId;
        }
        
        public Long getTeamId() {
            return teamId;
        }
        
        public void setTeamId(Long teamId) {
            this.teamId = teamId;
        }
    }

    // ============================================================
    // STATUS UPDATE REQUEST - Mở rộng cho Phase 2
    // ============================================================
    public static class TicketStatusUpdateRequest {
        @NotNull
        private TicketTypes.TicketStatus status;
        
        // NEW: Optional comment khi update status
        private String comment;
        
        // NEW: Notify requester
        private boolean notifyRequester = true;

        public TicketTypes.TicketStatus getStatus() {
            return status;
        }

        public void setStatus(TicketTypes.TicketStatus status) {
            this.status = status;
        }
        
        public String getComment() {
            return comment;
        }
        
        public void setComment(String comment) {
            this.comment = comment;
        }
        
        public boolean isNotifyRequester() {
            return notifyRequester;
        }
        
        public void setNotifyRequester(boolean notifyRequester) {
            this.notifyRequester = notifyRequester;
        }
    }

    // ============================================================
    // PRIORITY UPDATE REQUEST
    // ============================================================
    public static class TicketPriorityUpdateRequest {
        @NotNull
        private TicketTypes.TicketPriority priority;

        public TicketTypes.TicketPriority getPriority() {
            return priority;
        }

        public void setPriority(TicketTypes.TicketPriority priority) {
            this.priority = priority;
        }
    }

    // ============================================================
    // ASSIGNMENT UPDATE REQUEST - Mở rộng cho Phase 2
    // ============================================================
    public static class TicketAssigneeUpdateRequest {
        // Legacy field
        @Size(max = 120)
        private String assigneeName;
        
        // NEW: Assignee ID (ưu tiên dùng)
        private Long assigneeId;
        
        // NEW: Team ID
        private Long teamId;
        
        // NEW: Optional comment
        private String comment;

        public String getAssigneeName() {
            return assigneeName;
        }

        public void setAssigneeName(String assigneeName) {
            this.assigneeName = assigneeName;
        }
        
        public Long getAssigneeId() {
            return assigneeId;
        }
        
        public void setAssigneeId(Long assigneeId) {
            this.assigneeId = assigneeId;
        }
        
        public Long getTeamId() {
            return teamId;
        }
        
        public void setTeamId(Long teamId) {
            this.teamId = teamId;
        }
        
        public String getComment() {
            return comment;
        }
        
        public void setComment(String comment) {
            this.comment = comment;
        }
    }

    // ============================================================
    // CATEGORY UPDATE REQUEST - NEW
    // ============================================================
    public static class TicketCategoryUpdateRequest {
        private Long categoryId;
        private Long subcategoryId;
        private String comment;

        public Long getCategoryId() {
            return categoryId;
        }
        
        public void setCategoryId(Long categoryId) {
            this.categoryId = categoryId;
        }
        
        public Long getSubcategoryId() {
            return subcategoryId;
        }
        
        public void setSubcategoryId(Long subcategoryId) {
            this.subcategoryId = subcategoryId;
        }
        
        public String getComment() {
            return comment;
        }
        
        public void setComment(String comment) {
            this.comment = comment;
        }
    }

    // ============================================================
    // TICKET RESPONSE - Mở rộng cho Phase 2
    // ============================================================
    public static class TicketResponse {
        // Basic info
        private Long id;
        private String ticketNumber;
        private String title;
        private String description;
        private TicketTypes.TicketPriority priority;
        
        // Legacy category
        private TicketTypes.TicketCategory category;
        
        // NEW: Category info
        private CategoryInfo categoryInfo;
        private CategoryInfo subcategoryInfo;
        
        private TicketTypes.TicketStatus status;
        
        // Requester info
        private String requesterName;
        private String requesterUsername;
        private String requesterEmail;
        
        // Legacy assignee
        private String assigneeName;
        
        // NEW: Assignee info
        private UserInfo assignee;
        
        // NEW: Team info
        private TeamInfo team;
        
        // Department info
        private Long departmentId;
        private String departmentCode;
        private String departmentName;
        
        // Timestamps
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime resolvedAt;
        private LocalDateTime closedAt;
        
        // NEW: SLA info
        private LocalDateTime slaResponseAt;
        private LocalDateTime slaResolutionAt;
        private LocalDateTime firstResponseAt;
        private boolean responseSLABreached;
        private boolean resolutionSLABreached;
        
        // NEW: Counters
        private Integer escalationCount;
        private Integer reopenCount;

        public static TicketResponse from(Ticket ticket) {
            TicketResponse response = new TicketResponse();
            response.id = ticket.getId();
            response.ticketNumber = ticket.getTicketNumber();
            response.title = ticket.getTitle();
            response.description = ticket.getDescription();
            response.priority = ticket.getPriority();
            response.category = ticket.getCategory();
            
            // NEW: Category info
            if (ticket.getCategoryEntity() != null) {
                response.categoryInfo = new CategoryInfo(
                    ticket.getCategoryEntity().getId(),
                    ticket.getCategoryEntity().getCode(),
                    ticket.getCategoryEntity().getName()
                );
            }
            if (ticket.getSubcategoryEntity() != null) {
                response.subcategoryInfo = new CategoryInfo(
                    ticket.getSubcategoryEntity().getId(),
                    ticket.getSubcategoryEntity().getCode(),
                    ticket.getSubcategoryEntity().getName()
                );
            }
            
            response.status = ticket.getStatus();
            response.requesterName = ticket.getRequesterName();
            response.requesterUsername = ticket.getRequesterUsername();
            response.requesterEmail = ticket.getRequesterEmail();
            
            // Legacy assignee
            response.assigneeName = ticket.getAssigneeName();
            
            // NEW: Assignee info
            if (ticket.getAssignee() != null) {
                response.assignee = new UserInfo(
                    ticket.getAssignee().getId(),
                    ticket.getAssignee().getUsername(),
                    ticket.getAssignee().getDisplayName(),
                    ticket.getAssignee().getEmail(),
                    ticket.getAssignee().getTitle()
                );
            }
            
            // NEW: Team info
            if (ticket.getTeam() != null) {
                response.team = new TeamInfo(
                    ticket.getTeam().getId(),
                    ticket.getTeam().getCode(),
                    ticket.getTeam().getName()
                );
            }
            
            response.departmentId = ticket.getDepartmentId();
            response.departmentCode = ticket.getDepartmentCode();
            response.departmentName = ticket.getDepartment() != null ? ticket.getDepartment().getName() : null;
            
            response.createdAt = ticket.getCreatedAt();
            response.updatedAt = ticket.getUpdatedAt();
            response.resolvedAt = ticket.getResolvedAt();
            response.closedAt = ticket.getClosedAt();
            
            // NEW: SLA info
            response.slaResponseAt = ticket.getSlaResponseAt();
            response.slaResolutionAt = ticket.getSlaResolutionAt();
            response.firstResponseAt = ticket.getFirstResponseAt();
            response.responseSLABreached = ticket.isResponseSLABreached();
            response.resolutionSLABreached = ticket.isResolutionSLABreached();
            
            // NEW: Counters
            response.escalationCount = ticket.getEscalationCount();
            response.reopenCount = ticket.getReopenCount();
            
            return response;
        }

        // Nested classes for nested objects
        public static class CategoryInfo {
            private Long id;
            private String code;
            private String name;
            
            public CategoryInfo(Long id, String code, String name) {
                this.id = id;
                this.code = code;
                this.name = name;
            }
            
            public Long getId() { return id; }
            public String getCode() { return code; }
            public String getName() { return name; }
        }
        
        public static class UserInfo {
            private Long id;
            private String username;
            private String displayName;
            private String email;
            private String title;
            
            public UserInfo(Long id, String username, String displayName, String email, String title) {
                this.id = id;
                this.username = username;
                this.displayName = displayName;
                this.email = email;
                this.title = title;
            }
            
            public Long getId() { return id; }
            public String getUsername() { return username; }
            public String getDisplayName() { return displayName; }
            public String getEmail() { return email; }
            public String getTitle() { return title; }
        }
        
        public static class TeamInfo {
            private Long id;
            private String code;
            private String name;
            
            public TeamInfo(Long id, String code, String name) {
                this.id = id;
                this.code = code;
                this.name = name;
            }
            
            public Long getId() { return id; }
            public String getCode() { return code; }
            public String getName() { return name; }
        }

        // Getters & Setters
        public Long getId() { return id; }
        public String getTicketNumber() { return ticketNumber; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public TicketTypes.TicketPriority getPriority() { return priority; }
        public TicketTypes.TicketCategory getCategory() { return category; }
        public CategoryInfo getCategoryInfo() { return categoryInfo; }
        public CategoryInfo getSubcategoryInfo() { return subcategoryInfo; }
        public TicketTypes.TicketStatus getStatus() { return status; }
        public String getRequesterName() { return requesterName; }
        public String getRequesterUsername() { return requesterUsername; }
        public String getRequesterEmail() { return requesterEmail; }
        public String getAssigneeName() { return assigneeName; }
        public UserInfo getAssignee() { return assignee; }
        public TeamInfo getTeam() { return team; }
        public Long getDepartmentId() { return departmentId; }
        public String getDepartmentCode() { return departmentCode; }
        public String getDepartmentName() { return departmentName; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public LocalDateTime getResolvedAt() { return resolvedAt; }
        public LocalDateTime getClosedAt() { return closedAt; }
        public LocalDateTime getSlaResponseAt() { return slaResponseAt; }
        public LocalDateTime getSlaResolutionAt() { return slaResolutionAt; }
        public LocalDateTime getFirstResponseAt() { return firstResponseAt; }
        public boolean isResponseSLABreached() { return responseSLABreached; }
        public boolean isResolutionSLABreached() { return resolutionSLABreached; }
        public Integer getEscalationCount() { return escalationCount; }
        public Integer getReopenCount() { return reopenCount; }
        
        // Setters
        public void setId(Long id) { this.id = id; }
        public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }
        public void setTitle(String title) { this.title = title; }
        public void setDescription(String description) { this.description = description; }
        public void setPriority(TicketTypes.TicketPriority priority) { this.priority = priority; }
        public void setCategory(TicketTypes.TicketCategory category) { this.category = category; }
        public void setCategoryInfo(CategoryInfo categoryInfo) { this.categoryInfo = categoryInfo; }
        public void setSubcategoryInfo(CategoryInfo subcategoryInfo) { this.subcategoryInfo = subcategoryInfo; }
        public void setStatus(TicketTypes.TicketStatus status) { this.status = status; }
        public void setRequesterName(String requesterName) { this.requesterName = requesterName; }
        public void setRequesterUsername(String requesterUsername) { this.requesterUsername = requesterUsername; }
        public void setRequesterEmail(String requesterEmail) { this.requesterEmail = requesterEmail; }
        public void setAssigneeName(String assigneeName) { this.assigneeName = assigneeName; }
        public void setAssignee(UserInfo assignee) { this.assignee = assignee; }
        public void setTeam(TeamInfo team) { this.team = team; }
        public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
        public void setDepartmentCode(String departmentCode) { this.departmentCode = departmentCode; }
        public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
        public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
        public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
        public void setSlaResponseAt(LocalDateTime slaResponseAt) { this.slaResponseAt = slaResponseAt; }
        public void setSlaResolutionAt(LocalDateTime slaResolutionAt) { this.slaResolutionAt = slaResolutionAt; }
        public void setFirstResponseAt(LocalDateTime firstResponseAt) { this.firstResponseAt = firstResponseAt; }
        public void setResponseSLABreached(boolean responseSLABreached) { this.responseSLABreached = responseSLABreached; }
        public void setResolutionSLABreached(boolean resolutionSLABreached) { this.resolutionSLABreached = resolutionSLABreached; }
        public void setEscalationCount(Integer escalationCount) { this.escalationCount = escalationCount; }
        public void setReopenCount(Integer reopenCount) { this.reopenCount = reopenCount; }
    }

    // ============================================================
    // TICKET COMMENT REQUEST
    // ============================================================
    public static class TicketCommentCreateRequest {
        @NotNull
        private TicketTypes.CommentVisibility visibility;

        @NotBlank
        @Size(max = 2000)
        private String body;

        public TicketTypes.CommentVisibility getVisibility() {
            return visibility;
        }

        public void setVisibility(TicketTypes.CommentVisibility visibility) {
            this.visibility = visibility;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }
    }

    // ============================================================
    // DASHBOARD RESPONSE - Mở rộng cho IT Dashboard
    // ============================================================
    public static class TicketCountReport {
        private long total;
        private List<TicketStatusCountResponse> byStatus;
        // NEW: By priority
        private List<TicketPriorityCountResponse> byPriority;
        // NEW: By team
        private List<TicketTeamCountResponse> byTeam;
        // NEW: SLA stats
        private SlaStatsResponse slaStats;

        public TicketCountReport(long total, List<TicketStatusCountResponse> byStatus) {
            this.total = total;
            this.byStatus = byStatus;
        }

        public long getTotal() {
            return total;
        }

        public List<TicketStatusCountResponse> getByStatus() {
            return byStatus;
        }
        
        public List<TicketPriorityCountResponse> getByPriority() {
            return byPriority;
        }
        
        public void setByPriority(List<TicketPriorityCountResponse> byPriority) {
            this.byPriority = byPriority;
        }
        
        public List<TicketTeamCountResponse> getByTeam() {
            return byTeam;
        }
        
        public void setByTeam(List<TicketTeamCountResponse> byTeam) {
            this.byTeam = byTeam;
        }
        
        public SlaStatsResponse getSlaStats() {
            return slaStats;
        }
        
        public void setSlaStats(SlaStatsResponse slaStats) {
            this.slaStats = slaStats;
        }
    }
    
    public static class TicketPriorityCountResponse {
        private TicketTypes.TicketPriority priority;
        private long count;
        
        public TicketPriorityCountResponse(TicketTypes.TicketPriority priority, long count) {
            this.priority = priority;
            this.count = count;
        }
        
        public TicketTypes.TicketPriority getPriority() { return priority; }
        public long getCount() { return count; }
    }
    
    public static class TicketTeamCountResponse {
        private Long teamId;
        private String teamCode;
        private String teamName;
        private long count;
        
        public TicketTeamCountResponse(Long teamId, String teamCode, String teamName, long count) {
            this.teamId = teamId;
            this.teamCode = teamCode;
            this.teamName = teamName;
            this.count = count;
        }
        
        public Long getTeamId() { return teamId; }
        public String getTeamCode() { return teamCode; }
        public String getTeamName() { return teamName; }
        public long getCount() { return count; }
    }
    
    public static class SlaStatsResponse {
        private long totalOpen;
        private long slaBreached;
        private long slaAtRisk; // Within 1 hour of breaching
        private double complianceRate;
        
        public long getTotalOpen() { return totalOpen; }
        public void setTotalOpen(long totalOpen) { this.totalOpen = totalOpen; }
        public long getSlaBreached() { return slaBreached; }
        public void setSlaBreached(long slaBreached) { this.slaBreached = slaBreached; }
        public long getSlaAtRisk() { return slaAtRisk; }
        public void setSlaAtRisk(long slaAtRisk) { this.slaAtRisk = slaAtRisk; }
        public double getComplianceRate() { return complianceRate; }
        public void setComplianceRate(double complianceRate) { this.complianceRate = complianceRate; }
    }

    // ============================================================
    // ENGINEER REPORT
    // ============================================================
    public static class EngineerReportRow {
        private String engineer;
        private Long assigneeId;
        private long ticketsAssigned;
        private long ticketsCompleted;
        private double avgAssignedPerDay;
        private double avgCompletedPerDay;
        private double avgCompletionHours;

        public EngineerReportRow(
            String engineer,
            long ticketsAssigned,
            long ticketsCompleted,
            double avgAssignedPerDay,
            double avgCompletedPerDay,
            double avgCompletionHours
        ) {
            this.engineer = engineer;
            this.ticketsAssigned = ticketsAssigned;
            this.ticketsCompleted = ticketsCompleted;
            this.avgAssignedPerDay = avgAssignedPerDay;
            this.avgCompletedPerDay = avgCompletedPerDay;
            this.avgCompletionHours = avgCompletionHours;
        }

        public String getEngineer() {
            return engineer;
        }

        public long getTicketsAssigned() {
            return ticketsAssigned;
        }

        public long getTicketsCompleted() {
            return ticketsCompleted;
        }

        public double getAvgAssignedPerDay() {
            return avgAssignedPerDay;
        }

        public double getAvgCompletedPerDay() {
            return avgCompletedPerDay;
        }

        public double getAvgCompletionHours() {
            return avgCompletionHours;
        }
    }

    // ============================================================
    // REQUESTER REPORT
    // ============================================================
    public static class RequesterReportRow {
        private String requester;
        private long ticketsSubmitted;
        private double avgCompletionHours;

        public RequesterReportRow(String requester, long ticketsSubmitted, double avgCompletionHours) {
            this.requester = requester;
            this.ticketsSubmitted = ticketsSubmitted;
            this.avgCompletionHours = avgCompletionHours;
        }

        public String getRequester() {
            return requester;
        }

        public long getTicketsSubmitted() {
            return ticketsSubmitted;
        }

        public double getAvgCompletionHours() {
            return avgCompletionHours;
        }
    }

    // ============================================================
    // DASHBOARD SUMMARY - Mở rộng cho IT Dashboard
    // ============================================================
    public static class DashboardSummary {
        private long openCount;
        private long closedCount;
        private long overdueCount;
        private double avgCompletionHours;
        private List<TicketStatusCountResponse> openByStatus;
        
        // NEW: IT Dashboard specific stats
        private long newCount;
        private long assignedCount;
        private long inProgressCount;
        private long waitingCount;
        private long slaBreachedCount;

        public DashboardSummary(
            long openCount,
            long closedCount,
            long overdueCount,
            double avgCompletionHours,
            List<TicketStatusCountResponse> openByStatus
        ) {
            this.openCount = openCount;
            this.closedCount = closedCount;
            this.overdueCount = overdueCount;
            this.avgCompletionHours = avgCompletionHours;
            this.openByStatus = openByStatus;
        }

        public long getOpenCount() {
            return openCount;
        }

        public long getClosedCount() {
            return closedCount;
        }

        public long getOverdueCount() {
            return overdueCount;
        }

        public double getAvgCompletionHours() {
            return avgCompletionHours;
        }

        public List<TicketStatusCountResponse> getOpenByStatus() {
            return openByStatus;
        }
        
        // NEW Getters & Setters
        public long getNewCount() { return newCount; }
        public void setNewCount(long newCount) { this.newCount = newCount; }
        public long getAssignedCount() { return assignedCount; }
        public void setAssignedCount(long assignedCount) { this.assignedCount = assignedCount; }
        public long getInProgressCount() { return inProgressCount; }
        public void setInProgressCount(long inProgressCount) { this.inProgressCount = inProgressCount; }
        public long getWaitingCount() { return waitingCount; }
        public void setWaitingCount(long waitingCount) { this.waitingCount = waitingCount; }
        public long getSlaBreachedCount() { return slaBreachedCount; }
        public void setSlaBreachedCount(long slaBreachedCount) { this.slaBreachedCount = slaBreachedCount; }
    }

    // ============================================================
    // BACKLOG AGING
    // ============================================================
    public static class BacklogAgingRow {
        private TicketTypes.TicketStatus status;
        private long openCount;
        private double avgAgeHours;

        public BacklogAgingRow(TicketTypes.TicketStatus status, long openCount, double avgAgeHours) {
            this.status = status;
            this.openCount = openCount;
            this.avgAgeHours = avgAgeHours;
        }

        public TicketTypes.TicketStatus getStatus() {
            return status;
        }

        public long getOpenCount() {
            return openCount;
        }

        public double getAvgAgeHours() {
            return avgAgeHours;
        }
    }

    // ============================================================
    // SLA BUCKET
    // ============================================================
    public static class SlaBucketRow {
        private String bucket;
        private long count;

        public SlaBucketRow(String bucket, long count) {
            this.bucket = bucket;
            this.count = count;
        }

        public String getBucket() {
            return bucket;
        }

        public long getCount() {
            return count;
        }
    }

    // ============================================================
    // TICKET ASSIGNMENT RESPONSE
    // ============================================================
    public static class TicketAssignmentResponse {
        private Long id;
        private Long ticketId;
        private String previousAssignee;
        private Long previousAssigneeId;
        private String newAssignee;
        private Long newAssigneeId;
        private Long teamId;
        private String teamName;
        private String actorRole;
        private String actorName;
        private LocalDateTime createdAt;

        public static TicketAssignmentResponse from(TicketAssignment assignment) {
            TicketAssignmentResponse response = new TicketAssignmentResponse();
            response.id = assignment.getId();
            response.ticketId = assignment.getTicketId();
            response.previousAssignee = assignment.getPreviousAssignee();
            response.newAssignee = assignment.getNewAssignee();
            response.actorRole = assignment.getActorRole();
            response.actorName = assignment.getActorName();
            response.createdAt = assignment.getCreatedAt();
            return response;
        }

        public Long getId() { return id; }
        public Long getTicketId() { return ticketId; }
        public String getPreviousAssignee() { return previousAssignee; }
        public Long getPreviousAssigneeId() { return previousAssigneeId; }
        public void setPreviousAssigneeId(Long previousAssigneeId) { this.previousAssigneeId = previousAssigneeId; }
        public String getNewAssignee() { return newAssignee; }
        public Long getNewAssigneeId() { return newAssigneeId; }
        public void setNewAssigneeId(Long newAssigneeId) { this.newAssigneeId = newAssigneeId; }
        public Long getTeamId() { return teamId; }
        public void setTeamId(Long teamId) { this.teamId = teamId; }
        public String getTeamName() { return teamName; }
        public void setTeamName(String teamName) { this.teamName = teamName; }
        public String getActorRole() { return actorRole; }
        public String getActorName() { return actorName; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }

    // ============================================================
    // TICKET COMMENT RESPONSE
    // ============================================================
    public static class TicketCommentResponse {
        private Long id;
        private Long ticketId;
        private TicketTypes.CommentVisibility visibility;
        private String body;
        private String actorRole;
        private String actorName;
        private LocalDateTime createdAt;

        public static TicketCommentResponse from(TicketComment comment) {
            TicketCommentResponse response = new TicketCommentResponse();
            response.id = comment.getId();
            response.ticketId = comment.getTicketId();
            response.visibility = comment.getVisibility();
            response.body = comment.getBody();
            response.actorRole = comment.getActorRole();
            response.actorName = comment.getActorName();
            response.createdAt = comment.getCreatedAt();
            return response;
        }

        public Long getId() { return id; }
        public Long getTicketId() { return ticketId; }
        public TicketTypes.CommentVisibility getVisibility() { return visibility; }
        public String getBody() { return body; }
        public String getActorRole() { return actorRole; }
        public String getActorName() { return actorName; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }

    // ============================================================
    // STATUS COUNT RESPONSE
    // ============================================================
    public static class TicketStatusCountResponse {
        private TicketTypes.TicketStatus status;
        private long count;

        public TicketStatusCountResponse(TicketTypes.TicketStatus status, long count) {
            this.status = status;
            this.count = count;
        }

        public TicketTypes.TicketStatus getStatus() {
            return status;
        }

        public long getCount() {
            return count;
        }
    }

    // ============================================================
    // ASSIGNEE WORKLOAD RESPONSE
    // ============================================================
    public static class TicketAssigneeWorkloadResponse {
        private String assigneeName;
        private Long assigneeId;
        private String assigneeDisplayName;
        private long count;

        public TicketAssigneeWorkloadResponse(String assigneeName, long count) {
            this.assigneeName = assigneeName;
            this.count = count;
        }

        public String getAssigneeName() {
            return assigneeName;
        }

        public long getCount() {
            return count;
        }
        
        public Long getAssigneeId() { return assigneeId; }
        public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }
        public String getAssigneeDisplayName() { return assigneeDisplayName; }
        public void setAssigneeDisplayName(String assigneeDisplayName) { this.assigneeDisplayName = assigneeDisplayName; }
    }

    // ============================================================
    // RESOLUTION TIME RESPONSE
    // ============================================================
    public static class TicketResolutionTimeResponse {
        private long resolvedCount;
        private double averageSeconds;
        private double averageMinutes;
        private double averageHours;

        public TicketResolutionTimeResponse(
            long resolvedCount,
            double averageSeconds,
            double averageMinutes,
            double averageHours
        ) {
            this.resolvedCount = resolvedCount;
            this.averageSeconds = averageSeconds;
            this.averageMinutes = averageMinutes;
            this.averageHours = averageHours;
        }

        public long getResolvedCount() {
            return resolvedCount;
        }

        public double getAverageSeconds() {
            return averageSeconds;
        }

        public double getAverageMinutes() {
            return averageMinutes;
        }

        public double getAverageHours() {
            return averageHours;
        }
    }

    // ============================================================
    // AUDIT RESPONSE
    // ============================================================
    public static class TicketAuditResponse {
        private Long id;
        private Long ticketId;
        private TicketTypes.AuditAction action;
        private String fieldName;
        private String oldValue;
        private String newValue;
        private String actorRole;
        private String actorName;
        private LocalDateTime createdAt;

        public static TicketAuditResponse from(TicketAudit audit) {
            TicketAuditResponse response = new TicketAuditResponse();
            response.id = audit.getId();
            response.ticketId = audit.getTicketId();
            response.action = audit.getAction();
            response.fieldName = audit.getFieldName();
            response.oldValue = audit.getOldValue();
            response.newValue = audit.getNewValue();
            response.actorRole = audit.getActorRole();
            response.actorName = audit.getActorName();
            response.createdAt = audit.getCreatedAt();
            return response;
        }

        public Long getId() { return id; }
        public Long getTicketId() { return ticketId; }
        public TicketTypes.AuditAction getAction() { return action; }
        public String getFieldName() { return fieldName; }
        public String getOldValue() { return oldValue; }
        public String getNewValue() { return newValue; }
        public String getActorRole() { return actorRole; }
        public String getActorName() { return actorName; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }

    // ============================================================
    // ATTACHMENT RESPONSE
    // ============================================================
    public static class TicketAttachmentResponse {
        private Long id;
        private Long ticketId;
        private String fileName;
        private String originalName;
        private String contentType;
        private Long fileSize;
        private String uploadedBy;
        private LocalDateTime createdAt;

        public static TicketAttachmentResponse from(TicketAttachment attachment) {
            TicketAttachmentResponse response = new TicketAttachmentResponse();
            response.id = attachment.getId();
            response.ticketId = attachment.getTicket().getId();
            response.fileName = attachment.getFileName();
            response.originalName = attachment.getOriginalName();
            response.contentType = attachment.getContentType();
            response.fileSize = attachment.getFileSize();
            response.uploadedBy = attachment.getUploadedBy();
            response.createdAt = attachment.getCreatedAt();
            return response;
        }

        public Long getId() { return id; }
        public Long getTicketId() { return ticketId; }
        public String getFileName() { return fileName; }
        public String getOriginalName() { return originalName; }
        public String getContentType() { return contentType; }
        public Long getFileSize() { return fileSize; }
        public String getUploadedBy() { return uploadedBy; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }
    
    // ============================================================
    // TIMELINE ENTRY - NEW for Phase 2.7
    // ============================================================
    public static class TimelineEntry {
        private Long id;
        private TicketTypes.TimelineEntryType type;
        private String action;
        private String description;
        private String actorName;
        private String actorRole;
        private LocalDateTime timestamp;
        private Object details; // Can be comment, assignment change, etc.
        
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public TicketTypes.TimelineEntryType getType() { return type; }
        public void setType(TicketTypes.TimelineEntryType type) { this.type = type; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getActorName() { return actorName; }
        public void setActorName(String actorName) { this.actorName = actorName; }
        public String getActorRole() { return actorRole; }
        public void setActorRole(String actorRole) { this.actorRole = actorRole; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public Object getDetails() { return details; }
        public void setDetails(Object details) { this.details = details; }
    }
    
    // ============================================================
    // IT DASHBOARD STATS - NEW for Phase 2.7
    // ============================================================
    public static class ITDashboardStats {
        private long myTickets;
        private long newTickets;
        private long inProgressTickets;
        private long waitingForUserTickets;
        private long slaBreachedTickets;
        private List<TicketStatusCountResponse> byStatus;
        private List<TicketPriorityCountResponse> byPriority;
        private List<TicketTeamCountResponse> byTeam;
        private List<TicketCategoryCountResponse> byCategory;
        
        public long getMyTickets() { return myTickets; }
        public void setMyTickets(long myTickets) { this.myTickets = myTickets; }
        public long getNewTickets() { return newTickets; }
        public void setNewTickets(long newTickets) { this.newTickets = newTickets; }
        public long getInProgressTickets() { return inProgressTickets; }
        public void setInProgressTickets(long inProgressTickets) { this.inProgressTickets = inProgressTickets; }
        public long getWaitingForUserTickets() { return waitingForUserTickets; }
        public void setWaitingForUserTickets(long waitingForUserTickets) { this.waitingForUserTickets = waitingForUserTickets; }
        public long getSlaBreachedTickets() { return slaBreachedTickets; }
        public void setSlaBreachedTickets(long slaBreachedTickets) { this.slaBreachedTickets = slaBreachedTickets; }
        public List<TicketStatusCountResponse> getByStatus() { return byStatus; }
        public void setByStatus(List<TicketStatusCountResponse> byStatus) { this.byStatus = byStatus; }
        public List<TicketPriorityCountResponse> getByPriority() { return byPriority; }
        public void setByPriority(List<TicketPriorityCountResponse> byPriority) { this.byPriority = byPriority; }
        public List<TicketTeamCountResponse> getByTeam() { return byTeam; }
        public void setByTeam(List<TicketTeamCountResponse> byTeam) { this.byTeam = byTeam; }
        public List<TicketCategoryCountResponse> getByCategory() { return byCategory; }
        public void setByCategory(List<TicketCategoryCountResponse> byCategory) { this.byCategory = byCategory; }
    }
    
    public static class TicketCategoryCountResponse {
        private Long categoryId;
        private String categoryName;
        private String categoryCode;
        private long count;
        
        public TicketCategoryCountResponse(Long categoryId, String categoryName, String categoryCode, long count) {
            this.categoryId = categoryId;
            this.categoryName = categoryName;
            this.categoryCode = categoryCode;
            this.count = count;
        }
        
        public Long getCategoryId() { return categoryId; }
        public String getCategoryName() { return categoryName; }
        public String getCategoryCode() { return categoryCode; }
        public long getCount() { return count; }
    }
}

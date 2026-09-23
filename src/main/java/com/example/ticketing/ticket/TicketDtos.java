package com.example.ticketing.ticket;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TicketDtos {
    private TicketDtos() {
    }

    public static class TicketCreateRequest {
        @NotBlank
        @Size(max = 160)
        private String title;

        @NotBlank
        private String description;

        @NotNull
        private TicketTypes.TicketPriority priority;

        @NotNull
        private TicketTypes.TicketCategory category;

        @NotBlank
        @Size(max = 120)
        private String requesterName;

        @Email
        @Size(max = 160)
        private String requesterEmail;

        @Size(max = 120)
        private String assigneeName;

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
    }

    public static class TicketStatusUpdateRequest {
        @NotNull
        private TicketTypes.TicketStatus status;

        public TicketTypes.TicketStatus getStatus() {
            return status;
        }

        public void setStatus(TicketTypes.TicketStatus status) {
            this.status = status;
        }

    }

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

    public static class TicketAssigneeUpdateRequest {
        @Size(max = 120)
        private String assigneeName;

        public String getAssigneeName() {
            return assigneeName;
        }

        public void setAssigneeName(String assigneeName) {
            this.assigneeName = assigneeName;
        }

    }

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

    public static class TicketResponse {
        private Long id;
        private String ticketNumber;
        private String title;
        private String description;
        private TicketTypes.TicketPriority priority;
        private TicketTypes.TicketCategory category;
        private TicketTypes.TicketStatus status;
        private String requesterName;
        private String requesterEmail;
        private String assigneeName;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime resolvedAt;
        private LocalDateTime closedAt;

        public static TicketResponse from(Ticket ticket) {
            TicketResponse response = new TicketResponse();
            response.id = ticket.getId();
            response.ticketNumber = ticket.getTicketNumber();
            response.title = ticket.getTitle();
            response.description = ticket.getDescription();
            response.priority = ticket.getPriority();
            response.category = ticket.getCategory();
            response.status = ticket.getStatus();
            response.requesterName = ticket.getRequesterName();
            response.requesterEmail = ticket.getRequesterEmail();
            response.assigneeName = ticket.getAssigneeName();
            response.createdAt = ticket.getCreatedAt();
            response.updatedAt = ticket.getUpdatedAt();
            response.resolvedAt = ticket.getResolvedAt();
            response.closedAt = ticket.getClosedAt();
            return response;
        }

        public Long getId() {
            return id;
        }

        public String getTicketNumber() {
            return ticketNumber;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public TicketTypes.TicketPriority getPriority() {
            return priority;
        }

        public TicketTypes.TicketCategory getCategory() {
            return category;
        }

        public TicketTypes.TicketStatus getStatus() {
            return status;
        }

        public String getRequesterName() {
            return requesterName;
        }

        public String getRequesterEmail() {
            return requesterEmail;
        }

        public String getAssigneeName() {
            return assigneeName;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public LocalDateTime getResolvedAt() {
            return resolvedAt;
        }

        public LocalDateTime getClosedAt() {
            return closedAt;
        }
    }

    public static class TicketCountReport {
        private long total;
        private java.util.List<TicketStatusCountResponse> byStatus;

        public TicketCountReport(long total, java.util.List<TicketStatusCountResponse> byStatus) {
            this.total = total;
            this.byStatus = byStatus;
        }

        public long getTotal() {
            return total;
        }

        public java.util.List<TicketStatusCountResponse> getByStatus() {
            return byStatus;
        }
    }

    public static class EngineerReportRow {
        private String engineer;
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

    public static class DashboardSummary {
        private long openCount;
        private long closedCount;
        private long overdueCount;
        private double avgCompletionHours;
        private java.util.List<TicketStatusCountResponse> openByStatus;

        public DashboardSummary(
            long openCount,
            long closedCount,
            long overdueCount,
            double avgCompletionHours,
            java.util.List<TicketStatusCountResponse> openByStatus
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

        public java.util.List<TicketStatusCountResponse> getOpenByStatus() {
            return openByStatus;
        }
    }

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

    public static class TicketAssignmentResponse {
        private Long id;
        private Long ticketId;
        private String previousAssignee;
        private String newAssignee;
        private TicketTypes.TicketRole actorRole;
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

        public Long getId() {
            return id;
        }

        public Long getTicketId() {
            return ticketId;
        }

        public String getPreviousAssignee() {
            return previousAssignee;
        }

        public String getNewAssignee() {
            return newAssignee;
        }

        public TicketTypes.TicketRole getActorRole() {
            return actorRole;
        }

        public String getActorName() {
            return actorName;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }

    public static class TicketCommentResponse {
        private Long id;
        private Long ticketId;
        private TicketTypes.CommentVisibility visibility;
        private String body;
        private TicketTypes.TicketRole actorRole;
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

        public Long getId() {
            return id;
        }

        public Long getTicketId() {
            return ticketId;
        }

        public TicketTypes.CommentVisibility getVisibility() {
            return visibility;
        }

        public String getBody() {
            return body;
        }

        public TicketTypes.TicketRole getActorRole() {
            return actorRole;
        }

        public String getActorName() {
            return actorName;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }

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

    public static class TicketAssigneeWorkloadResponse {
        private String assigneeName;
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
    }

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

    public static class TicketAuditResponse {
        private Long id;
        private Long ticketId;
        private TicketTypes.AuditAction action;
        private String fieldName;
        private String oldValue;
        private String newValue;
        private TicketTypes.TicketRole actorRole;
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

        public Long getId() {
            return id;
        }

        public Long getTicketId() {
            return ticketId;
        }

        public TicketTypes.AuditAction getAction() {
            return action;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getOldValue() {
            return oldValue;
        }

        public String getNewValue() {
            return newValue;
        }

        public TicketTypes.TicketRole getActorRole() {
            return actorRole;
        }

        public String getActorName() {
            return actorName;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }
}

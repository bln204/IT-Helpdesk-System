package com.example.ticketing.sla;

import java.time.LocalDateTime;
import java.util.List;

import com.example.ticketing.ticket.TicketTypes.TicketPriority;

/**
 * DTOs cho SLA Policy API.
 */
public class SlaPolicyDtos {

    // ==================== Request DTOs ====================

    /**
     * Request để tạo/cập nhật SLA Policy.
     */
    public static class SlaPolicyRequest {
        private String name;
        private String description;
        private TicketPriority priority;
        private Integer responseMinutes;
        private Integer resolutionMinutes;
        private Boolean businessHoursOnly;
        private Integer warningThreshold;
        private Integer secondResponseMinutes;
        private Boolean isDefault;
        private Boolean enabled;

        // Constructors
        public SlaPolicyRequest() {
        }

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public TicketPriority getPriority() { return priority; }
        public void setPriority(TicketPriority priority) { this.priority = priority; }
        public Integer getResponseMinutes() { return responseMinutes; }
        public void setResponseMinutes(Integer responseMinutes) { this.responseMinutes = responseMinutes; }
        public Integer getResolutionMinutes() { return resolutionMinutes; }
        public void setResolutionMinutes(Integer resolutionMinutes) { this.resolutionMinutes = resolutionMinutes; }
        public Boolean getBusinessHoursOnly() { return businessHoursOnly; }
        public void setBusinessHoursOnly(Boolean businessHoursOnly) { this.businessHoursOnly = businessHoursOnly; }
        public Integer getWarningThreshold() { return warningThreshold; }
        public void setWarningThreshold(Integer warningThreshold) { this.warningThreshold = warningThreshold; }
        public Integer getSecondResponseMinutes() { return secondResponseMinutes; }
        public void setSecondResponseMinutes(Integer secondResponseMinutes) { this.secondResponseMinutes = secondResponseMinutes; }
        public Boolean getIsDefault() { return isDefault; }
        public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    }

    /**
     * Request để toggle enabled status.
     */
    public static class ToggleEnabledRequest {
        private Boolean enabled;

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    }

    // ==================== Response DTOs ====================

    /**
     * Response cho SLA Policy.
     */
    public static class SlaPolicyResponse {
        private Long id;
        private String name;
        private String description;
        private String priority;
        private Integer responseMinutes;
        private Integer resolutionMinutes;
        private String responseTimeFormatted;
        private String resolutionTimeFormatted;
        private Boolean businessHoursOnly;
        private Integer warningThreshold;
        private Integer secondResponseMinutes;
        private Boolean isDefault;
        private Boolean enabled;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String createdBy;
        private String updatedBy;

        // Convert from entity
        public static SlaPolicyResponse fromEntity(SlaPolicy policy) {
            SlaPolicyResponse response = new SlaPolicyResponse();
            response.setId(policy.getId());
            response.setName(policy.getName());
            response.setDescription(policy.getDescription());
            response.setPriority(policy.getPriority().name());
            response.setResponseMinutes(policy.getResponseMinutes());
            response.setResolutionMinutes(policy.getResolutionMinutes());
            response.setResponseTimeFormatted(policy.getResponseTimeFormatted());
            response.setResolutionTimeFormatted(policy.getResolutionTimeFormatted());
            response.setBusinessHoursOnly(policy.getBusinessHoursOnly());
            response.setWarningThreshold(policy.getWarningThreshold());
            response.setSecondResponseMinutes(policy.getSecondResponseMinutes());
            response.setIsDefault(policy.getIsDefault());
            response.setEnabled(policy.getEnabled());
            response.setCreatedAt(policy.getCreatedAt());
            response.setUpdatedAt(policy.getUpdatedAt());
            response.setCreatedBy(policy.getCreatedBy());
            response.setUpdatedBy(policy.getUpdatedBy());
            return response;
        }

        // Static list converter
        public static List<SlaPolicyResponse> fromEntities(List<SlaPolicy> policies) {
            return policies.stream()
                    .map(SlaPolicyResponse::fromEntity)
                    .collect(java.util.stream.Collectors.toList());
        }

        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public Integer getResponseMinutes() { return responseMinutes; }
        public void setResponseMinutes(Integer responseMinutes) { this.responseMinutes = responseMinutes; }
        public Integer getResolutionMinutes() { return resolutionMinutes; }
        public void setResolutionMinutes(Integer resolutionMinutes) { this.resolutionMinutes = resolutionMinutes; }
        public String getResponseTimeFormatted() { return responseTimeFormatted; }
        public void setResponseTimeFormatted(String responseTimeFormatted) { this.responseTimeFormatted = responseTimeFormatted; }
        public String getResolutionTimeFormatted() { return resolutionTimeFormatted; }
        public void setResolutionTimeFormatted(String resolutionTimeFormatted) { this.resolutionTimeFormatted = resolutionTimeFormatted; }
        public Boolean getBusinessHoursOnly() { return businessHoursOnly; }
        public void setBusinessHoursOnly(Boolean businessHoursOnly) { this.businessHoursOnly = businessHoursOnly; }
        public Integer getWarningThreshold() { return warningThreshold; }
        public void setWarningThreshold(Integer warningThreshold) { this.warningThreshold = warningThreshold; }
        public Integer getSecondResponseMinutes() { return secondResponseMinutes; }
        public void setSecondResponseMinutes(Integer secondResponseMinutes) { this.secondResponseMinutes = secondResponseMinutes; }
        public Boolean getIsDefault() { return isDefault; }
        public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    }

    /**
     * Response cho SLA Deadline (dùng khi tạo ticket).
     */
    public static class SlaDeadlineResponse {
        private Long policyId;
        private Integer responseMinutes;
        private Integer resolutionMinutes;
        private Integer warningThreshold;
        private LocalDateTime responseDeadline;
        private LocalDateTime resolutionDeadline;

        public static SlaDeadlineResponse fromDeadline(SlaPolicyService.SlaDeadline deadline) {
            SlaDeadlineResponse response = new SlaDeadlineResponse();
            response.setPolicyId(deadline.getPolicyId());
            response.setResponseMinutes(deadline.getResponseMinutes());
            response.setResolutionMinutes(deadline.getResolutionMinutes());
            response.setWarningThreshold(deadline.getWarningThreshold());
            response.setResponseDeadline(deadline.getResponseDeadline());
            response.setResolutionDeadline(deadline.getResolutionDeadline());
            return response;
        }

        // Getters and Setters
        public Long getPolicyId() { return policyId; }
        public void setPolicyId(Long policyId) { this.policyId = policyId; }
        public Integer getResponseMinutes() { return responseMinutes; }
        public void setResponseMinutes(Integer responseMinutes) { this.responseMinutes = responseMinutes; }
        public Integer getResolutionMinutes() { return resolutionMinutes; }
        public void setResolutionMinutes(Integer resolutionMinutes) { this.resolutionMinutes = resolutionMinutes; }
        public Integer getWarningThreshold() { return warningThreshold; }
        public void setWarningThreshold(Integer warningThreshold) { this.warningThreshold = warningThreshold; }
        public LocalDateTime getResponseDeadline() { return responseDeadline; }
        public void setResponseDeadline(LocalDateTime responseDeadline) { this.responseDeadline = responseDeadline; }
        public LocalDateTime getResolutionDeadline() { return resolutionDeadline; }
        public void setResolutionDeadline(LocalDateTime resolutionDeadline) { this.resolutionDeadline = resolutionDeadline; }
    }

    /**
     * Response cho Priority Summary (dùng trong dashboard).
     */
    public static class PrioritySlaSummary {
        private String priority;
        private String responseTimeFormatted;
        private String resolutionTimeFormatted;
        private Integer warningThreshold;

        // Getters and Setters
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public String getResponseTimeFormatted() { return responseTimeFormatted; }
        public void setResponseTimeFormatted(String responseTimeFormatted) { this.responseTimeFormatted = responseTimeFormatted; }
        public String getResolutionTimeFormatted() { return resolutionTimeFormatted; }
        public void setResolutionTimeFormatted(String resolutionTimeFormatted) { this.resolutionTimeFormatted = resolutionTimeFormatted; }
        public Integer getWarningThreshold() { return warningThreshold; }
        public void setWarningThreshold(Integer warningThreshold) { this.warningThreshold = warningThreshold; }
    }
}

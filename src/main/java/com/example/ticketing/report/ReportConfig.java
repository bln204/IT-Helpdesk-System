package com.example.ticketing.report;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Report Configuration Entity.
 */
@Entity
@Table(name = "report_configs")
public class ReportConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", length = 50, nullable = false)
    private ReportType reportType;

    @Column(length = 50)
    private String category;

    @Column(columnDefinition = "jsonb")
    private String config;

    @Column(name = "is_scheduled")
    private Boolean isScheduled = false;

    @Column(name = "schedule_cron", length = 100)
    private String scheduleCron;

    @Column(name = "output_format", length = 20)
    private String outputFormat = "TABLE";

    @Column(name = "is_public")
    private Boolean isPublic = false;

    @Column(columnDefinition = "TEXT")
    private String allowedRoles;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    // ==================== Enums ====================

    public enum ReportType {
        TICKET_VOLUME("Ticket Volume"),
        RESPONSE_TIME("Response Time"),
        RESOLUTION_TIME("Resolution Time"),
        SLA_COMPLIANCE("SLA Compliance"),
        ASSET_STATUS("Asset Status"),
        CHANGE_ANALYSIS("Change Analysis"),
        USER_SATISFACTION("User Satisfaction"),
        CUSTOM("Custom");

        private final String label;
        ReportType(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    // ==================== Constructors ====================

    public ReportConfig() {
    }

    // ==================== Getters and Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ReportType getReportType() { return reportType; }
    public void setReportType(ReportType reportType) { this.reportType = reportType; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }
    public Boolean getIsScheduled() { return isScheduled; }
    public void setIsScheduled(Boolean isScheduled) { this.isScheduled = isScheduled; }
    public String getScheduleCron() { return scheduleCron; }
    public void setScheduleCron(String scheduleCron) { this.scheduleCron = scheduleCron; }
    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }
    public Boolean getIsPublic() { return isPublic; }
    public void setIsPublic(Boolean isPublic) { this.isPublic = isPublic; }
    public String getAllowedRoles() { return allowedRoles; }
    public void setAllowedRoles(String allowedRoles) { this.allowedRoles = allowedRoles; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    @Override
    public String toString() {
        return "ReportConfig{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", reportType=" + reportType +
                '}';
    }
}

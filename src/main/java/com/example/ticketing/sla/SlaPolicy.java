package com.example.ticketing.sla;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.example.ticketing.ticket.TicketTypes.TicketPriority;

/**
 * SLA Policy Entity - Lưu trữ cấu hình SLA có thể tùy chỉnh.
 * 
 * Thay vì hardcode SLA trong enum, giờ đây có thể cấu hình qua database.
 */
@Entity
@Table(name = "sla_policies")
public class SlaPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    /**
     * Priority level mà policy này áp dụng.
     * CRITICAL, HIGH, MEDIUM, LOW, URGENT
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private TicketPriority priority;

    /**
     * Thời gian phản hồi tối đa (tính bằng phút).
     * VD: 15 phút cho Critical, 480 phút cho Low (8 giờ)
     */
    @Column(name = "response_minutes", nullable = false)
    private Integer responseMinutes;

    /**
     * Thời gian giải quyết tối đa (tính bằng phút).
     * VD: 120 phút cho Critical (2 giờ), 4320 phút cho Low (3 ngày)
     */
    @Column(name = "resolution_minutes", nullable = false)
    private Integer resolutionMinutes;

    /**
     * Nếu true, SLA chỉ tính trong giờ hành chính (9:00 - 18:00, Mon-Fri).
     * Cần cấu hình thêm business hours nếu cần.
     */
    @Column(name = "business_hours_only")
    private Boolean businessHoursOnly = false;

    /**
     * Ngưỡng cảnh báo (%).
     * VD: 75 = cảnh báo khi đã sử dụng 75% thời gian SLA.
     */
    @Column(name = "warning_threshold")
    private Integer warningThreshold = 75;

    /**
     * Thời gian phản hồi lần 2 (tùy chọn - cho multi-stage SLA).
     */
    @Column(name = "second_response_minutes")
    private Integer secondResponseMinutes;

    /**
     * Là policy mặc định cho priority này?
     * Mỗi priority chỉ có 1 default policy.
     */
    @Column(name = "is_default")
    private Boolean isDefault = false;

    /**
     * Policy có đang enabled không?
     */
    @Column(name = "enabled")
    private Boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    // ==================== Constructors ====================

    public SlaPolicy() {
    }

    public SlaPolicy(String name, TicketPriority priority, Integer responseMinutes, Integer resolutionMinutes) {
        this.name = name;
        this.priority = priority;
        this.responseMinutes = responseMinutes;
        this.resolutionMinutes = resolutionMinutes;
    }

    // ==================== Utility Methods ====================

    /**
     * Chuyển responseMinutes sang hours cho hiển thị.
     */
    public double getResponseHours() {
        return responseMinutes / 60.0;
    }

    /**
     * Chuyển resolutionMinutes sang hours cho hiển thị.
     */
    public double getResolutionHours() {
        return resolutionMinutes / 60.0;
    }

    /**
     * Format response time thành string dễ đọc.
     * VD: "2 giờ", "15 phút", "3 ngày"
     */
    public String getResponseTimeFormatted() {
        return formatMinutes(responseMinutes);
    }

    /**
     * Format resolution time thành string dễ đọc.
     */
    public String getResolutionTimeFormatted() {
        return formatMinutes(resolutionMinutes);
    }

    private String formatMinutes(Integer minutes) {
        if (minutes < 60) {
            return minutes + " phút";
        } else if (minutes < 1440) { // < 24 giờ
            double hours = minutes / 60.0;
            if (hours == Math.floor(hours)) {
                return (int) hours + " giờ";
            }
            return String.format("%.1f giờ", hours);
        } else {
            double days = minutes / 1440.0; // 1440 phút = 1 ngày
            if (days == Math.floor(days)) {
                return (int) days + " ngày";
            }
            return String.format("%.1f ngày", days);
        }
    }

    // ==================== Getters and Setters ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public void setPriority(TicketPriority priority) {
        this.priority = priority;
    }

    public Integer getResponseMinutes() {
        return responseMinutes;
    }

    public void setResponseMinutes(Integer responseMinutes) {
        this.responseMinutes = responseMinutes;
    }

    public Integer getResolutionMinutes() {
        return resolutionMinutes;
    }

    public void setResolutionMinutes(Integer resolutionMinutes) {
        this.resolutionMinutes = resolutionMinutes;
    }

    public Boolean getBusinessHoursOnly() {
        return businessHoursOnly;
    }

    public void setBusinessHoursOnly(Boolean businessHoursOnly) {
        this.businessHoursOnly = businessHoursOnly;
    }

    public Integer getWarningThreshold() {
        return warningThreshold;
    }

    public void setWarningThreshold(Integer warningThreshold) {
        this.warningThreshold = warningThreshold;
    }

    public Integer getSecondResponseMinutes() {
        return secondResponseMinutes;
    }

    public void setSecondResponseMinutes(Integer secondResponseMinutes) {
        this.secondResponseMinutes = secondResponseMinutes;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    @Override
    public String toString() {
        return "SlaPolicy{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", priority=" + priority +
                ", responseMinutes=" + responseMinutes +
                ", resolutionMinutes=" + resolutionMinutes +
                '}';
    }
}

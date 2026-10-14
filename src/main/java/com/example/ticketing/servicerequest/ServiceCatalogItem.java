package com.example.ticketing.servicerequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Service Catalog Item Entity - Định nghĩa các dịch vụ có sẵn.
 */
@Entity
@Table(name = "service_catalog")
public class ServiceCatalogItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String category;

    @Column(length = 50)
    private String icon;

    @Column(length = 20)
    private String color;

    @Column(name = "response_sla_minutes")
    private Integer responseSlaMinutes = 480; // 8 hours

    @Column(name = "resolution_sla_minutes")
    private Integer resolutionSlaMinutes = 2880; // 48 hours

    @Column(precision = 10, scale = 2)
    private BigDecimal cost = BigDecimal.ZERO;

    @Column
    private Boolean billable = false;

    @Column(name = "requires_approval")
    private Boolean requiresApproval = false;

    @Column(name = "approver_role", length = 50)
    private String approverRole;

    @Column(name = "auto_approve")
    private Boolean autoApprove = false;

    // CP-3.3.10: the column is JSONB in the schema (V48) and must
    // round-trip structured payloads. Without @JdbcTypeCode(SqlTypes.JSON)
    // Hibernate binds the parameter as varchar, which PostgreSQL
    // rejects with SQLSTATE 42804 ("column is of type jsonb but
    // expression is of type character varying"). Critically, this
    // failure also occurs when the Java field is null because the
    // driver still sends a typed-varchar NULL that PostgreSQL cannot
    // implicitly cast to jsonb. This annotation matches the proven
    // ChangeTimeline.event_data mapping (CP-3.3.7), the proven
    // ChangeRequest.risk_factors mapping (CP-3.3.8) and the proven
    // ServiceRequest.form_data mapping (CP-3.3.9) used in production
    // for other JSONB columns. CP-3.3.10 baseline run on PostgreSQL
    // reproduced the error before this annotation was added - both
    // for round-trip and for Java null.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "form_fields", columnDefinition = "jsonb")
    private String formFields;

    @Column
    private Boolean enabled = true;

    @Column(name = "is_public")
    private Boolean isPublic = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    // ==================== Constructors ====================

    public ServiceCatalogItem() {
    }

    // ==================== Getters and Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Integer getResponseSlaMinutes() { return responseSlaMinutes; }
    public void setResponseSlaMinutes(Integer responseSlaMinutes) { this.responseSlaMinutes = responseSlaMinutes; }
    public Integer getResolutionSlaMinutes() { return resolutionSlaMinutes; }
    public void setResolutionSlaMinutes(Integer resolutionSlaMinutes) { this.resolutionSlaMinutes = resolutionSlaMinutes; }
    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }
    public Boolean getBillable() { return billable; }
    public void setBillable(Boolean billable) { this.billable = billable; }
    public Boolean getRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(Boolean requiresApproval) { this.requiresApproval = requiresApproval; }
    public String getApproverRole() { return approverRole; }
    public void setApproverRole(String approverRole) { this.approverRole = approverRole; }
    public Boolean getAutoApprove() { return autoApprove; }
    public void setAutoApprove(Boolean autoApprove) { this.autoApprove = autoApprove; }
    public String getFormFields() { return formFields; }
    public void setFormFields(String formFields) { this.formFields = formFields; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getIsPublic() { return isPublic; }
    public void setIsPublic(Boolean isPublic) { this.isPublic = isPublic; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    @Override
    public String toString() {
        return "ServiceCatalogItem{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", category='" + category + '\'' +
                '}';
    }
}

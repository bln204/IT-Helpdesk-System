package com.example.ticketing.problem;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Known Error Entity - Known Error Database (KEDB).
 */
@Entity
@Table(name = "known_errors")
public class KnownError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "error_code", unique = true, nullable = false, length = 50)
    private String errorCode;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String symptoms;

    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;

    @Column(name = "root_cause_category", length = 100)
    private String rootCauseCategory;

    @Column(columnDefinition = "TEXT")
    private String workaround;

    @Column(columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "fix_steps", columnDefinition = "TEXT")
    private String fixSteps;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private KnownErrorStatus status = KnownErrorStatus.ACTIVE;

    @Column(name = "impact_description", columnDefinition = "TEXT")
    private String impactDescription;

    @Column(name = "affected_systems", columnDefinition = "TEXT")
    private String affectedSystems;

    @Column(name = "related_problems_count")
    private Integer relatedProblemsCount = 0;

    @Column(name = "knowledge_article_id")
    private Long knowledgeArticleId;

    @Column(name = "managed_by", length = 100)
    private String managedBy;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "occurrence_count")
    private Integer occurrenceCount = 0;

    @Column(name = "last_occurrence_at")
    private LocalDateTime lastOccurrenceAt;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

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

    // ==================== Enums ====================

    public enum KnownErrorStatus {
        ACTIVE("Đang hoạt động"),
        RESOLVED("Đã được sửa"),
        OBSOLETE("Không còn áp dụng");

        private final String label;
        KnownErrorStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    // ==================== Constructors ====================

    public KnownError() {
    }

    // ==================== Getters and Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSymptoms() { return symptoms; }
    public void setSymptoms(String symptoms) { this.symptoms = symptoms; }
    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    public String getRootCauseCategory() { return rootCauseCategory; }
    public void setRootCauseCategory(String rootCauseCategory) { this.rootCauseCategory = rootCauseCategory; }
    public String getWorkaround() { return workaround; }
    public void setWorkaround(String workaround) { this.workaround = workaround; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public String getFixSteps() { return fixSteps; }
    public void setFixSteps(String fixSteps) { this.fixSteps = fixSteps; }
    public KnownErrorStatus getStatus() { return status; }
    public void setStatus(KnownErrorStatus status) { this.status = status; }
    public String getImpactDescription() { return impactDescription; }
    public void setImpactDescription(String impactDescription) { this.impactDescription = impactDescription; }
    public String getAffectedSystems() { return affectedSystems; }
    public void setAffectedSystems(String affectedSystems) { this.affectedSystems = affectedSystems; }
    public Integer getRelatedProblemsCount() { return relatedProblemsCount; }
    public void setRelatedProblemsCount(Integer relatedProblemsCount) { this.relatedProblemsCount = relatedProblemsCount; }
    public Long getKnowledgeArticleId() { return knowledgeArticleId; }
    public void setKnowledgeArticleId(Long knowledgeArticleId) { this.knowledgeArticleId = knowledgeArticleId; }
    public String getManagedBy() { return managedBy; }
    public void setManagedBy(String managedBy) { this.managedBy = managedBy; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public Integer getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(Integer occurrenceCount) { this.occurrenceCount = occurrenceCount; }
    public LocalDateTime getLastOccurrenceAt() { return lastOccurrenceAt; }
    public void setLastOccurrenceAt(LocalDateTime lastOccurrenceAt) { this.lastOccurrenceAt = lastOccurrenceAt; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDateTime validUntil) { this.validUntil = validUntil; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    @Override
    public String toString() {
        return "KnownError{" +
                "id=" + id +
                ", errorCode='" + errorCode + '\'' +
                ", title='" + title + '\'' +
                '}';
    }
}

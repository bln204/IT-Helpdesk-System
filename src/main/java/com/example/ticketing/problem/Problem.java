package com.example.ticketing.problem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Problem Entity - Theo dõi nguyên nhân gốc rễ của incidents lặp lại.
 */
@Entity
@Table(name = "problems")
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "problem_number", unique = true, nullable = false, length = 50)
    private String problemNumber;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_level", length = 20)
    private ImpactLevel impactLevel = ImpactLevel.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Priority priority = Priority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ProblemStatus status = ProblemStatus.NEW;

    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;

    @Column(name = "root_cause_category", length = 100)
    private String rootCauseCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "root_cause_confidence", length = 20)
    private Confidence rootCauseConfidence = Confidence.MEDIUM;

    @Column(columnDefinition = "TEXT")
    private String workaround;

    @Column(columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "resolution_date")
    private LocalDateTime resolutionDate;

    @Column(name = "known_error_id")
    private Long knownErrorId;

    @Column(name = "total_incidents_linked")
    private Integer totalIncidentsLinked = 0;

    @Column(name = "total_downtime_minutes")
    private Integer totalDowntimeMinutes = 0;

    @Column(name = "estimated_cost", precision = 10, scale = 2)
    private BigDecimal estimatedCost;

    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "closed_by", length = 100)
    private String closedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closure_notes", columnDefinition = "TEXT")
    private String closureNotes;

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    private List<ProblemWorkaroundNote> notes = new ArrayList<>();

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

    public enum ProblemStatus {
        NEW("Mới tạo"),
        INVESTIGATING("Đang điều tra"),
        IDENTIFIED("Đã xác định"),
        SOLVING("Đang giải quyết"),
        RESOLVED("Đã giải quyết"),
        CLOSED("Đã đóng");

        private final String label;
        ProblemStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum ImpactLevel {
        LOW("Thấp"),
        MEDIUM("Trung bình"),
        HIGH("Cao"),
        CRITICAL("Nghiêm trọng");

        private final String label;
        ImpactLevel(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum Priority {
        LOW("Thấp"),
        MEDIUM("Trung bình"),
        HIGH("Cao"),
        CRITICAL("Nghiêm trọng");

        private final String label;
        Priority(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum Confidence {
        LOW("Thấp"),
        MEDIUM("Trung bình"),
        HIGH("Cao"),
        CONFIRMED("Đã xác nhận");

        private final String label;
        Confidence(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    // ==================== Constructors ====================

    public Problem() {
    }

    // ==================== Getters and Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProblemNumber() { return problemNumber; }
    public void setProblemNumber(String problemNumber) { this.problemNumber = problemNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public ImpactLevel getImpactLevel() { return impactLevel; }
    public void setImpactLevel(ImpactLevel impactLevel) { this.impactLevel = impactLevel; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public ProblemStatus getStatus() { return status; }
    public void setStatus(ProblemStatus status) { this.status = status; }
    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    public String getRootCauseCategory() { return rootCauseCategory; }
    public void setRootCauseCategory(String rootCauseCategory) { this.rootCauseCategory = rootCauseCategory; }
    public Confidence getRootCauseConfidence() { return rootCauseConfidence; }
    public void setRootCauseConfidence(Confidence rootCauseConfidence) { this.rootCauseConfidence = rootCauseConfidence; }
    public String getWorkaround() { return workaround; }
    public void setWorkaround(String workaround) { this.workaround = workaround; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public LocalDateTime getResolutionDate() { return resolutionDate; }
    public void setResolutionDate(LocalDateTime resolutionDate) { this.resolutionDate = resolutionDate; }
    public Long getKnownErrorId() { return knownErrorId; }
    public void setKnownErrorId(Long knownErrorId) { this.knownErrorId = knownErrorId; }
    public Integer getTotalIncidentsLinked() { return totalIncidentsLinked; }
    public void setTotalIncidentsLinked(Integer totalIncidentsLinked) { this.totalIncidentsLinked = totalIncidentsLinked; }
    public Integer getTotalDowntimeMinutes() { return totalDowntimeMinutes; }
    public void setTotalDowntimeMinutes(Integer totalDowntimeMinutes) { this.totalDowntimeMinutes = totalDowntimeMinutes; }
    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(BigDecimal estimatedCost) { this.estimatedCost = estimatedCost; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public String getClosedBy() { return closedBy; }
    public void setClosedBy(String closedBy) { this.closedBy = closedBy; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public String getClosureNotes() { return closureNotes; }
    public void setClosureNotes(String closureNotes) { this.closureNotes = closureNotes; }
    public List<ProblemWorkaroundNote> getNotes() { return notes; }
    public void setNotes(List<ProblemWorkaroundNote> notes) { this.notes = notes; }
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
        return "Problem{" +
                "id=" + id +
                ", problemNumber='" + problemNumber + '\'' +
                ", title='" + title + '\'' +
                ", status=" + status +
                '}';
    }
}

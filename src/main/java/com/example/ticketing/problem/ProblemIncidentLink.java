package com.example.ticketing.problem;

import java.time.LocalDateTime;

import jakarta.persistence.*;

/**
 * Link entity between Problem and Incident.
 */
@Entity
@Table(name = "problem_incident_links")
public class ProblemIncidentLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(name = "incident_id", nullable = false)
    private Long incidentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", length = 30)
    private LinkType linkType = LinkType.CAUSED_BY;

    @Column(name = "linked_at")
    private LocalDateTime linkedAt;

    @Column(name = "linked_by", length = 100)
    private String linkedBy;

    // ==================== Enums ====================

    public enum LinkType {
        CAUSED_BY("Caused by"),
        RELATED_TO("Related to"),
        DUPLICATE_OF("Duplicate of");

        private final String label;
        LinkType(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    // ==================== Constructors ====================

    public ProblemIncidentLink() {
        this.linkedAt = LocalDateTime.now();
    }

    public ProblemIncidentLink(Problem problem, Long incidentId) {
        this.problem = problem;
        this.incidentId = incidentId;
        this.linkedAt = LocalDateTime.now();
    }

    // ==================== Getters and Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Problem getProblem() { return problem; }
    public void setProblem(Problem problem) { this.problem = problem; }
    public Long getIncidentId() { return incidentId; }
    public void setIncidentId(Long incidentId) { this.incidentId = incidentId; }
    public LinkType getLinkType() { return linkType; }
    public void setLinkType(LinkType linkType) { this.linkType = linkType; }
    public LocalDateTime getLinkedAt() { return linkedAt; }
    public void setLinkedAt(LocalDateTime linkedAt) { this.linkedAt = linkedAt; }
    public String getLinkedBy() { return linkedBy; }
    public void setLinkedBy(String linkedBy) { this.linkedBy = linkedBy; }

    @Override
    public String toString() {
        return "ProblemIncidentLink{" +
                "id=" + id +
                ", problemId=" + (problem != null ? problem.getId() : null) +
                ", incidentId=" + incidentId +
                '}';
    }
}

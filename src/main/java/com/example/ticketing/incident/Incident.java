package com.example.ticketing.incident;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.team.Team;
import com.example.ticketing.ticket.TicketTypes.TicketPriority;

/**
 * Incident Entity - Đại diện cho một sự cố (incident).
 * Incident gom nhiều tickets liên quan lại với nhau.
 */
@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_number", unique = true, nullable = false, length = 50)
    private String incidentNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Incident Status.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private IncidentStatus status = IncidentStatus.INVESTIGATING;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TicketPriority priority;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private UserAccount assignedTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(name = "reported_by_username", length = 100)
    private String reportedByUsername;

    /**
     * Impact level.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "impact_level", length = 20)
    private ImpactLevel impactLevel = ImpactLevel.LOW;

    @Column(name = "affected_users")
    private Integer affectedUsers = 0;

    @Column(columnDefinition = "TEXT")
    private String rootCause;

    @Column(columnDefinition = "TEXT")
    private String workaround;

    @Column(columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "identified_at")
    private LocalDateTime identifiedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "linked_ticket_count")
    private Integer linkedTicketCount = 0;

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("linkedAt DESC")
    private List<TicketIncidentLink> ticketLinks = new ArrayList<>();

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

    public enum IncidentStatus {
        INVESTIGATING("Đang điều tra"),
        IDENTIFIED("Đã xác định nguyên nhân"),
        RESOLVED("Đã giải quyết"),
        CLOSED("Đã đóng");

        private final String label;

        IncidentStatus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum ImpactLevel {
        LOW("Thấp - Ảnh hưởng một vài người"),
        MEDIUM("Trung bình - Ảnh hưởng một team"),
        HIGH("Cao - Ảnh hưởng toàn bộ công ty"),
        CRITICAL("Nghiêm trọng - Ảnh hưởng nghiêm trọng");

        private final String label;

        ImpactLevel(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    // ==================== Constructors ====================

    public Incident() {
    }

    public Incident(String title, String description) {
        this.title = title;
        this.description = description;
        this.status = IncidentStatus.INVESTIGATING;
    }

    // ==================== Business Methods ====================

    /**
     * Link a ticket to this incident.
     */
    public void linkTicket(TicketIncidentLink link) {
        ticketLinks.add(link);
        link.setIncident(this);
        linkedTicketCount = ticketLinks.size();
    }

    /**
     * Unlink a ticket from this incident.
     */
    public void unlinkTicket(TicketIncidentLink link) {
        ticketLinks.remove(link);
        link.setIncident(null);
        linkedTicketCount = ticketLinks.size();
    }

    /**
     * Check if ticket is already linked.
     */
    public boolean isTicketLinked(Long ticketId) {
        return ticketLinks.stream()
                .anyMatch(link -> link.getTicket() != null && link.getTicket().getId().equals(ticketId));
    }

    /**
     * Get total affected users count.
     */
    public int getTotalAffectedUsers() {
        return ticketLinks.stream()
                .mapToInt(link -> link.getImpact() != null ? 1 : 0)
                .sum() + (affectedUsers != null ? affectedUsers : 0);
    }

    // ==================== Getters and Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getIncidentNumber() { return incidentNumber; }
    public void setIncidentNumber(String incidentNumber) { this.incidentNumber = incidentNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public IncidentStatus getStatus() { return status; }
    public void setStatus(IncidentStatus status) { this.status = status; }
    public TicketPriority getPriority() { return priority; }
    public void setPriority(TicketPriority priority) { this.priority = priority; }
    public UserAccount getAssignedTo() { return assignedTo; }
    public void setAssignedTo(UserAccount assignedTo) { this.assignedTo = assignedTo; }
    public Team getTeam() { return team; }
    public void setTeam(Team team) { this.team = team; }
    public String getReportedByUsername() { return reportedByUsername; }
    public void setReportedByUsername(String reportedByUsername) { this.reportedByUsername = reportedByUsername; }
    public ImpactLevel getImpactLevel() { return impactLevel; }
    public void setImpactLevel(ImpactLevel impactLevel) { this.impactLevel = impactLevel; }
    public Integer getAffectedUsers() { return affectedUsers; }
    public void setAffectedUsers(Integer affectedUsers) { this.affectedUsers = affectedUsers; }
    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    public String getWorkaround() { return workaround; }
    public void setWorkaround(String workaround) { this.workaround = workaround; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public LocalDateTime getIdentifiedAt() { return identifiedAt; }
    public void setIdentifiedAt(LocalDateTime identifiedAt) { this.identifiedAt = identifiedAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public Integer getLinkedTicketCount() { return linkedTicketCount; }
    public void setLinkedTicketCount(Integer linkedTicketCount) { this.linkedTicketCount = linkedTicketCount; }
    public List<TicketIncidentLink> getTicketLinks() { return ticketLinks; }
    public void setTicketLinks(List<TicketIncidentLink> ticketLinks) { this.ticketLinks = ticketLinks; }
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
        return "Incident{" +
                "id=" + id +
                ", incidentNumber='" + incidentNumber + '\'' +
                ", title='" + title + '\'' +
                ", status=" + status +
                '}';
    }
}

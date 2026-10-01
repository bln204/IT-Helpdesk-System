package com.example.ticketing.incident;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import com.example.ticketing.ticket.Ticket;

/**
 * Link entity giữa Ticket và Incident.
 * Một ticket có thể được link đến nhiều incidents.
 */
@Entity
@Table(name = "ticket_incident_links")
public class TicketIncidentLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(name = "link_reason", length = 200)
    private String linkReason;

    @Column(name = "linked_by", length = 100)
    private String linkedBy;

    @Column(name = "linked_at")
    private LocalDateTime linkedAt;

    /**
     * Impact của ticket này với incident.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Incident.ImpactLevel impact = Incident.ImpactLevel.LOW;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ==================== Constructors ====================

    public TicketIncidentLink() {
        this.linkedAt = LocalDateTime.now();
    }

    public TicketIncidentLink(Ticket ticket, Incident incident) {
        this();
        this.ticket = ticket;
        this.incident = incident;
    }

    // ==================== Getters and Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Ticket getTicket() { return ticket; }
    public void setTicket(Ticket ticket) { this.ticket = ticket; }
    public Incident getIncident() { return incident; }
    public void setIncident(Incident incident) { this.incident = incident; }
    public String getLinkReason() { return linkReason; }
    public void setLinkReason(String linkReason) { this.linkReason = linkReason; }
    public String getLinkedBy() { return linkedBy; }
    public void setLinkedBy(String linkedBy) { this.linkedBy = linkedBy; }
    public LocalDateTime getLinkedAt() { return linkedAt; }
    public void setLinkedAt(LocalDateTime linkedAt) { this.linkedAt = linkedAt; }
    public Incident.ImpactLevel getImpact() { return impact; }
    public void setImpact(Incident.ImpactLevel impact) { this.impact = impact; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "TicketIncidentLink{" +
                "id=" + id +
                ", ticketId=" + (ticket != null ? ticket.getId() : null) +
                ", incidentId=" + (incident != null ? incident.getId() : null) +
                '}';
    }
}

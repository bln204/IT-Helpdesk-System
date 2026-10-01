package com.example.ticketing.asset;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Asset Ticket Link Entity - Links assets with tickets/incidents/changes.
 */
@Entity
@Table(name = "asset_ticket_links")
public class AssetTicketLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "incident_id")
    private Long incidentId;

    @Column(name = "change_id")
    private Long changeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", length = 50)
    private LinkType linkType = LinkType.AFFECTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact_assessment", length = 50)
    private ImpactAssessment impactAssessment = ImpactAssessment.MODERATE;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "identified_by", length = 100)
    private String identifiedBy;

    @Column(name = "identified_at")
    private LocalDateTime identifiedAt;

    @Column
    private Boolean resolved = false;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ==================== Enums ====================

    public enum LinkType {
        AFFECTED("Bị ảnh hưởng"),
        CAUSED_BY("Gây ra bởi"),
        RELATED("Liên quan"),
        USED_FOR("Được dùng để giải quyết"),
        RESOLVED_BY("Được sử dụng để fix");

        private final String label;
        LinkType(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum ImpactAssessment {
        MINIMAL("Tối thiểu"),
        MODERATE("Trung bình"),
        SIGNIFICANT("Đáng kể"),
        SEVERE("Nghiêm trọng");

        private final String label;
        ImpactAssessment(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    // ==================== Constructors ====================

    public AssetTicketLink() {
        this.identifiedAt = LocalDateTime.now();
    }

    public AssetTicketLink(Asset asset, Long incidentId, LinkType linkType) {
        this.asset = asset;
        this.incidentId = incidentId;
        this.linkType = linkType;
        this.identifiedAt = LocalDateTime.now();
    }

    // ==================== Getters and Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Asset getAsset() { return asset; }
    public void setAsset(Asset asset) { this.asset = asset; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public Long getIncidentId() { return incidentId; }
    public void setIncidentId(Long incidentId) { this.incidentId = incidentId; }
    public Long getChangeId() { return changeId; }
    public void setChangeId(Long changeId) { this.changeId = changeId; }
    public LinkType getLinkType() { return linkType; }
    public void setLinkType(LinkType linkType) { this.linkType = linkType; }
    public ImpactAssessment getImpactAssessment() { return impactAssessment; }
    public void setImpactAssessment(ImpactAssessment impactAssessment) { this.impactAssessment = impactAssessment; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getIdentifiedBy() { return identifiedBy; }
    public void setIdentifiedBy(String identifiedBy) { this.identifiedBy = identifiedBy; }
    public LocalDateTime getIdentifiedAt() { return identifiedAt; }
    public void setIdentifiedAt(LocalDateTime identifiedAt) { this.identifiedAt = identifiedAt; }
    public Boolean getResolved() { return resolved; }
    public void setResolved(Boolean resolved) { this.resolved = resolved; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "AssetTicketLink{" +
                "id=" + id +
                ", linkType=" + linkType +
                ", incidentId=" + incidentId +
                '}';
    }
}

package com.example.ticketing.asset;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Asset Incident Link Entity - Direct link between asset and incident.
 */
@Entity
@Table(name = "asset_incident_links")
public class AssetIncidentLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "incident_id", nullable = false)
    private Long incidentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_role", length = 50)
    private LinkRole linkRole = LinkRole.AFFECTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_level", length = 20)
    private ConfidenceLevel confidenceLevel = ConfidenceLevel.MEDIUM;

    @Column(columnDefinition = "TEXT")
    private String symptoms;

    @Column(name = "impact_description", columnDefinition = "TEXT")
    private String impactDescription;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    // ==================== Enums ====================

    public enum LinkRole {
        AFFECTED("Bị ảnh hưởng"),
        ROOT_CAUSE("Nguyên nhân gốc"),
        SUSPECT("Nghi vấn"),
        RESOLVED_BY("Được dùng để fix");

        private final String label;
        LinkRole(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum ConfidenceLevel {
        LOW("Thấp"),
        MEDIUM("Trung bình"),
        HIGH("Cao");

        private final String label;
        ConfidenceLevel(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    // ==================== Constructors ====================

    public AssetIncidentLink() {
    }

    public AssetIncidentLink(Asset asset, Long incidentId, LinkRole linkRole) {
        this.asset = asset;
        this.incidentId = incidentId;
        this.linkRole = linkRole;
    }

    // ==================== Getters and Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Asset getAsset() { return asset; }
    public void setAsset(Asset asset) { this.asset = asset; }
    public Long getIncidentId() { return incidentId; }
    public void setIncidentId(Long incidentId) { this.incidentId = incidentId; }
    public LinkRole getLinkRole() { return linkRole; }
    public void setLinkRole(LinkRole linkRole) { this.linkRole = linkRole; }
    public ConfidenceLevel getConfidenceLevel() { return confidenceLevel; }
    public void setConfidenceLevel(ConfidenceLevel confidenceLevel) { this.confidenceLevel = confidenceLevel; }
    public String getSymptoms() { return symptoms; }
    public void setSymptoms(String symptoms) { this.symptoms = symptoms; }
    public String getImpactDescription() { return impactDescription; }
    public void setImpactDescription(String impactDescription) { this.impactDescription = impactDescription; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    @Override
    public String toString() {
        return "AssetIncidentLink{" +
                "id=" + id +
                ", linkRole=" + linkRole +
                ", incidentId=" + incidentId +
                '}';
    }
}

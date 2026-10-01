package com.example.ticketing.asset;

import java.time.LocalDate;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Software Catalog Entity.
 */
@Entity
@Table(name = "software_catalog")
public class SoftwareCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 200)
    private String publisher;

    @Column(length = 100)
    private String category;

    @Column(length = 50)
    private String version;

    @Column(name = "license_type", length = 50)
    private String licenseType;

    @Column(name = "total_licenses")
    private Integer totalLicenses = 0;

    @Column(name = "used_licenses")
    private Integer usedLicenses = 0;

    @Column(name = "available_licenses")
    private Integer availableLicenses = 0;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(precision = 12, scale = 2)
    private java.math.BigDecimal cost;

    @Column(name = "compliance_status", length = 20)
    private String complianceStatus = "COMPLIANT";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private java.time.LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;

    // ==================== Constructors ====================

    public SoftwareCatalog() {
    }

    // ==================== Getters and Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getLicenseType() { return licenseType; }
    public void setLicenseType(String licenseType) { this.licenseType = licenseType; }
    public Integer getTotalLicenses() { return totalLicenses; }
    public void setTotalLicenses(Integer totalLicenses) { this.totalLicenses = totalLicenses; }
    public Integer getUsedLicenses() { return usedLicenses; }
    public void setUsedLicenses(Integer usedLicenses) { this.usedLicenses = usedLicenses; }
    public Integer getAvailableLicenses() { return availableLicenses; }
    public void setAvailableLicenses(Integer availableLicenses) { this.availableLicenses = availableLicenses; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public java.math.BigDecimal getCost() { return cost; }
    public void setCost(java.math.BigDecimal cost) { this.cost = cost; }
    public String getComplianceStatus() { return complianceStatus; }
    public void setComplianceStatus(String complianceStatus) { this.complianceStatus = complianceStatus; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "SoftwareCatalog{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}

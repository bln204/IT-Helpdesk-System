package com.example.ticketing.asset;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Asset Entity - IT Asset tracking.
 */
@Entity
@Table(name = "assets")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_number", unique = true, nullable = false, length = 50)
    private String assetNumber;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", length = 50, nullable = false)
    private AssetType assetType;

    @Column(length = 100)
    private String category;

    @Column(length = 100)
    private String manufacturer;

    @Column(length = 100)
    private String model;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(name = "part_number", length = 100)
    private String partNumber;

    @Column(length = 200)
    private String location;

    @Column(length = 100)
    private String building;

    @Column(length = 50)
    private String floor;

    @Column(length = 100)
    private String room;

    @Column(name = "rack_position", length = 50)
    private String rackPosition;

    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    @Column(name = "assigned_department", length = 100)
    private String assignedDepartment;

    @Column(name = "assigned_location", length = 200)
    private String assignedLocation;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AssetStatus status = AssetStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", length = 20)
    private HealthStatus healthStatus = HealthStatus.HEALTHY;

    @Column(name = "health_notes", columnDefinition = "TEXT")
    private String healthNotes;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "purchase_cost", precision = 12, scale = 2)
    private BigDecimal purchaseCost;

    @Column(name = "warranty_expiry_date")
    private LocalDate warrantyExpiryDate;

    @Column(name = "lease_expiry_date")
    private LocalDate leaseExpiryDate;

    @Column(name = "depreciation_rate", precision = 5, scale = 2)
    private BigDecimal depreciationRate;

    @Column(name = "current_value", precision = 12, scale = 2)
    private BigDecimal currentValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> specifications;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "mac_address", length = 17)
    private String macAddress;

    @Column(length = 100)
    private String hostname;

    @Column(name = "network_segment", length = 50)
    private String networkSegment;

    @Column(length = 20)
    private String vlan;

    @Column(name = "software_name", length = 200)
    private String softwareName;

    @Column(name = "software_version", length = 50)
    private String softwareVersion;

    @Column(name = "license_key", length = 200)
    private String licenseKey;

    @Column(name = "license_type", length = 50)
    private String licenseType;

    @Column(name = "license_seats_total")
    private Integer licenseSeatsTotal = 0;

    @Column(name = "license_seats_used")
    private Integer licenseSeatsUsed = 0;

    @Column(name = "cloud_provider", length = 50)
    private String cloudProvider;

    @Column(name = "cloud_region", length = 50)
    private String cloudRegion;

    @Column(name = "cloud_resource_id", length = 200)
    private String cloudResourceId;

    @Column(name = "resource_type", length = 100)
    private String resourceType;

    @Column(name = "parent_asset_id")
    private Long parentAssetId;

    @Column(name = "vendor_name", length = 200)
    private String vendorName;

    @Column(name = "vendor_contact", length = 100)
    private String vendorContact;

    @Column(name = "vendor_contract_number", length = 100)
    private String vendorContractNumber;

    @Column(name = "last_maintenance_date")
    private LocalDate lastMaintenanceDate;

    @Column(name = "next_maintenance_date")
    private LocalDate nextMaintenanceDate;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

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

    public enum AssetType {
        SERVER("Server"),
        WORKSTATION("Workstation"),
        LAPTOP("Laptop"),
        NETWORK_DEVICE("Network Device"),
        PRINTER("Printer"),
        MONITOR("Monitor"),
        MOBILE_DEVICE("Mobile Device"),
        SOFTWARE_LICENSE("Software License"),
        DATABASE("Database"),
        STORAGE("Storage"),
        CLOUD_RESOURCE("Cloud Resource"),
        OTHER("Other");

        private final String label;
        AssetType(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum AssetStatus {
        ACTIVE("Đang sử dụng"),
        INACTIVE("Không sử dụng"),
        MAINTENANCE("Đang bảo trì"),
        REPAIR("Đang sửa chữa"),
        RETIRED("Đã ngưng"),
        DISPOSED("Đã thanh lý"),
        LOST("Mất");

        private final String label;
        AssetStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum HealthStatus {
        HEALTHY("Tốt"),
        WARNING("Cảnh báo"),
        CRITICAL("Nguy hiểm"),
        UNKNOWN("Không xác định");

        private final String label;
        HealthStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    // ==================== Constructors ====================

    public Asset() {
    }

    // ==================== Computed Methods ====================

    public boolean isWarrantyExpiringSoon() {
        if (warrantyExpiryDate == null) return false;
        return warrantyExpiryDate.isBefore(LocalDate.now().plusDays(30));
    }

    public boolean isWarrantyExpired() {
        if (warrantyExpiryDate == null) return false;
        return warrantyExpiryDate.isBefore(LocalDate.now());
    }

    public long getDaysUntilWarrantyExpiry() {
        if (warrantyExpiryDate == null) return -1;
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), warrantyExpiryDate);
    }

    // ==================== Getters and Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAssetNumber() { return assetNumber; }
    public void setAssetNumber(String assetNumber) { this.assetNumber = assetNumber; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public AssetType getAssetType() { return assetType; }
    public void setAssetType(AssetType assetType) { this.assetType = assetType; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getPartNumber() { return partNumber; }
    public void setPartNumber(String partNumber) { this.partNumber = partNumber; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }
    public String getFloor() { return floor; }
    public void setFloor(String floor) { this.floor = floor; }
    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }
    public String getRackPosition() { return rackPosition; }
    public void setRackPosition(String rackPosition) { this.rackPosition = rackPosition; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public String getAssignedDepartment() { return assignedDepartment; }
    public void setAssignedDepartment(String assignedDepartment) { this.assignedDepartment = assignedDepartment; }
    public String getAssignedLocation() { return assignedLocation; }
    public void setAssignedLocation(String assignedLocation) { this.assignedLocation = assignedLocation; }
    public AssetStatus getStatus() { return status; }
    public void setStatus(AssetStatus status) { this.status = status; }
    public HealthStatus getHealthStatus() { return healthStatus; }
    public void setHealthStatus(HealthStatus healthStatus) { this.healthStatus = healthStatus; }
    public String getHealthNotes() { return healthNotes; }
    public void setHealthNotes(String healthNotes) { this.healthNotes = healthNotes; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    public BigDecimal getPurchaseCost() { return purchaseCost; }
    public void setPurchaseCost(BigDecimal purchaseCost) { this.purchaseCost = purchaseCost; }
    public LocalDate getWarrantyExpiryDate() { return warrantyExpiryDate; }
    public void setWarrantyExpiryDate(LocalDate warrantyExpiryDate) { this.warrantyExpiryDate = warrantyExpiryDate; }
    public LocalDate getLeaseExpiryDate() { return leaseExpiryDate; }
    public void setLeaseExpiryDate(LocalDate leaseExpiryDate) { this.leaseExpiryDate = leaseExpiryDate; }
    public BigDecimal getDepreciationRate() { return depreciationRate; }
    public void setDepreciationRate(BigDecimal depreciationRate) { this.depreciationRate = depreciationRate; }
    public BigDecimal getCurrentValue() { return currentValue; }
    public void setCurrentValue(BigDecimal currentValue) { this.currentValue = currentValue; }
    public Map<String, Object> getSpecifications() { return specifications; }
    public void setSpecifications(Map<String, Object> specifications) { this.specifications = specifications; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }
    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }
    public String getNetworkSegment() { return networkSegment; }
    public void setNetworkSegment(String networkSegment) { this.networkSegment = networkSegment; }
    public String getVlan() { return vlan; }
    public void setVlan(String vlan) { this.vlan = vlan; }
    public String getSoftwareName() { return softwareName; }
    public void setSoftwareName(String softwareName) { this.softwareName = softwareName; }
    public String getSoftwareVersion() { return softwareVersion; }
    public void setSoftwareVersion(String softwareVersion) { this.softwareVersion = softwareVersion; }
    public String getLicenseKey() { return licenseKey; }
    public void setLicenseKey(String licenseKey) { this.licenseKey = licenseKey; }
    public String getLicenseType() { return licenseType; }
    public void setLicenseType(String licenseType) { this.licenseType = licenseType; }
    public Integer getLicenseSeatsTotal() { return licenseSeatsTotal; }
    public void setLicenseSeatsTotal(Integer licenseSeatsTotal) { this.licenseSeatsTotal = licenseSeatsTotal; }
    public Integer getLicenseSeatsUsed() { return licenseSeatsUsed; }
    public void setLicenseSeatsUsed(Integer licenseSeatsUsed) { this.licenseSeatsUsed = licenseSeatsUsed; }
    public String getCloudProvider() { return cloudProvider; }
    public void setCloudProvider(String cloudProvider) { this.cloudProvider = cloudProvider; }
    public String getCloudRegion() { return cloudRegion; }
    public void setCloudRegion(String cloudRegion) { this.cloudRegion = cloudRegion; }
    public String getCloudResourceId() { return cloudResourceId; }
    public void setCloudResourceId(String cloudResourceId) { this.cloudResourceId = cloudResourceId; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public Long getParentAssetId() { return parentAssetId; }
    public void setParentAssetId(Long parentAssetId) { this.parentAssetId = parentAssetId; }
    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }
    public String getVendorContact() { return vendorContact; }
    public void setVendorContact(String vendorContact) { this.vendorContact = vendorContact; }
    public String getVendorContractNumber() { return vendorContractNumber; }
    public void setVendorContractNumber(String vendorContractNumber) { this.vendorContractNumber = vendorContractNumber; }
    public LocalDate getLastMaintenanceDate() { return lastMaintenanceDate; }
    public void setLastMaintenanceDate(LocalDate lastMaintenanceDate) { this.lastMaintenanceDate = lastMaintenanceDate; }
    public LocalDate getNextMaintenanceDate() { return nextMaintenanceDate; }
    public void setNextMaintenanceDate(LocalDate nextMaintenanceDate) { this.nextMaintenanceDate = nextMaintenanceDate; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
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
        return "Asset{" +
                "id=" + id +
                ", assetNumber='" + assetNumber + '\'' +
                ", name='" + name + '\'' +
                ", assetType=" + assetType +
                '}';
    }
}

package com.example.ticketing.asset;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller cho Asset Management.
 */
@RestController
@RequestMapping("/api/assets")
@CrossOrigin(origins = "*")
public class AssetController {

    private static final Logger log = LoggerFactory.getLogger(AssetController.class);

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    // ==================== Assets ====================

    /**
     * Lấy tất cả assets.
     * GET /api/assets
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<Page<AssetDto>> getAllAssets(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String health,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/assets - type: {}, health: {}, search: {}", type, health, search);

        Pageable pageable = Pageable.ofSize(size).withPage(page);
        Page<Asset> assets;

        if (search != null && !search.isBlank()) {
            assets = assetService.searchAssets(search, pageable);
        } else if (type != null && !type.isBlank()) {
            Asset.AssetType assetType = Asset.AssetType.valueOf(type);
            assets = assetService.getAssetsByType(assetType, pageable);
        } else if (health != null && !health.isBlank()) {
            Asset.HealthStatus healthStatus = Asset.HealthStatus.valueOf(health);
            assets = assetService.getAssetsByHealth(healthStatus, pageable);
        } else {
            assets = assetService.getAllAssets(pageable);
        }

        return ResponseEntity.ok(assets.map(AssetDto::fromEntity));
    }

    /**
     * Lấy asset theo ID.
     * GET /api/assets/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<AssetDto> getAssetById(@PathVariable Long id) {
        log.info("GET /api/assets/{}", id);
        Asset asset = assetService.getAssetById(id);
        return ResponseEntity.ok(AssetDto.fromEntity(asset));
    }

    /**
     * Tạo asset.
     * POST /api/assets
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<AssetDto> createAsset(@RequestBody AssetDto request) {
        log.info("POST /api/assets - Creating: {}", request.getName());

        Asset asset = new Asset();
        asset.setAssetNumber(request.getAssetNumber());
        asset.setName(request.getName());
        asset.setDescription(request.getDescription());
        asset.setAssetType(request.getAssetTypeEnum());
        asset.setCategory(request.getCategory());
        asset.setManufacturer(request.getManufacturer());
        asset.setModel(request.getModel());
        asset.setSerialNumber(request.getSerialNumber());
        asset.setPartNumber(request.getPartNumber());
        asset.setLocation(request.getLocation());
        asset.setBuilding(request.getBuilding());
        asset.setFloor(request.getFloor());
        asset.setRoom(request.getRoom());
        asset.setRackPosition(request.getRackPosition());
        asset.setAssignedTo(request.getAssignedTo());
        asset.setAssignedDepartment(request.getAssignedDepartment());
        asset.setAssignedLocation(request.getAssignedLocation());
        asset.setStatus(request.getStatusEnum());
        asset.setHealthStatus(request.getHealthStatusEnum());
        asset.setHealthNotes(request.getHealthNotes());
        asset.setPurchaseDate(request.getPurchaseDate());
        asset.setPurchaseCost(request.getPurchaseCost());
        asset.setCurrentValue(request.getCurrentValue());
        asset.setDepreciationRate(request.getDepreciationRate());
        asset.setWarrantyExpiryDate(request.getWarrantyExpiryDate());
        asset.setLeaseExpiryDate(request.getLeaseExpiryDate());
        asset.setLastMaintenanceDate(request.getLastMaintenanceDate());
        asset.setNextMaintenanceDate(request.getNextMaintenanceDate());
        asset.setIpAddress(request.getIpAddress());
        asset.setMacAddress(request.getMacAddress());
        asset.setHostname(request.getHostname());
        asset.setNetworkSegment(request.getNetworkSegment());
        asset.setVlan(request.getVlan());
        asset.setSoftwareName(request.getSoftwareName());
        asset.setSoftwareVersion(request.getSoftwareVersion());
        asset.setLicenseKey(request.getLicenseKey());
        asset.setLicenseType(request.getLicenseType());
        asset.setLicenseSeatsTotal(request.getLicenseSeatsTotal() != null ? request.getLicenseSeatsTotal() : 0);
        asset.setLicenseSeatsUsed(request.getLicenseSeatsUsed() != null ? request.getLicenseSeatsUsed() : 0);
        asset.setCloudProvider(request.getCloudProvider());
        asset.setCloudRegion(request.getCloudRegion());
        asset.setCloudResourceId(request.getCloudResourceId());
        asset.setResourceType(request.getResourceType());
        asset.setVendorName(request.getVendorName());
        asset.setVendorContact(request.getVendorContact());
        asset.setVendorContractNumber(request.getVendorContractNumber());
        asset.setTags(request.getTags());
        asset.setNotes(request.getNotes());
        asset.setPhotoUrl(request.getPhotoUrl());
        asset.setSpecifications(request.getSpecifications());
        asset.setParentAssetId(request.getParentAssetId());

        Asset created = assetService.createAsset(asset, "admin");
        return ResponseEntity.status(HttpStatus.CREATED).body(AssetDto.fromEntity(created));
    }

    /**
     * Cập nhật asset.
     * PUT /api/assets/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<AssetDto> updateAsset(
            @PathVariable Long id,
            @RequestBody AssetDto request) {
        log.info("PUT /api/assets/{}", id);

        Asset updates = new Asset();
        updates.setAssetNumber(request.getAssetNumber());
        updates.setName(request.getName());
        updates.setDescription(request.getDescription());
        updates.setAssetType(request.getAssetTypeEnum());
        updates.setCategory(request.getCategory());
        updates.setManufacturer(request.getManufacturer());
        updates.setModel(request.getModel());
        updates.setSerialNumber(request.getSerialNumber());
        updates.setPartNumber(request.getPartNumber());
        updates.setLocation(request.getLocation());
        updates.setBuilding(request.getBuilding());
        updates.setFloor(request.getFloor());
        updates.setRoom(request.getRoom());
        updates.setRackPosition(request.getRackPosition());
        updates.setAssignedTo(request.getAssignedTo());
        updates.setAssignedDepartment(request.getAssignedDepartment());
        updates.setAssignedLocation(request.getAssignedLocation());
        updates.setStatus(request.getStatusEnum());
        updates.setHealthStatus(request.getHealthStatusEnum());
        updates.setHealthNotes(request.getHealthNotes());
        updates.setPurchaseDate(request.getPurchaseDate());
        updates.setPurchaseCost(request.getPurchaseCost());
        updates.setCurrentValue(request.getCurrentValue());
        updates.setDepreciationRate(request.getDepreciationRate());
        updates.setWarrantyExpiryDate(request.getWarrantyExpiryDate());
        updates.setLeaseExpiryDate(request.getLeaseExpiryDate());
        updates.setLastMaintenanceDate(request.getLastMaintenanceDate());
        updates.setNextMaintenanceDate(request.getNextMaintenanceDate());
        updates.setIpAddress(request.getIpAddress());
        updates.setMacAddress(request.getMacAddress());
        updates.setHostname(request.getHostname());
        updates.setNetworkSegment(request.getNetworkSegment());
        updates.setVlan(request.getVlan());
        updates.setSoftwareName(request.getSoftwareName());
        updates.setSoftwareVersion(request.getSoftwareVersion());
        updates.setLicenseKey(request.getLicenseKey());
        updates.setLicenseType(request.getLicenseType());
        updates.setLicenseSeatsTotal(request.getLicenseSeatsTotal() != null ? request.getLicenseSeatsTotal() : 0);
        updates.setLicenseSeatsUsed(request.getLicenseSeatsUsed() != null ? request.getLicenseSeatsUsed() : 0);
        updates.setCloudProvider(request.getCloudProvider());
        updates.setCloudRegion(request.getCloudRegion());
        updates.setCloudResourceId(request.getCloudResourceId());
        updates.setResourceType(request.getResourceType());
        updates.setVendorName(request.getVendorName());
        updates.setVendorContact(request.getVendorContact());
        updates.setVendorContractNumber(request.getVendorContractNumber());
        updates.setTags(request.getTags());
        updates.setNotes(request.getNotes());
        updates.setPhotoUrl(request.getPhotoUrl());
        updates.setSpecifications(request.getSpecifications());
        updates.setParentAssetId(request.getParentAssetId());

        Asset updated = assetService.updateAsset(id, updates, "admin");
        return ResponseEntity.ok(AssetDto.fromEntity(updated));
    }

    /**
     * Xóa asset.
     * DELETE /api/assets/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAsset(@PathVariable Long id) {
        log.info("DELETE /api/assets/{}", id);
        assetService.deleteAsset(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== Health Status ====================

    /**
     * Cập nhật health status.
     * PATCH /api/assets/{id}/health
     */
    @PatchMapping("/{id}/health")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<AssetDto> updateHealthStatus(
            @PathVariable Long id,
            @RequestBody HealthUpdateRequest request) {
        log.info("PATCH /api/assets/{}/health - status: {}", id, request.getHealthStatus());

        Asset updated = assetService.updateHealthStatus(
                id,
                Asset.HealthStatus.valueOf(request.getHealthStatus()),
                request.getHealthNotes(),
                "admin"
        );

        return ResponseEntity.ok(AssetDto.fromEntity(updated));
    }

    // ==================== Assignment ====================

    /**
     * Assign asset.
     * PATCH /api/assets/{id}/assign
     */
    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<AssetDto> assignAsset(
            @PathVariable Long id,
            @RequestBody AssignRequest request) {
        log.info("PATCH /api/assets/{}/assign - to: {}", id, request.getAssignedTo());

        Asset updated = assetService.assignAsset(
                id,
                request.getAssignedTo(),
                request.getAssignedToName(),
                request.getDepartment(),
                "admin"
        );

        return ResponseEntity.ok(AssetDto.fromEntity(updated));
    }

    /**
     * Unassign asset.
     * PATCH /api/assets/{id}/unassign
     */
    @PatchMapping("/{id}/unassign")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<AssetDto> unassignAsset(@PathVariable Long id) {
        log.info("PATCH /api/assets/{}/unassign", id);
        Asset updated = assetService.unassignAsset(id, "admin");
        return ResponseEntity.ok(AssetDto.fromEntity(updated));
    }

    // ==================== Maintenance ====================

    /**
     * Lấy maintenance records.
     * GET /api/assets/{id}/maintenance
     */
    @GetMapping("/{id}/maintenance")
    public ResponseEntity<List<MaintenanceRecordDto>> getMaintenanceRecords(@PathVariable Long id) {
        log.info("GET /api/assets/{}/maintenance", id);
        List<AssetMaintenanceRecord> records = assetService.getMaintenanceRecords(id);
        return ResponseEntity.ok(records.stream()
                .map(MaintenanceRecordDto::fromEntity)
                .collect(Collectors.toList()));
    }

    /**
     * Thêm maintenance record.
     * POST /api/assets/{id}/maintenance
     */
    @PostMapping("/{id}/maintenance")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<MaintenanceRecordDto> addMaintenanceRecord(
            @PathVariable Long id,
            @RequestBody MaintenanceRequest request) {
        log.info("POST /api/assets/{}/maintenance", id);

        AssetMaintenanceRecord record = assetService.addMaintenanceRecord(
                id,
                request.getMaintenanceType(),
                request.getDescription(),
                request.getPerformedBy(),
                request.getOutcome()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MaintenanceRecordDto.fromEntity(record));
    }

    // ==================== Statistics ====================

    /**
     * Lấy statistics.
     * GET /api/assets/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<AssetService.AssetStatistics> getStatistics() {
        log.info("GET /api/assets/stats");
        return ResponseEntity.ok(assetService.getStatistics());
    }

    // ==================== Warranty Alerts ====================

    /**
     * Lấy assets expiring warranty.
     * GET /api/assets/expiring-warranties
     */
    @GetMapping("/expiring-warranties")
    public ResponseEntity<List<AssetDto>> getExpiringWarranties(
            @RequestParam(defaultValue = "30") int days) {
        log.info("GET /api/assets/expiring-warranties - days: {}", days);
        List<Asset> assets = assetService.getExpiringWarranties(days);
        return ResponseEntity.ok(assets.stream()
                .map(AssetDto::fromEntity)
                .collect(Collectors.toList()));
    }

    // ==================== Exception Handlers ====================

    @ExceptionHandler(AssetService.AssetNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleNotFound(AssetService.AssetNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    // ==================== DTOs ====================

    public static class ErrorResponse {
        private String code;
        private String message;
        public ErrorResponse(String code, String message) {
            this.code = code;
            this.message = message;
        }
        public String getCode() { return code; }
        public String getMessage() { return message; }
    }

    public static class AssetDto {
        private Long id;
        private String assetNumber;
        private String name;
        private String description;
        private String assetType;
        private String assetTypeLabel;
        private String category;
        private String manufacturer;
        private String model;
        private String serialNumber;
        private String partNumber;
        private String location;
        private String building;
        private String floor;
        private String room;
        private String rackPosition;
        private String assignedTo;
        private String assignedDepartment;
        private String assignedLocation;
        private String status;
        private String statusLabel;
        private String healthStatus;
        private String healthStatusLabel;
        private String healthNotes;
        private java.time.LocalDate purchaseDate;
        private java.time.LocalDate warrantyExpiryDate;
        private java.time.LocalDate leaseExpiryDate;
        private java.math.BigDecimal purchaseCost;
        private java.math.BigDecimal currentValue;
        private java.math.BigDecimal depreciationRate;
        private Integer licenseSeatsTotal;
        private Integer licenseSeatsUsed;
        private String licenseKey;
        private String licenseType;
        private String ipAddress;
        private String macAddress;
        private String hostname;
        private String networkSegment;
        private String vlan;
        private String softwareName;
        private String softwareVersion;
        private String cloudProvider;
        private String cloudRegion;
        private String cloudResourceId;
        private String resourceType;
        private Long parentAssetId;
        private String vendorName;
        private String vendorContact;
        private String vendorContractNumber;
        private java.time.LocalDate lastMaintenanceDate;
        private java.time.LocalDate nextMaintenanceDate;
        private String tags;
        private String notes;
        private String photoUrl;
        private java.util.Map<String, Object> specifications;
        private Boolean warrantyExpiringSoon;
        private Boolean warrantyExpired;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;
        private String createdBy;
        private String updatedBy;

        public static AssetDto fromEntity(Asset a) {
            AssetDto dto = new AssetDto();
            dto.setId(a.getId());
            dto.setAssetNumber(a.getAssetNumber());
            dto.setName(a.getName());
            dto.setDescription(a.getDescription());
            dto.setAssetType(a.getAssetType() != null ? a.getAssetType().name() : null);
            dto.setAssetTypeLabel(a.getAssetType() != null ? a.getAssetType().getLabel() : null);
            dto.setCategory(a.getCategory());
            dto.setManufacturer(a.getManufacturer());
            dto.setModel(a.getModel());
            dto.setSerialNumber(a.getSerialNumber());
            dto.setPartNumber(a.getPartNumber());
            dto.setLocation(a.getLocation());
            dto.setBuilding(a.getBuilding());
            dto.setFloor(a.getFloor());
            dto.setRoom(a.getRoom());
            dto.setRackPosition(a.getRackPosition());
            dto.setAssignedTo(a.getAssignedTo());
            dto.setAssignedDepartment(a.getAssignedDepartment());
            dto.setAssignedLocation(a.getAssignedLocation());
            dto.setStatus(a.getStatus() != null ? a.getStatus().name() : null);
            dto.setStatusLabel(a.getStatus() != null ? a.getStatus().getLabel() : null);
            dto.setHealthStatus(a.getHealthStatus() != null ? a.getHealthStatus().name() : null);
            dto.setHealthStatusLabel(a.getHealthStatus() != null ? a.getHealthStatus().getLabel() : null);
            dto.setHealthNotes(a.getHealthNotes());
            dto.setPurchaseDate(a.getPurchaseDate());
            dto.setWarrantyExpiryDate(a.getWarrantyExpiryDate());
            dto.setLeaseExpiryDate(a.getLeaseExpiryDate());
            dto.setPurchaseCost(a.getPurchaseCost());
            dto.setCurrentValue(a.getCurrentValue());
            dto.setDepreciationRate(a.getDepreciationRate());
            dto.setLicenseSeatsTotal(a.getLicenseSeatsTotal());
            dto.setLicenseSeatsUsed(a.getLicenseSeatsUsed());
            dto.setLicenseKey(a.getLicenseKey());
            dto.setLicenseType(a.getLicenseType());
            dto.setIpAddress(a.getIpAddress());
            dto.setMacAddress(a.getMacAddress());
            dto.setHostname(a.getHostname());
            dto.setNetworkSegment(a.getNetworkSegment());
            dto.setVlan(a.getVlan());
            dto.setSoftwareName(a.getSoftwareName());
            dto.setSoftwareVersion(a.getSoftwareVersion());
            dto.setCloudProvider(a.getCloudProvider());
            dto.setCloudRegion(a.getCloudRegion());
            dto.setCloudResourceId(a.getCloudResourceId());
            dto.setResourceType(a.getResourceType());
            dto.setParentAssetId(a.getParentAssetId());
            dto.setVendorName(a.getVendorName());
            dto.setVendorContact(a.getVendorContact());
            dto.setVendorContractNumber(a.getVendorContractNumber());
            dto.setLastMaintenanceDate(a.getLastMaintenanceDate());
            dto.setNextMaintenanceDate(a.getNextMaintenanceDate());
            dto.setTags(a.getTags());
            dto.setNotes(a.getNotes());
            dto.setPhotoUrl(a.getPhotoUrl());
            dto.setSpecifications(a.getSpecifications());
            dto.setWarrantyExpiringSoon(a.isWarrantyExpiringSoon());
            dto.setWarrantyExpired(a.isWarrantyExpired());
            dto.setCreatedAt(a.getCreatedAt());
            dto.setUpdatedAt(a.getUpdatedAt());
            dto.setCreatedBy(a.getCreatedBy());
            dto.setUpdatedBy(a.getUpdatedBy());
            return dto;
        }

        // Getters/Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getAssetNumber() { return assetNumber; }
        public void setAssetNumber(String assetNumber) { this.assetNumber = assetNumber; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getAssetType() { return assetType; }
        public void setAssetType(String assetType) { this.assetType = assetType; }
        public String getAssetTypeLabel() { return assetTypeLabel; }
        public void setAssetTypeLabel(String assetTypeLabel) { this.assetTypeLabel = assetTypeLabel; }
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
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getStatusLabel() { return statusLabel; }
        public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }
        public String getHealthStatus() { return healthStatus; }
        public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }
        public String getHealthStatusLabel() { return healthStatusLabel; }
        public void setHealthStatusLabel(String healthStatusLabel) { this.healthStatusLabel = healthStatusLabel; }
        public String getHealthNotes() { return healthNotes; }
        public void setHealthNotes(String healthNotes) { this.healthNotes = healthNotes; }
        public java.time.LocalDate getPurchaseDate() { return purchaseDate; }
        public void setPurchaseDate(java.time.LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
        public java.time.LocalDate getWarrantyExpiryDate() { return warrantyExpiryDate; }
        public void setWarrantyExpiryDate(java.time.LocalDate warrantyExpiryDate) { this.warrantyExpiryDate = warrantyExpiryDate; }
        public java.time.LocalDate getLeaseExpiryDate() { return leaseExpiryDate; }
        public void setLeaseExpiryDate(java.time.LocalDate leaseExpiryDate) { this.leaseExpiryDate = leaseExpiryDate; }
        public java.math.BigDecimal getPurchaseCost() { return purchaseCost; }
        public void setPurchaseCost(java.math.BigDecimal purchaseCost) { this.purchaseCost = purchaseCost; }
        public java.math.BigDecimal getCurrentValue() { return currentValue; }
        public void setCurrentValue(java.math.BigDecimal currentValue) { this.currentValue = currentValue; }
        public java.math.BigDecimal getDepreciationRate() { return depreciationRate; }
        public void setDepreciationRate(java.math.BigDecimal depreciationRate) { this.depreciationRate = depreciationRate; }
        public Integer getLicenseSeatsTotal() { return licenseSeatsTotal; }
        public void setLicenseSeatsTotal(Integer licenseSeatsTotal) { this.licenseSeatsTotal = licenseSeatsTotal; }
        public Integer getLicenseSeatsUsed() { return licenseSeatsUsed; }
        public void setLicenseSeatsUsed(Integer licenseSeatsUsed) { this.licenseSeatsUsed = licenseSeatsUsed; }
        public String getLicenseKey() { return licenseKey; }
        public void setLicenseKey(String licenseKey) { this.licenseKey = licenseKey; }
        public String getLicenseType() { return licenseType; }
        public void setLicenseType(String licenseType) { this.licenseType = licenseType; }
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
        public java.time.LocalDate getLastMaintenanceDate() { return lastMaintenanceDate; }
        public void setLastMaintenanceDate(java.time.LocalDate lastMaintenanceDate) { this.lastMaintenanceDate = lastMaintenanceDate; }
        public java.time.LocalDate getNextMaintenanceDate() { return nextMaintenanceDate; }
        public void setNextMaintenanceDate(java.time.LocalDate nextMaintenanceDate) { this.nextMaintenanceDate = nextMaintenanceDate; }
        public String getTags() { return tags; }
        public void setTags(String tags) { this.tags = tags; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        public String getPhotoUrl() { return photoUrl; }
        public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
        public java.util.Map<String, Object> getSpecifications() { return specifications; }
        public void setSpecifications(java.util.Map<String, Object> specifications) { this.specifications = specifications; }
        public Boolean getWarrantyExpiringSoon() { return warrantyExpiringSoon; }
        public void setWarrantyExpiringSoon(Boolean warrantyExpiringSoon) { this.warrantyExpiringSoon = warrantyExpiringSoon; }
        public Boolean getWarrantyExpired() { return warrantyExpired; }
        public void setWarrantyExpired(Boolean warrantyExpired) { this.warrantyExpired = warrantyExpired; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
        public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public String getUpdatedBy() { return updatedBy; }
        public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

        // Enum converters for DTO -> Entity mapping
        public Asset.AssetType getAssetTypeEnum() {
            return assetType != null ? Asset.AssetType.valueOf(assetType) : null;
        }
        
        public Asset.AssetStatus getStatusEnum() {
            return status != null ? Asset.AssetStatus.valueOf(status) : null;
        }
        
        public Asset.HealthStatus getHealthStatusEnum() {
            return healthStatus != null ? Asset.HealthStatus.valueOf(healthStatus) : null;
        }
    }

    public static class HealthUpdateRequest {
        private String healthStatus;
        private String healthNotes;
        public String getHealthStatus() { return healthStatus; }
        public String getHealthNotes() { return healthNotes; }
    }

    public static class AssignRequest {
        private String assignedTo;
        private String assignedToName;
        private String department;
        public String getAssignedTo() { return assignedTo; }
        public String getAssignedToName() { return assignedToName; }
        public String getDepartment() { return department; }
    }

    public static class MaintenanceRequest {
        private String maintenanceType;
        private String description;
        private String performedBy;
        private String outcome;
        public String getMaintenanceType() { return maintenanceType; }
        public String getDescription() { return description; }
        public String getPerformedBy() { return performedBy; }
        public String getOutcome() { return outcome; }
    }

    public static class MaintenanceRecordDto {
        private Long id;
        private Long assetId;
        private String maintenanceType;
        private String description;
        private String performedBy;
        private java.time.LocalDate performedAt;
        private String outcome;
        private java.time.LocalDateTime createdAt;

        public static MaintenanceRecordDto fromEntity(AssetMaintenanceRecord r) {
            MaintenanceRecordDto dto = new MaintenanceRecordDto();
            dto.setId(r.getId());
            dto.setAssetId(r.getAsset() != null ? r.getAsset().getId() : null);
            dto.setMaintenanceType(r.getMaintenanceType());
            dto.setDescription(r.getDescription());
            dto.setPerformedBy(r.getPerformedBy());
            dto.setPerformedAt(r.getPerformedAt());
            dto.setOutcome(r.getOutcome());
            dto.setCreatedAt(r.getCreatedAt());
            return dto;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getAssetId() { return assetId; }
        public void setAssetId(Long assetId) { this.assetId = assetId; }
        public String getMaintenanceType() { return maintenanceType; }
        public void setMaintenanceType(String maintenanceType) { this.maintenanceType = maintenanceType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getPerformedBy() { return performedBy; }
        public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }
        public java.time.LocalDate getPerformedAt() { return performedAt; }
        public void setPerformedAt(java.time.LocalDate performedAt) { this.performedAt = performedAt; }
        public String getOutcome() { return outcome; }
        public void setOutcome(String outcome) { this.outcome = outcome; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
}

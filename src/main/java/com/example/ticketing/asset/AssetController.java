package com.example.ticketing.asset;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    // ==================== Assets CRUD ====================

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<Page<AssetResponse>> getAllAssets(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String health,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
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

        return ResponseEntity.ok(assets.map(AssetResponse::fromEntity));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssetResponse> getAssetById(@PathVariable Long id) {
        Asset asset = assetService.getAssetById(id);
        return ResponseEntity.ok(AssetResponse.fromEntity(asset));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<AssetResponse> createAsset(@RequestBody AssetRequest request) {
        Asset asset = new Asset();
        asset.setAssetNumber(request.assetNumber);
        asset.setName(request.name);
        asset.setDescription(request.description);
        asset.setAssetType(request.getAssetType());
        asset.setCategory(request.category);
        asset.setManufacturer(request.manufacturer);
        asset.setModel(request.model);
        asset.setSerialNumber(request.serialNumber);
        asset.setPartNumber(request.partNumber);
        asset.setLocation(request.location);
        asset.setBuilding(request.building);
        asset.setFloor(request.floor);
        asset.setRoom(request.room);
        asset.setRackPosition(request.rackPosition);
        asset.setAssignedTo(request.assignedTo);
        asset.setAssignedDepartment(request.assignedDepartment);
        asset.setAssignedLocation(request.assignedLocation);
        asset.setStatus(request.getStatus());
        asset.setHealthStatus(request.getHealthStatus());
        asset.setHealthNotes(request.healthNotes);
        asset.setPurchaseDate(request.purchaseDate);
        asset.setPurchaseCost(request.purchaseCost);
        asset.setCurrentValue(request.currentValue);
        asset.setDepreciationRate(request.depreciationRate);
        asset.setWarrantyExpiryDate(request.warrantyExpiryDate);
        asset.setLeaseExpiryDate(request.leaseExpiryDate);
        asset.setLastMaintenanceDate(request.lastMaintenanceDate);
        asset.setNextMaintenanceDate(request.nextMaintenanceDate);
        asset.setIpAddress(request.ipAddress);
        asset.setMacAddress(request.macAddress);
        asset.setHostname(request.hostname);
        asset.setNetworkSegment(request.networkSegment);
        asset.setVlan(request.vlan);
        asset.setSoftwareName(request.softwareName);
        asset.setSoftwareVersion(request.softwareVersion);
        asset.setLicenseKey(request.licenseKey);
        asset.setLicenseType(request.licenseType);
        asset.setLicenseSeatsTotal(request.licenseSeatsTotal != null ? request.licenseSeatsTotal : 0);
        asset.setLicenseSeatsUsed(request.licenseSeatsUsed != null ? request.licenseSeatsUsed : 0);
        asset.setCloudProvider(request.cloudProvider);
        asset.setCloudRegion(request.cloudRegion);
        asset.setCloudResourceId(request.cloudResourceId);
        asset.setResourceType(request.resourceType);
        asset.setVendorName(request.vendorName);
        asset.setVendorContact(request.vendorContact);
        asset.setVendorContractNumber(request.vendorContractNumber);
        asset.setTags(request.tags);
        asset.setNotes(request.notes);
        asset.setPhotoUrl(request.photoUrl);
        asset.setSpecifications(request.specifications);
        asset.setParentAssetId(request.parentAssetId);

        Asset created = assetService.createAsset(asset, "admin");
        return ResponseEntity.status(HttpStatus.CREATED).body(AssetResponse.fromEntity(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<AssetResponse> updateAsset(@PathVariable Long id, @RequestBody AssetRequest request) {
        log.info("=== UPDATE ASSET {} ===", id);
        log.info("Request - assetNumber: {}, name: {}", request.assetNumber, request.name);
        log.info("Request - purchaseCost: {}, currentValue: {}, depreciationRate: {}", 
                 request.purchaseCost, request.currentValue, request.depreciationRate);
        log.info("Request - purchaseDate: {}, warrantyExpiry: {}, leaseExpiry: {}", 
                 request.purchaseDate, request.warrantyExpiryDate, request.leaseExpiryDate);
        
        Asset updates = new Asset();
        updates.setAssetNumber(request.assetNumber);
        updates.setName(request.name);
        updates.setDescription(request.description);
        updates.setAssetType(request.getAssetType());
        updates.setCategory(request.category);
        updates.setManufacturer(request.manufacturer);
        updates.setModel(request.model);
        updates.setSerialNumber(request.serialNumber);
        updates.setPartNumber(request.partNumber);
        updates.setLocation(request.location);
        updates.setBuilding(request.building);
        updates.setFloor(request.floor);
        updates.setRoom(request.room);
        updates.setRackPosition(request.rackPosition);
        updates.setAssignedTo(request.assignedTo);
        updates.setAssignedDepartment(request.assignedDepartment);
        updates.setAssignedLocation(request.assignedLocation);
        updates.setStatus(request.getStatus());
        updates.setHealthStatus(request.getHealthStatus());
        updates.setHealthNotes(request.healthNotes);
        updates.setPurchaseDate(request.purchaseDate);
        updates.setPurchaseCost(request.purchaseCost);
        updates.setCurrentValue(request.currentValue);
        updates.setDepreciationRate(request.depreciationRate);
        updates.setWarrantyExpiryDate(request.warrantyExpiryDate);
        updates.setLeaseExpiryDate(request.leaseExpiryDate);
        updates.setLastMaintenanceDate(request.lastMaintenanceDate);
        updates.setNextMaintenanceDate(request.nextMaintenanceDate);
        updates.setIpAddress(request.ipAddress);
        updates.setMacAddress(request.macAddress);
        updates.setHostname(request.hostname);
        updates.setNetworkSegment(request.networkSegment);
        updates.setVlan(request.vlan);
        updates.setLicenseKey(request.licenseKey);
        updates.setLicenseType(request.licenseType);
        updates.setLicenseSeatsTotal(request.licenseSeatsTotal);
        updates.setLicenseSeatsUsed(request.licenseSeatsUsed);
        updates.setCloudProvider(request.cloudProvider);
        updates.setCloudRegion(request.cloudRegion);
        updates.setCloudResourceId(request.cloudResourceId);
        updates.setResourceType(request.resourceType);
        updates.setVendorName(request.vendorName);
        updates.setVendorContact(request.vendorContact);
        updates.setVendorContractNumber(request.vendorContractNumber);
        updates.setPhotoUrl(request.photoUrl);
        updates.setTags(request.tags);
        updates.setNotes(request.notes);
        updates.setSpecifications(request.specifications);
        updates.setParentAssetId(request.parentAssetId);

        Asset updated = assetService.updateAsset(id, updates, "admin");
        log.info("=== UPDATED ASSET {} ===", id);
        log.info("Result - name: {}, purchaseCost: {}, currentValue: {}", 
                 updated.getName(), updated.getPurchaseCost(), updated.getCurrentValue());
        return ResponseEntity.ok(AssetResponse.fromEntity(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAsset(@PathVariable Long id) {
        assetService.deleteAsset(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== Assignment ====================

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<AssetResponse> assignAsset(@PathVariable Long id, @RequestBody AssignRequest request) {
        Asset updated = assetService.assignAsset(id, request.assignedTo, request.assignedToName, request.department, "admin");
        return ResponseEntity.ok(AssetResponse.fromEntity(updated));
    }

    @PatchMapping("/{id}/unassign")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<AssetResponse> unassignAsset(@PathVariable Long id) {
        Asset updated = assetService.unassignAsset(id, "admin");
        return ResponseEntity.ok(AssetResponse.fromEntity(updated));
    }

    // ==================== Health Status ====================

    @PatchMapping("/{id}/health")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<AssetResponse> updateHealthStatus(@PathVariable Long id, @RequestBody HealthUpdateRequest request) {
        Asset.HealthStatus healthStatus = Asset.HealthStatus.valueOf(request.healthStatus);
        Asset updated = assetService.updateHealthStatus(id, healthStatus, request.notes, "admin");
        return ResponseEntity.ok(AssetResponse.fromEntity(updated));
    }

    // ==================== Maintenance ====================

    @GetMapping("/{id}/maintenance")
    public ResponseEntity<List<MaintenanceResponse>> getMaintenanceRecords(@PathVariable Long id) {
        List<AssetMaintenanceRecord> records = assetService.getMaintenanceRecords(id);
        return ResponseEntity.ok(records.stream().map(MaintenanceResponse::fromEntity).collect(Collectors.toList()));
    }

    @PostMapping("/{id}/maintenance")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<MaintenanceResponse> addMaintenanceRecord(@PathVariable Long id, @RequestBody MaintenanceRequest request) {
        AssetMaintenanceRecord record = assetService.addMaintenanceRecord(id, request.maintenanceType, request.description, request.performedBy, request.outcome);
        return ResponseEntity.status(HttpStatus.CREATED).body(MaintenanceResponse.fromEntity(record));
    }

    @PostMapping("/{id}/schedule-maintenance")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<AssetResponse> scheduleMaintenance(@PathVariable Long id, @RequestBody ScheduleMaintenanceRequest request) {
        Asset updated = assetService.scheduleMaintenance(id, request.nextMaintenanceDate, "admin");
        return ResponseEntity.ok(AssetResponse.fromEntity(updated));
    }

    // ==================== Statistics & Alerts ====================

    @GetMapping("/stats")
    public ResponseEntity<AssetStatisticsResponse> getStatistics() {
        AssetService.AssetStatistics stats = assetService.getStatistics();
        return ResponseEntity.ok(AssetStatisticsResponse.from(stats));
    }

    @GetMapping("/expiring-warranties")
    public ResponseEntity<List<AssetResponse>> getExpiringWarranties(@RequestParam(defaultValue = "30") int days) {
        List<Asset> assets = assetService.getExpiringWarranties(days);
        return ResponseEntity.ok(assets.stream().map(AssetResponse::fromEntity).collect(Collectors.toList()));
    }

    @GetMapping("/maintenance-schedule")
    public ResponseEntity<List<AssetResponse>> getMaintenanceSchedule(@RequestParam(defaultValue = "30") int days) {
        List<Asset> assets = assetService.getUpcomingMaintenance(days);
        return ResponseEntity.ok(assets.stream().map(AssetResponse::fromEntity).collect(Collectors.toList()));
    }

    // ==================== Linking ====================

    @PostMapping("/{id}/link-incident")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<LinkResponse> linkIncident(@PathVariable Long id, @RequestBody LinkRequest request) {
        AssetIncidentLink link = assetService.linkIncident(id, request.ticketId, request.linkedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(LinkResponse.fromEntity(link));
    }

    @GetMapping("/{id}/incidents")
    public ResponseEntity<List<LinkResponse>> getLinkedIncidents(@PathVariable Long id) {
        List<AssetIncidentLink> links = assetService.getLinkedIncidents(id);
        return ResponseEntity.ok(links.stream().map(LinkResponse::fromEntity).collect(Collectors.toList()));
    }

    @GetMapping("/{id}/children")
    public ResponseEntity<List<AssetResponse>> getChildAssets(@PathVariable Long id) {
        List<Asset> children = assetService.getChildAssets(id);
        return ResponseEntity.ok(children.stream().map(AssetResponse::fromEntity).collect(Collectors.toList()));
    }

    // ==================== Exception Handlers ====================

    @ExceptionHandler(AssetService.AssetNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleNotFound(AssetService.AssetNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    // ==================== Request/Response Classes ====================

    public static class ErrorResponse {
        private String code;
        private String message;
        public ErrorResponse(String code, String message) { this.code = code; this.message = message; }
        public String getCode() { return code; }
        public String getMessage() { return message; }
    }

    public static class AssetRequest {
        public String assetNumber;
        public String name;
        public String description;
        public String assetType;
        public String category;
        public String manufacturer;
        public String model;
        public String serialNumber;
        public String partNumber;
        public String location;
        public String building;
        public String floor;
        public String room;
        public String rackPosition;
        public String assignedTo;
        public String assignedToName;
        public String assignedDepartment;
        public String assignedLocation;
        public String status;
        public String healthStatus;
        public String healthNotes;
        public LocalDate purchaseDate;
        public java.math.BigDecimal purchaseCost;
        public java.math.BigDecimal currentValue;
        public java.math.BigDecimal depreciationRate;
        public LocalDate warrantyExpiryDate;
        public LocalDate leaseExpiryDate;
        public LocalDate lastMaintenanceDate;
        public LocalDate nextMaintenanceDate;
        public String ipAddress;
        public String macAddress;
        public String hostname;
        public String networkSegment;
        public String vlan;
        public String softwareName;
        public String softwareVersion;
        public String licenseKey;
        public String licenseType;
        public Integer licenseSeatsTotal;
        public Integer licenseSeatsUsed;
        public String cloudProvider;
        public String cloudRegion;
        public String cloudResourceId;
        public String resourceType;
        public String vendorName;
        public String vendorContact;
        public String vendorContractNumber;
        public String tags;
        public String notes;
        public String photoUrl;
        public Map<String, Object> specifications;
        public Long parentAssetId;

        public Asset.AssetType getAssetType() { return assetType != null ? Asset.AssetType.valueOf(assetType) : null; }
        public Asset.AssetStatus getStatus() { return status != null ? Asset.AssetStatus.valueOf(status) : null; }
        public Asset.HealthStatus getHealthStatus() { return healthStatus != null ? Asset.HealthStatus.valueOf(healthStatus) : null; }
    }

    public static class AssignRequest {
        public String assignedTo;
        public String assignedToName;
        public String department;
    }

    public static class HealthUpdateRequest {
        public String healthStatus;
        public String notes;
    }

    public static class MaintenanceRequest {
        public String maintenanceType;
        public String description;
        public String performedBy;
        public String outcome;
    }

    public static class ScheduleMaintenanceRequest {
        public LocalDate nextMaintenanceDate;
    }

    public static class LinkRequest {
        public String ticketId;
        public String linkedBy;
    }

    public static class AssetResponse {
        public Long id;
        public String assetNumber;
        public String name;
        public String description;
        public String assetType;
        public String assetTypeLabel;
        public String category;
        public String manufacturer;
        public String model;
        public String serialNumber;
        public String partNumber;
        public String location;
        public String building;
        public String floor;
        public String room;
        public String rackPosition;
        public String assignedTo;
        public String assignedToName;
        public String assignedDepartment;
        public String assignedLocation;
        public String status;
        public String statusLabel;
        public String healthStatus;
        public String healthStatusLabel;
        public String healthNotes;
        public LocalDate purchaseDate;
        public java.math.BigDecimal purchaseCost;
        public java.math.BigDecimal currentValue;
        public java.math.BigDecimal depreciationRate;
        public LocalDate warrantyExpiryDate;
        public LocalDate leaseExpiryDate;
        public LocalDate lastMaintenanceDate;
        public LocalDate nextMaintenanceDate;
        public String ipAddress;
        public String macAddress;
        public String hostname;
        public String networkSegment;
        public String vlan;
        public String softwareName;
        public String softwareVersion;
        public String licenseKey;
        public String licenseType;
        public Integer licenseSeatsTotal;
        public Integer licenseSeatsUsed;
        public String cloudProvider;
        public String cloudRegion;
        public String cloudResourceId;
        public String resourceType;
        public String vendorName;
        public String vendorContact;
        public String vendorContractNumber;
        public String tags;
        public String notes;
        public String photoUrl;
        public Map<String, Object> specifications;
        public Long parentAssetId;
        public LocalDateTime createdAt;
        public LocalDateTime updatedAt;
        public String createdBy;
        public String updatedBy;
        public boolean warrantyExpired;
        public boolean warrantyExpiringSoon;

        public static AssetResponse fromEntity(Asset a) {
            AssetResponse r = new AssetResponse();
            r.id = a.getId();
            r.assetNumber = a.getAssetNumber();
            r.name = a.getName();
            r.description = a.getDescription();
            r.assetType = a.getAssetType() != null ? a.getAssetType().name() : null;
            r.assetTypeLabel = a.getAssetType() != null ? a.getAssetType().getLabel() : null;
            r.category = a.getCategory();
            r.manufacturer = a.getManufacturer();
            r.model = a.getModel();
            r.serialNumber = a.getSerialNumber();
            r.partNumber = a.getPartNumber();
            r.location = a.getLocation();
            r.building = a.getBuilding();
            r.floor = a.getFloor();
            r.room = a.getRoom();
            r.rackPosition = a.getRackPosition();
            r.assignedTo = a.getAssignedTo();
            r.assignedDepartment = a.getAssignedDepartment();
            r.assignedLocation = a.getAssignedLocation();
            r.status = a.getStatus() != null ? a.getStatus().name() : null;
            r.statusLabel = a.getStatus() != null ? a.getStatus().getLabel() : null;
            r.healthStatus = a.getHealthStatus() != null ? a.getHealthStatus().name() : null;
            r.healthStatusLabel = a.getHealthStatus() != null ? a.getHealthStatus().getLabel() : null;
            r.healthNotes = a.getHealthNotes();
            r.purchaseDate = a.getPurchaseDate();
            r.purchaseCost = a.getPurchaseCost();
            r.currentValue = a.getCurrentValue();
            r.depreciationRate = a.getDepreciationRate();
            r.warrantyExpiryDate = a.getWarrantyExpiryDate();
            r.leaseExpiryDate = a.getLeaseExpiryDate();
            r.lastMaintenanceDate = a.getLastMaintenanceDate();
            r.nextMaintenanceDate = a.getNextMaintenanceDate();
            r.ipAddress = a.getIpAddress();
            r.macAddress = a.getMacAddress();
            r.hostname = a.getHostname();
            r.networkSegment = a.getNetworkSegment();
            r.vlan = a.getVlan();
            r.softwareName = a.getSoftwareName();
            r.softwareVersion = a.getSoftwareVersion();
            r.licenseKey = a.getLicenseKey();
            r.licenseType = a.getLicenseType();
            r.licenseSeatsTotal = a.getLicenseSeatsTotal();
            r.licenseSeatsUsed = a.getLicenseSeatsUsed();
            r.cloudProvider = a.getCloudProvider();
            r.cloudRegion = a.getCloudRegion();
            r.cloudResourceId = a.getCloudResourceId();
            r.resourceType = a.getResourceType();
            r.vendorName = a.getVendorName();
            r.vendorContact = a.getVendorContact();
            r.vendorContractNumber = a.getVendorContractNumber();
            r.tags = a.getTags();
            r.notes = a.getNotes();
            r.photoUrl = a.getPhotoUrl();
            r.specifications = a.getSpecifications();
            r.parentAssetId = a.getParentAssetId();
            r.createdAt = a.getCreatedAt();
            r.updatedAt = a.getUpdatedAt();
            r.createdBy = a.getCreatedBy();
            r.updatedBy = a.getUpdatedBy();
            r.warrantyExpired = a.isWarrantyExpired();
            r.warrantyExpiringSoon = a.isWarrantyExpiringSoon();
            return r;
        }

        public Long getId() { return id; }
        public String getAssetNumber() { return assetNumber; }
        public String getName() { return name; }
        public String getStatus() { return status; }
        public String getHealthStatus() { return healthStatus; }
    }

    public static class LinkResponse {
        public Long id;
        public Long assetId;
        public Long ticketId;
        public String createdBy;
        public LocalDateTime createdAt;

        public static LinkResponse fromEntity(AssetIncidentLink link) {
            LinkResponse lr = new LinkResponse();
            lr.id = link.getId();
            lr.assetId = link.getAsset() != null ? link.getAsset().getId() : null;
            lr.ticketId = link.getIncidentId();
            lr.createdBy = link.getCreatedBy();
            lr.createdAt = link.getCreatedAt();
            return lr;
        }
    }

    public static class MaintenanceResponse {
        public Long id;
        public Long assetId;
        public String maintenanceType;
        public String description;
        public String performedBy;
        public String outcome;
        public LocalDate performedAt;
        public LocalDateTime createdAt;

        public static MaintenanceResponse fromEntity(AssetMaintenanceRecord r) {
            MaintenanceResponse mr = new MaintenanceResponse();
            mr.id = r.getId();
            mr.assetId = r.getAsset() != null ? r.getAsset().getId() : null;
            mr.maintenanceType = r.getMaintenanceType();
            mr.description = r.getDescription();
            mr.performedBy = r.getPerformedBy();
            mr.outcome = r.getOutcome();
            mr.performedAt = r.getPerformedAt();
            mr.createdAt = r.getCreatedAt();
            return mr;
        }
    }

    public static class AssetStatisticsResponse {
        public long totalAssets;
        public long activeAssets;
        public long maintenanceAssets;
        public long retiredAssets;
        public long healthyAssets;
        public long warningAssets;
        public long criticalAssets;
        public long expiringWarranties;
        public long expiredWarranties;
        public long overdueMaintenance;

        public static AssetStatisticsResponse from(AssetService.AssetStatistics stats) {
            AssetStatisticsResponse r = new AssetStatisticsResponse();
            r.totalAssets = stats.totalAssets;
            r.activeAssets = stats.activeAssets;
            r.maintenanceAssets = stats.maintenanceAssets;
            r.retiredAssets = stats.retiredAssets;
            r.healthyAssets = stats.healthyAssets;
            r.warningAssets = stats.warningAssets;
            r.criticalAssets = stats.criticalAssets;
            r.expiringWarranties = stats.expiringWarranties;
            r.expiredWarranties = stats.expiredWarranties;
            r.overdueMaintenance = stats.overdueMaintenance;
            return r;
        }
    }
}

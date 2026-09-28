package com.example.ticketing.asset;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.asset.Asset.AssetStatus;
import com.example.ticketing.asset.Asset.AssetType;
import com.example.ticketing.asset.Asset.HealthStatus;

/**
 * Service cho Asset Management.
 */
@Service
@Transactional
public class AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetService.class);

    private final AssetRepository assetRepository;
    private final AssetMaintenanceRecordRepository maintenanceRepository;
    private final AssetAssignmentRepository assignmentRepository;

    public AssetService(
            AssetRepository assetRepository,
            AssetMaintenanceRecordRepository maintenanceRepository,
            AssetAssignmentRepository assignmentRepository) {
        this.assetRepository = assetRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.assignmentRepository = assignmentRepository;
    }

    // ==================== Asset CRUD ====================

    /**
     * Tạo asset mới.
     */
    public Asset createAsset(Asset asset, String createdBy) {
        log.info("Creating asset: {}", asset.getName());
        
        // Use provided asset number or generate new one
        if (asset.getAssetNumber() == null || asset.getAssetNumber().isBlank()) {
            asset.setAssetNumber(generateAssetNumber());
        }
        asset.setCreatedBy(createdBy);
        asset.setStatus(AssetStatus.ACTIVE);
        asset.setHealthStatus(HealthStatus.HEALTHY);
        
        return assetRepository.save(asset);
    }

    /**
     * Lấy asset theo ID.
     */
    @Transactional(readOnly = true)
    public Asset getAssetById(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new AssetNotFoundException(id));
    }

    /**
     * Lấy asset theo số.
     */
    @Transactional(readOnly = true)
    public Asset getAssetByNumber(String number) {
        return assetRepository.findByAssetNumber(number)
                .orElseThrow(() -> new AssetNotFoundException(number));
    }

    /**
     * Cập nhật asset.
     */
    public Asset updateAsset(Long id, Asset updates, String updatedBy) {
        Asset existing = getAssetById(id);
        
        existing.setAssetNumber(updates.getAssetNumber());
        existing.setName(updates.getName());
        existing.setDescription(updates.getDescription());
        existing.setAssetType(updates.getAssetType());
        existing.setCategory(updates.getCategory());
        existing.setManufacturer(updates.getManufacturer());
        existing.setModel(updates.getModel());
        existing.setSerialNumber(updates.getSerialNumber());
        existing.setPartNumber(updates.getPartNumber());
        existing.setLocation(updates.getLocation());
        existing.setBuilding(updates.getBuilding());
        existing.setFloor(updates.getFloor());
        existing.setRoom(updates.getRoom());
        existing.setRackPosition(updates.getRackPosition());
        existing.setAssignedTo(updates.getAssignedTo());
        existing.setAssignedDepartment(updates.getAssignedDepartment());
        existing.setAssignedLocation(updates.getAssignedLocation());
        existing.setStatus(updates.getStatus());
        existing.setHealthStatus(updates.getHealthStatus());
        existing.setHealthNotes(updates.getHealthNotes());
        existing.setPurchaseDate(updates.getPurchaseDate());
        existing.setPurchaseCost(updates.getPurchaseCost());
        existing.setWarrantyExpiryDate(updates.getWarrantyExpiryDate());
        existing.setLeaseExpiryDate(updates.getLeaseExpiryDate());
        existing.setDepreciationRate(updates.getDepreciationRate());
        existing.setCurrentValue(updates.getCurrentValue());
        existing.setIpAddress(updates.getIpAddress());
        existing.setMacAddress(updates.getMacAddress());
        existing.setHostname(updates.getHostname());
        existing.setNetworkSegment(updates.getNetworkSegment());
        existing.setVlan(updates.getVlan());
        existing.setSoftwareName(updates.getSoftwareName());
        existing.setSoftwareVersion(updates.getSoftwareVersion());
        existing.setLicenseKey(updates.getLicenseKey());
        existing.setLicenseType(updates.getLicenseType());
        existing.setLicenseSeatsTotal(updates.getLicenseSeatsTotal());
        existing.setLicenseSeatsUsed(updates.getLicenseSeatsUsed());
        existing.setCloudProvider(updates.getCloudProvider());
        existing.setCloudRegion(updates.getCloudRegion());
        existing.setCloudResourceId(updates.getCloudResourceId());
        existing.setResourceType(updates.getResourceType());
        existing.setVendorName(updates.getVendorName());
        existing.setVendorContact(updates.getVendorContact());
        existing.setVendorContractNumber(updates.getVendorContractNumber());
        existing.setLastMaintenanceDate(updates.getLastMaintenanceDate());
        existing.setNextMaintenanceDate(updates.getNextMaintenanceDate());
        existing.setTags(updates.getTags());
        existing.setNotes(updates.getNotes());
        existing.setPhotoUrl(updates.getPhotoUrl());
        existing.setSpecifications(updates.getSpecifications());
        existing.setParentAssetId(updates.getParentAssetId());
        existing.setUpdatedBy(updatedBy);
        
        return assetRepository.save(existing);
    }

    /**
     * Xóa asset.
     */
    public void deleteAsset(Long id) {
        log.info("Deleting asset: {}", id);
        assetRepository.deleteById(id);
    }

    // ==================== Status Management ====================

    /**
     * Cập nhật health status.
     */
    public Asset updateHealthStatus(Long id, HealthStatus healthStatus, String healthNotes, String updatedBy) {
        Asset asset = getAssetById(id);
        asset.setHealthStatus(healthStatus);
        asset.setHealthNotes(healthNotes);
        asset.setUpdatedBy(updatedBy);
        return assetRepository.save(asset);
    }

    /**
     * Assign asset cho user.
     */
    public Asset assignAsset(Long id, String assignedTo, String assignedToName, 
                            String department, String updatedBy) {
        Asset asset = getAssetById(id);
        
        // Create assignment record
        AssetAssignment assignment = new AssetAssignment(asset, assignedTo, assignedToName);
        assignment.setAssignedDepartment(department);
        assignmentRepository.save(assignment);
        
        // Update asset
        asset.setAssignedTo(assignedTo);
        asset.setAssignedDepartment(department);
        asset.setUpdatedBy(updatedBy);
        
        return assetRepository.save(asset);
    }

    /**
     * Unassign asset.
     */
    public Asset unassignAsset(Long id, String updatedBy) {
        Asset asset = getAssetById(id);
        asset.setAssignedTo(null);
        asset.setAssignedDepartment(null);
        asset.setUpdatedBy(updatedBy);
        
        // Mark assignment as returned
        List<AssetAssignment> assignments = assignmentRepository.findByAssetIdOrderByAssignedAtDesc(id);
        assignments.stream()
                .filter(a -> "ACTIVE".equals(a.getStatus()))
                .findFirst()
                .ifPresent(a -> {
                    a.setStatus("RETURNED");
                    a.setReturnedAt(LocalDateTime.now());
                    assignmentRepository.save(a);
                });
        
        return assetRepository.save(asset);
    }

    // ==================== Maintenance ====================

    /**
     * Thêm maintenance record.
     */
    public AssetMaintenanceRecord addMaintenanceRecord(Long assetId, String type, String description,
                                                     String performedBy, String outcome) {
        Asset asset = getAssetById(assetId);
        
        AssetMaintenanceRecord record = new AssetMaintenanceRecord(asset, type, description);
        record.setPerformedBy(performedBy);
        record.setPerformedAt(LocalDate.now());
        record.setOutcome(outcome);
        
        // Update last maintenance date
        asset.setLastMaintenanceDate(LocalDate.now());
        assetRepository.save(asset);
        
        return maintenanceRepository.save(record);
    }

    /**
     * Lấy maintenance records.
     */
    @Transactional(readOnly = true)
    public List<AssetMaintenanceRecord> getMaintenanceRecords(Long assetId) {
        return maintenanceRepository.findByAssetIdOrderByCreatedAtDesc(assetId);
    }

    // ==================== Query Methods ====================

    /**
     * Lấy tất cả assets.
     */
    @Transactional(readOnly = true)
    public Page<Asset> getAllAssets(Pageable pageable) {
        return assetRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * Tìm kiếm assets.
     */
    @Transactional(readOnly = true)
    public Page<Asset> searchAssets(String search, Pageable pageable) {
        return assetRepository.searchAssets(search, pageable);
    }

    /**
     * Lấy assets theo type.
     */
    @Transactional(readOnly = true)
    public Page<Asset> getAssetsByType(AssetType type, Pageable pageable) {
        return assetRepository.findByAssetTypeOrderByNameAsc(type, pageable);
    }

    /**
     * Lấy assets theo health status.
     */
    @Transactional(readOnly = true)
    public Page<Asset> getAssetsByHealth(HealthStatus health, Pageable pageable) {
        return assetRepository.findByHealthStatusOrderByNameAsc(health, pageable);
    }

    /**
     * Lấy assets hết warranty.
     */
    @Transactional(readOnly = true)
    public List<Asset> getExpiringWarranties(int days) {
        LocalDate today = LocalDate.now();
        LocalDate thirtyDays = today.plusDays(days);
        return assetRepository.findExpiringWarranties(today, thirtyDays);
    }

    /**
     * Lấy assets expired warranty.
     */
    @Transactional(readOnly = true)
    public List<Asset> getExpiredWarranties() {
        return assetRepository.findExpiredWarranties(LocalDate.now());
    }

    /**
     * Lấy assets cần bảo trì.
     */
    @Transactional(readOnly = true)
    public List<Asset> getOverdueMaintenance() {
        return assetRepository.findOverdueMaintenance(LocalDate.now());
    }

    /**
     * Lấy assets không khỏe mạnh.
     */
    @Transactional(readOnly = true)
    public List<Asset> getUnhealthyAssets() {
        return assetRepository.findByHealthStatusIn(List.of(
                HealthStatus.WARNING, 
                HealthStatus.CRITICAL, 
                HealthStatus.UNKNOWN
        ));
    }

    // ==================== Statistics ====================

    /**
     * Lấy statistics tổng quan.
     */
    @Transactional(readOnly = true)
    public AssetStatistics getStatistics() {
        AssetStatistics stats = new AssetStatistics();
        
        stats.totalAssets = assetRepository.count();
        stats.activeAssets = assetRepository.countByStatus(AssetStatus.ACTIVE);
        stats.maintenanceAssets = assetRepository.countByStatus(AssetStatus.MAINTENANCE);
        stats.retiredAssets = assetRepository.countByStatus(AssetStatus.RETIRED);
        
        stats.healthyAssets = assetRepository.countByHealthStatus(HealthStatus.HEALTHY);
        stats.warningAssets = assetRepository.countByHealthStatus(HealthStatus.WARNING);
        stats.criticalAssets = assetRepository.countByHealthStatus(HealthStatus.CRITICAL);
        
        stats.expiringWarranties = getExpiringWarranties(30).size();
        stats.expiredWarranties = getExpiredWarranties().size();
        stats.overdueMaintenance = getOverdueMaintenance().size();
        
        return stats;
    }

    // ==================== Helper Methods ====================

    private String generateAssetNumber() {
        String uuid = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "AST-" + uuid;
    }

    // ==================== Exceptions ====================

    public static class AssetNotFoundException extends RuntimeException {
        public AssetNotFoundException(Long id) {
            super("Không tìm thấy Asset với ID: " + id);
        }
        public AssetNotFoundException(String number) {
            super("Không tìm thấy Asset với số: " + number);
        }
    }

    // ==================== Statistics DTO ====================

    public static class AssetStatistics {
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
    }
}

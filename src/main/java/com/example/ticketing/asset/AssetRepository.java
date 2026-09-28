package com.example.ticketing.asset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository cho Asset entity.
 */
@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {

    Optional<Asset> findByAssetNumber(String assetNumber);

    Page<Asset> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Asset> findByAssetTypeOrderByNameAsc(Asset.AssetType assetType, Pageable pageable);

    Page<Asset> findByStatusOrderByNameAsc(Asset.AssetStatus status, Pageable pageable);

    Page<Asset> findByHealthStatusOrderByNameAsc(Asset.HealthStatus healthStatus, Pageable pageable);

    Page<Asset> findByLocationOrderByNameAsc(String location, Pageable pageable);

    Page<Asset> findByAssignedToOrderByNameAsc(String assignedTo, Pageable pageable);

    Page<Asset> findByCategoryOrderByNameAsc(String category, Pageable pageable);

    @Query("SELECT a FROM Asset a WHERE " +
           "LOWER(a.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(a.assetNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(a.serialNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(a.ipAddress) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Asset> searchAssets(@Param("search") String search, Pageable pageable);

    // Assets with expiring warranty (within 30 days)
    @Query("SELECT a FROM Asset a WHERE a.warrantyExpiryDate IS NOT NULL " +
           "AND a.warrantyExpiryDate BETWEEN :today AND :thirtyDays " +
           "ORDER BY a.warrantyExpiryDate ASC")
    List<Asset> findExpiringWarranties(
            @Param("today") LocalDate today, 
            @Param("thirtyDays") LocalDate thirtyDays);

    // Assets with expired warranty
    @Query("SELECT a FROM Asset a WHERE a.warrantyExpiryDate IS NOT NULL " +
           "AND a.warrantyExpiryDate < :today " +
           "ORDER BY a.warrantyExpiryDate DESC")
    List<Asset> findExpiredWarranties(@Param("today") LocalDate today);

    // Assets by health status
    @Query("SELECT a FROM Asset a WHERE a.healthStatus IN :statuses " +
           "ORDER BY a.healthStatus ASC, a.name ASC")
    List<Asset> findByHealthStatusIn(@Param("statuses") List<Asset.HealthStatus> statuses);

    // Count by type
    long countByAssetType(Asset.AssetType assetType);

    // Count by status
    long countByStatus(Asset.AssetStatus status);

    // Count by health status
    long countByHealthStatus(Asset.HealthStatus healthStatus);

    // Assets needing maintenance
    @Query("SELECT a FROM Asset a WHERE a.nextMaintenanceDate IS NOT NULL " +
           "AND a.nextMaintenanceDate <= :today")
    List<Asset> findOverdueMaintenance(@Param("today") LocalDate today);

    // Software licenses
    @Query("SELECT a FROM Asset a WHERE a.assetType = 'SOFTWARE_LICENSE' " +
           "ORDER BY a.name ASC")
    List<Asset> findSoftwareLicenses();
}

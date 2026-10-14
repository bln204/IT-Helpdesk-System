package com.example.ticketing.asset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
public interface AssetRepository extends JpaRepository<Asset, Long>, JpaSpecificationExecutor<Asset> {

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

    /**
     * Phase: AI System-Wide Access. Token-aware search. The full query
     * string is broken into whitespace-separated tokens, and the asset
     * matches if ANY token (not necessarily all) appears in any of the
     * searchable fields. This is more forgiving than the strict
     * substring match in {@link #searchAssets} when the question
     * contains extra words that do not appear in the database, e.g.
     * "asset MacBook Pro" should still surface "MacBook Pro 16".
     */
    @Query("SELECT DISTINCT a FROM Asset a WHERE " +
           "LOWER(a.name) LIKE LOWER(CONCAT('%', :t1, '%')) OR " +
           "LOWER(a.name) LIKE LOWER(CONCAT('%', :t2, '%')) OR " +
           "LOWER(a.name) LIKE LOWER(CONCAT('%', :t3, '%')) OR " +
           "LOWER(a.name) LIKE LOWER(CONCAT('%', :t4, '%')) OR " +
           "LOWER(a.name) LIKE LOWER(CONCAT('%', :t5, '%')) OR " +
           "LOWER(a.assetNumber) LIKE LOWER(CONCAT('%', :t1, '%')) OR " +
           "LOWER(a.assetNumber) LIKE LOWER(CONCAT('%', :t2, '%')) OR " +
           "LOWER(a.assetNumber) LIKE LOWER(CONCAT('%', :t3, '%')) OR " +
           "LOWER(a.assetNumber) LIKE LOWER(CONCAT('%', :t4, '%')) OR " +
           "LOWER(a.assetNumber) LIKE LOWER(CONCAT('%', :t5, '%')) OR " +
           "LOWER(a.serialNumber) LIKE LOWER(CONCAT('%', :t1, '%')) OR " +
           "LOWER(a.serialNumber) LIKE LOWER(CONCAT('%', :t2, '%')) OR " +
           "LOWER(a.serialNumber) LIKE LOWER(CONCAT('%', :t3, '%')) OR " +
           "LOWER(a.serialNumber) LIKE LOWER(CONCAT('%', :t4, '%')) OR " +
           "LOWER(a.serialNumber) LIKE LOWER(CONCAT('%', :t5, '%')) OR " +
           "LOWER(a.ipAddress) LIKE LOWER(CONCAT('%', :t1, '%')) OR " +
           "LOWER(a.ipAddress) LIKE LOWER(CONCAT('%', :t2, '%')) OR " +
           "LOWER(a.ipAddress) LIKE LOWER(CONCAT('%', :t3, '%')) OR " +
           "LOWER(a.ipAddress) LIKE LOWER(CONCAT('%', :t4, '%')) OR " +
           "LOWER(a.ipAddress) LIKE LOWER(CONCAT('%', :t5, '%'))")
    Page<Asset> searchAssetsByTokens(@Param("t1") String t1,
                                     @Param("t2") String t2,
                                     @Param("t3") String t3,
                                     @Param("t4") String t4,
                                     @Param("t5") String t5,
                                     Pageable pageable);

    /**
     * Phase: AI System-Wide Access. Same search as {@link #searchAssets}
     * but additionally restricted to a single department via
     * {@code assigned_department}. Used by the AI assistant to enforce
     * the DEPARTMENT_SCOPED RAG verdict for non-IT operators.
     *
     * <p>The {@code :deptCode} parameter is matched case-sensitively
     * because the canonical department codes stored in
     * {@code departments.code} are always uppercase (IT, HR, FIN, ...).
     */
    @Query("SELECT a FROM Asset a WHERE " +
           "a.assignedDepartment = :deptCode AND (" +
           "LOWER(a.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(a.assetNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(a.serialNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(a.ipAddress) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Asset> searchAssetsByDepartment(@Param("search") String search,
                                          @Param("deptCode") String deptCode,
                                          Pageable pageable);

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

    // Phase: AI System-Wide Access. Count assets in a department
    // for DEPARTMENT_SCOPED counting questions.
    long countByAssignedDepartment(String deptCode);

    // Assets needing maintenance
    @Query("SELECT a FROM Asset a WHERE a.nextMaintenanceDate IS NOT NULL " +
           "AND a.nextMaintenanceDate <= :today")
    List<Asset> findOverdueMaintenance(@Param("today") LocalDate today);

    // Assets with upcoming maintenance
    @Query("SELECT a FROM Asset a WHERE a.nextMaintenanceDate IS NOT NULL " +
           "AND a.nextMaintenanceDate BETWEEN :start AND :end " +
           "ORDER BY a.nextMaintenanceDate ASC")
    List<Asset> findUpcomingMaintenance(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    // Child assets
    List<Asset> findByParentAssetId(Long parentAssetId);

    // Software licenses
    @Query("SELECT a FROM Asset a WHERE a.assetType = 'SOFTWARE_LICENSE' " +
           "ORDER BY a.name ASC")
    List<Asset> findSoftwareLicenses();
}

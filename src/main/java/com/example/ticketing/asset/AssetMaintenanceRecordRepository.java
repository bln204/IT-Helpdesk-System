package com.example.ticketing.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository cho AssetMaintenanceRecord entity.
 */
@Repository
public interface AssetMaintenanceRecordRepository extends JpaRepository<AssetMaintenanceRecord, Long> {

    List<AssetMaintenanceRecord> findByAssetIdOrderByCreatedAtDesc(Long assetId);
}

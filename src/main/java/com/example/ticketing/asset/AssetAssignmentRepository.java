package com.example.ticketing.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository cho AssetAssignment entity.
 */
@Repository
public interface AssetAssignmentRepository extends JpaRepository<AssetAssignment, Long> {

    List<AssetAssignment> findByAssetIdOrderByAssignedAtDesc(Long assetId);

    List<AssetAssignment> findByAssignedToOrderByAssignedAtDesc(String assignedTo);

    List<AssetAssignment> findByStatusOrderByAssignedAtDesc(String status);
}

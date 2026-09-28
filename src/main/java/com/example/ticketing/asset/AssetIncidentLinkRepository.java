package com.example.ticketing.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho AssetIncidentLink entity.
 */
@Repository
public interface AssetIncidentLinkRepository extends JpaRepository<AssetIncidentLink, Long> {

    List<AssetIncidentLink> findByAssetIdOrderByCreatedAtDesc(Long assetId);

    List<AssetIncidentLink> findByIncidentIdOrderByCreatedAtDesc(Long incidentId);

    Optional<AssetIncidentLink> findByAssetIdAndIncidentId(Long assetId, Long incidentId);

    long countByAssetId(Long assetId);

    long countByIncidentId(Long incidentId);

    @Query("SELECT ail FROM AssetIncidentLink ail WHERE ail.asset.id = :assetId ORDER BY ail.createdAt DESC")
    List<AssetIncidentLink> findByAsset(@Param("assetId") Long assetId);
}

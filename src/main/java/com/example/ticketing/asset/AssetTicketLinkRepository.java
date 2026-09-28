package com.example.ticketing.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho AssetTicketLink entity.
 */
@Repository
public interface AssetTicketLinkRepository extends JpaRepository<AssetTicketLink, Long> {

    List<AssetTicketLink> findByAssetIdOrderByCreatedAtDesc(Long assetId);

    List<AssetTicketLink> findByTicketIdOrderByCreatedAtDesc(Long ticketId);

    List<AssetTicketLink> findByIncidentIdOrderByCreatedAtDesc(Long incidentId);

    List<AssetTicketLink> findByChangeIdOrderByCreatedAtDesc(Long changeId);

    Optional<AssetTicketLink> findByAssetIdAndIncidentId(Long assetId, Long incidentId);

    Optional<AssetTicketLink> findByAssetIdAndTicketId(Long assetId, Long ticketId);

    long countByIncidentId(Long incidentId);

    long countByTicketId(Long ticketId);

    long countByAssetId(Long assetId);

    @Query("SELECT atl FROM AssetTicketLink atl WHERE atl.resolved = false ORDER BY atl.createdAt DESC")
    List<AssetTicketLink> findUnresolvedLinks();
}

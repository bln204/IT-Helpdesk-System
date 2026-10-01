package com.example.ticketing.asset;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.asset.AssetIncidentLink.LinkRole;

/**
 * Service cho Asset ↔ Ticket Linking.
 */
@Service
@Transactional
public class AssetTicketLinkService {

    private static final Logger log = LoggerFactory.getLogger(AssetTicketLinkService.class);

    private final AssetTicketLinkRepository ticketLinkRepository;
    private final AssetIncidentLinkRepository incidentLinkRepository;
    private final AssetRepository assetRepository;
    private final AssetService assetService;

    public AssetTicketLinkService(
            AssetTicketLinkRepository ticketLinkRepository,
            AssetIncidentLinkRepository incidentLinkRepository,
            AssetRepository assetRepository,
            AssetService assetService) {
        this.ticketLinkRepository = ticketLinkRepository;
        this.incidentLinkRepository = incidentLinkRepository;
        this.assetRepository = assetRepository;
        this.assetService = assetService;
    }

    // ==================== Incident Linking ====================

    /**
     * Liên kết asset với incident.
     */
    public AssetIncidentLink linkAssetToIncident(
            Long assetId, 
            Long incidentId, 
            LinkRole linkRole,
            String impactDescription,
            String createdBy) {
        
        log.info("Linking asset {} to incident {} as {}", assetId, incidentId, linkRole);
        
        // Check if already linked
        if (incidentLinkRepository.findByAssetIdAndIncidentId(assetId, incidentId).isPresent()) {
            throw new IllegalStateException("Asset đã được liên kết với incident này");
        }
        
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new AssetService.AssetNotFoundException(assetId));
        
        AssetIncidentLink link = new AssetIncidentLink(asset, incidentId, linkRole);
        link.setImpactDescription(impactDescription);
        link.setCreatedBy(createdBy);
        
        // Update asset health if root cause
        if (linkRole == LinkRole.ROOT_CAUSE) {
            assetService.updateHealthStatus(
                    assetId, 
                    Asset.HealthStatus.WARNING, 
                    "Identified as root cause in incident #" + incidentId,
                    createdBy
            );
        }
        
        return incidentLinkRepository.save(link);
    }

    /**
     * Bỏ liên kết asset và incident.
     */
    public void unlinkAssetFromIncident(Long assetId, Long incidentId) {
        log.info("Unlinking asset {} from incident {}", assetId, incidentId);
        
        incidentLinkRepository.findByAssetIdAndIncidentId(assetId, incidentId)
                .ifPresent(incidentLinkRepository::delete);
    }

    /**
     * Lấy tất cả assets liên quan đến incident.
     */
    @Transactional(readOnly = true)
    public List<AssetIncidentLink> getAssetsByIncident(Long incidentId) {
        return incidentLinkRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId);
    }

    /**
     * Lấy tất cả incidents liên quan đến asset.
     */
    @Transactional(readOnly = true)
    public List<AssetIncidentLink> getIncidentsByAsset(Long assetId) {
        return incidentLinkRepository.findByAssetIdOrderByCreatedAtDesc(assetId);
    }

    /**
     * Cập nhật link role.
     */
    public AssetIncidentLink updateLinkRole(Long linkId, LinkRole newRole, String actorName) {
        AssetIncidentLink link = incidentLinkRepository.findById(linkId)
                .orElseThrow(() -> new LinkNotFoundException(linkId));
        
        link.setLinkRole(newRole);
        
        // Update asset health if changed to/from root cause
        if (newRole == LinkRole.ROOT_CAUSE) {
            assetService.updateHealthStatus(
                    link.getAsset().getId(),
                    Asset.HealthStatus.WARNING,
                    "Identified as root cause in incident #" + link.getIncidentId(),
                    actorName
            );
        }
        
        return incidentLinkRepository.save(link);
    }

    // ==================== Ticket Linking ====================

    /**
     * Liên kết asset với ticket.
     */
    public AssetTicketLink linkAssetToTicket(
            Long assetId,
            Long ticketId,
            AssetTicketLink.LinkType linkType,
            String notes) {
        
        log.info("Linking asset {} to ticket {} as {}", assetId, ticketId, linkType);
        
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new AssetService.AssetNotFoundException(assetId));
        
        AssetTicketLink link = new AssetTicketLink(asset, ticketId, linkType);
        link.setNotes(notes);
        
        return ticketLinkRepository.save(link);
    }

    /**
     * Lấy assets liên quan đến ticket.
     */
    @Transactional(readOnly = true)
    public List<AssetTicketLink> getAssetsByTicket(Long ticketId) {
        return ticketLinkRepository.findByTicketIdOrderByCreatedAtDesc(ticketId);
    }

    /**
     * Lấy tickets liên quan đến asset.
     */
    @Transactional(readOnly = true)
    public List<AssetTicketLink> getTicketsByAsset(Long assetId) {
        return ticketLinkRepository.findByAssetIdOrderByCreatedAtDesc(assetId);
    }

    // ==================== Statistics ====================

    /**
     * Đếm incidents liên quan đến asset.
     */
    @Transactional(readOnly = true)
    public long countIncidentsByAsset(Long assetId) {
        return incidentLinkRepository.countByAssetId(assetId);
    }

    /**
     * Đếm assets liên quan đến incident.
     */
    @Transactional(readOnly = true)
    public long countAssetsByIncident(Long incidentId) {
        return incidentLinkRepository.countByIncidentId(incidentId);
    }

    // ==================== Exceptions ====================

    public static class LinkNotFoundException extends RuntimeException {
        public LinkNotFoundException(Long id) {
            super("Không tìm thấy Link với ID: " + id);
        }
    }
}

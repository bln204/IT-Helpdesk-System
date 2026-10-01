package com.example.ticketing.asset;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.ticketing.asset.AssetIncidentLink.LinkRole;

/**
 * REST Controller cho Asset ↔ Ticket Linking.
 */
@RestController
@RequestMapping("/api/asset-links")
@CrossOrigin(origins = "*")
public class AssetTicketLinkController {

    private static final Logger log = LoggerFactory.getLogger(AssetTicketLinkController.class);

    private final AssetTicketLinkService linkService;

    public AssetTicketLinkController(AssetTicketLinkService linkService) {
        this.linkService = linkService;
    }

    // ==================== Incident Linking ====================

    /**
     * Lấy assets liên quan đến incident.
     * GET /api/asset-links/incident/{incidentId}
     */
    @GetMapping("/incident/{incidentId}")
    public ResponseEntity<List<AssetLinkDto>> getAssetsByIncident(@PathVariable Long incidentId) {
        log.info("GET /api/asset-links/incident/{}", incidentId);
        List<AssetIncidentLink> links = linkService.getAssetsByIncident(incidentId);
        return ResponseEntity.ok(links.stream()
                .map(AssetLinkDto::fromIncidentLink)
                .collect(Collectors.toList()));
    }

    /**
     * Lấy incidents liên quan đến asset.
     * GET /api/asset-links/asset/{assetId}/incidents
     */
    @GetMapping("/asset/{assetId}/incidents")
    public ResponseEntity<List<IncidentLinkDto>> getIncidentsByAsset(@PathVariable Long assetId) {
        log.info("GET /api/asset-links/asset/{}/incidents", assetId);
        List<AssetIncidentLink> links = linkService.getIncidentsByAsset(assetId);
        return ResponseEntity.ok(links.stream()
                .map(IncidentLinkDto::fromIncidentLink)
                .collect(Collectors.toList()));
    }

    /**
     * Liên kết asset với incident.
     * POST /api/asset-links/incident
     */
    @PostMapping("/incident")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<AssetLinkDto> linkAssetToIncident(@RequestBody LinkRequest request) {
        log.info("POST /api/asset-links/incident - asset: {}, incident: {}", 
                request.getAssetId(), request.getIncidentId());

        AssetIncidentLink link = linkService.linkAssetToIncident(
                request.getAssetId(),
                request.getIncidentId(),
                LinkRole.valueOf(request.getLinkRole()),
                request.getImpactDescription(),
                "admin"
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AssetLinkDto.fromIncidentLink(link));
    }

    /**
     * Bỏ liên kết.
     * DELETE /api/asset-links/incident/{incidentId}/asset/{assetId}
     */
    @DeleteMapping("/incident/{incidentId}/asset/{assetId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<Void> unlinkAssetFromIncident(
            @PathVariable Long incidentId,
            @PathVariable Long assetId) {
        log.info("DELETE /api/asset-links/incident/{}/asset/{}", incidentId, assetId);
        linkService.unlinkAssetFromIncident(assetId, incidentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Cập nhật link role.
     * PATCH /api/asset-links/{linkId}/role
     */
    @PatchMapping("/{linkId}/role")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<AssetLinkDto> updateLinkRole(
            @PathVariable Long linkId,
            @RequestBody RoleUpdateRequest request) {
        log.info("PATCH /api/asset-links/{}/role - role: {}", linkId, request.getLinkRole());

        AssetIncidentLink link = linkService.updateLinkRole(
                linkId,
                LinkRole.valueOf(request.getLinkRole()),
                "admin"
        );

        return ResponseEntity.ok(AssetLinkDto.fromIncidentLink(link));
    }

    /**
     * Đếm assets liên quan.
     * GET /api/asset-links/incident/{incidentId}/count
     */
    @GetMapping("/incident/{incidentId}/count")
    public ResponseEntity<Long> countAssetsByIncident(@PathVariable Long incidentId) {
        return ResponseEntity.ok(linkService.countAssetsByIncident(incidentId));
    }

    /**
     * Đếm incidents liên quan.
     * GET /api/asset-links/asset/{assetId}/incidents/count
     */
    @GetMapping("/asset/{assetId}/incidents/count")
    public ResponseEntity<Long> countIncidentsByAsset(@PathVariable Long assetId) {
        return ResponseEntity.ok(linkService.countIncidentsByAsset(assetId));
    }

    // ==================== Exception Handlers ====================

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONFLICT", ex.getMessage()));
    }

    @ExceptionHandler({AssetTicketLinkService.LinkNotFoundException.class, AssetService.AssetNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex) {
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

    public static class LinkRequest {
        private Long assetId;
        private Long incidentId;
        private String linkRole;
        private String impactDescription;

        public Long getAssetId() { return assetId; }
        public Long getIncidentId() { return incidentId; }
        public String getLinkRole() { return linkRole; }
        public String getImpactDescription() { return impactDescription; }
    }

    public static class RoleUpdateRequest {
        private String linkRole;
        public String getLinkRole() { return linkRole; }
    }

    public static class AssetLinkDto {
        private Long id;
        private Long assetId;
        private String assetNumber;
        private String assetName;
        private String assetType;
        private String healthStatus;
        private Long incidentId;
        private String linkRole;
        private String linkRoleLabel;
        private String confidenceLevel;
        private String impactDescription;
        private java.time.LocalDateTime createdAt;

        public static AssetLinkDto fromIncidentLink(AssetIncidentLink link) {
            AssetLinkDto dto = new AssetLinkDto();
            dto.setId(link.getId());
            dto.setAssetId(link.getAsset() != null ? link.getAsset().getId() : null);
            dto.setAssetNumber(link.getAsset() != null ? link.getAsset().getAssetNumber() : null);
            dto.setAssetName(link.getAsset() != null ? link.getAsset().getName() : null);
            dto.setAssetType(link.getAsset() != null && link.getAsset().getAssetType() != null 
                    ? link.getAsset().getAssetType().name() : null);
            dto.setHealthStatus(link.getAsset() != null && link.getAsset().getHealthStatus() != null
                    ? link.getAsset().getHealthStatus().name() : null);
            dto.setIncidentId(link.getIncidentId());
            dto.setLinkRole(link.getLinkRole() != null ? link.getLinkRole().name() : null);
            dto.setLinkRoleLabel(link.getLinkRole() != null ? link.getLinkRole().getLabel() : null);
            dto.setConfidenceLevel(link.getConfidenceLevel() != null ? link.getConfidenceLevel().name() : null);
            dto.setImpactDescription(link.getImpactDescription());
            dto.setCreatedAt(link.getCreatedAt());
            return dto;
        }

        // Getters/Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getAssetId() { return assetId; }
        public void setAssetId(Long assetId) { this.assetId = assetId; }
        public String getAssetNumber() { return assetNumber; }
        public void setAssetNumber(String assetNumber) { this.assetNumber = assetNumber; }
        public String getAssetName() { return assetName; }
        public void setAssetName(String assetName) { this.assetName = assetName; }
        public String getAssetType() { return assetType; }
        public void setAssetType(String assetType) { this.assetType = assetType; }
        public String getHealthStatus() { return healthStatus; }
        public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }
        public Long getIncidentId() { return incidentId; }
        public void setIncidentId(Long incidentId) { this.incidentId = incidentId; }
        public String getLinkRole() { return linkRole; }
        public void setLinkRole(String linkRole) { this.linkRole = linkRole; }
        public String getLinkRoleLabel() { return linkRoleLabel; }
        public void setLinkRoleLabel(String linkRoleLabel) { this.linkRoleLabel = linkRoleLabel; }
        public String getConfidenceLevel() { return confidenceLevel; }
        public void setConfidenceLevel(String confidenceLevel) { this.confidenceLevel = confidenceLevel; }
        public String getImpactDescription() { return impactDescription; }
        public void setImpactDescription(String impactDescription) { this.impactDescription = impactDescription; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    public static class IncidentLinkDto {
        private Long id;
        private Long incidentId;
        private String incidentNumber;
        private String incidentTitle;
        private String incidentStatus;
        private Long assetId;
        private String linkRole;
        private String linkRoleLabel;
        private String confidenceLevel;
        private String impactDescription;
        private java.time.LocalDateTime createdAt;

        public static IncidentLinkDto fromIncidentLink(AssetIncidentLink link) {
            IncidentLinkDto dto = new IncidentLinkDto();
            dto.setId(link.getId());
            dto.setIncidentId(link.getIncidentId());
            dto.setAssetId(link.getAsset() != null ? link.getAsset().getId() : null);
            dto.setLinkRole(link.getLinkRole() != null ? link.getLinkRole().name() : null);
            dto.setLinkRoleLabel(link.getLinkRole() != null ? link.getLinkRole().getLabel() : null);
            dto.setConfidenceLevel(link.getConfidenceLevel() != null ? link.getConfidenceLevel().name() : null);
            dto.setImpactDescription(link.getImpactDescription());
            dto.setCreatedAt(link.getCreatedAt());
            return dto;
        }

        // Getters/Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getIncidentId() { return incidentId; }
        public void setIncidentId(Long incidentId) { this.incidentId = incidentId; }
        public String getIncidentNumber() { return incidentNumber; }
        public void setIncidentNumber(String incidentNumber) { this.incidentNumber = incidentNumber; }
        public String getIncidentTitle() { return incidentTitle; }
        public void setIncidentTitle(String incidentTitle) { this.incidentTitle = incidentTitle; }
        public String getIncidentStatus() { return incidentStatus; }
        public void setIncidentStatus(String incidentStatus) { this.incidentStatus = incidentStatus; }
        public Long getAssetId() { return assetId; }
        public void setAssetId(Long assetId) { this.assetId = assetId; }
        public String getLinkRole() { return linkRole; }
        public void setLinkRole(String linkRole) { this.linkRole = linkRole; }
        public String getLinkRoleLabel() { return linkRoleLabel; }
        public void setLinkRoleLabel(String linkRoleLabel) { this.linkRoleLabel = linkRoleLabel; }
        public String getConfidenceLevel() { return confidenceLevel; }
        public void setConfidenceLevel(String confidenceLevel) { this.confidenceLevel = confidenceLevel; }
        public String getImpactDescription() { return impactDescription; }
        public void setImpactDescription(String impactDescription) { this.impactDescription = impactDescription; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
}

package com.example.ticketing.incident;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.ticketing.ticket.Ticket;
import com.example.ticketing.ticket.TicketTypes.TicketPriority;

/**
 * REST Controller cho Incident Management.
 */
@RestController
@RequestMapping("/api/incidents")
@CrossOrigin(origins = "*")
public class IncidentController {

    private static final Logger log = LoggerFactory.getLogger(IncidentController.class);

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    // ==================== CRUD Operations ====================

    /**
     * Lấy tất cả incidents.
     * GET /api/incidents
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<Page<IncidentDto>> getAllIncidents(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/incidents - status: {}, search: {}", status, search);

        Pageable pageable = Pageable.ofSize(size).withPage(page);
        Page<Incident> incidents;

        if (search != null && !search.isBlank()) {
            incidents = incidentService.searchIncidents(search, pageable);
        } else if (status != null && !status.isBlank()) {
            List<Incident.IncidentStatus> statuses = List.of(Incident.IncidentStatus.valueOf(status));
            incidents = incidentService.getIncidentsByStatus(statuses, pageable);
        } else {
            incidents = incidentService.getAllIncidents(pageable);
        }

        return ResponseEntity.ok(incidents.map(IncidentDto::fromEntity));
    }

    /**
     * Lấy incident theo ID.
     * GET /api/incidents/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<IncidentDto> getIncidentById(@PathVariable Long id) {
        log.info("GET /api/incidents/{}", id);
        Incident incident = incidentService.getIncidentById(id);
        return ResponseEntity.ok(IncidentDto.fromEntity(incident));
    }

    /**
     * Lấy incident theo incident number.
     * GET /api/incidents/number/{incidentNumber}
     */
    @GetMapping("/number/{incidentNumber}")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<IncidentDto> getIncidentByNumber(@PathVariable String incidentNumber) {
        log.info("GET /api/incidents/number/{}", incidentNumber);
        Incident incident = incidentService.getIncidentByNumber(incidentNumber);
        return ResponseEntity.ok(IncidentDto.fromEntity(incident));
    }

    /**
     * Tạo incident mới.
     * POST /api/incidents
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<IncidentDto> createIncident(@RequestBody IncidentRequest request) {
        log.info("POST /api/incidents - Creating: {}", request.getTitle());

        Incident incident = mapRequestToEntity(request);
        Incident created = incidentService.createIncident(incident, "admin");

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(IncidentDto.fromEntity(created));
    }

    /**
     * Cập nhật incident.
     * PUT /api/incidents/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<IncidentDto> updateIncident(
            @PathVariable Long id,
            @RequestBody IncidentRequest request) {
        log.info("PUT /api/incidents/{}", id);

        Incident updates = mapRequestToEntity(request);
        Incident updated = incidentService.updateIncident(id, updates, "admin");

        return ResponseEntity.ok(IncidentDto.fromEntity(updated));
    }

    /**
     * Xóa incident.
     * DELETE /api/incidents/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteIncident(@PathVariable Long id) {
        log.info("DELETE /api/incidents/{}", id);
        incidentService.deleteIncident(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== Status Management ====================

    /**
     * Cập nhật trạng thái incident.
     * PATCH /api/incidents/{id}/status
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<IncidentDto> updateStatus(
            @PathVariable Long id,
            @RequestBody StatusUpdateRequest request) {
        log.info("PATCH /api/incidents/{}/status - status: {}", id, request.getStatus());

        Incident updated = incidentService.updateStatus(id, 
                Incident.IncidentStatus.valueOf(request.getStatus()), "admin");

        return ResponseEntity.ok(IncidentDto.fromEntity(updated));
    }

    /**
     * Assign incident cho user.
     * PATCH /api/incidents/{id}/assign
     */
    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<IncidentDto> assignIncident(
            @PathVariable Long id,
            @RequestBody AssignRequest request) {
        log.info("PATCH /api/incidents/{}/assign - to: {}", id, request.getAssignee());

        Incident updated = incidentService.assignIncident(id, request.getAssignee(), "admin");

        return ResponseEntity.ok(IncidentDto.fromEntity(updated));
    }

    /**
     * Assign incident cho team.
     * PATCH /api/incidents/{id}/assign-team
     */
    @PatchMapping("/{id}/assign-team")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<IncidentDto> assignTeam(
            @PathVariable Long id,
            @RequestBody AssignTeamRequest request) {
        log.info("PATCH /api/incidents/{}/assign-team - team: {}", id, request.getTeamId());

        Incident updated = incidentService.assignTeam(id, request.getTeamId(), "admin");

        return ResponseEntity.ok(IncidentDto.fromEntity(updated));
    }

    // ==================== Ticket Linking ====================

    /**
     * Link ticket với incident.
     * POST /api/incidents/{id}/tickets
     */
    @PostMapping("/{id}/tickets")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<TicketIncidentLinkDto> linkTicket(
            @PathVariable Long id,
            @RequestBody LinkTicketRequest request) {
        log.info("POST /api/incidents/{}/tickets - ticketId: {}", id, request.getTicketId());

        TicketIncidentLink link = incidentService.linkTicket(
                id, request.getTicketId(), request.getReason(), "admin");

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TicketIncidentLinkDto.fromEntity(link));
    }

    /**
     * Unlink ticket khỏi incident.
     * DELETE /api/incidents/{id}/tickets/{ticketId}
     */
    @DeleteMapping("/{id}/tickets/{ticketId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<Void> unlinkTicket(
            @PathVariable Long id,
            @PathVariable Long ticketId) {
        log.info("DELETE /api/incidents/{}/tickets/{}", id, ticketId);
        incidentService.unlinkTicket(id, ticketId, "admin");
        return ResponseEntity.noContent().build();
    }

    /**
     * Lấy tickets liên quan đến incident.
     * GET /api/incidents/{id}/tickets
     */
    @GetMapping("/{id}/tickets")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<List<TicketSummaryDto>> getLinkedTickets(@PathVariable Long id) {
        log.info("GET /api/incidents/{}/tickets", id);
        List<Ticket> tickets = incidentService.getLinkedTickets(id);
        return ResponseEntity.ok(tickets.stream()
                .map(TicketSummaryDto::fromEntity)
                .toList());
    }

    /**
     * Lấy incidents của ticket.
     * GET /api/incidents/ticket/{ticketId}
     */
    @GetMapping("/ticket/{ticketId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<List<IncidentSummaryDto>> getTicketIncidents(@PathVariable Long ticketId) {
        log.info("GET /api/incidents/ticket/{}", ticketId);
        List<Incident> incidents = incidentService.getTicketIncidents(ticketId);
        return ResponseEntity.ok(incidents.stream()
                .map(IncidentSummaryDto::fromEntity)
                .toList());
    }

    // ==================== Timeline ====================

    /**
     * Lấy timeline của incident.
     * GET /api/incidents/{id}/timeline
     */
    @GetMapping("/{id}/timeline")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<List<IncidentTimelineDto>> getTimeline(@PathVariable Long id) {
        log.info("GET /api/incidents/{}/timeline", id);
        List<IncidentTimeline> timeline = incidentService.getIncidentTimeline(id);
        return ResponseEntity.ok(timeline.stream()
                .map(IncidentTimelineDto::fromEntity)
                .toList());
    }

    /**
     * Thêm note vào incident.
     * POST /api/incidents/{id}/notes
     */
    @PostMapping("/{id}/notes")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<IncidentTimelineDto> addNote(
            @PathVariable Long id,
            @RequestBody AddNoteRequest request) {
        log.info("POST /api/incidents/{}/notes", id);
        IncidentTimeline timeline = incidentService.addNote(id, request.getNote(), "admin", "ADMIN");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(IncidentTimelineDto.fromEntity(timeline));
    }

    // ==================== Helper Methods ====================

    private Incident mapRequestToEntity(IncidentRequest request) {
        Incident incident = new Incident();
        incident.setTitle(request.getTitle());
        incident.setDescription(request.getDescription());
        incident.setPriority(request.getPriority());
        incident.setImpactLevel(request.getImpactLevel());
        incident.setAffectedUsers(request.getAffectedUsers());
        incident.setRootCause(request.getRootCause());
        incident.setWorkaround(request.getWorkaround());
        incident.setResolution(request.getResolution());
        return incident;
    }

    // ==================== Exception Handlers ====================

    @ExceptionHandler(IncidentService.IncidentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleNotFound(IncidentService.IncidentNotFoundException ex) {
        log.error("Incident not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        log.error("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalStateException ex) {
        log.error("Conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONFLICT", ex.getMessage()));
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

    public static class IncidentRequest {
        private String title;
        private String description;
        private TicketPriority priority;
        private Incident.ImpactLevel impactLevel;
        private Integer affectedUsers;
        private String rootCause;
        private String workaround;
        private String resolution;

        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public TicketPriority getPriority() { return priority; }
        public Incident.ImpactLevel getImpactLevel() { return impactLevel; }
        public Integer getAffectedUsers() { return affectedUsers; }
        public String getRootCause() { return rootCause; }
        public String getWorkaround() { return workaround; }
        public String getResolution() { return resolution; }
    }

    public static class StatusUpdateRequest {
        private String status;

        public String getStatus() { return status; }
    }

    public static class AssignRequest {
        private String assignee;

        public String getAssignee() { return assignee; }
    }

    public static class AssignTeamRequest {
        private Long teamId;

        public Long getTeamId() { return teamId; }
    }

    public static class LinkTicketRequest {
        private Long ticketId;
        private String reason;

        public Long getTicketId() { return ticketId; }
        public String getReason() { return reason; }
    }

    public static class AddNoteRequest {
        private String note;

        public String getNote() { return note; }
    }

    public static class IncidentDto {
        private Long id;
        private String incidentNumber;
        private String title;
        private String description;
        private String status;
        private String statusLabel;
        private String priority;
        private String assignedToUsername;
        private String teamName;
        private String impactLevel;
        private Integer affectedUsers;
        private String rootCause;
        private String workaround;
        private String resolution;
        private Integer linkedTicketCount;
        private java.time.LocalDateTime identifiedAt;
        private java.time.LocalDateTime resolvedAt;
        private java.time.LocalDateTime closedAt;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;

        public static IncidentDto fromEntity(Incident incident) {
            IncidentDto dto = new IncidentDto();
            dto.setId(incident.getId());
            dto.setIncidentNumber(incident.getIncidentNumber());
            dto.setTitle(incident.getTitle());
            dto.setDescription(incident.getDescription());
            dto.setStatus(incident.getStatus() != null ? incident.getStatus().name() : null);
            dto.setStatusLabel(incident.getStatus() != null ? incident.getStatus().getLabel() : null);
            dto.setPriority(incident.getPriority() != null ? incident.getPriority().name() : null);
            dto.setAssignedToUsername(incident.getAssignedTo() != null ? incident.getAssignedTo().getUsername() : null);
            dto.setTeamName(incident.getTeam() != null ? incident.getTeam().getName() : null);
            dto.setImpactLevel(incident.getImpactLevel() != null ? incident.getImpactLevel().name() : null);
            dto.setAffectedUsers(incident.getAffectedUsers());
            dto.setRootCause(incident.getRootCause());
            dto.setWorkaround(incident.getWorkaround());
            dto.setResolution(incident.getResolution());
            dto.setLinkedTicketCount(incident.getLinkedTicketCount());
            dto.setIdentifiedAt(incident.getIdentifiedAt());
            dto.setResolvedAt(incident.getResolvedAt());
            dto.setClosedAt(incident.getClosedAt());
            dto.setCreatedAt(incident.getCreatedAt());
            dto.setUpdatedAt(incident.getUpdatedAt());
            return dto;
        }

        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getIncidentNumber() { return incidentNumber; }
        public void setIncidentNumber(String incidentNumber) { this.incidentNumber = incidentNumber; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getStatusLabel() { return statusLabel; }
        public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public String getAssignedToUsername() { return assignedToUsername; }
        public void setAssignedToUsername(String assignedToUsername) { this.assignedToUsername = assignedToUsername; }
        public String getTeamName() { return teamName; }
        public void setTeamName(String teamName) { this.teamName = teamName; }
        public String getImpactLevel() { return impactLevel; }
        public void setImpactLevel(String impactLevel) { this.impactLevel = impactLevel; }
        public Integer getAffectedUsers() { return affectedUsers; }
        public void setAffectedUsers(Integer affectedUsers) { this.affectedUsers = affectedUsers; }
        public String getRootCause() { return rootCause; }
        public void setRootCause(String rootCause) { this.rootCause = rootCause; }
        public String getWorkaround() { return workaround; }
        public void setWorkaround(String workaround) { this.workaround = workaround; }
        public String getResolution() { return resolution; }
        public void setResolution(String resolution) { this.resolution = resolution; }
        public Integer getLinkedTicketCount() { return linkedTicketCount; }
        public void setLinkedTicketCount(Integer linkedTicketCount) { this.linkedTicketCount = linkedTicketCount; }
        public java.time.LocalDateTime getIdentifiedAt() { return identifiedAt; }
        public void setIdentifiedAt(java.time.LocalDateTime identifiedAt) { this.identifiedAt = identifiedAt; }
        public java.time.LocalDateTime getResolvedAt() { return resolvedAt; }
        public void setResolvedAt(java.time.LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
        public java.time.LocalDateTime getClosedAt() { return closedAt; }
        public void setClosedAt(java.time.LocalDateTime closedAt) { this.closedAt = closedAt; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
        public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class IncidentSummaryDto {
        private Long id;
        private String incidentNumber;
        private String title;
        private String status;

        public static IncidentSummaryDto fromEntity(Incident incident) {
            IncidentSummaryDto dto = new IncidentSummaryDto();
            dto.setId(incident.getId());
            dto.setIncidentNumber(incident.getIncidentNumber());
            dto.setTitle(incident.getTitle());
            dto.setStatus(incident.getStatus() != null ? incident.getStatus().name() : null);
            return dto;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getIncidentNumber() { return incidentNumber; }
        public void setIncidentNumber(String incidentNumber) { this.incidentNumber = incidentNumber; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class TicketSummaryDto {
        private Long id;
        private String ticketNumber;
        private String title;
        private String status;
        private String priority;

        public static TicketSummaryDto fromEntity(Ticket ticket) {
            TicketSummaryDto dto = new TicketSummaryDto();
            dto.setId(ticket.getId());
            dto.setTicketNumber(ticket.getTicketNumber());
            dto.setTitle(ticket.getTitle());
            dto.setStatus(ticket.getStatus() != null ? ticket.getStatus().name() : null);
            dto.setPriority(ticket.getPriority() != null ? ticket.getPriority().name() : null);
            return dto;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTicketNumber() { return ticketNumber; }
        public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
    }

    public static class TicketIncidentLinkDto {
        private Long id;
        private Long ticketId;
        private String ticketNumber;
        private Long incidentId;
        private String incidentNumber;
        private String linkReason;
        private String linkedBy;
        private java.time.LocalDateTime linkedAt;

        public static TicketIncidentLinkDto fromEntity(TicketIncidentLink link) {
            TicketIncidentLinkDto dto = new TicketIncidentLinkDto();
            dto.setId(link.getId());
            dto.setTicketId(link.getTicket() != null ? link.getTicket().getId() : null);
            dto.setTicketNumber(link.getTicket() != null ? link.getTicket().getTicketNumber() : null);
            dto.setIncidentId(link.getIncident() != null ? link.getIncident().getId() : null);
            dto.setIncidentNumber(link.getIncident() != null ? link.getIncident().getIncidentNumber() : null);
            dto.setLinkReason(link.getLinkReason());
            dto.setLinkedBy(link.getLinkedBy());
            dto.setLinkedAt(link.getLinkedAt());
            return dto;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTicketId() { return ticketId; }
        public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
        public String getTicketNumber() { return ticketNumber; }
        public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }
        public Long getIncidentId() { return incidentId; }
        public void setIncidentId(Long incidentId) { this.incidentId = incidentId; }
        public String getIncidentNumber() { return incidentNumber; }
        public void setIncidentNumber(String incidentNumber) { this.incidentNumber = incidentNumber; }
        public String getLinkReason() { return linkReason; }
        public void setLinkReason(String linkReason) { this.linkReason = linkReason; }
        public String getLinkedBy() { return linkedBy; }
        public void setLinkedBy(String linkedBy) { this.linkedBy = linkedBy; }
        public java.time.LocalDateTime getLinkedAt() { return linkedAt; }
        public void setLinkedAt(java.time.LocalDateTime linkedAt) { this.linkedAt = linkedAt; }
    }

    public static class IncidentTimelineDto {
        private Long id;
        private String eventType;
        private String title;
        private String description;
        private String actorName;
        private String oldValue;
        private String newValue;
        private java.time.LocalDateTime createdAt;

        public static IncidentTimelineDto fromEntity(IncidentTimeline timeline) {
            IncidentTimelineDto dto = new IncidentTimelineDto();
            dto.setId(timeline.getId());
            dto.setEventType(timeline.getEventType() != null ? timeline.getEventType().name() : null);
            dto.setTitle(timeline.getTitle());
            dto.setDescription(timeline.getDescription());
            dto.setActorName(timeline.getActorName());
            dto.setOldValue(timeline.getOldValue());
            dto.setNewValue(timeline.getNewValue());
            dto.setCreatedAt(timeline.getCreatedAt());
            return dto;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getActorName() { return actorName; }
        public void setActorName(String actorName) { this.actorName = actorName; }
        public String getOldValue() { return oldValue; }
        public void setOldValue(String oldValue) { this.oldValue = oldValue; }
        public String getNewValue() { return newValue; }
        public void setNewValue(String newValue) { this.newValue = newValue; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
}

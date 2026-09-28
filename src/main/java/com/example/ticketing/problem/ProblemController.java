package com.example.ticketing.problem;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller cho Problem Management.
 */
@RestController
@RequestMapping("/api/problems")
@CrossOrigin(origins = "*")
public class ProblemController {

    private static final Logger log = LoggerFactory.getLogger(ProblemController.class);

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    // ==================== Problems ====================

    /**
     * Lấy tất cả problems.
     * GET /api/problems
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<Page<ProblemDto>> getAllProblems(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/problems - status: {}, search: {}", status, search);

        Pageable pageable = Pageable.ofSize(size).withPage(page);
        Page<Problem> problems;

        if (search != null && !search.isBlank()) {
            problems = problemService.searchProblems(search, pageable);
        } else if (status != null && !status.isBlank()) {
            Problem.ProblemStatus problemStatus = Problem.ProblemStatus.valueOf(status);
            problems = problemService.getProblemsByStatus(problemStatus, pageable);
        } else {
            problems = problemService.getAllProblems(pageable);
        }

        return ResponseEntity.ok(problems.map(ProblemDto::fromEntity));
    }

    /**
     * Lấy problem theo ID.
     * GET /api/problems/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProblemDto> getProblemById(@PathVariable Long id) {
        log.info("GET /api/problems/{}", id);
        Problem problem = problemService.getProblemById(id);
        return ResponseEntity.ok(ProblemDto.fromEntity(problem));
    }

    /**
     * Tạo problem mới.
     * POST /api/problems
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<ProblemDto> createProblem(@RequestBody ProblemRequest request) {
        log.info("POST /api/problems - Creating: {}", request.getTitle());

        Problem problem = new Problem();
        problem.setTitle(request.getTitle());
        problem.setDescription(request.getDescription());
        problem.setCategory(request.getCategory());
        problem.setPriority(request.getPriority());
        problem.setImpactLevel(request.getImpactLevel());
        problem.setRootCause(request.getRootCause());
        problem.setRootCauseCategory(request.getRootCauseCategory());
        problem.setWorkaround(request.getWorkaround());

        Problem created = problemService.createProblem(problem, "admin");
        return ResponseEntity.status(HttpStatus.CREATED).body(ProblemDto.fromEntity(created));
    }

    /**
     * Cập nhật problem.
     * PUT /api/problems/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<ProblemDto> updateProblem(
            @PathVariable Long id,
            @RequestBody ProblemRequest request) {
        log.info("PUT /api/problems/{}", id);

        Problem updates = new Problem();
        updates.setTitle(request.getTitle());
        updates.setDescription(request.getDescription());
        updates.setCategory(request.getCategory());
        updates.setPriority(request.getPriority());
        updates.setImpactLevel(request.getImpactLevel());
        updates.setRootCause(request.getRootCause());
        updates.setRootCauseCategory(request.getRootCauseCategory());
        updates.setRootCauseConfidence(request.getRootCauseConfidence());
        updates.setWorkaround(request.getWorkaround());
        updates.setResolution(request.getResolution());

        Problem updated = problemService.updateProblem(id, updates, "admin");
        return ResponseEntity.ok(ProblemDto.fromEntity(updated));
    }

    /**
     * Xóa problem.
     * DELETE /api/problems/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProblem(@PathVariable Long id) {
        log.info("DELETE /api/problems/{}", id);
        problemService.deleteProblem(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== Status Management ====================

    /**
     * Cập nhật trạng thái.
     * PATCH /api/problems/{id}/status
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<ProblemDto> updateStatus(
            @PathVariable Long id,
            @RequestBody StatusUpdateRequest request) {
        log.info("PATCH /api/problems/{}/status - status: {}", id, request.getStatus());

        Problem updated = problemService.updateStatus(
                id,
                Problem.ProblemStatus.valueOf(request.getStatus()),
                "admin"
        );

        return ResponseEntity.ok(ProblemDto.fromEntity(updated));
    }

    /**
     * Gán problem.
     * PATCH /api/problems/{id}/assign
     */
    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<ProblemDto> assignProblem(
            @PathVariable Long id,
            @RequestBody AssignRequest request) {
        log.info("PATCH /api/problems/{}/assign - to: {}", id, request.getAssignee());

        Problem updated = problemService.assignProblem(id, request.getAssignee(), "admin");
        return ResponseEntity.ok(ProblemDto.fromEntity(updated));
    }

    // ==================== Incident Linking ====================

    /**
     * Liên kết incident.
     * POST /api/problems/{id}/incidents
     */
    @PostMapping("/{id}/incidents")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<IncidentLinkDto> linkIncident(
            @PathVariable Long id,
            @RequestBody IncidentLinkRequest request) {
        log.info("POST /api/problems/{}/incidents - incident: {}", id, request.getIncidentId());

        ProblemIncidentLink link = problemService.linkIncident(id, request.getIncidentId(), "admin");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(IncidentLinkDto.fromEntity(link));
    }

    /**
     * Hủy liên kết incident.
     * DELETE /api/problems/{id}/incidents/{incidentId}
     */
    @DeleteMapping("/{id}/incidents/{incidentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<Void> unlinkIncident(
            @PathVariable Long id,
            @PathVariable Long incidentId) {
        log.info("DELETE /api/problems/{}/incidents/{}", id, incidentId);
        problemService.unlinkIncident(id, incidentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lấy incidents liên quan.
     * GET /api/problems/{id}/incidents
     */
    @GetMapping("/{id}/incidents")
    public ResponseEntity<List<IncidentLinkDto>> getLinkedIncidents(@PathVariable Long id) {
        log.info("GET /api/problems/{}/incidents", id);
        List<ProblemIncidentLink> links = problemService.getLinkedIncidents(id);
        return ResponseEntity.ok(links.stream()
                .map(IncidentLinkDto::fromEntity)
                .toList());
    }

    // ==================== Known Errors ====================

    /**
     * Lấy known errors.
     * GET /api/problems/known-errors
     */
    @GetMapping("/known-errors")
    public ResponseEntity<List<KnownErrorDto>> getKnownErrors(
            @RequestParam(required = false) String status) {
        log.info("GET /api/problems/known-errors - status: {}", status);

        KnownError.KnownErrorStatus keStatus = null;
        if (status != null && !status.isBlank()) {
            keStatus = KnownError.KnownErrorStatus.valueOf(status);
        }

        List<KnownError> errors = problemService.getKnownErrors(keStatus);
        return ResponseEntity.ok(errors.stream()
                .map(KnownErrorDto::fromEntity)
                .toList());
    }

    /**
     * Tạo known error.
     * POST /api/problems/known-errors
     */
    @PostMapping("/known-errors")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<KnownErrorDto> createKnownError(@RequestBody KnownErrorRequest request) {
        log.info("POST /api/problems/known-errors - Creating: {}", request.getTitle());

        KnownError ke = new KnownError();
        ke.setTitle(request.getTitle());
        ke.setDescription(request.getDescription());
        ke.setCategory(request.getCategory());
        ke.setSymptoms(request.getSymptoms());
        ke.setRootCause(request.getRootCause());
        ke.setWorkaround(request.getWorkaround());
        ke.setResolution(request.getResolution());

        KnownError created = problemService.createKnownError(ke, "admin");
        return ResponseEntity.status(HttpStatus.CREATED).body(KnownErrorDto.fromEntity(created));
    }

    // ==================== Notes ====================

    /**
     * Lấy notes.
     * GET /api/problems/{id}/notes
     */
    @GetMapping("/{id}/notes")
    public ResponseEntity<List<NoteDto>> getNotes(@PathVariable Long id) {
        log.info("GET /api/problems/{}/notes", id);
        List<ProblemWorkaroundNote> notes = problemService.getNotes(id);
        return ResponseEntity.ok(notes.stream()
                .map(NoteDto::fromEntity)
                .toList());
    }

    /**
     * Thêm note.
     * POST /api/problems/{id}/notes
     */
    @PostMapping("/{id}/notes")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<NoteDto> addNote(
            @PathVariable Long id,
            @RequestBody NoteRequest request) {
        log.info("POST /api/problems/{}/notes", id);

        ProblemWorkaroundNote.NoteType noteType = ProblemWorkaroundNote.NoteType.WORKAROUND;
        if (request.getNoteType() != null) {
            noteType = ProblemWorkaroundNote.NoteType.valueOf(request.getNoteType());
        }

        ProblemWorkaroundNote note = problemService.addNote(
                id,
                request.getBody(),
                request.getAuthorName(),
                request.getAuthorUsername(),
                noteType,
                request.getEffectivenessRating()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(NoteDto.fromEntity(note));
    }

    // ==================== Exception Handlers ====================

    @ExceptionHandler(ProblemService.ProblemNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleProblemNotFound(ProblemService.ProblemNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ProblemService.KnownErrorNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleKnownErrorNotFound(ProblemService.KnownErrorNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalStateException ex) {
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

    public static class ProblemRequest {
        private String title;
        private String description;
        private String category;
        private Problem.Priority priority;
        private Problem.ImpactLevel impactLevel;
        private String rootCause;
        private String rootCauseCategory;
        private Problem.Confidence rootCauseConfidence;
        private String workaround;
        private String resolution;

        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getCategory() { return category; }
        public Problem.Priority getPriority() { return priority; }
        public Problem.ImpactLevel getImpactLevel() { return impactLevel; }
        public String getRootCause() { return rootCause; }
        public String getRootCauseCategory() { return rootCauseCategory; }
        public Problem.Confidence getRootCauseConfidence() { return rootCauseConfidence; }
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

    public static class IncidentLinkRequest {
        private Long incidentId;
        public Long getIncidentId() { return incidentId; }
    }

    public static class KnownErrorRequest {
        private String title;
        private String description;
        private String category;
        private String symptoms;
        private String rootCause;
        private String workaround;
        private String resolution;

        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getCategory() { return category; }
        public String getSymptoms() { return symptoms; }
        public String getRootCause() { return rootCause; }
        public String getWorkaround() { return workaround; }
        public String getResolution() { return resolution; }
    }

    public static class NoteRequest {
        private String body;
        private String authorName;
        private String authorUsername;
        private String noteType;
        private Integer effectivenessRating;

        public String getBody() { return body; }
        public String getAuthorName() { return authorName; }
        public String getAuthorUsername() { return authorUsername; }
        public String getNoteType() { return noteType; }
        public Integer getEffectivenessRating() { return effectivenessRating; }
    }

    public static class ProblemDto {
        private Long id;
        private String problemNumber;
        private String title;
        private String description;
        private String category;
        private String impactLevel;
        private String priority;
        private String status;
        private String statusLabel;
        private String rootCause;
        private String rootCauseCategory;
        private String workaround;
        private String resolution;
        private Integer totalIncidentsLinked;
        private String assignedTo;
        private java.time.LocalDateTime resolutionDate;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;

        public static ProblemDto fromEntity(Problem p) {
            ProblemDto dto = new ProblemDto();
            dto.setId(p.getId());
            dto.setProblemNumber(p.getProblemNumber());
            dto.setTitle(p.getTitle());
            dto.setDescription(p.getDescription());
            dto.setCategory(p.getCategory());
            dto.setImpactLevel(p.getImpactLevel() != null ? p.getImpactLevel().name() : null);
            dto.setPriority(p.getPriority() != null ? p.getPriority().name() : null);
            dto.setStatus(p.getStatus() != null ? p.getStatus().name() : null);
            dto.setStatusLabel(p.getStatus() != null ? p.getStatus().getLabel() : null);
            dto.setRootCause(p.getRootCause());
            dto.setRootCauseCategory(p.getRootCauseCategory());
            dto.setWorkaround(p.getWorkaround());
            dto.setResolution(p.getResolution());
            dto.setTotalIncidentsLinked(p.getTotalIncidentsLinked());
            dto.setAssignedTo(p.getAssignedTo());
            dto.setResolutionDate(p.getResolutionDate());
            dto.setCreatedAt(p.getCreatedAt());
            dto.setUpdatedAt(p.getUpdatedAt());
            return dto;
        }

        // Getters/Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getProblemNumber() { return problemNumber; }
        public void setProblemNumber(String problemNumber) { this.problemNumber = problemNumber; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getImpactLevel() { return impactLevel; }
        public void setImpactLevel(String impactLevel) { this.impactLevel = impactLevel; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getStatusLabel() { return statusLabel; }
        public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }
        public String getRootCause() { return rootCause; }
        public void setRootCause(String rootCause) { this.rootCause = rootCause; }
        public String getRootCauseCategory() { return rootCauseCategory; }
        public void setRootCauseCategory(String rootCauseCategory) { this.rootCauseCategory = rootCauseCategory; }
        public String getWorkaround() { return workaround; }
        public void setWorkaround(String workaround) { this.workaround = workaround; }
        public String getResolution() { return resolution; }
        public void setResolution(String resolution) { this.resolution = resolution; }
        public Integer getTotalIncidentsLinked() { return totalIncidentsLinked; }
        public void setTotalIncidentsLinked(Integer totalIncidentsLinked) { this.totalIncidentsLinked = totalIncidentsLinked; }
        public String getAssignedTo() { return assignedTo; }
        public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
        public java.time.LocalDateTime getResolutionDate() { return resolutionDate; }
        public void setResolutionDate(java.time.LocalDateTime resolutionDate) { this.resolutionDate = resolutionDate; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
        public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class KnownErrorDto {
        private Long id;
        private String errorCode;
        private String title;
        private String description;
        private String category;
        private String symptoms;
        private String rootCause;
        private String workaround;
        private String resolution;
        private String status;
        private String statusLabel;
        private Integer occurrenceCount;
        private java.time.LocalDateTime lastOccurrenceAt;

        public static KnownErrorDto fromEntity(KnownError ke) {
            KnownErrorDto dto = new KnownErrorDto();
            dto.setId(ke.getId());
            dto.setErrorCode(ke.getErrorCode());
            dto.setTitle(ke.getTitle());
            dto.setDescription(ke.getDescription());
            dto.setCategory(ke.getCategory());
            dto.setSymptoms(ke.getSymptoms());
            dto.setRootCause(ke.getRootCause());
            dto.setWorkaround(ke.getWorkaround());
            dto.setResolution(ke.getResolution());
            dto.setStatus(ke.getStatus() != null ? ke.getStatus().name() : null);
            dto.setStatusLabel(ke.getStatus() != null ? ke.getStatus().getLabel() : null);
            dto.setOccurrenceCount(ke.getOccurrenceCount());
            dto.setLastOccurrenceAt(ke.getLastOccurrenceAt());
            return dto;
        }

        // Getters/Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getSymptoms() { return symptoms; }
        public void setSymptoms(String symptoms) { this.symptoms = symptoms; }
        public String getRootCause() { return rootCause; }
        public void setRootCause(String rootCause) { this.rootCause = rootCause; }
        public String getWorkaround() { return workaround; }
        public void setWorkaround(String workaround) { this.workaround = workaround; }
        public String getResolution() { return resolution; }
        public void setResolution(String resolution) { this.resolution = resolution; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getStatusLabel() { return statusLabel; }
        public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }
        public Integer getOccurrenceCount() { return occurrenceCount; }
        public void setOccurrenceCount(Integer occurrenceCount) { this.occurrenceCount = occurrenceCount; }
        public java.time.LocalDateTime getLastOccurrenceAt() { return lastOccurrenceAt; }
        public void setLastOccurrenceAt(java.time.LocalDateTime lastOccurrenceAt) { this.lastOccurrenceAt = lastOccurrenceAt; }
    }

    public static class IncidentLinkDto {
        private Long id;
        private Long problemId;
        private Long incidentId;
        private String linkType;
        private java.time.LocalDateTime linkedAt;

        public static IncidentLinkDto fromEntity(ProblemIncidentLink link) {
            IncidentLinkDto dto = new IncidentLinkDto();
            dto.setId(link.getId());
            dto.setProblemId(link.getProblem() != null ? link.getProblem().getId() : null);
            dto.setIncidentId(link.getIncidentId());
            dto.setLinkType(link.getLinkType() != null ? link.getLinkType().name() : null);
            dto.setLinkedAt(link.getLinkedAt());
            return dto;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getProblemId() { return problemId; }
        public void setProblemId(Long problemId) { this.problemId = problemId; }
        public Long getIncidentId() { return incidentId; }
        public void setIncidentId(Long incidentId) { this.incidentId = incidentId; }
        public String getLinkType() { return linkType; }
        public void setLinkType(String linkType) { this.linkType = linkType; }
        public java.time.LocalDateTime getLinkedAt() { return linkedAt; }
        public void setLinkedAt(java.time.LocalDateTime linkedAt) { this.linkedAt = linkedAt; }
    }

    public static class NoteDto {
        private Long id;
        private Long problemId;
        private String authorName;
        private String authorUsername;
        private String noteType;
        private String noteTypeLabel;
        private String body;
        private Integer effectivenessRating;
        private java.time.LocalDateTime createdAt;

        public static NoteDto fromEntity(ProblemWorkaroundNote note) {
            NoteDto dto = new NoteDto();
            dto.setId(note.getId());
            dto.setProblemId(note.getProblem() != null ? note.getProblem().getId() : null);
            dto.setAuthorName(note.getAuthorName());
            dto.setAuthorUsername(note.getAuthorUsername());
            dto.setNoteType(note.getNoteType() != null ? note.getNoteType().name() : null);
            dto.setNoteTypeLabel(note.getNoteType() != null ? note.getNoteType().getLabel() : null);
            dto.setBody(note.getBody());
            dto.setEffectivenessRating(note.getEffectivenessRating());
            dto.setCreatedAt(note.getCreatedAt());
            return dto;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getProblemId() { return problemId; }
        public void setProblemId(Long problemId) { this.problemId = problemId; }
        public String getAuthorName() { return authorName; }
        public void setAuthorName(String authorName) { this.authorName = authorName; }
        public String getAuthorUsername() { return authorUsername; }
        public void setAuthorUsername(String authorUsername) { this.authorUsername = authorUsername; }
        public String getNoteType() { return noteType; }
        public void setNoteType(String noteType) { this.noteType = noteType; }
        public String getNoteTypeLabel() { return noteTypeLabel; }
        public void setNoteTypeLabel(String noteTypeLabel) { this.noteTypeLabel = noteTypeLabel; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        public Integer getEffectivenessRating() { return effectivenessRating; }
        public void setEffectivenessRating(Integer effectivenessRating) { this.effectivenessRating = effectivenessRating; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
}

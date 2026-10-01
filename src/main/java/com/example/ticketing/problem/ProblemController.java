package com.example.ticketing.problem;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<Page<ProblemDto>> getAllProblems(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
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

    @GetMapping("/{id}")
    public ResponseEntity<ProblemDto> getProblemById(@PathVariable Long id) {
        Problem problem = problemService.getProblemById(id);
        return ResponseEntity.ok(ProblemDto.fromEntity(problem));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<ProblemDto> createProblem(@RequestBody ProblemRequest request) {
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

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<ProblemDto> updateProblem(@PathVariable Long id, @RequestBody ProblemRequest request) {
        Problem updates = new Problem();
        updates.setTitle(request.getTitle());
        updates.setDescription(request.getDescription());
        updates.setCategory(request.getCategory());
        updates.setPriority(request.getPriority());
        updates.setImpactLevel(request.getImpactLevel());
        updates.setRootCause(request.getRootCause());
        updates.setRootCauseCategory(request.getRootCauseCategory());
        updates.setWorkaround(request.getWorkaround());
        updates.setResolution(request.getResolution());

        Problem updated = problemService.updateProblem(id, updates, "admin");
        return ResponseEntity.ok(ProblemDto.fromEntity(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProblem(@PathVariable Long id) {
        problemService.deleteProblem(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== Status Management ====================

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<ProblemDto> updateStatus(@PathVariable Long id, @RequestBody StatusUpdateRequest request) {
        Problem updated = problemService.updateStatus(id, Problem.ProblemStatus.valueOf(request.getStatus()), "admin");
        return ResponseEntity.ok(ProblemDto.fromEntity(updated));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<ProblemDto> assignProblem(@PathVariable Long id, @RequestBody AssignRequest request) {
        Problem updated = problemService.assignProblem(id, request.getAssignee(), "admin");
        return ResponseEntity.ok(ProblemDto.fromEntity(updated));
    }

    // ==================== Incident Linking ====================

    @PostMapping("/{id}/incidents")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<IncidentLinkDto> linkIncident(@PathVariable Long id, @RequestBody IncidentLinkRequest request) {
        ProblemIncidentLink link = problemService.linkIncident(id, request.getIncidentId(), "admin");
        return ResponseEntity.status(HttpStatus.CREATED).body(IncidentLinkDto.fromEntity(link));
    }

    @DeleteMapping("/{id}/incidents/{incidentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<Void> unlinkIncident(@PathVariable Long id, @PathVariable Long incidentId) {
        problemService.unlinkIncident(id, incidentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/incidents")
    public ResponseEntity<List<IncidentLinkDto>> getLinkedIncidents(@PathVariable Long id) {
        List<ProblemIncidentLink> links = problemService.getLinkedIncidents(id);
        return ResponseEntity.ok(links.stream().map(IncidentLinkDto::fromEntity).toList());
    }

    // ==================== Known Errors ====================

    @GetMapping("/known-errors")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<List<KnownErrorDto>> getKnownErrors(@RequestParam(required = false) String status) {
        List<KnownError> errors;
        if (status != null && !status.isBlank()) {
            errors = problemService.getKnownErrors(KnownError.KnownErrorStatus.valueOf(status));
        } else {
            errors = problemService.getKnownErrors(null);
        }
        return ResponseEntity.ok(errors.stream().map(KnownErrorDto::fromEntity).toList());
    }

    @GetMapping("/known-errors/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<List<KnownErrorDto>> searchKnownErrors(@RequestParam String q, HttpServletRequest request) {
        try {
            String rawQueryString = request.getQueryString();
            log.info("Raw query string: {}", rawQueryString);
            
            String keyword;
            if (rawQueryString != null && rawQueryString.startsWith("q=")) {
                String encoded = rawQueryString.substring(2);
                log.info("Encoded value: {}", encoded);
                keyword = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
                log.info("Decoded keyword: {} (expected: lỗi)", keyword);
            } else {
                keyword = q;
            }
            
            log.info("Search known errors with keyword: '{}' (length: {})", keyword, keyword.length());
            
            List<KnownError> errors = problemService.searchKnownErrors(keyword);
            log.info("Found {} known errors. Titles: {}", errors.size(), 
                errors.stream().map(KnownError::getTitle).toList());
            return ResponseEntity.ok(errors.stream().map(KnownErrorDto::fromEntity).toList());
        } catch (Exception e) {
            log.error("Error searching known errors: {}", e.getMessage(), e);
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/known-errors/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<KnownErrorDto> getKnownErrorById(@PathVariable Long id) {
        KnownError error = problemService.getKnownErrorById(id);
        return ResponseEntity.ok(KnownErrorDto.fromEntity(error));
    }

    @PostMapping("/known-errors")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<KnownErrorDto> createKnownError(@RequestBody KnownErrorRequest request) {
        KnownError error = new KnownError();
        error.setTitle(request.getTitle());
        error.setDescription(request.getDescription());
        error.setSymptoms(request.getSymptoms());
        error.setRootCause(request.getRootCause());
        error.setWorkaround(request.getWorkaround());
        error.setFixSteps(request.getFixSteps());
        error.setStatus(KnownError.KnownErrorStatus.ACTIVE);

        KnownError created = problemService.createKnownError(error, "admin");
        return ResponseEntity.status(HttpStatus.CREATED).body(KnownErrorDto.fromEntity(created));
    }

    @PutMapping("/known-errors/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<KnownErrorDto> updateKnownError(@PathVariable Long id, @RequestBody KnownErrorRequest request) {
        KnownError updates = new KnownError();
        updates.setTitle(request.getTitle());
        updates.setDescription(request.getDescription());
        updates.setSymptoms(request.getSymptoms());
        updates.setRootCause(request.getRootCause());
        updates.setWorkaround(request.getWorkaround());
        updates.setFixSteps(request.getFixSteps());
        updates.setStatus(request.getStatus());

        KnownError updated = problemService.updateKnownError(id, updates, "admin");
        return ResponseEntity.ok(KnownErrorDto.fromEntity(updated));
    }

    @DeleteMapping("/known-errors/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG')")
    public ResponseEntity<Void> deleteKnownError(@PathVariable Long id) {
        problemService.deleteKnownError(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/known-errors/{id}/workaround")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<KnownErrorDto> addWorkaround(@PathVariable Long id, @RequestBody WorkaroundRequest request) {
        KnownError updated = problemService.addWorkaround(id, request.getWorkaround(), request.getAuthor(), "admin");
        return ResponseEntity.ok(KnownErrorDto.fromEntity(updated));
    }

    // ==================== Create Problem from Incident ====================

    @PostMapping("/from-incident/{incidentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<ProblemDto> createProblemFromIncident(
            @PathVariable Long incidentId,
            @RequestBody(required = false) ProblemFromIncidentRequest request) {
        Problem problem = problemService.createProblemFromIncident(incidentId,
                request != null ? request.getTitle() : null,
                request != null ? request.getDescription() : null,
                "admin");
        return ResponseEntity.status(HttpStatus.CREATED).body(ProblemDto.fromEntity(problem));
    }

    // ==================== Notes ====================

    @GetMapping("/{id}/notes")
    public ResponseEntity<List<NoteDto>> getNotes(@PathVariable Long id) {
        List<ProblemWorkaroundNote> notes = problemService.getNotes(id);
        return ResponseEntity.ok(notes.stream().map(NoteDto::fromEntity).toList());
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<NoteDto> addNote(@PathVariable Long id, @RequestBody NoteRequest request) {
        ProblemWorkaroundNote.NoteType noteType = ProblemWorkaroundNote.NoteType.WORKAROUND;
        if (request.getNoteType() != null) {
            noteType = ProblemWorkaroundNote.NoteType.valueOf(request.getNoteType());
        }
        ProblemWorkaroundNote note = problemService.addNote(id, request.getBody(), request.getAuthorName(),
                request.getAuthorUsername(), noteType, request.getEffectivenessRating());
        return ResponseEntity.status(HttpStatus.CREATED).body(NoteDto.fromEntity(note));
    }

    // ==================== Exception Handlers ====================

    @ExceptionHandler(ProblemService.ProblemNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleProblemNotFound(ProblemService.ProblemNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ProblemService.KnownErrorNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleKnownErrorNotFound(ProblemService.KnownErrorNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("CONFLICT", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        log.error("Bad request: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        log.error("Internal server error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR", "Đã xảy ra lỗi nội bộ: " + ex.getMessage()));
    }

    // ==================== DTOs ====================

    public static class ErrorResponse {
        private String code;
        private String message;
        public ErrorResponse(String code, String message) { this.code = code; this.message = message; }
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
        private String workaround;
        private String resolution;
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getCategory() { return category; }
        public Problem.Priority getPriority() { return priority; }
        public Problem.ImpactLevel getImpactLevel() { return impactLevel; }
        public String getRootCause() { return rootCause; }
        public String getRootCauseCategory() { return rootCauseCategory; }
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
        private String symptoms;
        private String rootCause;
        private String workaround;
        private String fixSteps;
        private KnownError.KnownErrorStatus status;
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getSymptoms() { return symptoms; }
        public String getRootCause() { return rootCause; }
        public String getWorkaround() { return workaround; }
        public String getFixSteps() { return fixSteps; }
        public KnownError.KnownErrorStatus getStatus() { return status; }
    }

    public static class WorkaroundRequest {
        private String workaround;
        private String author;
        public String getWorkaround() { return workaround; }
        public String getAuthor() { return author; }
    }

    public static class ProblemFromIncidentRequest {
        private String title;
        private String description;
        public String getTitle() { return title; }
        public String getDescription() { return description; }
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
        private String workaround;
        private String resolution;
        private Integer totalIncidentsLinked;
        private String assignedTo;
        private java.time.LocalDateTime resolutionDate;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;

        public static ProblemDto fromEntity(Problem p) {
            ProblemDto dto = new ProblemDto();
            dto.id = p.getId();
            dto.problemNumber = p.getProblemNumber();
            dto.title = p.getTitle();
            dto.description = p.getDescription();
            dto.category = p.getCategory();
            dto.impactLevel = p.getImpactLevel() != null ? p.getImpactLevel().name() : null;
            dto.priority = p.getPriority() != null ? p.getPriority().name() : null;
            dto.status = p.getStatus() != null ? p.getStatus().name() : null;
            dto.statusLabel = p.getStatus() != null ? p.getStatus().getLabel() : null;
            dto.rootCause = p.getRootCause();
            dto.workaround = p.getWorkaround();
            dto.resolution = p.getResolution();
            dto.totalIncidentsLinked = p.getTotalIncidentsLinked();
            dto.assignedTo = p.getAssignedTo();
            dto.resolutionDate = p.getResolutionDate();
            dto.createdAt = p.getCreatedAt();
            dto.updatedAt = p.getUpdatedAt();
            return dto;
        }

        public Long getId() { return id; }
        public String getProblemNumber() { return problemNumber; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getCategory() { return category; }
        public String getImpactLevel() { return impactLevel; }
        public String getPriority() { return priority; }
        public String getStatus() { return status; }
        public String getStatusLabel() { return statusLabel; }
        public String getRootCause() { return rootCause; }
        public String getWorkaround() { return workaround; }
        public String getResolution() { return resolution; }
        public Integer getTotalIncidentsLinked() { return totalIncidentsLinked; }
        public String getAssignedTo() { return assignedTo; }
        public java.time.LocalDateTime getResolutionDate() { return resolutionDate; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    }

    public static class KnownErrorDto {
        private Long id;
        private String errorCode;
        private String title;
        private String description;
        private String symptoms;
        private String rootCause;
        private String workaround;
        private String fixSteps;
        private String status;
        private String statusLabel;
        private String managedBy;
        private Integer occurrenceCount;
        private java.time.LocalDateTime createdAt;

        public static KnownErrorDto fromEntity(KnownError ke) {
            KnownErrorDto dto = new KnownErrorDto();
            dto.id = ke.getId();
            dto.errorCode = ke.getErrorCode();
            dto.title = ke.getTitle();
            dto.description = ke.getDescription();
            dto.symptoms = ke.getSymptoms();
            dto.rootCause = ke.getRootCause();
            dto.workaround = ke.getWorkaround();
            dto.fixSteps = ke.getFixSteps();
            dto.status = ke.getStatus() != null ? ke.getStatus().name() : null;
            dto.statusLabel = ke.getStatus() != null ? ke.getStatus().getLabel() : null;
            dto.managedBy = ke.getManagedBy();
            dto.occurrenceCount = ke.getOccurrenceCount();
            dto.createdAt = ke.getCreatedAt();
            return dto;
        }

        public Long getId() { return id; }
        public String getErrorCode() { return errorCode; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getSymptoms() { return symptoms; }
        public String getRootCause() { return rootCause; }
        public String getWorkaround() { return workaround; }
        public String getFixSteps() { return fixSteps; }
        public String getStatus() { return status; }
        public String getStatusLabel() { return statusLabel; }
        public String getManagedBy() { return managedBy; }
        public Integer getOccurrenceCount() { return occurrenceCount; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    }

    public static class IncidentLinkDto {
        private Long id;
        private Long incidentId;
        private String linkedBy;
        private java.time.LocalDateTime linkedAt;

        public static IncidentLinkDto fromEntity(ProblemIncidentLink link) {
            IncidentLinkDto dto = new IncidentLinkDto();
            dto.id = link.getId();
            dto.incidentId = link.getIncidentId();
            dto.linkedBy = link.getLinkedBy();
            dto.linkedAt = link.getLinkedAt();
            return dto;
        }

        public Long getId() { return id; }
        public Long getIncidentId() { return incidentId; }
        public String getLinkedBy() { return linkedBy; }
        public java.time.LocalDateTime getLinkedAt() { return linkedAt; }
    }

    public static class NoteDto {
        private Long id;
        private String body;
        private String authorName;
        private String noteType;
        private Integer effectivenessRating;
        private java.time.LocalDateTime createdAt;

        public static NoteDto fromEntity(ProblemWorkaroundNote note) {
            NoteDto dto = new NoteDto();
            dto.id = note.getId();
            dto.body = note.getBody();
            dto.authorName = note.getAuthorName();
            dto.noteType = note.getNoteType() != null ? note.getNoteType().name() : null;
            dto.effectivenessRating = note.getEffectivenessRating();
            dto.createdAt = note.getCreatedAt();
            return dto;
        }

        public Long getId() { return id; }
        public String getBody() { return body; }
        public String getAuthorName() { return authorName; }
        public String getNoteType() { return noteType; }
        public Integer getEffectivenessRating() { return effectivenessRating; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    }
}

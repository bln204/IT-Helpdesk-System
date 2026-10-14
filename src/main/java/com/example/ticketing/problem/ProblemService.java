package com.example.ticketing.problem;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.incident.IncidentRepository;

/**
 * Service cho Problem Management.
 */
@Service
@Transactional
public class ProblemService {

    private static final Logger log = LoggerFactory.getLogger(ProblemService.class);

    private final ProblemRepository problemRepository;
    private final KnownErrorRepository knownErrorRepository;
    private final ProblemIncidentLinkRepository linkRepository;
    private final ProblemWorkaroundNoteRepository noteRepository;
    private final IncidentRepository incidentRepository;
    /**
     * Phase 6.6.4 - may be {@code null} when the AI infrastructure is
     * disabled (e.g. test profile that does not wire the bean). When
     * present, KnownError create / update operations enqueue a PENDING
     * embedding row so the AI self-service pipeline can pick the
     * knowledge up. The call swallows all exceptions: AI failures must
     * never block the business workflow.
     */
    private final KnownErrorEmbeddingService knownErrorEmbeddingService;

    @org.springframework.beans.factory.annotation.Autowired
    public ProblemService(
            ProblemRepository problemRepository,
            KnownErrorRepository knownErrorRepository,
            ProblemIncidentLinkRepository linkRepository,
            ProblemWorkaroundNoteRepository noteRepository,
            IncidentRepository incidentRepository) {
        this.problemRepository = problemRepository;
        this.knownErrorRepository = knownErrorRepository;
        this.linkRepository = linkRepository;
        this.noteRepository = noteRepository;
        this.incidentRepository = incidentRepository;
        this.knownErrorEmbeddingService = null;
    }

    public ProblemService(
            ProblemRepository problemRepository,
            KnownErrorRepository knownErrorRepository,
            ProblemIncidentLinkRepository linkRepository,
            ProblemWorkaroundNoteRepository noteRepository,
            IncidentRepository incidentRepository,
            KnownErrorEmbeddingService knownErrorEmbeddingService) {
        this.problemRepository = problemRepository;
        this.knownErrorRepository = knownErrorRepository;
        this.linkRepository = linkRepository;
        this.noteRepository = noteRepository;
        this.incidentRepository = incidentRepository;
        this.knownErrorEmbeddingService = knownErrorEmbeddingService;
    }

    // ==================== Problem CRUD ====================

    /**
     * Tạo problem mới.
     */
    public Problem createProblem(Problem problem, String createdBy) {
        log.info("Creating problem: {}", problem.getTitle());
        
        problem.setProblemNumber(generateProblemNumber());
        problem.setCreatedBy(createdBy);
        problem.setStatus(Problem.ProblemStatus.NEW);
        
        return problemRepository.save(problem);
    }

    /**
     * Lấy problem theo ID.
     */
    @Transactional(readOnly = true)
    public Problem getProblemById(Long id) {
        return problemRepository.findById(id)
                .orElseThrow(() -> new ProblemNotFoundException(id));
    }

    /**
     * Lấy problem theo số.
     */
    @Transactional(readOnly = true)
    public Problem getProblemByNumber(String number) {
        return problemRepository.findByProblemNumber(number)
                .orElseThrow(() -> new ProblemNotFoundException(number));
    }

    /**
     * Cập nhật problem.
     */
    public Problem updateProblem(Long id, Problem updates, String updatedBy) {
        Problem existing = getProblemById(id);
        
        existing.setTitle(updates.getTitle());
        existing.setDescription(updates.getDescription());
        existing.setCategory(updates.getCategory());
        existing.setPriority(updates.getPriority());
        existing.setImpactLevel(updates.getImpactLevel());
        existing.setRootCause(updates.getRootCause());
        existing.setRootCauseCategory(updates.getRootCauseCategory());
        existing.setRootCauseConfidence(updates.getRootCauseConfidence());
        existing.setWorkaround(updates.getWorkaround());
        existing.setResolution(updates.getResolution());
        existing.setAssignedTo(updates.getAssignedTo());
        existing.setUpdatedBy(updatedBy);
        
        return problemRepository.save(existing);
    }

    /**
     * Xóa problem.
     */
    public void deleteProblem(Long id) {
        log.info("Deleting problem: {}", id);
        problemRepository.deleteById(id);
    }

    // ==================== Status Management ====================

    /**
     * Cập nhật trạng thái problem.
     */
    public Problem updateStatus(Long id, Problem.ProblemStatus newStatus, String actorName) {
        Problem problem = getProblemById(id);
        Problem.ProblemStatus oldStatus = problem.getStatus();
        
        problem.setStatus(newStatus);
        problem.setUpdatedBy(actorName);
        
        if (newStatus == Problem.ProblemStatus.RESOLVED) {
            problem.setResolutionDate(LocalDateTime.now());
        }
        
        if (newStatus == Problem.ProblemStatus.CLOSED) {
            problem.setClosedBy(actorName);
            problem.setClosedAt(LocalDateTime.now());
        }
        
        log.info("Problem {} status changed from {} to {}", problem.getProblemNumber(), oldStatus, newStatus);
        
        return problemRepository.save(problem);
    }

    /**
     * Gán problem cho người xử lý.
     */
    public Problem assignProblem(Long id, String assignee, String actorName) {
        Problem problem = getProblemById(id);
        problem.setAssignedTo(assignee);
        problem.setUpdatedBy(actorName);
        
        // Auto-update status if currently NEW
        if (problem.getStatus() == Problem.ProblemStatus.NEW) {
            problem.setStatus(Problem.ProblemStatus.INVESTIGATING);
        }
        
        return problemRepository.save(problem);
    }

    // ==================== Incident Linking ====================

    /**
     * Liên kết incident với problem.
     */
    public ProblemIncidentLink linkIncident(Long problemId, Long incidentId, String linkedBy) {
        Problem problem = getProblemById(problemId);
        
        // Check if already linked
        if (linkRepository.findByProblemIdAndIncidentId(problemId, incidentId).isPresent()) {
            throw new IllegalStateException("Incident đã được liên kết với problem này");
        }
        
        ProblemIncidentLink link = new ProblemIncidentLink(problem, incidentId);
        link.setLinkedBy(linkedBy);
        
        // Update incident count
        problem.setTotalIncidentsLinked(problem.getTotalIncidentsLinked() + 1);
        problemRepository.save(problem);
        
        return linkRepository.save(link);
    }

    /**
     * Hủy liên kết incident.
     */
    public void unlinkIncident(Long problemId, Long incidentId) {
        linkRepository.findByProblemIdAndIncidentId(problemId, incidentId)
                .ifPresent(link -> {
                    linkRepository.delete(link);
                    
                    // Update count
                    Problem problem = getProblemById(problemId);
                    problem.setTotalIncidentsLinked(Math.max(0, problem.getTotalIncidentsLinked() - 1));
                    problemRepository.save(problem);
                });
    }

    /**
     * Lấy incidents liên quan.
     */
    @Transactional(readOnly = true)
    public List<ProblemIncidentLink> getLinkedIncidents(Long problemId) {
        return linkRepository.findByProblemId(problemId);
    }

    // ==================== Known Errors ====================

    /**
     * Lấy known errors.
     */
    @Transactional(readOnly = true)
    public List<KnownError> getKnownErrors(KnownError.KnownErrorStatus status) {
        if (status != null) {
            return knownErrorRepository.findByStatus(status);
        }
        return knownErrorRepository.findAll();
    }

    /**
     * Lấy known error theo ID.
     */
    @Transactional(readOnly = true)
    public KnownError getKnownErrorById(Long id) {
        return knownErrorRepository.findById(id)
                .orElseThrow(() -> new KnownErrorNotFoundException(id));
    }

    /**
     * Tạo known error.
     *
     * <p>PHASE 6.6.3 - this is the legacy "create detached KnownError" path.
     * Prefer {@link #createKnownErrorForProblem(Long, KnownError, String)}
     * when a Problem is the source of the knowledge, so the relationship
     * is created atomically and {@code related_problems_count} is updated
     * in the same transaction.
     */
    public KnownError createKnownError(KnownError knownError, String createdBy) {
        log.info("Creating known error: {}", knownError.getTitle());
        knownError.setErrorCode(generateErrorCode());
        knownError.setCreatedBy(createdBy);
        KnownError saved = knownErrorRepository.save(knownError);
        enqueueKnownErrorEmbedding(saved.getId());
        return saved;
    }

    // ============================================================
    // PHASE 6.6.4 - Enqueue embedding (best effort, never throws)
    // ============================================================

    /**
     * Enqueue a PENDING embedding for a Known Error. The actual Ollama
     * call is performed asynchronously by the scheduler so the current
     * transaction is not held open across an external IO.
     *
     * <p>When the AI infrastructure is not wired (e.g. in a non-AI
     * Spring profile) this is a no-op. Failures are logged and swallowed
     * so the business workflow is never blocked by AI problems.
     */
    private void enqueueKnownErrorEmbedding(Long knownErrorId) {
        if (knownErrorEmbeddingService == null || knownErrorId == null) {
            return;
        }
        try {
            knownErrorEmbeddingService.createEmbedding(knownErrorId);
        } catch (Exception e) {
            log.warn("Failed to enqueue KnownError embedding for id={}: {}",
                    knownErrorId, e.getMessage());
        }
    }

    /**
     * Enqueue a re-embedding. Used after semantic field updates.
     */
    private void enqueueKnownErrorReembed(Long knownErrorId) {
        if (knownErrorEmbeddingService == null || knownErrorId == null) {
            return;
        }
        try {
            knownErrorEmbeddingService.reembedKnownError(knownErrorId);
        } catch (Exception e) {
            log.warn("Failed to re-embed KnownError id={}: {}", knownErrorId, e.getMessage());
        }
    }

    // ============================================================
    // PHASE 6.6.3 - Problem <-> Known Error relationship
    // ============================================================

    /**
     * Atomic business operation: create a new {@link KnownError} and link it
     * to the given {@link Problem}. The new KE is created with status
     * {@code ACTIVE}; the Problem status is left untouched. The whole
     * operation runs in the surrounding service transaction so a failure
     * mid-flight will roll back both the new row and the FK assignment.
     *
     * <p>Authorization is enforced at the controller layer
     * ({@code ADMIN / GIAM_DOC / TRUONG_PHONG}); this method assumes the
     * caller is allowed.
     *
     * @param problemId  id of the Problem to link
     * @param payload    payload fields for the new KnownError
     * @param createdBy  actor username for audit fields
     * @return the freshly created KnownError
     * @throws ProblemNotFoundException       if the problem does not exist
     * @throws IllegalStateException          if the problem is closed and cannot accept a KE
     */
    public KnownError createKnownErrorForProblem(Long problemId, KnownError payload, String createdBy) {
        log.info("createKnownErrorForProblem: problemId={}, createdBy={}", problemId, createdBy);
        Problem problem = getProblemById(problemId);
        rejectIfProblemClosedForKeLink(problem, "create");

        // If the problem already points at a KE we treat this as a change-link:
        // the old KE loses one reference, the new one gains one. This keeps
        // the count consistent regardless of which path the user took.
        Long previousKeId = problem.getKnownErrorId();

        KnownError ke = new KnownError();
        ke.setTitle(payload.getTitle());
        ke.setDescription(payload.getDescription());
        ke.setSymptoms(payload.getSymptoms());
        ke.setRootCause(payload.getRootCause());
        ke.setRootCauseCategory(payload.getRootCauseCategory());
        ke.setWorkaround(payload.getWorkaround());
        ke.setResolution(payload.getResolution());
        ke.setFixSteps(payload.getFixSteps());
        ke.setImpactDescription(payload.getImpactDescription());
        ke.setAffectedSystems(payload.getAffectedSystems());
        ke.setManagedBy(createdBy);
        ke.setCreatedBy(createdBy);
        ke.setStatus(KnownError.KnownErrorStatus.ACTIVE);
        ke.setErrorCode(generateErrorCode());

        KnownError saved = knownErrorRepository.save(ke);

        // Wire up the relationship. applyKeLink does the count arithmetic.
        applyKeLink(problem, previousKeId, saved.getId(), createdBy);

        // PHASE 6.6.4 - enqueue embedding for the new KE.
        enqueueKnownErrorEmbedding(saved.getId());

        return knownErrorRepository.findById(saved.getId()).orElse(saved);
    }

    /**
     * Atomic business operation: link an existing {@link KnownError} to a
     * {@link Problem}. Idempotent: re-linking to the same KE is a no-op
     * (no count change). Linking to a different KE moves the count from
     * the old KE to the new one.
     *
     * <p>Authorization is enforced at the controller layer.
     *
     * @throws ProblemNotFoundException   if the problem does not exist
     * @throws KnownErrorNotFoundException if the known error does not exist
     * @throws IllegalStateException       if the known error is {@code OBSOLETE}
     *                                    or the problem is closed
     */
    public KnownError linkExistingKnownError(Long problemId, Long knownErrorId, String actorName) {
        log.info("linkExistingKnownError: problemId={}, knownErrorId={}, actor={}",
                problemId, knownErrorId, actorName);

        Problem problem = getProblemById(problemId);
        rejectIfProblemClosedForKeLink(problem, "link");

        KnownError ke = getKnownErrorById(knownErrorId);
        if (ke.getStatus() == KnownError.KnownErrorStatus.OBSOLETE) {
            throw new IllegalStateException(
                    "Known Error " + ke.getErrorCode() + " is OBSOLETE and cannot be linked");
        }

        Long previousKeId = problem.getKnownErrorId();
        if (previousKeId != null && previousKeId.equals(knownErrorId)) {
            // Idempotent: same KE re-linked.
            log.info("linkExistingKnownError: no-op (already linked)");
            return ke;
        }

        applyKeLink(problem, previousKeId, knownErrorId, actorName);
        return knownErrorRepository.findById(knownErrorId).orElse(ke);
    }

    /**
     * Atomic business operation: unlink the {@link KnownError} from a
     * {@link Problem} and decrement {@code related_problems_count} on the
     * affected KE. Idempotent: unlinking when there is no link is a no-op.
     */
    public void unlinkKnownError(Long problemId, String actorName) {
        log.info("unlinkKnownError: problemId={}, actor={}", problemId, actorName);
        Problem problem = getProblemById(problemId);
        Long previousKeId = problem.getKnownErrorId();
        if (previousKeId == null) {
            log.info("unlinkKnownError: no-op (problem has no linked KE)");
            return;
        }
        applyKeLink(problem, previousKeId, null, actorName);
    }

    /**
     * Internal chokepoint that updates {@code Problem.knownErrorId} and the
     * {@code related_problems_count} of both old and new KEs in one
     * transaction. This is the single place that mutates the relationship
     * and the count - no other method in the service touches these fields.
     *
     * <p>All three write operations (problem save, old KE save, new KE save)
     * share the surrounding transaction. The Problem row is flushed last so
     * an FK violation surfaces as a real exception, not a deferred failure.
     *
     * @param problem      the Problem entity loaded inside the transaction
     * @param previousKeId id of the previously linked KE, or null
     * @param newKeId      id of the newly linked KE, or null to unlink
     * @param actorName    username for audit trail
     */
    private void applyKeLink(Problem problem, Long previousKeId, Long newKeId, String actorName) {
        // Step 1: decrement the count of the old KE (if any).
        if (previousKeId != null && (newKeId == null || !previousKeId.equals(newKeId))) {
            knownErrorRepository.findById(previousKeId).ifPresent(oldKe -> {
                int oldCount = oldKe.getRelatedProblemsCount() == null ? 0 : oldKe.getRelatedProblemsCount();
                int next = Math.max(0, oldCount - 1);
                oldKe.setRelatedProblemsCount(next);
                oldKe.setUpdatedBy(actorName);
                knownErrorRepository.save(oldKe);
            });
        }

        // Step 2: increment the count of the new KE (if linking).
        if (newKeId != null && (previousKeId == null || !previousKeId.equals(newKeId))) {
            knownErrorRepository.findById(newKeId).ifPresent(newKe -> {
                int oldCount = newKe.getRelatedProblemsCount() == null ? 0 : newKe.getRelatedProblemsCount();
                newKe.setRelatedProblemsCount(oldCount + 1);
                newKe.setUpdatedBy(actorName);
                knownErrorRepository.save(newKe);
            });
        }

        // Step 3: update Problem.knownErrorId. Flushed last so FK errors surface here.
        problem.setKnownErrorId(newKeId);
        problem.setUpdatedBy(actorName);
        problemRepository.saveAndFlush(problem);
    }

    /**
     * Read all Problems currently linked to a Known Error.
     */
    @Transactional(readOnly = true)
    public List<Problem> getProblemsForKnownError(Long knownErrorId) {
        return problemRepository.findByKnownErrorId(knownErrorId);
    }

    /**
     * Defensive: recompute {@code related_problems_count} for a Known Error
     * from the relational source of truth. Exposed for tests and for any
     * future reconciliation job. Not part of the happy path.
     */
    @Transactional
    public int recomputeRelatedProblemsCount(Long knownErrorId) {
        long actual = problemRepository.countByKnownErrorId(knownErrorId);
        KnownError ke = getKnownErrorById(knownErrorId);
        int stored = ke.getRelatedProblemsCount() == null ? 0 : ke.getRelatedProblemsCount();
        int target = (int) actual;
        if (target != stored) {
            log.warn("recomputeRelatedProblemsCount: drift detected ke={}, stored={}, actual={}",
                    ke.getErrorCode(), stored, target);
            ke.setRelatedProblemsCount(target);
            knownErrorRepository.save(ke);
        }
        return target;
    }

    /**
     * Guard rail: closing a Problem is a final action. Promoting / linking a
     * KE after that point would distort the relationship history. Allow
     * linking only while the Problem is still active.
     */
    private void rejectIfProblemClosedForKeLink(Problem problem, String operation) {
        Problem.ProblemStatus status = problem.getStatus();
        if (status == Problem.ProblemStatus.CLOSED) {
            throw new IllegalStateException(
                    "Cannot " + operation + " Known Error for a CLOSED Problem ("
                            + problem.getProblemNumber() + ")");
        }
    }

    /**
     * Cập nhật known error.
     */
    public KnownError updateKnownError(Long id, KnownError updates, String updatedBy) {
        KnownError existing = getKnownErrorById(id);

        // PHASE 6.6.4 - snapshot the fields that drive the semantic text
        // BEFORE we apply updates, so we can decide whether re-embedding
        // is required (status-only changes must NOT trigger a re-embed).
        boolean semanticChanged = isSemanticFieldChanged(existing, updates);

        existing.setTitle(updates.getTitle());
        existing.setDescription(updates.getDescription());
        existing.setSymptoms(updates.getSymptoms());
        existing.setRootCause(updates.getRootCause());
        existing.setWorkaround(updates.getWorkaround());
        existing.setResolution(updates.getResolution());
        existing.setFixSteps(updates.getFixSteps());
        existing.setStatus(updates.getStatus());
        existing.setManagedBy(updates.getManagedBy());
        existing.setUpdatedBy(updatedBy);

        KnownError saved = knownErrorRepository.save(existing);

        if (semanticChanged) {
            enqueueKnownErrorReembed(id);
        }
        return saved;
    }

    /**
     * PHASE 6.6.4 - decide whether a KnownError update touches the
     * semantic text. Status / relatedProblemsCount / timestamps / actor
     * fields do NOT count; everything that appears in
     * {@link KnownErrorEmbeddingTextBuilder} does.
     */
    private boolean isSemanticFieldChanged(KnownError before, KnownError after) {
        return !java.util.Objects.equals(before.getTitle(), after.getTitle())
                || !java.util.Objects.equals(before.getDescription(), after.getDescription())
                || !java.util.Objects.equals(before.getSymptoms(), after.getSymptoms())
                || !java.util.Objects.equals(before.getRootCause(), after.getRootCause())
                || !java.util.Objects.equals(before.getRootCauseCategory(), after.getRootCauseCategory())
                || !java.util.Objects.equals(before.getWorkaround(), after.getWorkaround())
                || !java.util.Objects.equals(before.getResolution(), after.getResolution())
                || !java.util.Objects.equals(before.getFixSteps(), after.getFixSteps())
                || !java.util.Objects.equals(before.getImpactDescription(), after.getImpactDescription())
                || !java.util.Objects.equals(before.getAffectedSystems(), after.getAffectedSystems());
    }

    /**
     * Xóa known error.
     */
    public void deleteKnownError(Long id) {
        log.info("Deleting known error: {}", id);
        knownErrorRepository.deleteById(id);
    }

    /**
     * Tăng occurrence count.
     */
    public void incrementOccurrence(Long knownErrorId) {
        knownErrorRepository.findById(knownErrorId).ifPresent(ke -> {
            ke.setOccurrenceCount(ke.getOccurrenceCount() + 1);
            ke.setLastOccurrenceAt(LocalDateTime.now());
            knownErrorRepository.save(ke);
        });
    }

    // ==================== Notes/Workarounds ====================

    /**
     * Thêm note/workaround.
     */
    public ProblemWorkaroundNote addNote(Long problemId, String body, String authorName, 
                                        String authorUsername, ProblemWorkaroundNote.NoteType noteType,
                                        Integer effectivenessRating) {
        Problem problem = getProblemById(problemId);
        
        ProblemWorkaroundNote note = new ProblemWorkaroundNote(problem, authorName, authorUsername, body);
        note.setNoteType(noteType);
        note.setEffectivenessRating(effectivenessRating);
        
        return noteRepository.save(note);
    }

    /**
     * Lấy notes của problem.
     */
    @Transactional(readOnly = true)
    public List<ProblemWorkaroundNote> getNotes(Long problemId) {
        return noteRepository.findByProblemIdOrderByCreatedAtDesc(problemId);
    }

    // ==================== Query Methods ====================

    /**
     * Lấy tất cả problems.
     */
    @Transactional(readOnly = true)
    public Page<Problem> getAllProblems(Pageable pageable) {
        return problemRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * Tìm kiếm problems.
     */
    @Transactional(readOnly = true)
    public Page<Problem> searchProblems(String search, Pageable pageable) {
        return problemRepository.searchProblems(search, pageable);
    }

    /**
     * Lấy problems theo status.
     */
    @Transactional(readOnly = true)
    public Page<Problem> getProblemsByStatus(Problem.ProblemStatus status, Pageable pageable) {
        return problemRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    /**
     * Lấy active problems.
     */
    @Transactional(readOnly = true)
    public List<Problem> getActiveProblems() {
        return problemRepository.findByStatusIn(List.of(
                Problem.ProblemStatus.NEW,
                Problem.ProblemStatus.INVESTIGATING,
                Problem.ProblemStatus.IDENTIFIED,
                Problem.ProblemStatus.SOLVING
        ));
    }

    /**
     * Tìm kiếm known errors.
     */
    @Transactional(readOnly = true)
    public List<KnownError> searchKnownErrors(String keyword) {
        log.info("searchKnownErrors called with keyword: '{}'", keyword);
        try {
            List<KnownError> result = knownErrorRepository.findByKeyword(keyword);
            log.info("findByKeyword returned {} results", result.size());
            return result;
        } catch (Exception e) {
            log.error("Error in findByKeyword: {}", e.getMessage(), e);
            // Fallback: return empty list instead of all records
            return List.of();
        }
    }

    /**
     * Thêm workaround vào known error.
     */
    public KnownError addWorkaround(Long knownErrorId, String workaround, String authorName, String updatedBy) {
        KnownError knownError = getKnownErrorById(knownErrorId);
        knownError.setWorkaround(workaround);
        knownError.setManagedBy(authorName);
        knownError.setUpdatedBy(updatedBy);
        return knownErrorRepository.save(knownError);
    }

    /**
     * Tạo problem từ incident đã resolve.
     */
    public Problem createProblemFromIncident(Long incidentId, String title, String description, String createdBy) {
        log.info("Creating problem from incident: {}", incidentId);
        
        com.example.ticketing.incident.Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));
        
        log.info("Found incident: {}", incident.getIncidentNumber());
        
        Problem problem = new Problem();
        problem.setProblemNumber(generateProblemNumber());
        
        // Auto-fill from incident if not provided
        if (title != null && !title.isBlank()) {
            problem.setTitle(title);
        } else {
            problem.setTitle("[Tự động] Vấn đề từ " +incident.getIncidentNumber());
        }
        
        if (description != null && !description.isBlank()) {
            problem.setDescription(description);
        } else {
            StringBuilder desc = new StringBuilder();
            desc.append("Created from Incident: ").append(incident.getIncidentNumber()).append("\n\n");
            desc.append("Original Issue: ").append(incident.getTitle()).append("\n");
            if (incident.getDescription() != null) {
                desc.append("Description: ").append(incident.getDescription()).append("\n");
            }
            if (incident.getRootCause() != null) {
                desc.append("Root Cause: ").append(incident.getRootCause()).append("\n");
            }
            if (incident.getResolution() != null) {
                desc.append("Resolution: ").append(incident.getResolution());
            }
            problem.setDescription(desc.toString());
        }
        
        problem.setStatus(Problem.ProblemStatus.NEW);
        problem.setCreatedBy(createdBy);
        
        Problem saved = problemRepository.save(problem);
        log.info("Problem saved with ID: {}", saved.getId());
        
        // Auto-link incident
        linkIncident(saved.getId(), incidentId, createdBy);
        
        return saved;
    }

    // ==================== Helper Methods ====================

    private String generateProblemNumber() {
        String uuid = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "PRB-" + uuid;
    }

    private String generateErrorCode() {
        String uuid = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "KE-" + uuid;
    }

    // ==================== Exceptions ====================

    public static class ProblemNotFoundException extends RuntimeException {
        public ProblemNotFoundException(Long id) {
            super("Không tìm thấy Problem với ID: " + id);
        }
        public ProblemNotFoundException(String number) {
            super("Không tìm thấy Problem với số: " + number);
        }
    }

    public static class KnownErrorNotFoundException extends RuntimeException {
        public KnownErrorNotFoundException(Long id) {
            super("Không tìm thấy Known Error với ID: " + id);
        }
    }
}

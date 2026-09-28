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
     */
    public KnownError createKnownError(KnownError knownError, String createdBy) {
        log.info("Creating known error: {}", knownError.getTitle());
        knownError.setErrorCode(generateErrorCode());
        knownError.setCreatedBy(createdBy);
        return knownErrorRepository.save(knownError);
    }

    /**
     * Cập nhật known error.
     */
    public KnownError updateKnownError(Long id, KnownError updates, String updatedBy) {
        KnownError existing = getKnownErrorById(id);
        
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
        
        return knownErrorRepository.save(existing);
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

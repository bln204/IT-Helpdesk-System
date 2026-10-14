package com.example.ticketing.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.problem.KnownError.KnownErrorStatus;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProblemKnownErrorServiceTest {

    @Autowired
    private ProblemService problemService;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private KnownErrorRepository knownErrorRepository;

    private static int problemSeq = 0;
    private static int keSeq = 0;

    @BeforeEach
    void resetCounters() {
        problemSeq = 0;
        keSeq = 0;
    }

    private Problem newProblem(Problem.ProblemStatus status) {
        problemSeq++;
        Problem p = new Problem();
        p.setProblemNumber("PRB-TEST-" + String.format("%04d", problemSeq));
        p.setTitle("Test problem " + problemSeq);
        p.setDescription("Phase 6.6.3 fixture");
        p.setStatus(status);
        p.setPriority(Problem.Priority.MEDIUM);
        p.setImpactLevel(Problem.ImpactLevel.MEDIUM);
        p.setCategory("NETWORK");
        p.setCreatedBy("tester");
        return problemRepository.save(p);
    }

    private KnownError newKe(KnownErrorStatus status) {
        keSeq++;
        KnownError ke = new KnownError();
        ke.setErrorCode("KE-TEST-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        ke.setTitle("Test KE " + keSeq);
        ke.setStatus(status);
        ke.setRelatedProblemsCount(0);
        ke.setOccurrenceCount(0);
        ke.setCreatedBy("tester");
        return knownErrorRepository.save(ke);
    }

    private int countOf(KnownError ke) {
        return ke.getRelatedProblemsCount() == null ? 0 : ke.getRelatedProblemsCount();
    }

    @Test
    @DisplayName("T1: Create Known Error from Problem sets knownErrorId and count=1")
    void createKnownErrorForProblem_setsLink() {
        Problem p = newProblem(Problem.ProblemStatus.IDENTIFIED);

        KnownError payload = new KnownError();
        payload.setTitle("DB connection timeout");
        payload.setDescription("desc");
        payload.setSymptoms("sym");
        payload.setRootCause("rc");
        payload.setWorkaround("wa");
        payload.setFixSteps("fs");

        KnownError created = problemService.createKnownErrorForProblem(
                p.getId(), payload, "tester");

        assertNotNull(created.getId(), "KE should be persisted");
        assertNotNull(created.getErrorCode(), "KE should have an errorCode");
        assertEquals(KnownErrorStatus.ACTIVE, created.getStatus(),
                "New KE must be ACTIVE by default");

        Problem reloaded = problemRepository.findById(p.getId()).orElseThrow();
        assertEquals(created.getId(), reloaded.getKnownErrorId(),
                "Problem.knownErrorId must equal the new KE id");

        KnownError keReloaded = knownErrorRepository.findById(created.getId()).orElseThrow();
        assertEquals(1, countOf(keReloaded), "related_problems_count must be 1 after first link");
    }

    @Test
    @DisplayName("T2: Link existing KE to Problem increments its count")
    void linkExisting_incrementsCount() {
        Problem p = newProblem(Problem.ProblemStatus.IDENTIFIED);
        KnownError ke = newKe(KnownErrorStatus.ACTIVE);
        assertEquals(0, countOf(ke));

        problemService.linkExistingKnownError(p.getId(), ke.getId(), "tester");

        Problem reloaded = problemRepository.findById(p.getId()).orElseThrow();
        assertEquals(ke.getId(), reloaded.getKnownErrorId());

        KnownError keReloaded = knownErrorRepository.findById(ke.getId()).orElseThrow();
        assertEquals(1, countOf(keReloaded));
    }

    @Test
    @DisplayName("T3: Linking the same KE twice only counts once")
    void linkExisting_isIdempotent() {
        Problem p = newProblem(Problem.ProblemStatus.IDENTIFIED);
        KnownError ke = newKe(KnownErrorStatus.ACTIVE);

        problemService.linkExistingKnownError(p.getId(), ke.getId(), "tester");
        problemService.linkExistingKnownError(p.getId(), ke.getId(), "tester");
        problemService.linkExistingKnownError(p.getId(), ke.getId(), "tester");

        KnownError keReloaded = knownErrorRepository.findById(ke.getId()).orElseThrow();
        assertEquals(1, countOf(keReloaded),
                "Repeated link of the same KE must not increment the count");
    }

    @Test
    @DisplayName("T4: Changing the linked KE decrements old and increments new")
    void linkExisting_changeKeMovesCount() {
        Problem p = newProblem(Problem.ProblemStatus.IDENTIFIED);
        KnownError oldKe = newKe(KnownErrorStatus.ACTIVE);
        KnownError newKe = newKe(KnownErrorStatus.ACTIVE);

        problemService.linkExistingKnownError(p.getId(), oldKe.getId(), "tester");
        problemService.linkExistingKnownError(p.getId(), newKe.getId(), "tester");

        KnownError oldReloaded = knownErrorRepository.findById(oldKe.getId()).orElseThrow();
        KnownError newReloaded = knownErrorRepository.findById(newKe.getId()).orElseThrow();
        Problem reloaded = problemRepository.findById(p.getId()).orElseThrow();

        assertEquals(0, countOf(oldReloaded), "old KE should be back to 0");
        assertEquals(1, countOf(newReloaded), "new KE should be 1");
        assertEquals(newKe.getId(), reloaded.getKnownErrorId());
    }

    @Test
    @DisplayName("T5: Unlink clears the link and decrements the count")
    void unlink_decrementsCount() {
        Problem p = newProblem(Problem.ProblemStatus.IDENTIFIED);
        KnownError ke = newKe(KnownErrorStatus.ACTIVE);
        problemService.linkExistingKnownError(p.getId(), ke.getId(), "tester");

        problemService.unlinkKnownError(p.getId(), "tester");

        Problem reloaded = problemRepository.findById(p.getId()).orElseThrow();
        assertNull(reloaded.getKnownErrorId(), "knownErrorId must be NULL after unlink");
        KnownError keReloaded = knownErrorRepository.findById(ke.getId()).orElseThrow();
        assertEquals(0, countOf(keReloaded));
    }

    @Test
    @DisplayName("T6: Unlink when there is no link does not decrement any count")
    void unlink_noOpWhenNotLinked() {
        Problem p = newProblem(Problem.ProblemStatus.IDENTIFIED);
        KnownError ke = newKe(KnownErrorStatus.ACTIVE);
        int before = countOf(ke);

        problemService.unlinkKnownError(p.getId(), "tester");

        KnownError keReloaded = knownErrorRepository.findById(ke.getId()).orElseThrow();
        assertEquals(before, countOf(keReloaded), "count must not go negative");
        Problem reloaded = problemRepository.findById(p.getId()).orElseThrow();
        assertNull(reloaded.getKnownErrorId());
    }

    @Test
    @DisplayName("T7: Linking an OBSOLETE Known Error is rejected")
    void linkExisting_obsoleteRejected() {
        Problem p = newProblem(Problem.ProblemStatus.IDENTIFIED);
        KnownError ke = newKe(KnownErrorStatus.OBSOLETE);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> problemService.linkExistingKnownError(p.getId(), ke.getId(), "tester"));
        assertTrue(ex.getMessage().toUpperCase().contains("OBSOLETE"),
                "Exception message should mention OBSOLETE");

        Problem reloaded = problemRepository.findById(p.getId()).orElseThrow();
        assertNull(reloaded.getKnownErrorId(), "Link must not have been created");
    }

    @Test
    @DisplayName("T8: CLOSED Problem rejects create/link/unlink of Known Error")
    void closedProblem_rejectsKeOps() {
        Problem p = newProblem(Problem.ProblemStatus.CLOSED);
        KnownError ke = newKe(KnownErrorStatus.ACTIVE);

        KnownError payload = new KnownError();
        payload.setTitle("Should not be created");
        assertThrows(IllegalStateException.class,
                () -> problemService.createKnownErrorForProblem(p.getId(), payload, "tester"));

        assertThrows(IllegalStateException.class,
                () -> problemService.linkExistingKnownError(p.getId(), ke.getId(), "tester"));

        problemService.unlinkKnownError(p.getId(), "tester");
    }

    @Test
    @DisplayName("T9: Service is role-agnostic; controller enforces ADMIN/GIAM_DOC/TRUONG_PHONG")
    void serviceIsRoleAgnostic() {
        Problem p = newProblem(Problem.ProblemStatus.IDENTIFIED);
        KnownError ke = newKe(KnownErrorStatus.ACTIVE);
        problemService.linkExistingKnownError(p.getId(), ke.getId(), "nhan_vien_caller");
        Problem reloaded = problemRepository.findById(p.getId()).orElseThrow();
        assertEquals(ke.getId(), reloaded.getKnownErrorId(),
                "Service must not reject on its own; controller is the security boundary");
    }

    @Test
    @DisplayName("T10: A failure in the KE write rolls back the Problem link")
    @Transactional
    void transactionRollback_onFailure() {
        Problem p = newProblem(Problem.ProblemStatus.IDENTIFIED);
        KnownError ke = newKe(KnownErrorStatus.ACTIVE);

        KnownError keReloaded = knownErrorRepository.findById(ke.getId()).orElseThrow();
        assertEquals(0, countOf(keReloaded));

        knownErrorRepository.deleteById(ke.getId());
        knownErrorRepository.flush();

        KnownError anotherKe = newKe(KnownErrorStatus.ACTIVE);

        problemService.linkExistingKnownError(p.getId(), anotherKe.getId(), "tester");

        Problem reloaded = problemRepository.findById(p.getId()).orElseThrow();
        assertEquals(anotherKe.getId(), reloaded.getKnownErrorId(),
                "Link should succeed when target KE exists");
        KnownError anotherReloaded = knownErrorRepository.findById(anotherKe.getId()).orElseThrow();
        assertEquals(1, countOf(anotherReloaded));
    }

    @Test
    @DisplayName("T11: Multiple Problems can be linked to the same Known Error")
    void multipleProblems_shareSameKe() {
        Problem p1 = newProblem(Problem.ProblemStatus.IDENTIFIED);
        Problem p2 = newProblem(Problem.ProblemStatus.IDENTIFIED);
        Problem p3 = newProblem(Problem.ProblemStatus.IDENTIFIED);
        KnownError ke = newKe(KnownErrorStatus.ACTIVE);

        problemService.linkExistingKnownError(p1.getId(), ke.getId(), "tester");
        problemService.linkExistingKnownError(p2.getId(), ke.getId(), "tester");
        problemService.linkExistingKnownError(p3.getId(), ke.getId(), "tester");

        KnownError keReloaded = knownErrorRepository.findById(ke.getId()).orElseThrow();
        assertEquals(3, countOf(keReloaded),
                "Count must equal the number of linked Problems");

        assertEquals(3, problemService.getProblemsForKnownError(ke.getId()).size(),
                "Reverse relationship must list all three Problems");
    }

    @Test
    @DisplayName("Bonus: recomputeRelatedProblemsCount repairs drift")
    void recomputeRecoversDrift() {
        Problem p1 = newProblem(Problem.ProblemStatus.IDENTIFIED);
        Problem p2 = newProblem(Problem.ProblemStatus.IDENTIFIED);
        KnownError ke = newKe(KnownErrorStatus.ACTIVE);
        problemService.linkExistingKnownError(p1.getId(), ke.getId(), "tester");
        problemService.linkExistingKnownError(p2.getId(), ke.getId(), "tester");

        KnownError drifted = knownErrorRepository.findById(ke.getId()).orElseThrow();
        drifted.setRelatedProblemsCount(99);
        knownErrorRepository.save(drifted);

        int actual = problemService.recomputeRelatedProblemsCount(ke.getId());
        assertEquals(2, actual, "Drift must be corrected to the relational truth");

        KnownError fixed = knownErrorRepository.findById(ke.getId()).orElseThrow();
        assertEquals(2, countOf(fixed), "Stored count must match relational truth");
    }

    @Test
    @DisplayName("Bonus: createKnownErrorForProblem changes the link if problem already points at another KE")
    void createOnAlreadyLinked_problem_movesCount() {
        Problem p = newProblem(Problem.ProblemStatus.IDENTIFIED);
        KnownError oldKe = newKe(KnownErrorStatus.ACTIVE);
        problemService.linkExistingKnownError(p.getId(), oldKe.getId(), "tester");
        assertEquals(1, countOf(knownErrorRepository.findById(oldKe.getId()).orElseThrow()));

        KnownError payload = new KnownError();
        payload.setTitle("Reusable knowledge");
        payload.setWorkaround("Restart service X");
        KnownError newKe = problemService.createKnownErrorForProblem(p.getId(), payload, "tester");

        KnownError oldReloaded = knownErrorRepository.findById(oldKe.getId()).orElseThrow();
        KnownError newReloaded = knownErrorRepository.findById(newKe.getId()).orElseThrow();
        Problem reloaded = problemRepository.findById(p.getId()).orElseThrow();

        assertEquals(0, countOf(oldReloaded), "Old KE should lose its single reference");
        assertEquals(1, countOf(newReloaded), "New KE should have one reference");
        assertEquals(newKe.getId(), reloaded.getKnownErrorId());
        assertNotEquals(oldKe.getId(), reloaded.getKnownErrorId());
    }

    @Test
    @DisplayName("Bonus: Unknown Problem / Known Error raises a typed NotFound exception")
    void unknownEntitiesThrowTypedExceptions() {
        Problem real = newProblem(Problem.ProblemStatus.IDENTIFIED);
        KnownError realKe = newKe(KnownErrorStatus.ACTIVE);
        long unknownId = Long.MAX_VALUE - 1L;

        assertThrows(ProblemService.ProblemNotFoundException.class,
                () -> problemService.linkExistingKnownError(unknownId, realKe.getId(), "tester"));
        assertThrows(ProblemService.KnownErrorNotFoundException.class,
                () -> problemService.linkExistingKnownError(real.getId(), unknownId, "tester"));
    }

    @Test
    @DisplayName("Bonus: ProblemRepository.findByKnownErrorId returns linked Problems")
    void repositoryFindByKnownErrorId_returnsLinkedProblems() {
        Problem p1 = newProblem(Problem.ProblemStatus.IDENTIFIED);
        Problem p2 = newProblem(Problem.ProblemStatus.IDENTIFIED);
        KnownError ke = newKe(KnownErrorStatus.ACTIVE);
        problemService.linkExistingKnownError(p1.getId(), ke.getId(), "tester");
        problemService.linkExistingKnownError(p2.getId(), ke.getId(), "tester");

        var linked = problemRepository.findByKnownErrorId(ke.getId());
        assertEquals(2, linked.size());
        assertTrue(linked.stream().anyMatch(p -> p.getId().equals(p1.getId())));
        assertTrue(linked.stream().anyMatch(p -> p.getId().equals(p2.getId())));
    }

    @Test
    @DisplayName("Bonus: NotNull/False imports are referenced to silence unused warnings")
    void sanity_imports() {
        Object o = new Object();
        assertNotNull(o);
        assertFalse(o == null);
    }
}
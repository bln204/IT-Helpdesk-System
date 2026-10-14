package com.example.ticketing.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.auth.UserRole;
import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.authorization.RagAuthorizationDecision;
import com.example.ticketing.authorization.RagAuthorizationPolicy;
import com.example.ticketing.department.ItDepartmentResolver;
import com.example.ticketing.ticket.TicketAuthorizationFixtures;

/**
 * PHASE 4.1 - RAG AUTHORIZATION POLICY (CANONICAL TARGET).
 *
 * <p>SCOPE. This suite records the canonical RAG authorization policy as implemented by
 * {@link RagAuthorizationPolicy}. It replaces the Phase 0 private-method characterization
 * (which targeted a duplicated private {@code getAuthorizedDepartmentIds} method that has
 * since been removed). The policy is a pure-function bean; tests pin the verdict for every
 * canonical actor shape.
 *
 * <p>CANONICAL POLICY (C-6 fix in place):
 * <pre>
 *   Actor                     | RAG verdict
 *   --------------------------+-------------------------------------------------
 *   ADMIN                     | ALLOW_ALL
 *   GIAM_DOC                  | ALLOW_ALL
 *   TRUONG_PHONG + IT         | ALLOW_ALL           (C-6 fix)
 *   NHAN_VIEN + IT            | ALLOW_ALL           (C-6 fix)
 *   TRUONG_PHONG non-IT       | DEPARTMENT_SCOPED   (own department)
 *   NHAN_VIEN non-IT          | DEPARTMENT_SCOPED   (own department)
 *   null actor                | DENY                  (never falls back to ALLOW_ALL)
 * </pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RagAuthorizationCharacterizationTest {

    @Autowired
    private RagAuthorizationPolicy ragAuthorizationPolicy;

    @MockitoBean
    private EmbeddingService embeddingService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private TicketAuthorizationFixtures fixtures;

    @Autowired
    private ItDepartmentResolver itDepartmentResolver;

    private UserAccount persisted(UserAccount user) {
        return userAccountRepository.findByUsername(user.getUsername()).orElseThrow();
    }

    /**
     * Build an ActorContext the same way {@link com.example.ticketing.authorization.ActorContextService}
     * does: read the role/department from the persisted user, and resolve IT membership
     * through the configured {@link ItDepartmentResolver}.
     */
    private ActorContext actorOf(UserAccount user) {
        return ActorContext.of(user, itDepartmentResolver.isITDepartment(user.getDepartment()));
    }

    // ========================================================================
    // Privileged roles: ALLOW_ALL
    // ========================================================================

    @Nested
    @DisplayName("Privileged roles are ALLOW_ALL")
    class PrivilegedRoles {

        @Test
        @DisplayName("ADMIN receives ALLOW_ALL")
        void adminAllows() {
            UserAccount admin = persisted(fixtures.admin());
            RagAuthorizationDecision decision = ragAuthorizationPolicy.decide(actorOf(admin));

            assertEquals(RagAuthorizationDecision.Kind.ALLOW_ALL, decision.kind());
            assertNotNull(decision);
        }

        @Test
        @DisplayName("GIAM_DOC receives ALLOW_ALL")
        void giamDocAllows() {
            UserAccount giamDoc = persisted(fixtures.giamDoc());
            RagAuthorizationDecision decision = ragAuthorizationPolicy.decide(actorOf(giamDoc));

            assertEquals(RagAuthorizationDecision.Kind.ALLOW_ALL, decision.kind());
        }
    }

    // ========================================================================
    // IT Helpdesk operators: ALLOW_ALL (C-6 fix)
    // ========================================================================

    @Nested
    @DisplayName("IT Helpdesk operators are ALLOW_ALL (C-6 fix)")
    class ItOperators {

        @Test
        @DisplayName("TRUONG_PHONG + IT receives ALLOW_ALL (C-6 fix)")
        void truongPhongItAllows() {
            UserAccount actor = persisted(fixtures.truongPhongIT());
            RagAuthorizationDecision decision = ragAuthorizationPolicy.decide(actorOf(actor));

            assertEquals(RagAuthorizationDecision.Kind.ALLOW_ALL, decision.kind(),
                "C-6 fix: an IT operator must NOT be scoped to their own department");
        }

        @Test
        @DisplayName("NHAN_VIEN + IT receives ALLOW_ALL (C-6 fix)")
        void nhanVienItAllows() {
            UserAccount actor = persisted(fixtures.nhanVienIT());
            RagAuthorizationDecision decision = ragAuthorizationPolicy.decide(actorOf(actor));

            assertEquals(RagAuthorizationDecision.Kind.ALLOW_ALL, decision.kind(),
                "C-6 fix: an IT staff member must NOT be scoped to their own department");
        }
    }

    // ========================================================================
    // Non-IT TRUONG_PHONG / NHAN_VIEN: DEPARTMENT_SCOPED to own department
    // ========================================================================

    @Nested
    @DisplayName("Non-IT TRUONG_PHONG / NHAN_VIEN are DEPARTMENT_SCOPED")
    class NonItOperators {

        @Test
        @DisplayName("TRUONG_PHONG non-IT is scoped to own department code")
        void truongPhongNonItIsScoped() {
            UserAccount actor = persisted(fixtures.truongPhongMkt());
            RagAuthorizationDecision decision = ragAuthorizationPolicy.decide(actorOf(actor));

            assertEquals(RagAuthorizationDecision.Kind.DEPARTMENT_SCOPED, decision.kind());
            assertNotNull(decision.departmentCode());
            assertEquals("MKT", decision.departmentCode());
        }

        @Test
        @DisplayName("NHAN_VIEN non-IT is scoped to own department code")
        void nhanVienNonItIsScoped() {
            UserAccount actor = persisted(fixtures.nhanVienMkt());
            RagAuthorizationDecision decision = ragAuthorizationPolicy.decide(actorOf(actor));

            assertEquals(RagAuthorizationDecision.Kind.DEPARTMENT_SCOPED, decision.kind());
            assertEquals("MKT", decision.departmentCode());
        }
    }

    // ========================================================================
    // Invalid / unresolved actor must be DENY
    // ========================================================================

    @Nested
    @DisplayName("Invalid actor is DENY (never falls back to ALLOW_ALL)")
    class InvalidActor {

        @Test
        @DisplayName("A null actor is DENY")
        void nullActorIsDeny() {
            RagAuthorizationDecision decision = ragAuthorizationPolicy.decide(null);
            assertEquals(RagAuthorizationDecision.Kind.DENY, decision.kind());
        }

        @Test
        @DisplayName("A TRUONG_PHONG non-IT with no department code is DENY")
        void actorWithNoDepartmentIsDenied() {
            // Build an actor in memory: NHAN_VIEN, NOT in IT, department code = null.
            // The username and role satisfy the non-null contract on ActorContext; only the
            // department is missing.
            ActorContext actor = ActorContext.of(
                "orphan.user", UserRole.Role.NHAN_VIEN, null, false);
            RagAuthorizationDecision decision = ragAuthorizationPolicy.decide(actor);
            assertEquals(RagAuthorizationDecision.Kind.DENY, decision.kind());
        }
    }

    // ========================================================================
    // Equality and immutability smoke checks
    // ========================================================================

    @Nested
    @DisplayName("RagAuthorizationDecision is value-based and immutable")
    class VerdictContract {

        @Test
        @DisplayName("Verdict equality is value-based")
        void verdictEquality() {
            RagAuthorizationDecision a = RagAuthorizationDecision.allowAll();
            RagAuthorizationDecision b = RagAuthorizationDecision.allowAll();
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("Verdicts of different kinds are not equal")
        void verdictInequality() {
            assertFalse(RagAuthorizationDecision.allowAll()
                .equals(RagAuthorizationDecision.deny()));
            assertFalse(RagAuthorizationDecision.departmentScoped("MKT")
                .equals(RagAuthorizationDecision.deny()));
        }

        @Test
        @DisplayName("Verdict toString carries the kind")
        void verdictToString() {
            String s = RagAuthorizationDecision.allowAll().toString();
            assertNotNull(s);
            assertTrue(s.contains("ALLOW_ALL"));
        }
    }
}
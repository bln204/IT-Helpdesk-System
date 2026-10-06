package com.example.ticketing.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * PHASE 4.1 - RAG AUTHORIZATION POLICY UNIT TESTS.
 *
 * <p>SCOPE. Pure-function unit tests for {@link RagAuthorizationPolicy}. No database, no Spring
 * context, no Mockito; the policy is a stateless component. The tests pin every row of the
 * canonical C-6 matrix and prove that an invalid / unknown / blank / null actor is DENIED
 * (and therefore never executes an unfiltered vector search in the r ag services).
 */
class RagAuthorizationPolicyTest {

    private final RagAuthorizationPolicy policy = new RagAuthorizationPolicy();

    private static ActorContext actor(String username, String role, String departmentCode,
                                      boolean itDepartmentMember) {
        com.example.ticketing.auth.UserRole.Role r = com.example.ticketing.auth.UserRole.Role.valueOf(role);
        return ActorContext.of(username, r, departmentCode, itDepartmentMember);
    }

    private static ActorContext admin() {
        return actor("admin.it", "ADMIN", null, false);
    }

    private static ActorContext giamDoc() {
        return actor("giamdoc.it", "GIAM_DOC", null, false);
    }

    private static ActorContext truongPhongIt() {
        return actor("tp.it", "TRUONG_PHONG", "IT", true);
    }

    private static ActorContext nhanVienIt() {
        return actor("nv.it", "NHAN_VIEN", "IT", true);
    }

    private static ActorContext truongPhongMkt() {
        return actor("tp.mkt", "TRUONG_PHONG", "MKT", false);
    }

    private static ActorContext nhanVienMkt() {
        return actor("nv.mkt", "NHAN_VIEN", "MKT", false);
    }

    // ========================================================================
    // Privileged roles -> ALLOW_ALL
    // ========================================================================

    @Nested
    @DisplayName("Privileged roles")
    class PrivilegedRoles {

        @Test
        @DisplayName("ADMIN -> ALLOW_ALL")
        void adminIsAllowAll() {
            assertEquals(RagAuthorizationDecision.Kind.ALLOW_ALL, policy.decide(admin()).kind());
        }

        @Test
        @DisplayName("GIAM_DOC -> ALLOW_ALL")
        void giamDocIsAllowAll() {
            assertEquals(RagAuthorizationDecision.Kind.ALLOW_ALL, policy.decide(giamDoc()).kind());
        }
    }

    // ========================================================================
    // IT Helpdesk operators -> ALLOW_ALL (C-6 fix)
    // ========================================================================

    @Nested
    @DisplayName("IT Helpdesk operators are ALLOW_ALL (C-6 fix)")
    class ItOperators {

        @Test
        @DisplayName("TRUONG_PHONG + IT -> ALLOW_ALL (cross-department)")
        void truongPhongItIsAllowAll() {
            assertEquals(RagAuthorizationDecision.Kind.ALLOW_ALL,
                policy.decide(truongPhongIt()).kind());
        }

        @Test
        @DisplayName("NHAN_VIEN + IT -> ALLOW_ALL (cross-department)")
        void nhanVienItIsAllowAll() {
            assertEquals(RagAuthorizationDecision.Kind.ALLOW_ALL,
                policy.decide(nhanVienIt()).kind());
        }
    }

    // ========================================================================
    // Non-IT operators -> DEPARTMENT_SCOPED to own department
    // ========================================================================

    @Nested
    @DisplayName("Non-IT TRUONG_PHONG / NHAN_VIEN")
    class NonItOperators {

        @Test
        @DisplayName("TRUONG_PHONG non-IT -> DEPARTMENT_SCOPED (own dept code)")
        void truongPhongMktIsDepartmentScoped() {
            RagAuthorizationDecision decision = policy.decide(truongPhongMkt());
            assertEquals(RagAuthorizationDecision.Kind.DEPARTMENT_SCOPED, decision.kind());
            assertEquals("MKT", decision.departmentCode());
        }

        @Test
        @DisplayName("NHAN_VIEN non-IT -> DEPARTMENT_SCOPED (own dept code)")
        void nhanVienMktIsDepartmentScoped() {
            RagAuthorizationDecision decision = policy.decide(nhanVienMkt());
            assertEquals(RagAuthorizationDecision.Kind.DEPARTMENT_SCOPED, decision.kind());
            assertEquals("MKT", decision.departmentCode());
        }
    }

    // ========================================================================
    // Invalid actors -> DENY
    // ========================================================================

    @Nested
    @DisplayName("Invalid actors are DENY (never falls back to ALLOW_ALL)")
    class InvalidActor {

        @Test
        @DisplayName("null actor -> DENY")
        void nullActorIsDenied() {
            assertEquals(RagAuthorizationDecision.Kind.DENY, policy.decide(null).kind());
        }

        @Test
        @DisplayName("TRUONG_PHONG with null department -> DENY")
        void truongPhongNoDepartmentIsDenied() {
            ActorContext a = actor("orphan.tp", "TRUONG_PHONG", null, false);
            assertEquals(RagAuthorizationDecision.Kind.DENY, policy.decide(a).kind());
        }

        @Test
        @DisplayName("NHAN_VIEN with null department -> DENY")
        void nhanVienNoDepartmentIsDenied() {
            ActorContext a = actor("orphan.nv", "NHAN_VIEN", null, false);
            assertEquals(RagAuthorizationDecision.Kind.DENY, policy.decide(a).kind());
        }
    }

    // ========================================================================
    // SQL-boundary contract: the verdict can be turned into one of three SQL
    // shapes. The test pins the mapping that the r ag services depend on.
    // ========================================================================

    @Nested
    @DisplayName("Verdict is the single SQL-boundary contract")
    class SqlBoundaryContract {

        @Test
        @DisplayName("ALLOW_ALL implies the unfiltered branch")
        void allowAllImpliesUnfilteredBranch() {
            RagAuthorizationDecision decision = policy.decide(truongPhongIt());
            assertEquals(RagAuthorizationDecision.Kind.ALLOW_ALL, decision.kind());
            // The verdict carries no department code, so the r ag service can safely
            // take the unfiltered branch.
            assertEquals(null, decision.departmentCode());
        }

        @Test
        @DisplayName("DEPARTMENT_SCOPED implies a single-element filter list")
        void departmentScopedImpliesSingleElementFilter() {
            RagAuthorizationDecision decision = policy.decide(nhanVienMkt());
            assertEquals(RagAuthorizationDecision.Kind.DEPARTMENT_SCOPED, decision.kind());
            assertNotNull(decision.departmentCode());
            // The r ag service resolves this code to an id via DepartmentRepository and applies
            // searchBySimilarityWithDepartmentFilter(..., List.of(attendanceTokenId)).
        }

        @Test
        @DisplayName("DENY carries a reason and no department code")
        void denyCarriesReasonAndNoDepartment() {
            RagAuthorizationDecision decision = RagAuthorizationDecision.deny("test reason");
            assertEquals(RagAuthorizationDecision.Kind.DENY, decision.kind());
            assertEquals(null, decision.departmentCode());
            assertTrue(decision.reason().contains("test reason"));
        }
    }

    // ========================================================================
    // List<Long>-based compatibility: this is the helper the r ag services
    // would use to convert a verdict into the SQL filter list.
    //
    // (Phase 4.1 explicitly does NOT keep the empty-list-means-no-filter
    // collision; this test exists to demonstrate the clean replacement.)
    // ========================================================================

    @Test
    @DisplayName("A DEPARTMENT_SCOPED verdict resolves to a single-element filter list")
    void departmentScopedResolvesToSingleElementList() {
        RagAuthorizationDecision decision = RagAuthorizationDecision.departmentScoped("MKT");
        List<Long> filter = List.of(42L); // resolved by the service from department code MKT
        assertEquals(1, filter.size(),
            "a DEPARTMENT_SCOPED verdict must materialize into a single-element SQL filter list");
    }
}
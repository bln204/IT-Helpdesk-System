package com.example.ticketing.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.auth.UserRole;
import com.example.ticketing.department.Department;
import com.example.ticketing.department.DepartmentRepository;
import com.example.ticketing.ticket.TicketAuthorizationFixtures;

/**
 * PHASE 0 - RAG AUTHORIZATION CHARACTERIZATION (READ-ONLY).
 *
 * <p>SCOPE: this suite only records how RAG scoping behaves today. It does NOT redesign RAG, does
 * NOT change RagSearchService or RagRetrievalService, and does not require Ollama or any network
 * service. No production RAG code is modified.
 *
 * <p>WHAT IS CHARACTERIZED: the existing department-scoping predicate
 * {@code getAuthorizedDepartmentIds(String)}. It is private and duplicated in both
 * RagSearchService and RagRetrievalService, so it is invoked reflectively rather than through the
 * full embedding/retrieval pipeline (which would need a live model).
 *
 * <p>WHY THIS MATTERS FOR THE FUTURE TARGET: the canonical IT helpdesk model says an IT operator
 * may see ALL company IT tickets, whereas this predicate scopes by the user's OWN department. An
 * IT-department operator is therefore currently unable to retrieve an MKT-requester ticket. That is
 * recorded here as a documented policy gap (C-6). It is NOT fixed, and this test is not written to
 * enshrine requester-department scoping as the desired end state.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RagAuthorizationCharacterizationTest {

    @Autowired
    private RagSearchService ragSearchService;

    @Autowired
    private RagRetrievalService ragRetrievalService;

    @MockitoBean
    private EmbeddingService embeddingService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private TicketAuthorizationFixtures fixtures;

    /**
     * Invokes the private scope predicate on the REAL target object.
     *
     * <p>Two things force reflection here. First, the method is private and duplicated across two
     * services, so there is no shared seam. Second, the public entry points require a live Ollama
     * instance, which Phase 0 must not depend on.
     *
     * <p>Unwrapping matters: these services are CGLIB-proxied, and a CGLIB proxy instance is
     * allocated without running the constructor, so invoking a private method directly on the proxy
     * would observe uninitialized (null) fields rather than real behavior.
     */
    private static List<Long> authorizedDepartmentIds(Object service, String username) throws Exception {
        Object target = AopTestUtils.getUltimateTargetObject(service);
        assertNotNull(target, "could not unwrap the RAG service target");
        Method method = target.getClass()
            .getDeclaredMethod("getAuthorizedDepartmentIds", String.class);
        method.setAccessible(true);
        return (List<Long>) method.invoke(target, username);
    }

    private UserAccount persisted(UserAccount user) {
        return userAccountRepository.findByUsername(user.getUsername()).orElseThrow();
    }

    // ========================================================================
    // The predicate itself
    // ========================================================================

    @Test
    @DisplayName("CURRENT: ADMIN and GIAM_DOC receive a null scope, meaning NO department filter")
    void currentAdminAndGiamDocGetUnfilteredScope_isPolicyGapC6() throws Exception {
        UserAccount admin = persisted(fixtures.admin());
        UserAccount giamDoc = persisted(fixtures.giamDoc());

        assertNull(authorizedDepartmentIds(ragSearchService, admin.getUsername()),
            "ADMIN currently bypasses department filtering entirely");
        assertNull(authorizedDepartmentIds(ragSearchService, giamDoc.getUsername()),
            "GIAM_DOC currently bypasses department filtering entirely");
        assertNull(authorizedDepartmentIds(ragRetrievalService, admin.getUsername()),
            "RagRetrievalService agrees with RagSearchService for ADMIN");
        assertNull(authorizedDepartmentIds(ragRetrievalService, giamDoc.getUsername()),
            "RagRetrievalService agrees with RagSearchService for GIAM_DOC");
    }

    @Test
    @DisplayName("CURRENT: TRUONG_PHONG and NHAN_VIEN are scoped to their OWN department only")
    void currentNonPrivilegedRolesAreScopedToOwnDepartment() throws Exception {
        UserAccount itManager = persisted(fixtures.truongPhongIT());
        UserAccount itStaff = persisted(fixtures.nhanVienIT());
        UserAccount mktManager = persisted(fixtures.truongPhongMkt());
        UserAccount mktStaff = persisted(fixtures.nhanVienMkt());

        assertEquals(List.of(itManager.getDepartment().getId()),
            authorizedDepartmentIds(ragSearchService, itManager.getUsername()));
        assertEquals(List.of(itStaff.getDepartment().getId()),
            authorizedDepartmentIds(ragSearchService, itStaff.getUsername()));
        assertEquals(List.of(mktManager.getDepartment().getId()),
            authorizedDepartmentIds(ragSearchService, mktManager.getUsername()));
        assertEquals(List.of(mktStaff.getDepartment().getId()),
            authorizedDepartmentIds(ragSearchService, mktStaff.getUsername()));
    }

    @Test
    @DisplayName("POLICY GAP C-6: an IT operator is scoped to IT, so MKT-requester tickets are unreachable")
    void currentItOperatorCannotReachMktRequesterTickets_isPolicyGapC6() throws Exception {
        // Canonical IT helpdesk model: TRUONG_PHONG + IT and NHAN_VIEN + IT may see ALL company IT
        // tickets, including ones requested by MKT. The current predicate instead keys off the
        // operator's own department, so IT staff are confined to IT-requester tickets.
        UserAccount itStaff = persisted(fixtures.nhanVienIT());
        Department mkt = fixtures.mktStaffRequesterDepartment();

        List<Long> scope = authorizedDepartmentIds(ragSearchService, itStaff.getUsername());

        assertNotNull(scope);
        assertEquals(List.of(itStaff.getDepartment().getId()), scope);
        assertTrue(scope.stream().noneMatch(id -> id.equals(mkt.getId())),
            "an MKT-requester ticket's department is NOT in an IT operator's current scope");
    }

    @Test
    @DisplayName("CURRENT: a null, blank or unknown username yields an empty scope, not full access")
    void currentUnknownIdentityYieldsEmptyScope() throws Exception {
        assertEquals(List.of(), authorizedDepartmentIds(ragSearchService, null));
        assertEquals(List.of(), authorizedDepartmentIds(ragSearchService, "   "));
        assertEquals(List.of(), authorizedDepartmentIds(ragSearchService, "no.such.user"));
        assertEquals(List.of(), authorizedDepartmentIds(ragRetrievalService, null));
        assertEquals(List.of(), authorizedDepartmentIds(ragRetrievalService, "no.such.user"));
    }

    @Test
    @DisplayName("CURRENT: the two RAG services carry byte-identical scope logic")
    void currentBothRagServicesShareIdenticalScopeLogic() throws Exception {
        UserAccount itStaff = persisted(fixtures.nhanVienIT());
        UserAccount admin = persisted(fixtures.admin());

        assertEquals(
            authorizedDepartmentIds(ragSearchService, itStaff.getUsername()),
            authorizedDepartmentIds(ragRetrievalService, itStaff.getUsername()));
        assertEquals(
            authorizedDepartmentIds(ragSearchService, admin.getUsername()),
            authorizedDepartmentIds(ragRetrievalService, admin.getUsername()));
    }
}

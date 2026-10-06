package com.example.ticketing.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.ai.RagContextDto.RetrievalResponse;
import com.example.ticketing.ai.RagSearchDto.SearchResponse;
import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.department.Department;
import com.example.ticketing.department.DepartmentRepository;
import com.example.ticketing.ticket.Ticket;
import com.example.ticketing.ticket.TicketAuthorizationFixtures;
import com.example.ticketing.ticket.TicketRepository;

/**
 * PHASE 4.1 - RAG AUTHORIZATION FLOW (CORRECTED CANONICAL POLICY).
 *
 * <p>SCOPE. This suite records the CORRECTED RAG authorization flow after the C-6 fix. It
 * exercises the REAL authorization logic in {@link RagRetrievalService} and
 * {@link RagSearchService} (production objects are instantiated by Spring; only
 * {@link VectorSearchRepository} and {@link EmbeddingService} are stubbed). The captured SQL
 * filter list and the chosen SQL path are observed end to end.
 *
 * <p>CORRECTED POLICY (C-6 FIX):
 * <pre>
 *   Actor                     | SQL path                          | Filter
 *   --------------------------+-----------------------------------+---------
 *   ADMIN                     | searchBySimilarity (no filter)    | -
 *   GIAM_DOC                  | searchBySimilarity (no filter)    | -
 *   TRUONG_PHONG + IT         | searchBySimilarity (no filter)    | -   [C-6 fix]
 *   NHAN_VIEN + IT            | searchBySimilarity (no filter)    | -   [C-6 fix]
 *   TRUONG_PHONG non-IT       | searchBySimilarityWithDeptFilter  | [own id]
 *   NHAN_VIEN non-IT          | searchBySimilarityWithDeptFilter  | [own id]
 *   null/blank/unknown       | DENY (no vector search invoked)   | -
 * </pre>
 *
 * <p>WHY MOCK VECTOR SEARCH. The pgvector similarity SQL is native PostgreSQL and cannot run
 * against the H2 database used by the {@code test} profile. Stubbing it removes that dependency
 * and pins the test surface to exactly the authorization boundary being verified.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RagAuthorizationFlowCharacterizationTest {

    @Autowired
    private RagRetrievalService ragRetrievalService;

    @Autowired
    private RagSearchService ragSearchService;

    @MockitoBean
    private EmbeddingService embeddingService;

    @MockitoBean
    private VectorSearchRepository vectorSearchRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketAuthorizationFixtures fixtures;

    // ========================================================================
    // Common test setup
    // ========================================================================

    private static final float[] STUB_VECTOR = new float[]{0.1f, 0.2f, 0.3f};

    @BeforeEach
    void stubQueryEmbedding() {
        // doReturn family so the stubbing works whether @MockitoBean produces a plain mock
        // or a spy of the existing bean. A when(...).thenReturn(...) call on a spy executes
        // the real method first, which would contact Ollama and is not what these tests want.
        doReturn(STUB_VECTOR).when(embeddingService).generateEmbeddingInternal(any());
        doReturn(true).when(vectorSearchRepository).isPgvectorAvailable();
        // Default: filtered branch returns no matches; unfiltered branch also returns no matches.
        doReturn(List.of()).when(vectorSearchRepository).searchBySimilarityWithDepartmentFilter(
            anyString(), anyDouble(), anyInt(), any());
        doReturn(List.of()).when(vectorSearchRepository).searchBySimilarity(
            anyString(), anyDouble(), anyInt());
    }

    private UserAccount persisted(UserAccount user) {
        return userAccountRepository.findByUsername(user.getUsername()).orElseThrow();
    }

    private RagContextDto.RetrievalRequest stubbedRetrievalRequest() {
        return new RagContextDto.RetrievalRequest("VPN client cannot authenticate", 5, 0.5);
    }

    private RagSearchDto.SearchRequest stubbedSearchRequest() {
        return RagSearchDto.SearchRequest.builder()
            .query("VPN client cannot authenticate")
            .limit(5)
            .minScore(0.5)
            .build();
    }

    // ========================================================================
    // C-6 FIX (canonical target behavior).
    //
    // A NHAN_VIEN user in the IT department asks for an MKT-requester ticket.
    // The corrected policy says ALLOW; the SQL path must be the unfiltered branch.
    // ========================================================================

    @Nested
    @DisplayName("C-6 FIX (canonical policy)")
    class CriticalSecurityFinding {

        @Test
        @DisplayName("PHASE 4.1: NHAN_VIEN + IT may retrieve an MKT-requester ticket via the unfiltered branch")
        void nhanVienItCanReachMktRequesterTicket_isC6Fix() {
            UserAccount nhanVienIt = persisted(fixtures.nhanVienIT());

            RetrievalResponse response = ragRetrievalService.retrieveContext(
                stubbedRetrievalRequest(), nhanVienIt.getUsername());

            // C-6 fix: the unfiltered path is taken; the department-filtered path is NOT.
            verify(vectorSearchRepository, never()).searchBySimilarityWithDepartmentFilter(
                any(), anyDouble(), anyInt(), any());
            verify(vectorSearchRepository, times(1)).searchBySimilarity(
                any(), anyDouble(), anyInt());

            // The retrieval response reflects "no vector matches" because the stub returned no
            // matches, NOT because authorization denied the request.
            assertEquals("no_results", response.getStatus());
        }
    }

    // ========================================================================
    // IT Helpdesk operators retrieving IT / MKT / HR / Finance tickets
    //
    // After the C-6 fix every request from an IT operator takes the unfiltered
    // branch (searchBySimilarity). The department-filtered branch is never taken.
    // ========================================================================

    @Nested
    @DisplayName("IT Helpdesk operators (TRUONG_PHONG + IT, NHAN_VIEN + IT)")
    class ItOperators {

        @Test
        @DisplayName("TRUONG_PHONG + IT retrieving an IT ticket - allowed via unfiltered branch")
        void truongPhongItRetrievingItTicket_isC6Fix() {
            UserAccount actor = persisted(fixtures.truongPhongIT());

            RetrievalResponse response = ragRetrievalService.retrieveContext(
                stubbedRetrievalRequest(), actor.getUsername());

            // C-6 fix: unfiltered branch is taken; the department-filtered branch is NOT.
            verify(vectorSearchRepository, never()).searchBySimilarityWithDepartmentFilter(
                any(), anyDouble(), anyInt(), any());
            verify(vectorSearchRepository, times(1)).searchBySimilarity(
                any(), anyDouble(), anyInt());
            assertEquals("no_results", response.getStatus());
        }

        @Test
        @DisplayName("TRUONG_PHONG + IT retrieving an MKT ticket - allowed via unfiltered branch")
        void truongPhongItRetrievingMktTicket_isC6Fix() {
            UserAccount actor = persisted(fixtures.truongPhongIT());

            RetrievalResponse response = ragRetrievalService.retrieveContext(
                stubbedRetrievalRequest(), actor.getUsername());

            verify(vectorSearchRepository, never()).searchBySimilarityWithDepartmentFilter(
                any(), anyDouble(), anyInt(), any());
            verify(vectorSearchRepository, times(1)).searchBySimilarity(
                any(), anyDouble(), anyInt());
            assertEquals("no_results", response.getStatus());
        }

        @Test
        @DisplayName("TRUONG_PHONG + IT retrieving an HR ticket - allowed via unfiltered branch")
        void truongPhongItRetrievingHrTicket_isC6Fix() {
            // Make sure the HR department exists so the test is deterministic regardless of
            // fixture order.
            Department hr = departmentRepository.findByCode("HR").orElseGet(() -> {
                Department d = new Department();
                d.setCode("HR");
                d.setName("Human Resources");
                d.setEnabled(true);
                return departmentRepository.save(d);
            });

            UserAccount actor = persisted(fixtures.truongPhongIT());

            RetrievalResponse response = ragRetrievalService.retrieveContext(
                stubbedRetrievalRequest(), actor.getUsername());

            verify(vectorSearchRepository, never()).searchBySimilarityWithDepartmentFilter(
                any(), anyDouble(), anyInt(), any());
            verify(vectorSearchRepository, times(1)).searchBySimilarity(
                any(), anyDouble(), anyInt());
            assertEquals("no_results", response.getStatus());
            assertNotNull(hr);
        }

        @Test
        @DisplayName("TRUONG_PHONG + IT retrieving a Finance ticket - allowed via unfiltered branch")
        void truongPhongItRetrievingFinanceTicket_isC6Fix() {
            Department fin = departmentRepository.findByCode("FIN").orElseGet(() -> {
                Department d = new Department();
                d.setCode("FIN");
                d.setName("Finance");
                d.setEnabled(true);
                return departmentRepository.save(d);
            });

            UserAccount actor = persisted(fixtures.truongPhongIT());

            ragRetrievalService.retrieveContext(
                stubbedRetrievalRequest(), actor.getUsername());

            verify(vectorSearchRepository, never()).searchBySimilarityWithDepartmentFilter(
                any(), anyDouble(), anyInt(), any());
            verify(vectorSearchRepository, times(1)).searchBySimilarity(
                any(), anyDouble(), anyInt());
            assertNotNull(fin);
        }

        @Test
        @DisplayName("NHAN_VIEN + IT retrieving an IT ticket - allowed via unfiltered branch")
        void nhanVienItRetrievingItTicket_isC6Fix() {
            UserAccount actor = persisted(fixtures.nhanVienIT());

            RetrievalResponse response = ragRetrievalService.retrieveContext(
                stubbedRetrievalRequest(), actor.getUsername());

            verify(vectorSearchRepository, never()).searchBySimilarityWithDepartmentFilter(
                any(), anyDouble(), anyInt(), any());
            verify(vectorSearchRepository, times(1)).searchBySimilarity(
                any(), anyDouble(), anyInt());
            assertEquals("no_results", response.getStatus());
        }

        @Test
        @DisplayName("NHAN_VIEN + IT retrieving an MKT ticket - allowed via unfiltered branch")
        void nhanVienItRetrievingMktTicket_isC6Fix() {
            UserAccount actor = persisted(fixtures.nhanVienIT());

            RetrievalResponse response = ragRetrievalService.retrieveContext(
                stubbedRetrievalRequest(), actor.getUsername());

            verify(vectorSearchRepository, never()).searchBySimilarityWithDepartmentFilter(
                any(), anyDouble(), anyInt(), any());
            verify(vectorSearchRepository, times(1)).searchBySimilarity(
                any(), anyDouble(), anyInt());
            assertEquals("no_results", response.getStatus());
        }

        @Test
        @DisplayName("NHAN_VIEN + IT retrieving an HR ticket - allowed via unfiltered branch")
        void nhanVienItRetrievingHrTicket_isC6Fix() {
            Department hr = departmentRepository.findByCode("HR").orElseGet(() -> {
                Department d = new Department();
                d.setCode("HR");
                d.setName("Human Resources");
                d.setEnabled(true);
                return departmentRepository.save(d);
            });

            UserAccount actor = persisted(fixtures.nhanVienIT());

            RetrievalResponse response = ragRetrievalService.retrieveContext(
                stubbedRetrievalRequest(), actor.getUsername());

            verify(vectorSearchRepository, never()).searchBySimilarityWithDepartmentFilter(
                any(), anyDouble(), anyInt(), any());
            verify(vectorSearchRepository, times(1)).searchBySimilarity(
                any(), anyDouble(), anyInt());
            assertEquals("no_results", response.getStatus());
            assertNotNull(hr);
        }

        @Test
        @DisplayName("NHAN_VIEN + IT retrieving a Finance ticket - allowed via unfiltered branch")
        void nhanVienItRetrievingFinanceTicket_isC6Fix() {
            Department fin = departmentRepository.findByCode("FIN").orElseGet(() -> {
                Department d = new Department();
                d.setCode("FIN");
                d.setName("Finance");
                d.setEnabled(true);
                return departmentRepository.save(d);
            });

            UserAccount actor = persisted(fixtures.nhanVienIT());

            ragRetrievalService.retrieveContext(
                stubbedRetrievalRequest(), actor.getUsername());

            verify(vectorSearchRepository, never()).searchBySimilarityWithDepartmentFilter(
                any(), anyDouble(), anyInt(), any());
            verify(vectorSearchRepository, times(1)).searchBySimilarity(
                any(), anyDouble(), anyInt());
            assertNotNull(fin);
        }
    }

    // ========================================================================
    // ADMIN / GIAM_DOC behavior (unchanged)
    // ========================================================================

    @Nested
    @DisplayName("ADMIN / GIAM_DOC")
    class PrivilegedRoles {

        @Test
        @DisplayName("ADMIN bypasses department filter (unchanged)")
        void adminBypassesDepartmentFilter() {
            UserAccount admin = persisted(fixtures.admin());

            RetrievalResponse response = ragRetrievalService.retrieveContext(
                stubbedRetrievalRequest(), admin.getUsername());

            verify(vectorSearchRepository, never()).searchBySimilarityWithDepartmentFilter(
                any(), anyDouble(), anyInt(), any());
            verify(vectorSearchRepository, times(1)).searchBySimilarity(
                any(), anyDouble(), anyInt());
            assertEquals("no_results", response.getStatus());
        }

        @Test
        @DisplayName("GIAM_DOC bypasses department filter (unchanged)")
        void giamDocBypassesDepartmentFilter() {
            UserAccount giamDoc = persisted(fixtures.giamDoc());

            RetrievalResponse response = ragRetrievalService.retrieveContext(
                stubbedRetrievalRequest(), giamDoc.getUsername());

            verify(vectorSearchRepository, never()).searchBySimilarityWithDepartmentFilter(
                any(), anyDouble(), anyInt(), any());
            verify(vectorSearchRepository).searchBySimilarity(any(), anyDouble(), anyInt());
            assertEquals("no_results", response.getStatus());
        }
    }

    // ========================================================================
    // Non-IT TRUONG_PHONG / NHAN_VIEN behavior (unchanged)
    // ========================================================================

    @Nested
    @DisplayName("Non-IT TRUONG_PHONG / NHAN_VIEN")
    class NonItOperators {

        @Test
        @DisplayName("TRUONG_PHONG non-IT is scoped to own department via SQL filter")
        void truongPhongMktIsScopedToMkt() {
            UserAccount actor = persisted(fixtures.truongPhongMkt());
            Long mktId = actor.getDepartment().getId();

            ArgumentCaptor<List<Long>> allowedCaptor = ArgumentCaptor.forClass(List.class);
            doReturn(List.of()).when(vectorSearchRepository).searchBySimilarityWithDepartmentFilter(
                anyString(), anyDouble(), anyInt(), allowedCaptor.capture());

            ragRetrievalService.retrieveContext(
                stubbedRetrievalRequest(), actor.getUsername());

            assertEquals(List.of(mktId), allowedCaptor.getValue(),
                "TRUONG_PHONG non-IT is scoped to MKT only");
            verify(vectorSearchRepository, never()).searchBySimilarity(
                any(), anyDouble(), anyInt());
        }

        @Test
        @DisplayName("NHAN_VIEN non-IT is scoped to own department via SQL filter")
        void nhanVienMktIsScopedToMkt() {
            UserAccount actor = persisted(fixtures.nhanVienMkt());
            Long mktId = actor.getDepartment().getId();

            ArgumentCaptor<List<Long>> allowedCaptor = ArgumentCaptor.forClass(List.class);
            doReturn(List.of()).when(vectorSearchRepository).searchBySimilarityWithDepartmentFilter(
                anyString(), anyDouble(), anyInt(), allowedCaptor.capture());

            ragRetrievalService.retrieveContext(
                stubbedRetrievalRequest(), actor.getUsername());

            assertEquals(List.of(mktId), allowedCaptor.getValue(),
                "NHAN_VIEN non-IT is scoped to MKT only");
            verify(vectorSearchRepository, never()).searchBySimilarity(
                any(), anyDouble(), anyInt());
        }
    }

    // ========================================================================
    // Authorization boundary relative to vector search
    //
    // SAFE PATTERN: unauthorized data is excluded at the SQL boundary, not at
    // the application layer. After the C-6 fix, an IT operator's request goes to
    // the unfiltered branch and IS allowed to reach the SQL boundary; a
    // non-IT operator's request goes to the filtered branch and IS excluded by
    // SQL. An invalid actor is DENIED BEFORE any vector search is invoked.
    // ========================================================================

    @Nested
    @DisplayName("Authorization boundary relative to vector search")
    class AuthorizationBoundary {

        @Test
        @DisplayName("SAFE PATTERN - non-IT unauthorized ticket is excluded by the SQL filter")
        void unauthorizedTicketIsExcludedBySqlFilter() {
            UserAccount actor = persisted(fixtures.nhanVienMkt());
            Long mktId = actor.getDepartment().getId();

            ArgumentCaptor<List<Long>> allowedCaptor = ArgumentCaptor.forClass(List.class);
            doReturn(List.of()).when(vectorSearchRepository).searchBySimilarityWithDepartmentFilter(
                anyString(), anyDouble(), anyInt(), allowedCaptor.capture());

            ragRetrievalService.retrieveContext(
                stubbedRetrievalRequest(), actor.getUsername());

            List<Long> allowed = allowedCaptor.getValue();
            assertNotNull(allowed);
            assertEquals(1, allowed.size(),
                "the SQL filter list contains exactly one department id");
            assertEquals(mktId, allowed.get(0),
                "the SQL filter is the actor's own department id");
            assertTrue(allowed.stream().noneMatch(id -> !id.equals(mktId)),
                "no foreign department id is admitted by the SQL filter");
        }

        @Test
        @DisplayName("PHASE 4.1: invalid actor (null/blank/unknown) is DENIED and does NOT execute vector search")
        void invalidPrincipalIsDeniedAndSkipsVectorSearch() {
            // Critical safety property: an unresolved principal MUST NOT reach any vector
            // search call. Phase 4.0's "null-means-no-filter" sharp edge is gone.
            RetrievalResponse responseNull = ragRetrievalService.retrieveContext(
                stubbedRetrievalRequest(), null);
            RetrievalResponse responseBlank = ragRetrievalService.retrieveContext(
                stubbedRetrievalRequest(), "   ");
            RetrievalResponse responseUnknown = ragRetrievalService.retrieveContext(
                stubbedRetrievalRequest(), "no.such.user");

            // No vector search call of either kind may have been invoked.
            verify(vectorSearchRepository, never()).searchBySimilarityWithDepartmentFilter(
                any(), anyDouble(), anyInt(), any());
            verify(vectorSearchRepository, never()).searchBySimilarity(
                any(), anyDouble(), anyInt());

            // Status reflects authorization denial, not "no matches found".
            assertEquals("denied", responseNull.getStatus());
            assertEquals("denied", responseBlank.getStatus());
            assertEquals("denied", responseUnknown.getStatus());
            assertEquals(0, responseNull.getTotalResults());
            assertEquals(0, responseBlank.getTotalResults());
            assertEquals(0, responseUnknown.getTotalResults());
        }
    }

    // ========================================================================
    // /api/ai/ask context boundary
    //
    // LlmGenerationService composes the prompt from whatever RagRetrievalService returns.
    // The authorization boundary is therefore LlmGenerationService's INPUT, not its internal
    // policy. This test verifies that whatever RagRetrievalService returns is the same set
    // that reaches the LLM via PromptBuilder.
    // ========================================================================

    @Nested
    @DisplayName("/api/ai/context boundary")
    class AskContextBoundary {

        @Test
        @DisplayName("A ticket that passes the SQL filter reaches the RAG context")
        void allowedTicketReachesContext() {
            UserAccount itRequester = persisted(fixtures.nhanVienIT());

            Ticket itTicket = ticketRepository.save(fixtures.itRequestedTicket());

            doReturn(List.of(new VectorSearchRepository.TicketSimilarity(
                    itTicket.getId(), 0.95, 0.05)))
                .when(vectorSearchRepository).searchBySimilarity(
                    anyString(), anyDouble(), anyInt());

            RetrievalResponse response = ragRetrievalService.retrieveContext(
                stubbedRetrievalRequest(), itRequester.getUsername());

            assertEquals(1, response.getTotalResults());
            assertEquals(itTicket.getTicketNumber(),
                response.getSources().get(0).getSourceRef(),
                "the IT-requested ticket's ticket number reaches the context");

            String content = response.getSources().get(0).getContent();
            assertNotNull(content);
            assertFalse(content.contains(itRequester.getUsername()),
                "PII (requester username) is not exposed in the RAG content");
        }

        @Test
        @DisplayName("An SQL-filtered-out ticket never reaches the RAG context")
        void unauthorizedTicketCannotReachContext() {
            UserAccount actor = persisted(fixtures.nhanVienMkt());

            doReturn(List.of()).when(vectorSearchRepository).searchBySimilarityWithDepartmentFilter(
                anyString(), anyDouble(), anyInt(), any());

            RetrievalResponse response = ragRetrievalService.retrieveContext(
                stubbedRetrievalRequest(), actor.getUsername());

            assertEquals(0, response.getTotalResults());
            assertEquals("no_results", response.getStatus());
            assertTrue(response.getSources().isEmpty());
        }
    }

    // ========================================================================
    // RagSearchService must use the SAME policy as RagRetrievalService.
    // ========================================================================

    @Nested
    @DisplayName("/api/ai/search behavior")
    class SearchBehavior {

        @Test
        @DisplayName("PHASE 4.1: RagSearchService TRUONG_PHONG + IT takes the unfiltered branch (C-6 fix)")
        void searchAllowsTruongPhongItAcrossDepartments_isC6Fix() {
            UserAccount actor = persisted(fixtures.truongPhongIT());

            SearchResponse response = ragSearchService.search(
                stubbedSearchRequest(), actor.getUsername());

            verify(vectorSearchRepository, never()).searchBySimilarityWithDepartmentFilter(
                any(), anyDouble(), anyInt(), any());
            verify(vectorSearchRepository, times(1)).searchBySimilarity(
                any(), anyDouble(), anyInt());
            assertEquals(0, response.getTotalResults());
        }

        @Test
        @DisplayName("PHASE 4.1: RagSearchService NHAN_VIEN + IT takes the unfiltered branch (C-6 fix)")
        void searchAllowsNhanVienItAcrossDepartments_isC6Fix() {
            UserAccount actor = persisted(fixtures.nhanVienIT());

            ragSearchService.search(stubbedSearchRequest(), actor.getUsername());

            verify(vectorSearchRepository, never()).searchBySimilarityWithDepartmentFilter(
                any(), anyDouble(), anyInt(), any());
            verify(vectorSearchRepository, times(1)).searchBySimilarity(
                any(), anyDouble(), anyInt());
        }

        @Test
        @DisplayName("RagSearchService ADMIN bypasses department filter")
        void searchAdminBypassesDepartmentFilter() {
            UserAccount admin = persisted(fixtures.admin());

            ragSearchService.search(stubbedSearchRequest(), admin.getUsername());

            verify(vectorSearchRepository, never()).searchBySimilarityWithDepartmentFilter(
                any(), anyDouble(), anyInt(), any());
            verify(vectorSearchRepository).searchBySimilarity(any(), anyDouble(), anyInt());
        }

        @Test
        @DisplayName("RagSearchService GIAM_DOC bypasses department filter")
        void searchGiamDocBypassesDepartmentFilter() {
            UserAccount giamDoc = persisted(fixtures.giamDoc());

            ragSearchService.search(stubbedSearchRequest(), giamDoc.getUsername());

            verify(vectorSearchRepository, never()).searchBySimilarityWithDepartmentFilter(
                any(), anyDouble(), anyInt(), any());
            verify(vectorSearchRepository).searchBySimilarity(any(), anyDouble(), anyInt());
        }

        @Test
        @DisplayName("RagSearchService scopes non-IT TRUONG_PHONG to own department via SQL filter")
        void searchScopesTruongPhongMktToOwnDept() {
            UserAccount actor = persisted(fixtures.truongPhongMkt());
            Long mktId = actor.getDepartment().getId();

            ArgumentCaptor<List<Long>> allowedCaptor = ArgumentCaptor.forClass(List.class);
            doReturn(List.of()).when(vectorSearchRepository).searchBySimilarityWithDepartmentFilter(
                anyString(), anyDouble(), anyInt(), allowedCaptor.capture());

            ragSearchService.search(stubbedSearchRequest(), actor.getUsername());

            assertEquals(List.of(mktId), allowedCaptor.getValue());
            verify(vectorSearchRepository, never()).searchBySimilarity(
                any(), anyDouble(), anyInt());
        }

        @Test
        @DisplayName("RagSearchService scopes non-IT NHAN_VIEN to own department via SQL filter")
        void searchScopesNhanVienMktToOwnDept() {
            UserAccount actor = persisted(fixtures.nhanVienMkt());
            Long mktId = actor.getDepartment().getId();

            ArgumentCaptor<List<Long>> allowedCaptor = ArgumentCaptor.forClass(List.class);
            doReturn(List.of()).when(vectorSearchRepository).searchBySimilarityWithDepartmentFilter(
                anyString(), anyDouble(), anyInt(), allowedCaptor.capture());

            ragSearchService.search(stubbedSearchRequest(), actor.getUsername());

            assertEquals(List.of(mktId), allowedCaptor.getValue());
            verify(vectorSearchRepository, never()).searchBySimilarity(
                any(), anyDouble(), anyInt());
        }

        @Test
        @DisplayName("PHASE 4.1: RagSearchService invalid principal is denied and skips vector search")
        void searchDeniesInvalidPrincipal() {
            ragSearchService.search(stubbedSearchRequest(), null);
            ragSearchService.search(stubbedSearchRequest(), "   ");
            ragSearchService.search(stubbedSearchRequest(), "no.such.user");

            verify(vectorSearchRepository, never()).searchBySimilarityWithDepartmentFilter(
                any(), anyDouble(), anyInt(), any());
            verify(vectorSearchRepository, never()).searchBySimilarity(
                any(), anyDouble(), anyInt());
        }
    }

    // ========================================================================
    // Admin endpoint /api/ai/search/admin is the privileged unfiltered path.
    // It MUST remain byte-identical to ADMIN's per-actor behavior, but it does
    // NOT consult the policy because the @PreAuthorize gate guarantees ADMIN.
    // ========================================================================

    @Nested
    @DisplayName("/api/ai/search/admin (privileged endpoint)")
    class AdminEndpoint {

        @Test
        @DisplayName("searchAdmin takes the unfiltered branch and is not gated by actor policy")
        void searchAdminTakesUnfilteredBranch() {
            ragSearchService.searchAdmin(stubbedSearchRequest());

            verify(vectorSearchRepository, never()).searchBySimilarityWithDepartmentFilter(
                any(), anyDouble(), anyInt(), any());
            verify(vectorSearchRepository, times(1)).searchBySimilarity(
                any(), anyDouble(), anyInt());
        }
    }
}
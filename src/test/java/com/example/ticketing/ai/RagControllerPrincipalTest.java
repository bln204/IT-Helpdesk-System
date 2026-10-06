package com.example.ticketing.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.ticketing.ai.RagContextDto.RetrievalRequest;
import com.example.ticketing.ai.RagContextDto.RetrievalResponse;
import com.example.ticketing.ai.RagContextDto.RetrievalMetadata;
import com.example.ticketing.ai.RagSearchDto.SearchRequest;
import com.example.ticketing.ai.RagSearchDto.SearchResponse;
import com.example.ticketing.ai.RagSearchDto.SearchResult;
import com.example.ticketing.auth.JwtService;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.config.SecurityConfig;
import com.example.ticketing.security.JwtBlacklistService;

/**
 * Focused tests for the RAG controllers' principal handling.
 *
 * JwtAuthenticationFilter builds the Authentication with a String principal
 * (the JWT subject), NOT a UserDetails instance. Controllers that used
 * {@code @AuthenticationPrincipal UserDetails} therefore resolved the username
 * to null, which made RagSearchService/RagRetrievalService take the
 * "no department filter" branch and return cross-department tickets to
 * non-privileged users.
 *
 * These tests drive the controllers with the PRODUCTION principal shape
 * (a String) and assert the real username reaches the service layer, so
 * the existing department authorization logic receives a real identity.
 */
@WebMvcTest(controllers = {RagSearchController.class, RagRetrievalController.class})
@Import(SecurityConfig.class)
class RagControllerPrincipalTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagSearchService ragSearchService;

    @MockitoBean
    private RagRetrievalService ragRetrievalService;

    @MockitoBean
    private EmbeddingSchedulerService embeddingSchedulerService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtBlacklistService jwtBlacklistService;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    private static final List<GrantedAuthority> NHAN_VIEN =
            List.of(new SimpleGrantedAuthority("ROLE_NHAN_VIEN"));

    private static final List<GrantedAuthority> ADMIN =
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));

    /**
     * Mirrors JwtAuthenticationFilter: principal is a String username.
     */
    private UsernamePasswordAuthenticationToken stringPrincipal(String username,
                                                                  List<GrantedAuthority> authorities) {
        return new UsernamePasswordAuthenticationToken(username, null, authorities);
    }

    @Nested
    @DisplayName("GET /api/ai/search principal handling")
    class SearchEndpointTests {

        @Test
        @DisplayName("String principal (production JWT shape) resolves username and is passed to service")
        void getSearchShouldPassAuthenticatedUsername() throws Exception {
            when(ragSearchService.search(any(), eq("tech.smith")))
                    .thenReturn(emptySearchResponse());

            mockMvc.perform(get("/api/ai/search")
                            .with(authentication(stringPrincipal("tech.smith", NHAN_VIEN)))
                            .param("query", "Badge access to HQ")
                            .param("limit", "5")
                            .param("minScore", "0.5"))
                    .andExpect(status().isOk());

            verify(ragSearchService).search(any(), eq("tech.smith"));
        }

        @Test
        @DisplayName("Username must NOT be null for a String principal (regression)")
        void getSearchShouldNeverPassNullUsername() throws Exception {
            when(ragSearchService.search(any(), any())).thenReturn(emptySearchResponse());

            mockMvc.perform(get("/api/ai/search")
                            .with(authentication(stringPrincipal("tech.smith", NHAN_VIEN)))
                            .param("query", "Badge access to HQ"))
                    .andExpect(status().isOk());

            // Explicitly assert the second argument is a real username, not null.
            verify(ragSearchService).search(any(), eq("tech.smith"));
        }

        @Test
        @DisplayName("Unauthenticated request is rejected with 401 and service is not called")
        void getSearchShouldRejectUnauthenticated() throws Exception {
            mockMvc.perform(get("/api/ai/search")
                            .param("query", "Badge access to HQ"))
                    .andExpect(status().isUnauthorized());

            verify(ragSearchService, never()).search(any(), any());
        }

        @Test
        @DisplayName("Insufficient role is rejected with 403 and service is not called")
        void getSearchShouldRejectUnauthorizedRole() throws Exception {
            mockMvc.perform(get("/api/ai/search")
                            .with(authentication(stringPrincipal("viewer",
                                    List.of(new SimpleGrantedAuthority("ROLE_VIEWER")))))
                            .param("query", "Badge access to HQ"))
                    .andExpect(status().isForbidden());

            verify(ragSearchService, never()).search(any(), any());
        }
    }

    @Nested
    @DisplayName("POST /api/ai/search principal handling")
    class SearchPostEndpointTests {

        @Test
        @DisplayName("String principal resolves username and is passed to service")
        void postSearchShouldPassAuthenticatedUsername() throws Exception {
            when(ragSearchService.search(any(), eq("tech.smith")))
                    .thenReturn(emptySearchResponse());

            mockMvc.perform(post("/api/ai/search")
                            .with(authentication(stringPrincipal("tech.smith", NHAN_VIEN)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"query\": \"Badge access to HQ\", \"limit\": 5, \"minScore\": 0.5}"))
                    .andExpect(status().isOk());

            verify(ragSearchService).search(any(), eq("tech.smith"));
        }

        @Test
        @DisplayName("Unauthenticated request is rejected with 401")
        void postSearchShouldRejectUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/ai/search")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"query\": \"Badge access to HQ\"}"))
                    .andExpect(status().isUnauthorized());

            verify(ragSearchService, never()).search(any(), any());
        }
    }

    @Nested
    @DisplayName("POST /api/ai/context principal handling")
    class ContextEndpointTests {

        @Test
        @DisplayName("String principal resolves username and is passed to service")
        void contextShouldPassAuthenticatedUsername() throws Exception {
            when(ragRetrievalService.retrieveContext(any(), eq("tech.smith")))
                    .thenReturn(emptyRetrievalResponse());

            mockMvc.perform(post("/api/ai/context")
                            .with(authentication(stringPrincipal("tech.smith", NHAN_VIEN)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"query\": \"Badge access to HQ\", \"limit\": 5, \"minScore\": 0.5}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));

            verify(ragRetrievalService).retrieveContext(any(), eq("tech.smith"));
        }

        @Test
        @DisplayName("Unauthenticated request is rejected with 401")
        void contextShouldRejectUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/ai/context")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"query\": \"Badge access to HQ\"}"))
                    .andExpect(status().isUnauthorized());

            verify(ragRetrievalService, never()).retrieveContext(any(), any());
        }
    }

    @Nested
    @DisplayName("Privileged access preserved")
    class PrivilegedAccessTests {

        @Test
        @DisplayName("ADMIN username is passed through so all-department policy still applies")
        void adminShouldPassOwnUsername() throws Exception {
            when(ragSearchService.search(any(), eq("admin")))
                    .thenReturn(emptySearchResponse());

            mockMvc.perform(get("/api/ai/search")
                            .with(authentication(stringPrincipal("admin", ADMIN)))
                            .param("query", "Badge access to HQ"))
                    .andExpect(status().isOk());

            // The service, not the controller, decides that ADMIN sees all departments.
            verify(ragSearchService).search(any(), eq("admin"));
        }

        @Test
        @DisplayName("GET /api/ai/search/admin still delegates to the unfiltered admin path")
        void adminSearchEndpointRemainsUnfiltered() throws Exception {
            when(ragSearchService.searchAdmin(any())).thenReturn(emptySearchResponse());

            mockMvc.perform(get("/api/ai/search/admin")
                            .with(authentication(stringPrincipal("admin", ADMIN)))
                            .param("query", "Badge access to HQ"))
                    .andExpect(status().isOk());

            verify(ragSearchService).searchAdmin(any());
        }

        @Test
        @DisplayName("Non-admin cannot use the admin search endpoint")
        void nonAdminCannotUseAdminSearch() throws Exception {
            mockMvc.perform(get("/api/ai/search/admin")
                            .with(authentication(stringPrincipal("tech.smith", NHAN_VIEN)))
                            .param("query", "Badge access to HQ"))
                    .andExpect(status().isForbidden());

            verify(ragSearchService, never()).searchAdmin(any());
        }
    }

    /**
     * PHASE 0 (additive): the only pre-existing forbidden-role assertion used ROLE_VIEWER, which
     * is not one of the four business roles. These cases pin the actual business-role boundaries
     * declared on the RAG controllers, so a future authorization change cannot quietly widen them.
     * Purely additive: no existing test above is modified.
     */
    @Nested
    @DisplayName("Business-role boundaries on the RAG controllers")
    class BusinessRoleBoundaryTests {

        private static final List<GrantedAuthority> GIAM_DOC =
                List.of(new SimpleGrantedAuthority("ROLE_GIAM_DOC"));

        private static final List<GrantedAuthority> TRUONG_PHONG =
                List.of(new SimpleGrantedAuthority("ROLE_TRUONG_PHONG"));

        @Test
        @DisplayName("Each business role is admitted to GET /api/ai/search")
        void everyBusinessRoleMaySearch() throws Exception {
            when(ragSearchService.search(any(), any())).thenReturn(emptySearchResponse());

            for (List<GrantedAuthority> role : List.of(ADMIN, GIAM_DOC, TRUONG_PHONG, NHAN_VIEN)) {
                mockMvc.perform(get("/api/ai/search")
                                .with(authentication(stringPrincipal("business.user", role)))
                                .param("query", "Badge access to HQ"))
                        .andExpect(status().isOk());
            }
        }

        @Test
        @DisplayName("GIAM_DOC is rejected by the ADMIN-only search endpoint")
        void giamDocCannotUseAdminSearch() throws Exception {
            mockMvc.perform(get("/api/ai/search/admin")
                            .with(authentication(stringPrincipal("giamdoc", GIAM_DOC)))
                            .param("query", "Badge access to HQ"))
                    .andExpect(status().isForbidden());

            verify(ragSearchService, never()).searchAdmin(any());
        }

        @Test
        @DisplayName("TRUONG_PHONG may read embedding status, NHAN_VIEN may not")
        void embeddingStatusAllowsManagerButNotStaff() throws Exception {
            when(ragSearchService.getEmbeddingStatus()).thenReturn(null);

            mockMvc.perform(get("/api/ai/embeddings/status")
                            .with(authentication(stringPrincipal("tp_it", TRUONG_PHONG))))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/ai/embeddings/status")
                            .with(authentication(stringPrincipal("nv_it", NHAN_VIEN))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Only ADMIN may trigger the manual embedding job")
        void embeddingProcessIsAdminOnly() throws Exception {
            mockMvc.perform(post("/api/ai/embeddings/process")
                            .with(authentication(stringPrincipal("tp_it", TRUONG_PHONG))))
                    .andExpect(status().isForbidden());

            verify(embeddingSchedulerService, never()).triggerEmbeddingJob();
        }
    }

    // ============================================================
    // Helpers
    // ============================================================
    private SearchResponse emptySearchResponse() {
        return SearchResponse.builder()
                .query("Badge access to HQ")
                .totalResults(0)
                .results(List.<SearchResult>of())
                .build();
    }

    private RetrievalResponse emptyRetrievalResponse() {
        return RetrievalResponse.builder()
                .query("Badge access to HQ")
                .totalResults(0)
                .sources(List.of())
                .status("success")
                .metadata(new RetrievalMetadata(1L, "nomic-embed-text", 0, "no_results"))
                .build();
    }
}

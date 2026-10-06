package com.example.ticketing.ai;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.example.ticketing.ai.AiAssistantDto.AnswerResponse;
import com.example.ticketing.ai.AiAssistantDto.ResponseMetadata;
import com.example.ticketing.ai.AiAssistantDto.SourceReference;
import com.example.ticketing.auth.JwtService;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.config.SecurityConfig;
import com.example.ticketing.security.JwtBlacklistService;

/**
 * Unit tests for AiAssistantController.
 * Tests endpoint behavior without requiring real Ollama or database.
 */
@WebMvcTest(AiAssistantController.class)
@Import(SecurityConfig.class)
class AiAssistantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LlmGenerationService llmGenerationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtBlacklistService jwtBlacklistService;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    private static final String ENDPOINT = "/api/ai/ask";

    /** Authority satisfying the endpoint's @PreAuthorize ADMIN/… rule. */
    private static final List<org.springframework.security.core.GrantedAuthority> ADMIN_AUTHORITY =
            List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"));

    @Nested
    @DisplayName("Authentication Tests")
    class AuthenticationTests {

        @Test
        @DisplayName("Unauthenticated request should be rejected with 401")
        void unauthenticatedRequestShouldBeRejected() throws Exception {
            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"How to reset password?\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Authenticated request should reach the service")
        @WithMockUser(username = "testuser", roles = {"ADMIN"})
        void authenticatedRequestShouldReachService() throws Exception {
            // Arrange
            AnswerResponse mockResponse = createMockResponse("Test answer");
            when(llmGenerationService.generateAnswer(any(), eq("testuser")))
                    .thenReturn(mockResponse);

            // Act & Assert
            mockMvc.perform(post(ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"How to reset password?\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.answer").value("Test answer"))
                    .andExpect(jsonPath("$.contextCount").value(1))
                    .andExpect(jsonPath("$.hasContext").value(true));

            // Verify service was called
            verify(llmGenerationService).generateAnswer(any(), eq("testuser"));
        }
    }

    @Nested
    @DisplayName("Authorization Tests")
    class AuthorizationTests {

        @Test
        @DisplayName("ADMIN role should be allowed")
        @WithMockUser(username = "admin", roles = {"ADMIN"})
        void adminRoleShouldBeAllowed() throws Exception {
            AnswerResponse mockResponse = createMockResponse("Admin answer");
            when(llmGenerationService.generateAnswer(any(), eq("admin")))
                    .thenReturn(mockResponse);

            mockMvc.perform(post(ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"Test question\"}"))
                    .andExpect(status().isOk());

            verify(llmGenerationService).generateAnswer(any(), eq("admin"));
        }

        @Test
        @DisplayName("GIAM_DOC role should be allowed")
        @WithMockUser(username = "giamdoc", roles = {"GIAM_DOC"})
        void giamDocRoleShouldBeAllowed() throws Exception {
            AnswerResponse mockResponse = createMockResponse("Giam Doc answer");
            when(llmGenerationService.generateAnswer(any(), eq("giamdoc")))
                    .thenReturn(mockResponse);

            mockMvc.perform(post(ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"Test question\"}"))
                    .andExpect(status().isOk());

            verify(llmGenerationService).generateAnswer(any(), eq("giamdoc"));
        }

        @Test
        @DisplayName("TRUONG_PHONG role should be allowed")
        @WithMockUser(username = "truongphong", roles = {"TRUONG_PHONG"})
        void truongPhongRoleShouldBeAllowed() throws Exception {
            AnswerResponse mockResponse = createMockResponse("Truong Phong answer");
            when(llmGenerationService.generateAnswer(any(), eq("truongphong")))
                    .thenReturn(mockResponse);

            mockMvc.perform(post(ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"Test question\"}"))
                    .andExpect(status().isOk());

            verify(llmGenerationService).generateAnswer(any(), eq("truongphong"));
        }

        @Test
        @DisplayName("NHAN_VIEN role should be allowed")
        @WithMockUser(username = "nhanvien", roles = {"NHAN_VIEN"})
        void nhanVienRoleShouldBeAllowed() throws Exception {
            AnswerResponse mockResponse = createMockResponse("Nhan Vien answer");
            when(llmGenerationService.generateAnswer(any(), eq("nhanvien")))
                    .thenReturn(mockResponse);

            mockMvc.perform(post(ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"Test question\"}"))
                    .andExpect(status().isOk());

            verify(llmGenerationService).generateAnswer(any(), eq("nhanvien"));
        }

        @Test
        @DisplayName("Unknown role should be rejected with 403")
        @WithMockUser(username = "unknown", roles = {"UNKNOWN_ROLE"})
        void unknownRoleShouldBeRejected() throws Exception {
            mockMvc.perform(post(ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"Test question\"}"))
                    .andExpect(status().isForbidden());

            // Service should not be called
            verify(llmGenerationService, never()).generateAnswer(any(), any());
        }
    }

    @Nested
    @DisplayName("Request Validation Tests")
    class RequestValidationTests {

        @Test
        @DisplayName("Question with less than 5 characters should be rejected")
        @WithMockUser(username = "testuser", roles = {"ADMIN"})
        void shortQuestionShouldBeRejected() throws Exception {
            mockMvc.perform(post(ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"Hi\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Empty question should be rejected")
        @WithMockUser(username = "testuser", roles = {"ADMIN"})
        void emptyQuestionShouldBeRejected() throws Exception {
            mockMvc.perform(post(ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Missing question should be rejected")
        @WithMockUser(username = "testuser", roles = {"ADMIN"})
        void missingQuestionShouldBeRejected() throws Exception {
            mockMvc.perform(post(ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Default limit and minScore should be used when not provided")
        @WithMockUser(username = "testuser", roles = {"ADMIN"})
        void defaultParametersShouldBeUsed() throws Exception {
            AnswerResponse mockResponse = createMockResponse("Answer");
            when(llmGenerationService.generateAnswer(any(), eq("testuser")))
                    .thenReturn(mockResponse);

            mockMvc.perform(post(ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"What is Docker?\"}"))
                    .andExpect(status().isOk());

            verify(llmGenerationService).generateAnswer(
                    argThat(req -> 
                        req.getQuestion().equals("What is Docker?") &&
                        req.getLimit() == 5 && // default
                        req.getMinScore() == 0.5 // default
                    ),
                    eq("testuser")
            );
        }

        @Test
        @DisplayName("Custom limit and minScore should be passed to service")
        @WithMockUser(username = "testuser", roles = {"ADMIN"})
        void customParametersShouldBePassed() throws Exception {
            AnswerResponse mockResponse = createMockResponse("Answer");
            when(llmGenerationService.generateAnswer(any(), eq("testuser")))
                    .thenReturn(mockResponse);

            mockMvc.perform(post(ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"Test question\", \"limit\": 3, \"minScore\": 0.7}"))
                    .andExpect(status().isOk());

            verify(llmGenerationService).generateAnswer(
                    argThat(req -> 
                        req.getLimit() == 3 &&
                        req.getMinScore() == 0.7
                    ),
                    eq("testuser")
            );
        }
    }

    @Nested
    @DisplayName("Service Response Tests")
    class ServiceResponseTests {

        @Test
        @DisplayName("Service response should be returned correctly")
        @WithMockUser(username = "testuser", roles = {"ADMIN"})
        void serviceResponseShouldBeReturned() throws Exception {
            AnswerResponse mockResponse = AnswerResponse.builder()
                    .answer("PostgreSQL is a database system.")
                    .contextCount(2)
                    .sources(List.of(
                            new SourceReference("TKT-001", "PostgreSQL issue", 0.85),
                            new SourceReference("TKT-002", "Database connection", 0.78)
                    ))
                    .hasContext(true)
                    .metadata(new ResponseMetadata(150, "qwen3:4b", "department_filtered"))
                    .build();

            when(llmGenerationService.generateAnswer(any(), eq("testuser")))
                    .thenReturn(mockResponse);

            mockMvc.perform(post(ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"What is PostgreSQL?\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.answer").value("PostgreSQL is a database system."))
                    .andExpect(jsonPath("$.contextCount").value(2))
                    .andExpect(jsonPath("$.hasContext").value(true))
                    .andExpect(jsonPath("$.sources[0].ticketNumber").value("TKT-001"))
                    .andExpect(jsonPath("$.sources[0].similarityScore").value(0.85))
                    .andExpect(jsonPath("$.metadata.model").value("qwen3:4b"));
        }

        @Test
        @DisplayName("Response without context should be returned correctly")
        @WithMockUser(username = "testuser", roles = {"ADMIN"})
        void responseWithoutContextShouldBeReturned() throws Exception {
            AnswerResponse mockResponse = AnswerResponse.builder()
                    .answer("Docker is a containerization platform.")
                    .contextCount(0)
                    .sources(List.of())
                    .hasContext(false)
                    .metadata(new ResponseMetadata(100, "qwen3:4b", "no_results"))
                    .build();

            when(llmGenerationService.generateAnswer(any(), eq("testuser")))
                    .thenReturn(mockResponse);

            mockMvc.perform(post(ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"What is Docker?\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.answer").value("Docker is a containerization platform."))
                    .andExpect(jsonPath("$.contextCount").value(0))
                    .andExpect(jsonPath("$.hasContext").value(false))
                    .andExpect(jsonPath("$.sources").isEmpty());
        }
    }

    @Nested
    @DisplayName("Exception Propagation Tests")
    class ExceptionPropagationTests {

        @Test
        @DisplayName("LlmGenerationException should propagate (not be swallowed)")
        @WithMockUser(username = "testuser", roles = {"ADMIN"})
        void llmGenerationExceptionShouldPropagate() throws Exception {
            when(llmGenerationService.generateAnswer(any(), eq("testuser")))
                    .thenThrow(LlmGenerationException.ollamaUnavailable(
                            new RuntimeException("Connection refused")));

            // Exception should propagate - global handler will convert to appropriate status
            // In unit test, we verify the exception is NOT caught by the controller
            mockMvc.perform(post(ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"Test question\"}"))
                    .andExpect(result -> {
                        // Verify exception is thrown, not swallowed
                        Throwable ex = result.getResolvedException();
                        if (ex == null) {
                            throw new AssertionError("Expected exception to propagate");
                        }
                        // The exception should be LlmGenerationException or wrapped
                        if (!(ex instanceof LlmGenerationException) && 
                            ex.getCause() instanceof LlmGenerationException) {
                            // This is acceptable - wrapped exception
                        } else if (!(ex instanceof LlmGenerationException)) {
                            throw new AssertionError("Expected LlmGenerationException, got: " + ex.getClass().getName());
                        }
                    });
        }

        @Test
        @DisplayName("Timeout exception should propagate")
        @WithMockUser(username = "testuser", roles = {"ADMIN"})
        void timeoutExceptionShouldPropagate() throws Exception {
            when(llmGenerationService.generateAnswer(any(), eq("testuser")))
                    .thenThrow(LlmGenerationException.timeout(
                            new RuntimeException("Request timeout")));

            mockMvc.perform(post(ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"Test question\"}"))
                    .andExpect(result -> {
                        Throwable ex = result.getResolvedException();
                        if (ex == null) {
                            throw new AssertionError("Expected exception to propagate");
                        }
                    });
        }
    }

    // ============================================================
    // Username logging tests (regression: user=null in logs)
    // ============================================================

    /**
     * Regression tests for the AI logging username bug.
     *
     * JwtAuthenticationFilter sets the principal to a String (the JWT subject),
     * NOT a UserDetails instance. @AuthenticationPrincipal UserDetails therefore
     * resolved to null, and the AI request/response logs printed user=null.
     *
     * The Authentication#getName() approach must resolve the username regardless
     * of whether the principal is a String or a UserDetails instance.
     */
    @Nested
    @DisplayName("Authenticated Username Logging Tests")
    class UsernameLoggingTests {

        @Test
        @DisplayName("Username should resolve when principal is a String (JWT filter behavior)")
        void usernameShouldResolveWithStringPrincipal() throws Exception {
            AnswerResponse mockResponse = createMockResponse("Answer");
            when(llmGenerationService.generateAnswer(any(), eq("tech.smith")))
                    .thenReturn(mockResponse);

            // withAuthentication() with a String principal mirrors the real
            // JwtAuthenticationFilter, which stores UsernamePasswordAuthenticationToken
            // with a String principal. The ADMIN authority is required to satisfy
            // the endpoint's @PreAuthorize rule.
            mockMvc.perform(post(ENDPOINT)
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    "tech.smith", null, ADMIN_AUTHORITY)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"How to reset password?\"}"))
                    .andExpect(status().isOk());

            // The username must be passed to the service, NOT null.
            verify(llmGenerationService).generateAnswer(any(), eq("tech.smith"));
        }

        @Test
        @DisplayName("Username should resolve when principal is a UserDetails instance")
        void usernameShouldResolveWithUserDetailsPrincipal() throws Exception {
            AnswerResponse mockResponse = createMockResponse("Answer");
            when(llmGenerationService.generateAnswer(any(), eq("detail.user")))
                    .thenReturn(mockResponse);

            org.springframework.security.core.userdetails.User userDetails =
                    new org.springframework.security.core.userdetails.User(
                            "detail.user", "n/a",
                            List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));

            mockMvc.perform(post(ENDPOINT)
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    userDetails, null, ADMIN_AUTHORITY)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"How to reset password?\"}"))
                    .andExpect(status().isOk());

            verify(llmGenerationService).generateAnswer(any(), eq("detail.user"));
        }

        @Test
        @DisplayName("Username must never be sourced from the request body")
        void usernameMustNotComeFromRequestBody() throws Exception {
            AnswerResponse mockResponse = createMockResponse("Answer");
            when(llmGenerationService.generateAnswer(any(), any()))
                    .thenReturn(mockResponse);

            // A client-supplied username field must be ignored for logging/authz.
            mockMvc.perform(post(ENDPOINT)
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    "real.user", null, ADMIN_AUTHORITY)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"How to reset password?\", "
                                    + "\"username\": \"attacker.injected\"}"))
                    .andExpect(status().isOk());

            // The authenticated principal wins over any client-supplied value.
            verify(llmGenerationService).generateAnswer(any(), eq("real.user"));
        }
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    private AnswerResponse createMockResponse(String answer) {
        return AnswerResponse.builder()
                .answer(answer)
                .contextCount(1)
                .sources(List.of(new SourceReference("TKT-001", "Test ticket", 0.85)))
                .hasContext(true)
                .metadata(new ResponseMetadata(100, "qwen3:4b", "department_filtered"))
                .build();
    }
}

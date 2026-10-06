package com.example.ticketing.exception;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.example.ticketing.ai.AiAssistantController;
import com.example.ticketing.ai.AiAssistantDto.AnswerResponse;
import com.example.ticketing.ai.LlmGenerationException;
import com.example.ticketing.ai.LlmGenerationService;
import com.example.ticketing.auth.JwtService;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.config.SecurityConfig;
import com.example.ticketing.security.JwtBlacklistService;

/**
 * Tests for LLM exception handling in GlobalExceptionHandler.
 * Verifies HTTP status mapping and safe error responses.
 */
@WebMvcTest(AiAssistantController.class)
@Import(SecurityConfig.class)
class LlmGenerationExceptionHandlerTest {

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

    @Nested
    @DisplayName("HTTP Status Mapping Tests")
    class HttpStatusMappingTests {

        @Test
        @DisplayName("LLM_UNAVAILABLE should return HTTP 503")
        @WithMockUser(username = "testuser", roles = {"ADMIN"})
        void llmUnavailableShouldReturn503() throws Exception {
            when(llmGenerationService.generateAnswer(any(), any()))
                    .thenThrow(LlmGenerationException.ollamaUnavailable(new RuntimeException("Connection refused")));

            mockMvc.perform(post(ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"Test question\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.errorCode").value("LLM_UNAVAILABLE"))
                    .andExpect(jsonPath("$.message").value("AI service temporarily unavailable. Please try again later."));
        }

        @Test
        @DisplayName("LLM_TIMEOUT should return HTTP 504")
        @WithMockUser(username = "testuser", roles = {"ADMIN"})
        void llmTimeoutShouldReturn504() throws Exception {
            when(llmGenerationService.generateAnswer(any(), any()))
                    .thenThrow(LlmGenerationException.timeout(new RuntimeException("Timeout")));

            mockMvc.perform(post(ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"Test question\"}"))
                    .andExpect(status().isGatewayTimeout())
                    .andExpect(jsonPath("$.errorCode").value("LLM_TIMEOUT"))
                    .andExpect(jsonPath("$.message").value("AI service took too long to respond. Please try again."));
        }

        @Test
        @DisplayName("LLM_ERROR_RESPONSE should return HTTP 502")
        @WithMockUser(username = "testuser", roles = {"ADMIN"})
        void llmErrorResponseShouldReturn502() throws Exception {
            when(llmGenerationService.generateAnswer(any(), any()))
                    .thenThrow(LlmGenerationException.nonSuccessfulResponse(500));

            mockMvc.perform(post(ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"Test question\"}"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.errorCode").value("LLM_ERROR_RESPONSE"))
                    .andExpect(jsonPath("$.message").value("AI service returned an unexpected response. Please try again."));
        }

        @Test
        @DisplayName("LLM_MALFORMED_RESPONSE should return HTTP 502")
        @WithMockUser(username = "testuser", roles = {"ADMIN"})
        void llmMalformedResponseShouldReturn502() throws Exception {
            when(llmGenerationService.generateAnswer(any(), any()))
                    .thenThrow(LlmGenerationException.malformedResponse(new RuntimeException("Parse error")));

            mockMvc.perform(post(ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"Test question\"}"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.errorCode").value("LLM_MALFORMED_RESPONSE"))
                    .andExpect(jsonPath("$.message").value("AI service returned an invalid response. Please try again."));
        }

        @Test
        @DisplayName("LLM_EMPTY_RESPONSE should return HTTP 502")
        @WithMockUser(username = "testuser", roles = {"ADMIN"})
        void llmEmptyResponseShouldReturn502() throws Exception {
            when(llmGenerationService.generateAnswer(any(), any()))
                    .thenThrow(LlmGenerationException.emptyResponse());

            mockMvc.perform(post(ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"Test question\"}"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.errorCode").value("LLM_EMPTY_RESPONSE"))
                    .andExpect(jsonPath("$.message").value("AI service did not generate a response. Please try again."));
        }
    }

    @Nested
    @DisplayName("Error Response Format Tests")
    class ErrorResponseFormatTests {

        @Test
        @DisplayName("Error response should use existing ErrorResponse format")
        @WithMockUser(username = "testuser", roles = {"ADMIN"})
        void errorResponseShouldUseExistingFormat() throws Exception {
            when(llmGenerationService.generateAnswer(any(), any()))
                    .thenThrow(LlmGenerationException.ollamaUnavailable(new RuntimeException("Connection refused")));

            mockMvc.perform(post(ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"Test question\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.errorCode").exists())
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.path").value(ENDPOINT));
        }

        @Test
        @DisplayName("Error response should not expose internal details")
        @WithMockUser(username = "testuser", roles = {"ADMIN"})
        void errorResponseShouldNotExposeInternalDetails() throws Exception {
            when(llmGenerationService.generateAnswer(any(), any()))
                    .thenThrow(LlmGenerationException.ollamaUnavailable(
                            new RuntimeException("Connection refused to localhost:11434")));

            mockMvc.perform(post(ENDPOINT)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\": \"Test question\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(result -> {
                        String content = result.getResponse().getContentAsString();
                        // Should not contain internal details
                        assert !content.contains("Connection refused") : "Should not expose connection details";
                        assert !content.contains("localhost") : "Should not expose internal URLs";
                        assert !content.contains("11434") : "Should not expose internal ports";
                    });
        }
    }
}

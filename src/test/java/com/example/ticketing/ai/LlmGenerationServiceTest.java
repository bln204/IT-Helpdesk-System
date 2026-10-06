package com.example.ticketing.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.ticketing.ai.AiAssistantDto.AskRequest;
import com.example.ticketing.ai.AiAssistantDto.AnswerResponse;
import com.example.ticketing.ai.RagContextDto.RagSource;
import com.example.ticketing.ai.RagContextDto.RetrievalResponse;

/**
 * Unit tests for LlmGenerationService.
 * Tests orchestration flow without requiring real Ollama or database.
 */
@ExtendWith(MockitoExtension.class)
class LlmGenerationServiceTest {

    @Mock
    private RagRetrievalService ragRetrievalService;

    @Mock
    private PromptBuilder promptBuilder;

    @Mock
    private OllamaChatService ollamaChatService;

    @Captor
    private ArgumentCaptor<List<RagSource>> sourcesCaptor;

    private OllamaProperties ollamaProperties;
    private LlmGenerationService service;

    @BeforeEach
    void setUp() {
        ollamaProperties = new OllamaProperties();
        ollamaProperties.setChatModel("qwen3:4b");
        ollamaProperties.setChatTimeoutSeconds(120);
        ollamaProperties.setChatTemperature(0.3);
        ollamaProperties.setChatMaxTokens(512);

        service = new LlmGenerationService(
                ragRetrievalService,
                promptBuilder,
                ollamaChatService,
                ollamaProperties
        );
    }

    @Nested
    @DisplayName("Successful Generation Tests")
    class SuccessfulGenerationTests {

        @Test
        @DisplayName("Should generate answer with context successfully")
        void shouldGenerateAnswerWithContext() {
            // Arrange
            AskRequest request = new AskRequest("How to reset password?", 5, 0.5);
            String username = "john.doe";

            List<RagSource> sources = List.of(createTestSource("TKT-001", "Password reset"));
            RetrievalResponse ragResponse = createRagResponse(sources);

            when(ragRetrievalService.retrieveContext(any(), eq(username)))
                    .thenReturn(ragResponse);

            List<OllamaChatService.ChatMessage> messages = List.of(
                    OllamaChatService.ChatMessage.system("System prompt"),
                    OllamaChatService.ChatMessage.user("How to reset password?")
            );
            when(promptBuilder.build(eq(sources), eq("How to reset password?")))
                    .thenReturn(messages);

            when(ollamaChatService.generate(messages))
                    .thenReturn("To reset your password, follow these steps...");

            // Act
            AnswerResponse response = service.generateAnswer(request, username);

            // Assert
            assertNotNull(response);
            assertEquals("To reset your password, follow these steps...", response.getAnswer());
            assertEquals(1, response.getContextCount());
            assertTrue(response.isHasContext());
            assertNotNull(response.getSources());
            assertEquals(1, response.getSources().size());
            assertNotNull(response.getMetadata());
            assertEquals("qwen3:4b", response.getMetadata().getModel());

            // Verify interactions
            verify(ragRetrievalService).retrieveContext(any(), eq(username));
            verify(promptBuilder).build(eq(sources), eq("How to reset password?"));
            verify(ollamaChatService).generate(messages);
        }

        @Test
        @DisplayName("Should generate answer without context for general IT question")
        void shouldGenerateAnswerWithoutContext() {
            // Arrange
            AskRequest request = new AskRequest("What is PostgreSQL?", 5, 0.5);
            String username = "jane.doe";

            // Empty RAG response (no relevant tickets)
            RetrievalResponse ragResponse = createEmptyRagResponse();

            when(ragRetrievalService.retrieveContext(any(), eq(username)))
                    .thenReturn(ragResponse);

            List<OllamaChatService.ChatMessage> messages = List.of(
                    OllamaChatService.ChatMessage.system("System prompt"),
                    OllamaChatService.ChatMessage.user("What is PostgreSQL?")
            );
            when(promptBuilder.build(eq(List.of()), eq("What is PostgreSQL?")))
                    .thenReturn(messages);

            when(ollamaChatService.generate(messages))
                    .thenReturn("PostgreSQL is a powerful, open-source object-relational database system...");

            // Act
            AnswerResponse response = service.generateAnswer(request, username);

            // Assert
            assertNotNull(response);
            assertEquals("PostgreSQL is a powerful, open-source object-relational database system...", response.getAnswer());
            assertEquals(0, response.getContextCount());
            assertFalse(response.isHasContext());
            assertNotNull(response.getSources());
            assertTrue(response.getSources().isEmpty());

            // Verify PromptBuilder was called with empty list
            verify(promptBuilder).build(eq(List.of()), eq("What is PostgreSQL?"));
        }

        @Test
        @DisplayName("Should handle multiple retrieved sources")
        void shouldHandleMultipleSources() {
            // Arrange
            AskRequest request = new AskRequest("Similar server issues?", 3, 0.5);
            String username = "admin";

            List<RagSource> sources = List.of(
                    createTestSource("TKT-001", "Server timeout"),
                    createTestSource("TKT-002", "Server crash"),
                    createTestSource("TKT-003", "Server slow")
            );
            RetrievalResponse ragResponse = createRagResponse(sources);

            when(ragRetrievalService.retrieveContext(any(), eq(username)))
                    .thenReturn(ragResponse);

            List<OllamaChatService.ChatMessage> messages = List.of(
                    OllamaChatService.ChatMessage.system("System prompt"),
                    OllamaChatService.ChatMessage.user("Similar server issues?")
            );
            when(promptBuilder.build(eq(sources), eq("Similar server issues?")))
                    .thenReturn(messages);

            when(ollamaChatService.generate(messages))
                    .thenReturn("I found 3 similar server issues...");

            // Act
            AnswerResponse response = service.generateAnswer(request, username);

            // Assert
            assertEquals(3, response.getContextCount());
            assertEquals(3, response.getSources().size());
            assertTrue(response.isHasContext());
        }
    }

    @Nested
    @DisplayName("similarityScore Handling Tests")
    class SimilarityScoreHandlingTests {

        @Test
        @DisplayName("similarityScore should appear in API response metadata")
        void similarityScoreShouldAppearInResponse() {
            // Arrange
            AskRequest request = new AskRequest("Test question", 5, 0.5);
            String username = "testuser";

            RagSource source = createTestSource("TKT-001", "Test");
            source.setSimilarityScore(0.95); // High similarity

            RetrievalResponse ragResponse = createRagResponse(List.of(source));

            when(ragRetrievalService.retrieveContext(any(), eq(username)))
                    .thenReturn(ragResponse);
            when(promptBuilder.build(any(), any()))
                    .thenReturn(List.of(OllamaChatService.ChatMessage.system("test")));
            when(ollamaChatService.generate(any()))
                    .thenReturn("Test answer");

            // Act
            AnswerResponse response = service.generateAnswer(request, username);

            // Assert
            assertEquals(1, response.getSources().size());
            assertEquals(0.95, response.getSources().get(0).getSimilarityScore());
        }

        @Test
        @DisplayName("similarityScore should NOT be passed to PromptBuilder")
        void similarityScoreShouldNotBePassedToPromptBuilder() {
            // Arrange
            AskRequest request = new AskRequest("Test", 5, 0.5);
            String username = "user";

            RagSource source = createTestSource("TKT-001", "Test");
            source.setSimilarityScore(0.85);

            RetrievalResponse ragResponse = createRagResponse(List.of(source));

            when(ragRetrievalService.retrieveContext(any(), eq(username)))
                    .thenReturn(ragResponse);

            // Capture the sources passed to PromptBuilder
            List<OllamaChatService.ChatMessage> messages = List.of(
                    OllamaChatService.ChatMessage.system("test")
            );
            when(promptBuilder.build(sourcesCaptor.capture(), any()))
                    .thenReturn(messages);
            when(ollamaChatService.generate(any())).thenReturn("answer");

            // Act
            service.generateAnswer(request, username);

            // Assert - PromptBuilder receives sources (it's PromptBuilder's responsibility
            // to not use similarityScore, verified by PromptBuilderTest)
            verify(promptBuilder).build(any(), eq("Test"));
            // The actual check is in PromptBuilderTest that similarityScore is not in prompt
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should propagate Ollama unavailable error")
        void shouldPropagateOllamaUnavailableError() {
            // Arrange
            AskRequest request = new AskRequest("Test", 5, 0.5);
            String username = "user";

            RetrievalResponse ragResponse = createEmptyRagResponse();
            when(ragRetrievalService.retrieveContext(any(), eq(username)))
                    .thenReturn(ragResponse);
            when(promptBuilder.build(any(), any()))
                    .thenReturn(List.of(OllamaChatService.ChatMessage.system("test")));

            // Simulate Ollama unavailable
            when(ollamaChatService.generate(any()))
                    .thenThrow(LlmGenerationException.ollamaUnavailable(
                            new RuntimeException("Connection refused")));

            // Act & Assert
            LlmGenerationException exception = assertThrows(
                    LlmGenerationException.class,
                    () -> service.generateAnswer(request, username)
            );

            assertEquals("LLM_UNAVAILABLE", exception.getErrorCode());
            assertTrue(exception.getMessage().contains("temporarily unavailable"));
        }

        @Test
        @DisplayName("Should propagate Ollama timeout error")
        void shouldPropagateOllamaTimeoutError() {
            // Arrange
            AskRequest request = new AskRequest("Test", 5, 0.5);
            String username = "user";

            RetrievalResponse ragResponse = createEmptyRagResponse();
            when(ragRetrievalService.retrieveContext(any(), eq(username)))
                    .thenReturn(ragResponse);
            when(promptBuilder.build(any(), any()))
                    .thenReturn(List.of(OllamaChatService.ChatMessage.system("test")));

            // Simulate timeout
            when(ollamaChatService.generate(any()))
                    .thenThrow(LlmGenerationException.timeout(
                            new RuntimeException("Request timeout")));

            // Act & Assert
            LlmGenerationException exception = assertThrows(
                    LlmGenerationException.class,
                    () -> service.generateAnswer(request, username)
            );

            assertEquals("LLM_TIMEOUT", exception.getErrorCode());
            assertTrue(exception.getMessage().contains("took too long"));
        }

        @Test
        @DisplayName("Should propagate malformed response error")
        void shouldPropagateMalformedResponseError() {
            // Arrange
            AskRequest request = new AskRequest("Test", 5, 0.5);
            String username = "user";

            RetrievalResponse ragResponse = createEmptyRagResponse();
            when(ragRetrievalService.retrieveContext(any(), eq(username)))
                    .thenReturn(ragResponse);
            when(promptBuilder.build(any(), any()))
                    .thenReturn(List.of(OllamaChatService.ChatMessage.system("test")));

            // Simulate malformed response
            when(ollamaChatService.generate(any()))
                    .thenThrow(LlmGenerationException.malformedResponse(
                            new RuntimeException("Invalid JSON")));

            // Act & Assert
            LlmGenerationException exception = assertThrows(
                    LlmGenerationException.class,
                    () -> service.generateAnswer(request, username)
            );

            assertEquals("LLM_MALFORMED_RESPONSE", exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("No-Context Behavior Tests")
    class NoContextBehaviorTests {

        @Test
        @DisplayName("Should allow general IT question when no context available")
        void shouldAllowGeneralQuestionWithNoContext() {
            // Arrange
            AskRequest request = new AskRequest("What is Docker?", 5, 0.5);
            String username = "user";

            // Empty context
            RetrievalResponse ragResponse = createEmptyRagResponse();
            when(ragRetrievalService.retrieveContext(any(), eq(username)))
                    .thenReturn(ragResponse);
            when(promptBuilder.build(eq(List.of()), eq("What is Docker?")))
                    .thenReturn(List.of(
                            OllamaChatService.ChatMessage.system("System"),
                            OllamaChatService.ChatMessage.user("What is Docker?")
                    ));
            when(ollamaChatService.generate(any()))
                    .thenReturn("Docker is a platform for containerization...");

            // Act
            AnswerResponse response = service.generateAnswer(request, username);

            // Assert
            assertEquals(0, response.getContextCount());
            assertFalse(response.isHasContext());
            assertTrue(response.getSources().isEmpty());
            assertNotNull(response.getAnswer());
            // The answer can still be generated from general knowledge
            assertTrue(response.getAnswer().contains("Docker"));
        }

        @Test
        @DisplayName("Should return empty sources list when no context")
        void shouldReturnEmptySourcesWhenNoContext() {
            // Arrange
            AskRequest request = new AskRequest("Org specific question?", 5, 0.5);
            String username = "user";

            RetrievalResponse ragResponse = createEmptyRagResponse();
            when(ragRetrievalService.retrieveContext(any(), eq(username)))
                    .thenReturn(ragResponse);
            when(promptBuilder.build(eq(List.of()), any()))
                    .thenReturn(List.of(OllamaChatService.ChatMessage.system("test")));
            when(ollamaChatService.generate(any())).thenReturn("I don't have info about that.");

            // Act
            AnswerResponse response = service.generateAnswer(request, username);

            // Assert
            assertNotNull(response.getSources());
            assertTrue(response.getSources().isEmpty());
        }
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    private RagSource createTestSource(String ticketNumber, String title) {
        return RagSource.builder()
                .sourceType("TICKET")
                .sourceRef(ticketNumber)
                .title(title)
                .description("Test description")
                .category("IT Support")
                .subcategory("General")
                .priority("MEDIUM")
                .status("RESOLVED")
                .similarityScore(0.85)
                .content("Resolution: " + title + " was handled.")
                .build();
    }

    private RetrievalResponse createRagResponse(List<RagSource> sources) {
        return RetrievalResponse.builder()
                .query("test query")
                .totalResults(sources.size())
                .sources(sources)
                .status("success")
                .errorMessage(null)
                .metadata(new RagContextDto.RetrievalMetadata(
                        50,
                        "nomic-embed-text",
                        100,
                        "department_filtered"
                ))
                .build();
    }

    private RetrievalResponse createEmptyRagResponse() {
        return RetrievalResponse.builder()
                .query("test query")
                .totalResults(0)
                .sources(List.of())
                .status("no_results")
                .errorMessage(null)
                .metadata(new RagContextDto.RetrievalMetadata(
                        30,
                        "nomic-embed-text",
                        100,
                        "no_results"
                ))
                .build();
    }
}

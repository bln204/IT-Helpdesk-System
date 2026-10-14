package com.example.ticketing.ai;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ticketing.ai.AiAssistantDto.AnswerResponse;
import com.example.ticketing.ai.AiSessionDtos.ConfirmTicketRequest;
import com.example.ticketing.ai.AiSessionDtos.CreateSessionRequest;
import com.example.ticketing.ai.AiSessionDtos.MessageView;
import com.example.ticketing.ai.AiSessionDtos.SessionSummary;
import com.example.ticketing.ai.session.AiMessage;
import com.example.ticketing.ai.session.AiSession;
import com.example.ticketing.ai.session.AiSessionService;
import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.auth.UserRole;
import com.example.ticketing.ticket.Ticket;
import com.example.ticketing.ticket.TicketService;
import com.example.ticketing.ticket.TicketTypes.TicketCategory;
import com.example.ticketing.ticket.TicketTypes.TicketPriority;

import jakarta.validation.Valid;

/**
 * REST Controller for AI Assistant (LLM/RAG Generation).
 * 
 * This controller exposes the AI assistant endpoint that generates answers
 * using authorized RAG context from Ollama/Gemma3.
 * 
 * Architecture:
 * Controller -> AiAssistantPipeline -> QueryPlanner / QueryExecutor / TroubleshootService / TicketDraftService -> OllamaChatService
 *
 * Authorization:
 * - ADMIN, GIAM_DOC, TRUONG_PHONG, NHAN_VIEN can access this endpoint
 * - Role gates and row scope are applied by QueryExecutor and the per-domain query adapters
 */
@RestController
@RequestMapping("/api/ai")
@Validated
public class AiAssistantController {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantController.class);

    private final AiSessionService sessionService;
    private final TicketService ticketService;
    private final UserAccountRepository userAccountRepository;
    private final com.example.ticketing.ai.query.AiAssistantPipeline pipeline;

    public AiAssistantController(AiSessionService sessionService,
                                 TicketService ticketService,
                                 UserAccountRepository userAccountRepository,
                                 com.example.ticketing.ai.query.AiAssistantPipeline pipeline) {
        this.sessionService = sessionService;
        this.ticketService = ticketService;
        this.userAccountRepository = userAccountRepository;
        this.pipeline = pipeline;
    }

    /**
     * Ask the AI assistant a question.
     * 
     * The assistant uses authorized RAG context (filtered by department access)
     * to answer questions about tickets and general IT topics.
     * 
     * Authorization:
     * - ADMIN: Full access to all departments' ticket context
     * - GIAM_DOC: Full access to all departments' ticket context
     * - TRUONG_PHONG: Access to own department's ticket context only
     * - NHAN_VIEN: Access to own department's ticket context only
     * 
     * @param request The question request with optional limit and minScore parameters
     * @param userDetails The authenticated user (for authorization)
     * @param authentication The authenticated principal, used to obtain the username for logging
     * @return AI assistant answer with context metadata
     */
    @PostMapping("/ask")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<AnswerResponse> askQuestion(
            @Valid @RequestBody AiAssistantDto.AskRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            Authentication authentication) {

        // Username MUST come from the authenticated principal established by Spring Security.
        // JwtAuthenticationFilter stores the principal as a String, so @AuthenticationPrincipal
        // UserDetails resolves to null; Authentication#getName() is the project's established
        // idiom and always resolves. Never source this from the request DTO or client input.
        String username = (authentication != null) ? authentication.getName() : null;

        // Apply API defaults when client omits optional parameters
        // Jackson deserialization omits field initializers for absent JSON properties
        if (request.getLimit() == null) {
            request.setLimit(5);
        }
        if (request.getMinScore() == null) {
            // 0.65 chosen empirically: with the current multilingual
            // embedder (gemma3 + nomic-embed-text hybrid) tickets that
            // are topically unrelated score ~0.45-0.55 even when
            // keywords overlap ("laptop", "phong hop"). Bumping from
            // 0.5 -> 0.65 dramatically reduces "AI nói về ticket
            // khác" false positives without losing real matches.
            request.setMinScore(0.65);
        }

        // Log safe request metadata only (no question content)
        if (log.isInfoEnabled()) {
            log.info("AI assistant request from user: {}, question length: {}, limit: {}",
                    username,
                    request.getQuestion() != null ? request.getQuestion().length() : 0,
                    request.getLimit());
        }

        // Single path: query-plan pipeline (data lookup, self-help, ticket drafting).
        AnswerResponse response = pipeline.answer(request, username);

        // Log safe response metadata
        if (log.isInfoEnabled()) {
            log.info("AI assistant response: contextCount={}, hasContext={}, user={}",
                    response.getContextCount(),
                    response.isHasContext(),
                    username);
        }

        return ResponseEntity.ok(response);
    }

    // ============================================================
    // SESSION / MESSAGE ENDPOINTS
    // ============================================================

    /**
     * List the current user's recent (non-archived) AI sessions,
     * most-recent-first. Used to render the chat sidebar.
     */
    @GetMapping("/sessions")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<List<SessionSummary>> listSessions(
            Authentication authentication,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "30") int limit) {
        String username = (authentication != null) ? authentication.getName() : null;
        List<SessionSummary> out = sessionService.listActive(username, limit).stream()
                .map(s -> new SessionSummary(s.getId(), s.getTitle(),
                        s.getLastActiveAt(), s.getCreatedAt()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(out);
    }

    /**
     * Create a new (empty) session. Returns the new id so the client
     * can immediately start posting questions with sessionId set.
     */
    @PostMapping("/sessions")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<SessionSummary> createSession(
            @RequestBody(required = false) CreateSessionRequest body,
            Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : null;
        String title = body == null ? null : body.getTitle();
        UUID id = sessionService.getOrCreate(username, null, title);
        AiSession s = sessionService.listActive(username, 1).stream()
                .filter(x -> x.getId().equals(id)).findFirst().orElse(null);
        return ResponseEntity.ok(new SessionSummary(
                id, title, s == null ? null : s.getLastActiveAt(),
                s == null ? null : s.getCreatedAt()));
    }

    /**
     * Get the full chronological message history of a session.
     */
    @GetMapping("/sessions/{id}/messages")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<List<MessageView>> listMessages(
            @PathVariable("id") UUID id,
            Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : null;
        // Ownership check: load the session scoped to the user.
        sessionService.listActive(username, 1000).stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Session not found"));
        List<MessageView> out = sessionService.allMessages(id).stream()
                .map(this::toMessageView)
                .collect(Collectors.toList());
        return ResponseEntity.ok(out);
    }

    /**
     * Archive (soft-delete) a session. The session is hidden from
     * the sidebar but its messages stay in the database so the
     * existing chat history is not lost.
     */
    @DeleteMapping("/sessions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<Void> archiveSession(
            @PathVariable("id") UUID id,
            Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : null;
        sessionService.archive(id, username);
        return ResponseEntity.noContent().build();
    }

    /**
     * Confirm and create a real ticket from a TicketDraft the AI
     * proposed in a previous answer. Validates the four required
     * fields, calls the canonical TicketService, and returns a
     * TICKET_CREATED action so the frontend can show the new
     * ticket number.
     */
    @PostMapping("/sessions/{id}/confirm-ticket")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<AnswerResponse> confirmTicketDraft(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ConfirmTicketRequest request,
            Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : null;
        // Ownership check.
        sessionService.listActive(username, 1000).stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Session not found"));

        // Validate required fields.
        StringBuilder err = new StringBuilder();
        if (isBlank(request.getTitle())) err.append("title; ");
        if (isBlank(request.getDescription())) err.append("description; ");
        if (!isValidPriority(request.getPriority())) err.append("priority; ");
        if (!isValidCategory(request.getCategory())) err.append("category; ");
        if (err.length() > 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Missing or invalid fields: " + err);
        }

        // Build a Ticket entity the same way TicketController does,
        // then delegate to TicketService so the canonical creation
        // pipeline (number generation, audit, notifications, etc.)
        // runs.
        UserAccount user = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED, "User not found"));
        UserRole.Role role = user.getRole();

        Ticket t = new Ticket();
        t.setTitle(request.getTitle().trim());
        t.setDescription(request.getDescription().trim());
        t.setPriority(TicketPriority.valueOf(request.getPriority().toUpperCase(java.util.Locale.ROOT)));
        t.setCategory(TicketCategory.valueOf(request.getCategory().toUpperCase(java.util.Locale.ROOT)));
        t.setRequesterName(user.getDisplayName() == null ? username : user.getDisplayName());
        t.setRequesterEmail(user.getEmail());

        Ticket created = ticketService.createTicket(t, username, role);

        // Persist a system turn so the chat log shows what happened.
        try {
            String md = "{\"action\":\"TICKET_CREATED\",\"ticketNumber\":\""
                    + safe(created.getTicketNumber()) + "\"}";
            sessionService.append(id, username,
                    com.example.ticketing.ai.session.AiMessageRole.SYSTEM,
                    "Ticket " + created.getTicketNumber() + " đã được tạo.", md);
        } catch (RuntimeException ex) {
            // best effort
        }

        return ResponseEntity.ok(AnswerResponse.builder()
                .answer("Đã tạo ticket " + created.getTicketNumber() + " thành công.")
                .contextCount(0)
                .hasContext(false)
                .action("TICKET_CREATED")
                .actionPayload("{\"ticketNumber\":\""
                        + safe(created.getTicketNumber())
                        + "\",\"id\":" + created.getId() + "}")
                .sessionId(id.toString())
                .build());
    }

    private MessageView toMessageView(AiMessage m) {
        return new MessageView(m.getId(),
                m.getRole() == null ? null : m.getRole().name(),
                m.getContent(), m.getMetadata(), m.getCreatedAt());
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean isValidPriority(String s) {
        if (s == null) return false;
        try {
            TicketPriority p = TicketPriority.valueOf(s.toUpperCase(java.util.Locale.ROOT));
            return p != null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean isValidCategory(String s) {
        if (s == null) return false;
        try {
            TicketCategory c = TicketCategory.valueOf(s.toUpperCase(java.util.Locale.ROOT));
            return c != null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}

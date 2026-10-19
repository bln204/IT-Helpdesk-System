package com.example.ticketing.ai.session;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Owns ai_session + ai_message lifecycle: create new thread, append
 * a turn, load recent history. All access is scoped to the
 * authenticated user; cross-user access is forbidden.
 */
@Service
public class AiSessionService {

    private static final Logger log = LoggerFactory.getLogger(AiSessionService.class);

    /** Soft cap: number of recent messages fed into a prompt. */
    public static final int HISTORY_LIMIT = 20;

    private final AiSessionRepository sessionRepo;
    private final AiMessageRepository messageRepo;

    public AiSessionService(AiSessionRepository sessionRepo,
                            AiMessageRepository messageRepo) {
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
    }

    /**
     * Get-or-create a session. If sessionId is null, a new session is
     * created and its id is returned. If sessionId is provided but
     * does not exist (or belongs to another user), a new session is
     * silently created instead - matches the spirit of "stateless
     * chat" the user can resume at any time.
     */
    @Transactional
    public UUID getOrCreate(String username, UUID sessionId, String initialTitle) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        if (sessionId != null) {
            // Archived (deleted) sessions are treated as missing so new turns never land in a hidden thread.
            var existing = sessionRepo.findByIdAndUserUsername(sessionId, username)
                    .filter(s -> !s.isArchived());
            if (existing.isPresent()) {
                touch(existing.get());
                return sessionId;
            }
            log.info("AI session {} not found for user {}; creating new", sessionId, username);
        }
        UUID id = UUID.randomUUID();
        AiSession s = new AiSession(id, username, initialTitle);
        sessionRepo.save(s);
        return id;
    }

    private void touch(AiSession s) {
        s.setLastActiveAt(LocalDateTime.now());
        sessionRepo.save(s);
    }

    /**
     * Append one turn. The session is also "touched" (lastActiveAt
     * bumped) on every append so the most-recent session sorts to
     * the top in the sidebar.
     */
    @Transactional
    public AiMessage append(UUID sessionId, String username,
                            AiMessageRole role, String content, String metadataJson) {
        AiSession s = sessionRepo.findByIdAndUserUsername(sessionId, username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Session not found: " + sessionId));
        AiMessage m = new AiMessage(UUID.randomUUID(), sessionId, role, content, metadataJson);
        messageRepo.save(m);
        touch(s);
        return m;
    }

    /**
     * Load the most recent {@code limit} messages, oldest first so
     * the prompt builder can append them as a chronological thread.
     */
    @Transactional(readOnly = true)
    public List<AiMessage> recentMessages(UUID sessionId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, HISTORY_LIMIT));
        List<AiMessage> recent = messageRepo.findRecent(sessionId, safeLimit);
        java.util.Collections.reverse(recent); // oldest first
        return recent;
    }

    @Transactional(readOnly = true)
    public List<AiSession> listActive(String username, int limit) {
        return sessionRepo.findActiveByUser(username, PageRequest.of(0, Math.max(1, limit)));
    }

    @Transactional(readOnly = true)
    public List<AiMessage> allMessages(UUID sessionId) {
        return messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    /**
     * CP-5 Checkpoint 3.2: find the most recent ASSISTANT message
     * in the given session. Used by the continuation logic to read
     * the previous turn's manifest without loading the full history.
     *
     * @param sessionId the session to search
     * @return the last ASSISTANT message, or {@code null} if none
     */
    @Transactional(readOnly = true)
    public AiMessage findLastAssistantMessage(UUID sessionId) {
        if (sessionId == null) return null;
        return messageRepo.findLastAssistantMessage(sessionId);
    }

    /**
     * Load a non-archived session owned by the user, or fail with 404.
     * Used by every endpoint that takes a session id from the client.
     */
    @Transactional(readOnly = true)
    public AiSession requireOwned(UUID sessionId, String username) {
        return sessionRepo.findByIdAndUserUsername(sessionId, username)
                .filter(s -> !s.isArchived())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Session not found"));
    }

    @Transactional
    public void archive(UUID sessionId, String username) {
        AiSession s = requireOwned(sessionId, username);
        s.setArchived(true);
        sessionRepo.save(s);
    }
}

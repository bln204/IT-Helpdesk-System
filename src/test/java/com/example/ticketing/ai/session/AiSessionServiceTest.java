package com.example.ticketing.ai.session;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ownership / archive rules for chat history sessions.
 */
class AiSessionServiceTest {

    private final AiSessionRepository sessionRepo = mock(AiSessionRepository.class);
    private final AiMessageRepository messageRepo = mock(AiMessageRepository.class);
    private final AiSessionService service = new AiSessionService(sessionRepo, messageRepo);

    private AiSession archivedSession(UUID id) {
        AiSession s = new AiSession(id, "alice", "Old");
        s.setArchived(true);
        return s;
    }

    @Test
    void archiveOfMissingSessionIs404() {
        UUID id = UUID.randomUUID();
        when(sessionRepo.findByIdAndUserUsername(id, "alice")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.archive(id, "alice"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void archivedSessionIsNotOwnedAnymore() {
        UUID id = UUID.randomUUID();
        when(sessionRepo.findByIdAndUserUsername(id, "alice")).thenReturn(Optional.of(archivedSession(id)));

        assertThatThrownBy(() -> service.requireOwned(id, "alice"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void askingWithArchivedSessionIdStartsNewSession() {
        UUID id = UUID.randomUUID();
        when(sessionRepo.findByIdAndUserUsername(id, "alice")).thenReturn(Optional.of(archivedSession(id)));

        UUID result = service.getOrCreate("alice", id, "New");

        assertThat(result).isNotEqualTo(id);
        verify(sessionRepo).save(any(AiSession.class));
    }

    @Test
    void activeSessionIsReused() {
        UUID id = UUID.randomUUID();
        when(sessionRepo.findByIdAndUserUsername(id, "alice"))
                .thenReturn(Optional.of(new AiSession(id, "alice", "Live")));

        assertThat(service.getOrCreate("alice", id, "x")).isEqualTo(id);
        assertThat(service.requireOwned(id, "alice").getId()).isEqualTo(id);
    }
}

package com.example.ticketing.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class NotificationOwnershipTest {

    private final NotificationRepository repo = mock(NotificationRepository.class);
    private final NotificationService service = new NotificationService(
            repo, mock(UserAccountRepository.class), mock(NotificationPreferencesRepository.class));

    private Notification owned(String owner) {
        Notification n = new Notification();
        n.setRecipientUsername(owner);
        when(repo.findById(7L)).thenReturn(Optional.of(n));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        return n;
    }

    @Test
    void cannotMarkSomeoneElsesNotification() {
        owned("bob");
        assertThat(service.markAsRead(7L, "alice")).isNull();
        verify(repo, never()).save(any());
    }

    @Test
    void ownerCanMarkAsReadCaseInsensitive() {
        owned("Alice");
        assertThat(service.markAsRead(7L, "alice")).isNotNull();
    }

    @Test
    void cannotDeleteSomeoneElsesNotification() {
        Notification n = owned("bob");
        assertThat(service.deleteNotification(7L, "alice")).isFalse();
        verify(repo, never()).delete(n);
    }
}

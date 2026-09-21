package com.example.ticketing.auth;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for Notification entity.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Find all notifications for a user, ordered by creation time (newest first).
     */
    List<Notification> findByRecipientUsernameOrderByCreatedAtDesc(String recipientUsername);

    /**
     * Find paginated notifications for a user.
     */
    Page<Notification> findByRecipientUsernameOrderByCreatedAtDesc(String recipientUsername, Pageable pageable);

    /**
     * Find unread notifications for a user.
     */
    List<Notification> findByRecipientUsernameAndReadFalseOrderByCreatedAtDesc(String recipientUsername);

    /**
     * Count unread notifications for a user.
     */
    long countByRecipientUsernameAndReadFalse(String recipientUsername);

    /**
     * Mark all notifications as read for a user.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipientUsername = :username AND n.read = false")
    int markAllAsRead(@Param("username") String username);

    /**
     * Delete old notifications (older than specified days).
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < CURRENT_TIMESTAMP - :days DAY")
    int deleteOlderThan(@Param("days") int days);
}

package com.example.ticketing.auth;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// #region agent debug log
import org.springframework.beans.factory.annotation.Value;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
// #endregion

/**
 * Service for managing notifications.
 */
@Service
public class NotificationService {
    
    // #region agent debug log
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    
    @Value("${debug.logging.endpoint:}")
    private String debugEndpoint;
    
    @Value("${debug.logging.session-id:}")
    private String sessionId;
    
    private void debugLog(String hypothesisId, String runId, String location, String message, Object data) {
        if (debugEndpoint == null || debugEndpoint.isBlank()) return;
        try {
            String payload = String.format(
                "{\"sessionId\":\"%s\",\"hypothesisId\":\"%s\",\"runId\":\"%s\",\"location\":\"%s\",\"message\":\"%s\",\"data\":%s,\"timestamp\":%d}",
                sessionId != null ? sessionId : "",
                hypothesisId,
                runId,
                location,
                message.replace("\"", "\\\""),
                data != null ? new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data) : "null",
                System.currentTimeMillis()
            );
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(debugEndpoint))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.warn("Debug log failed: {}", e.getMessage());
        }
    }
    // #endregion

    private final NotificationRepository notificationRepository;
    private final UserAccountRepository userAccountRepository;

    public NotificationService(NotificationRepository notificationRepository, UserAccountRepository userAccountRepository) {
        this.notificationRepository = notificationRepository;
        this.userAccountRepository = userAccountRepository;
    }

    /**
     * Send a notification to a user.
     */
    @Transactional
    public Notification sendNotification(String recipientUsername, Notification.NotificationType type,
                                        String title, String message, Long relatedUserId, String actorUsername) {
        // #region agent debug log
        debugLog("C", "initial", "NotificationService.java:sendNotification_ENTRY",
            String.format("Sending notification - recipient=%s, type=%s, title=%s, actor=%s",
                recipientUsername, type, title, actorUsername),
            java.util.Map.of("recipient", recipientUsername, "type", type != null ? type.name() : "null", "actor", actorUsername));
        // #endregion
        
        Notification notification = new Notification(recipientUsername, type, title, message, relatedUserId, actorUsername);
        Notification saved = notificationRepository.save(notification);
        
        // #region agent debug log
        debugLog("C", "initial", "NotificationService.java:sendNotification_EXIT",
            String.format("Notification sent - id=%d, recipient=%s, type=%s",
                saved.getId(), saved.getRecipientUsername(), saved.getType().name()),
            java.util.Map.of("notificationId", saved.getId(), "recipient", saved.getRecipientUsername(), "type", saved.getType().name()));
        // #endregion
        
        return saved;
    }

    /**
     * Send notification to admin when a TRUONG_PHONG creates an account.
     */
    @Transactional
    public void notifyAdminOfPendingAccount(String createdUsername, String displayName, String actorUsername) {
        // #region agent debug log
        debugLog("A", "initial", "NotificationService.java:notifyAdminOfPendingAccount_ENTRY",
            "notifyAdminOfPendingAccount called",
            java.util.Map.of("createdUsername", createdUsername, "displayName", displayName, "actorUsername", actorUsername));
        // #endregion
        
        // Get all admin usernames
        List<String> adminUsernames = userAccountRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.Role.ADMIN)
                .map(UserAccount::getUsername)
                .collect(Collectors.toList());
        
        // #region agent debug log
        debugLog("A", "initial", "NotificationService.java:notifyAdminOfPendingAccount_ADMINS_FOUND",
            String.format("Found %d admin(s): %s", adminUsernames.size(), adminUsernames),
            java.util.Map.of("adminCount", adminUsernames.size(), "admins", adminUsernames));
        // #endregion

        String title = "Yêu cầu duyệt tài khoản mới";
        String message = String.format("Trưởng phòng \"%s\" đã tạo tài khoản \"%s\" (%s) và đang chờ bạn phê duyệt.",
                actorUsername, createdUsername, displayName != null ? displayName : createdUsername);

        for (String adminUsername : adminUsernames) {
            // #region agent debug log
            debugLog("A", "initial", "NotificationService.java:notifyAdminOfPendingAccount_SEND_TO_ADMIN",
                String.format("Sending notification to admin: %s", adminUsername),
                java.util.Map.of("adminUsername", adminUsername));
            // #endregion
            sendNotification(adminUsername, Notification.NotificationType.ACCOUNT_CREATED_PENDING,
                    title, message, null, actorUsername);
        }
        
        // #region agent debug log
        debugLog("A", "initial", "NotificationService.java:notifyAdminOfPendingAccount_EXIT",
            String.format("Completed - sent to %d admin(s)", adminUsernames.size()),
            java.util.Map.of("totalAdminsNotified", adminUsernames.size()));
        // #endregion
    }

    /**
     * Send notification to TRUONG_PHONG when admin approves an account.
     */
    @Transactional
    public void notifyCreatorOfApproval(Long userId, String creatorUsername, String approvedUsername, 
                                        String displayName, String adminUsername) {
        String title = "Tài khoản đã được duyệt";
        String message = String.format("Tài khoản \"%s\" (%s) mà bạn tạo đã được Admin \"%s\" phê duyệt.",
                approvedUsername, displayName != null ? displayName : approvedUsername, adminUsername);

        sendNotification(creatorUsername, Notification.NotificationType.ACCOUNT_APPROVED,
                title, message, userId, adminUsername);
    }

    /**
     * Send notification to TRUONG_PHONG when admin rejects an account.
     */
    @Transactional
    public void notifyCreatorOfRejection(Long userId, String creatorUsername, String rejectedUsername,
                                         String displayName, String adminUsername, String reason) {
        String title = "Tài khoản bị từ chối";
        String message = String.format("Tài khoản \"%s\" (%s) mà bạn tạo đã bị Admin \"%s\" từ chối. Lý do: %s",
                rejectedUsername, displayName != null ? displayName : rejectedUsername, adminUsername, reason);

        sendNotification(creatorUsername, Notification.NotificationType.ACCOUNT_REJECTED,
                title, message, userId, adminUsername);
    }

    /**
     * Notify all admins when TRUONG_PHONG requests to delete a user.
     */
    @Transactional
    public void notifyAdminsOfDeleteRequest(Long userId, String targetUsername, String displayName, String requesterUsername) {
        String title = "Yêu cầu xóa tài khoản";
        String message = String.format("Trưởng phòng \"%s\" yêu cầu xóa tài khoản \"%s\" (%s). Vui lòng xem xét và duyệt xóa.",
                requesterUsername, targetUsername, displayName != null ? displayName : targetUsername);

        // Send to all admins
        List<UserAccount> admins = userAccountRepository.findByRoleAndEnabledTrue(UserRole.Role.ADMIN);
        for (UserAccount admin : admins) {
            sendNotification(admin.getUsername(), Notification.NotificationType.ACCOUNT_REJECTED,
                    title, message, userId, requesterUsername);
        }
    }

    /**
     * Notify TRUONG_PHONG when their delete request was approved and user was deleted.
     */
    @Transactional
    public void notifyUserDeleted(Long userId, String requesterUsername, String deletedUsername, 
                                  String displayName, String adminUsername) {
        String title = "Tài khoản đã được xóa";
        String message = String.format("Tài khoản \"%s\" (%s) đã được Admin \"%s\" duyệt xóa thành công.",
                deletedUsername, displayName != null ? displayName : deletedUsername, adminUsername);

        sendNotification(requesterUsername, Notification.NotificationType.ACCOUNT_APPROVED,
                title, message, userId, adminUsername);
    }

    /**
     * Get all notifications for a user.
     */
    public List<Notification.NotificationResponse> getNotifications(String username) {
        List<Notification> notifications = notificationRepository
                .findByRecipientUsernameOrderByCreatedAtDesc(username);
        return notifications.stream()
                .map(Notification.NotificationResponse::new)
                .collect(Collectors.toList());
    }

    /**
     * Get paginated notifications for a user.
     */
    public Page<Notification.NotificationResponse> getNotifications(String username, int page, int size) {
        Page<Notification> notifications = notificationRepository
                .findByRecipientUsernameOrderByCreatedAtDesc(username, PageRequest.of(page, size));
        return notifications.map(Notification.NotificationResponse::new);
    }

    /**
     * Get unread notification count for a user.
     */
    public long getUnreadCount(String username) {
        return notificationRepository.countByRecipientUsernameAndReadFalse(username);
    }

    /**
     * Mark all notifications as read for a user.
     */
    @Transactional
    public int markAllAsRead(String username) {
        return notificationRepository.markAllAsRead(username);
    }

    /**
     * Mark a single notification as read.
     */
    @Transactional
    public Notification markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification != null) {
            notification.setRead(true);
            return notificationRepository.save(notification);
        }
        return null;
    }
    
    /**
     * Delete all notifications for a user.
     */
    @Transactional
    public void deleteAllNotifications(String username) {
        notificationRepository.deleteByRecipientUsername(username);
    }
}

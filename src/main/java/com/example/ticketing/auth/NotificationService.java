package com.example.ticketing.auth;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final NotificationPreferencesRepository preferencesRepository;
    
    @Autowired
    private EmailService emailService;
    
    public NotificationService(
            NotificationRepository notificationRepository,
            UserAccountRepository userAccountRepository,
            NotificationPreferencesRepository preferencesRepository) {
        this.notificationRepository = notificationRepository;
        this.userAccountRepository = userAccountRepository;
        this.preferencesRepository = preferencesRepository;
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
     * Notify user when their profile has been changed by an admin or manager.
     * Sends both in-app notification and real email with detailed change information.
     * 
     * @param recipientUsername Username of the user whose profile was changed
     * @param recipientEmail Email address of the user
     * @param displayName Display name of the user
     * @param actorUsername Username of who made the changes
     * @param actorRole Role of who made the changes
     * @param changes ChangeSet containing all profile changes with old/new values
     */
    @Transactional
    public void notifyProfileChanged(String recipientUsername, String recipientEmail,
                                     String displayName, String actorUsername, 
                                     String actorRole, ProfileChange.ChangeSet changes) {
        String actorLabel = formatActorRole(actorRole);
        String title = "Thông tin tài khoản đã được thay đổi";
        
        // Generate message for in-app notification with change count
        String message;
        if (changes.hasChanges()) {
            int count = changes.getChangeCount();
            String countText = count == 1 ? "1 trường thông tin" : count + " trường thông tin";
            message = String.format("Thông tin tài khoản (%s) đã được %s \"%s\" thay đổi. Có %s: %s",
                    displayName != null ? displayName : recipientUsername,
                    actorLabel,
                    actorUsername,
                    countText,
                    changes.getChanges().stream()
                        .map(ProfileChange::getFieldName)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("không xác định"));
        } else {
            message = String.format("Thông tin tài khoản (%s) đã được %s \"%s\" thay đổi.",
                    displayName != null ? displayName : recipientUsername,
                    actorLabel,
                    actorUsername);
        }

        // Send in-app notification
        sendNotification(recipientUsername, Notification.NotificationType.PROFILE_CHANGED,
                title, message, null, actorUsername);
        
        // Send real email notification with detailed changes
        if (recipientEmail != null && !recipientEmail.isBlank()) {
            try {
                emailService.sendProfileChangeNotification(
                    recipientEmail, displayName, actorUsername, actorRole, changes);
                log.info("Profile change email sent to: {}", recipientEmail);
            } catch (Exception e) {
                log.error("Failed to send profile change email to {}: {}", recipientEmail, e.getMessage());
            }
        }
    }

    /**
     * Notify user when their profile AND/OR password has been changed.
     * Sends ONE combined email notification for all changes including password.
     * 
     * @param recipientUsername Username of the user
     * @param recipientEmail Email address of the user
     * @param displayName Display name of the user
     * @param actorUsername Username of who made the change
     * @param actorRole Role of who made the change
     * @param changes ChangeSet containing all profile changes (including password if any)
     */
    @Transactional
    public void notifyProfileAndPasswordChanged(String recipientUsername, String recipientEmail,
                                                 String displayName, String actorUsername, 
                                                 String actorRole, ProfileChange.ChangeSet changes) {
        String actorLabel = formatActorRole(actorRole);
        String title = "Thong tin tai khoan da duoc thay doi";
        
        // Generate message for in-app notification with change count
        String message;
        if (changes.hasChanges()) {
            int count = changes.getChangeCount();
            String countText = count == 1 ? "1 truong thong tin" : count + " truong thong tin";
            message = String.format("Thong tin tai khoan (%s) da duoc %s \"%s\" thay doi. Co %s: %s",
                    displayName != null ? displayName : recipientUsername,
                    actorLabel,
                    actorUsername,
                    countText,
                    changes.getChanges().stream()
                        .map(ProfileChange::getFieldName)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("khong xac dinh"));
        } else {
            message = String.format("Thong tin tai khoan (%s) da duoc %s \"%s\" thay doi.",
                    displayName != null ? displayName : recipientUsername,
                    actorLabel,
                    actorUsername);
        }

        // Send in-app notification
        sendNotification(recipientUsername, Notification.NotificationType.PROFILE_CHANGED,
                title, message, null, actorUsername);
        
        // Send ONE combined email notification for all changes
        if (recipientEmail != null && !recipientEmail.isBlank()) {
            try {
                emailService.sendProfileChangeNotification(
                    recipientEmail, displayName, actorUsername, actorRole, changes);
                log.info("Combined profile+password change email sent to: {}", recipientEmail);
            } catch (Exception e) {
                log.error("Failed to send combined email to {}: {}", recipientEmail, e.getMessage());
            }
        }
    }

    /**
     * Notify user when their password has been reset by an admin or manager.
     * Sends both in-app notification and real email with detailed information.
     * Password change is sent as a profile change notification.
     * 
     * @param recipientUsername Username of the user whose password was reset
     * @param recipientEmail Email address of the user
     * @param displayName Display name of the user
     * @param actorUsername Username of who reset the password
     * @param actorRole Role of who reset the password
     */
    @Transactional
    public void notifyPasswordReset(String recipientUsername, String recipientEmail,
                                    String displayName, String actorUsername, String actorRole,
                                    String newPassword) {
        String actorLabel = formatActorRole(actorRole);
        String title = "Mat khau tai khoan da duoc dat lai";
        String message = String.format("Mat khau tai khoan cua ban da duoc %s \"%s\" dat lai. Vui long kiem tra email de biet mat khau moi.",
                actorLabel, actorUsername);

        // Send in-app notification
        sendNotification(recipientUsername, Notification.NotificationType.PROFILE_CHANGED,
                title, message, null, actorUsername);
        
        // Send email with password reset as a profile change
        if (recipientEmail != null && !recipientEmail.isBlank()) {
            try {
                ProfileChange.ChangeSet changes = new ProfileChange.ChangeSet();
                changes.addChange("Mat khau", "(khong hien thi)", newPassword);
                
                emailService.sendProfileChangeNotification(
                    recipientEmail, displayName, actorUsername, actorRole, changes);
                log.info("Password reset email sent to: {}", recipientEmail);
            } catch (Exception e) {
                log.error("Failed to send password reset email to {}: {}", recipientEmail, e.getMessage());
            }
        }
    }
    
    private String formatActorRole(String role) {
        return switch (role) {
            case "ADMIN" -> "Admin";
            case "GIAM_DOC" -> "Giám đốc";
            case "TRUONG_PHONG" -> "Trưởng phòng";
            case "NHAN_VIEN" -> "Nhân viên";
            default -> role;
        };
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
    
    /**
     * Delete a single notification.
     */
    @Transactional
    public void deleteNotification(Long notificationId) {
        if (notificationRepository.existsById(notificationId)) {
            notificationRepository.deleteById(notificationId);
        }
    }
    
    /**
     * Update notification preferences.
     */
    @Transactional
    public NotificationPreferences updatePreferences(String username, NotificationPreferences.NotificationPreferencesRequest request) {
        NotificationPreferences prefs = preferencesRepository.findByUserUsername(username)
            .orElseThrow(() -> new RuntimeException("User preferences not found"));
        
        if (request.notifyTicketCreated != null) prefs.setNotifyTicketCreated(request.notifyTicketCreated);
        if (request.notifyTicketAssigned != null) prefs.setNotifyTicketAssigned(request.notifyTicketAssigned);
        if (request.notifyStatusChanged != null) prefs.setNotifyStatusChanged(request.notifyStatusChanged);
        if (request.notifyCommentAdded != null) prefs.setNotifyCommentAdded(request.notifyCommentAdded);
        if (request.notifySlaWarning != null) prefs.setNotifySlaWarning(request.notifySlaWarning);
        if (request.notifySlaBreached != null) prefs.setNotifySlaBreached(request.notifySlaBreached);
        if (request.notifyEscalated != null) prefs.setNotifyEscalated(request.notifyEscalated);
        if (request.emailEnabled != null) prefs.setEmailEnabled(request.emailEnabled);
        if (request.inAppEnabled != null) prefs.setInAppEnabled(request.inAppEnabled);
        
        return preferencesRepository.save(prefs);
    }
    
    /**
     * Enable/disable email notifications.
     */
    @Transactional
    public NotificationPreferences setEmailEnabled(String username, boolean enabled) {
        NotificationPreferences prefs = preferencesRepository.findByUserUsername(username)
            .orElseThrow(() -> new RuntimeException("User preferences not found"));
        prefs.setEmailEnabled(enabled);
        return preferencesRepository.save(prefs);
    }
    
    /**
     * Enable/disable in-app notifications.
     */
    @Transactional
    public NotificationPreferences setInAppEnabled(String username, boolean enabled) {
        NotificationPreferences prefs = preferencesRepository.findByUserUsername(username)
            .orElseThrow(() -> new RuntimeException("User preferences not found"));
        prefs.setInAppEnabled(enabled);
        return preferencesRepository.save(prefs);
    }
}

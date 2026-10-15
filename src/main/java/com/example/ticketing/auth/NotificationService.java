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


/**
 * Service for managing notifications.
 */
@Service
public class NotificationService {
    
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

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
        
        Notification notification = new Notification(recipientUsername, type, title, message, relatedUserId, actorUsername);
        Notification saved = notificationRepository.save(notification);
        
        
        return saved;
    }

    /**
     * Send notification to admin when a TRUONG_PHONG creates an account.
     */
    @Transactional
    public void notifyAdminOfPendingAccount(String createdUsername, String displayName, String actorUsername) {
        
        // Get all admin usernames
        List<String> adminUsernames = userAccountRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.Role.ADMIN)
                .map(UserAccount::getUsername)
                .collect(Collectors.toList());
        

        String title = "Yêu cầu duyệt tài khoản mới";
        String message = String.format("Trưởng phòng \"%s\" đã tạo tài khoản \"%s\" (%s) và đang chờ bạn phê duyệt.",
                actorUsername, createdUsername, displayName != null ? displayName : createdUsername);

        for (String adminUsername : adminUsernames) {
            sendNotification(adminUsername, Notification.NotificationType.ACCOUNT_CREATED_PENDING,
                    title, message, null, actorUsername);
        }
        
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
    public Notification markAsRead(Long notificationId, String username) {
        return notificationRepository.findById(notificationId)
                .filter(n -> username != null && username.equalsIgnoreCase(n.getRecipientUsername()))
                .map(n -> {
                    n.setRead(true);
                    return notificationRepository.save(n);
                })
                .orElse(null);
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
    public boolean deleteNotification(Long notificationId, String username) {
        return notificationRepository.findById(notificationId)
                .filter(n -> username != null && username.equalsIgnoreCase(n.getRecipientUsername()))
                .map(n -> {
                    notificationRepository.delete(n);
                    return true;
                })
                .orElse(false);
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

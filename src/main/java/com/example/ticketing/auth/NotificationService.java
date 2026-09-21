package com.example.ticketing.auth;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing notifications.
 */
@Service
public class NotificationService {

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
        Notification notification = new Notification(recipientUsername, type, title, message, relatedUserId, actorUsername);
        return notificationRepository.save(notification);
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
}

package com.example.ticketing.auth;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for managing notifications.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Get all notifications for the current user.
     */
    @GetMapping
    public ResponseEntity<List<Notification.NotificationResponse>> getNotifications(Authentication authentication) {
        String username = authentication.getName();
        List<Notification.NotificationResponse> notifications = notificationService.getNotifications(username);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Get paginated notifications for the current user.
     */
    @GetMapping("/page")
    public ResponseEntity<Page<Notification.NotificationResponse>> getNotificationsPage(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String username = authentication.getName();
        Page<Notification.NotificationResponse> notifications = notificationService.getNotifications(username, page, size);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Get unread notification count for the current user.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication authentication) {
        String username = authentication.getName();
        long count = notificationService.getUnreadCount(username);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Mark all notifications as read for the current user.
     */
    @PostMapping("/mark-all-read")
    public ResponseEntity<Map<String, String>> markAllAsRead(Authentication authentication) {
        String username = authentication.getName();
        int count = notificationService.markAllAsRead(username);
        return ResponseEntity.ok(Map.of("message", "Đã đánh dấu " + count + " thông báo là đã đọc"));
    }

    /**
     * Mark a single notification as read.
     */
    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, String>> markAsRead(@PathVariable Long id) {
        Notification notification = notificationService.markAsRead(id);
        if (notification != null) {
            return ResponseEntity.ok(Map.of("message", "Đã đánh dấu thông báo là đã đọc"));
        }
        return ResponseEntity.notFound().build();
    }
}

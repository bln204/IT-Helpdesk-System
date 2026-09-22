package com.example.ticketing.auth;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// #region agent debug log
import org.springframework.beans.factory.annotation.Value;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
// #endregion

/**
 * REST Controller for managing notifications.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    
    // #region agent debug log
    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);
    
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
        // #region agent debug log
        debugLog("E", "initial", "NotificationController.java:getNotifications",
            String.format("GET /api/notifications - user=%s", username),
            java.util.Map.of("username", username));
        // #endregion
        List<Notification.NotificationResponse> notifications = notificationService.getNotifications(username);
        // #region agent debug log
        debugLog("E", "initial", "NotificationController.java:getNotifications_RESPONSE",
            String.format("Returning %d notifications for user=%s", notifications.size(), username),
            java.util.Map.of("username", username, "count", notifications.size()));
        // #endregion
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
    
    /**
     * Delete all notifications for the current user.
     */
    @DeleteMapping
    public ResponseEntity<Map<String, String>> deleteAllNotifications(Authentication authentication) {
        String username = authentication.getName();
        notificationService.deleteAllNotifications(username);
        return ResponseEntity.ok(Map.of("message", "Đã xóa tất cả thông báo"));
    }

    /**
     * Create a new notification (for frontend to save to backend).
     * POST /api/notifications
     */
    @PostMapping
    public ResponseEntity<Notification.NotificationResponse> createNotification(
            Authentication authentication,
            @RequestBody CreateNotificationRequest request) {
        String username = authentication.getName();
        
        // Determine notification type
        Notification.NotificationType type;
        switch (request.type) {
            case "approve":
                type = Notification.NotificationType.ACCOUNT_APPROVED;
                break;
            case "reject":
                type = Notification.NotificationType.ACCOUNT_REJECTED;
                break;
            case "delete":
            case "request-delete":
                type = Notification.NotificationType.ACCOUNT_REJECTED;
                break;
            default:
                type = Notification.NotificationType.ACCOUNT_APPROVED;
        }

        Notification notification = notificationService.sendNotification(
                username,
                type,
                request.title,
                request.message,
                request.userId,
                "SYSTEM"
        );

        return ResponseEntity.ok(new Notification.NotificationResponse(notification));
    }

    /**
     * Request body for creating a notification.
     */
    public static class CreateNotificationRequest {
        public String type;
        public String title;
        public String message;
        public Long userId;
    }
}

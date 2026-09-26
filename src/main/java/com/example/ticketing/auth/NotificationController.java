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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ticketing.ticket.SlaSchedulerService;
import com.example.ticketing.ticket.SlaSchedulerService.SlaStatus;

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
    private final NotificationPreferencesRepository preferencesRepository;
    private final com.example.ticketing.ticket.SlaSchedulerService slaSchedulerService;

    public NotificationController(
            NotificationService notificationService,
            NotificationPreferencesRepository preferencesRepository,
            com.example.ticketing.ticket.SlaSchedulerService slaSchedulerService) {
        this.notificationService = notificationService;
        this.preferencesRepository = preferencesRepository;
        this.slaSchedulerService = slaSchedulerService;
    }

    // ============================================================
    // NOTIFICATION ENDPOINTS
    // ============================================================
    
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
     * Get unread count by type.
     */
    @GetMapping("/unread-by-type")
    public ResponseEntity<Map<String, Long>> getUnreadCountByType(Authentication authentication) {
        String username = authentication.getName();
        List<Notification.NotificationResponse> notifications = notificationService.getNotifications(username);
        
        Map<String, Long> counts = notifications.stream()
            .filter(n -> !n.isRead())
            .collect(java.util.stream.Collectors.groupingBy(
                Notification.NotificationResponse::getType,
                java.util.stream.Collectors.counting()
            ));
        
        return ResponseEntity.ok(counts);
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
     * Delete a single notification.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(id);
        return ResponseEntity.noContent().build();
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

    // ============================================================
    // NOTIFICATION PREFERENCES ENDPOINTS
    // ============================================================
    
    /**
     * Get notification preferences for current user.
     */
    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferences.NotificationPreferencesResponse> getPreferences(Authentication authentication) {
        String username = authentication.getName();
        return preferencesRepository.findByUserUsername(username)
            .map(prefs -> ResponseEntity.ok(new NotificationPreferences.NotificationPreferencesResponse(prefs)))
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Update notification preferences.
     */
    @PutMapping("/preferences")
    public ResponseEntity<NotificationPreferences.NotificationPreferencesResponse> updatePreferences(
            Authentication authentication,
            @RequestBody NotificationPreferences.NotificationPreferencesRequest request) {
        String username = authentication.getName();
        NotificationPreferences prefs = notificationService.updatePreferences(username, request);
        return ResponseEntity.ok(new NotificationPreferences.NotificationPreferencesResponse(prefs));
    }
    
    /**
     * Enable/disable email notifications.
     */
    @PutMapping("/preferences/email")
    public ResponseEntity<NotificationPreferences.NotificationPreferencesResponse> setEmailEnabled(
            Authentication authentication,
            @RequestParam boolean enabled) {
        String username = authentication.getName();
        NotificationPreferences prefs = notificationService.setEmailEnabled(username, enabled);
        return ResponseEntity.ok(new NotificationPreferences.NotificationPreferencesResponse(prefs));
    }
    
    /**
     * Enable/disable in-app notifications.
     */
    @PutMapping("/preferences/in-app")
    public ResponseEntity<NotificationPreferences.NotificationPreferencesResponse> setInAppEnabled(
            Authentication authentication,
            @RequestParam boolean enabled) {
        String username = authentication.getName();
        NotificationPreferences prefs = notificationService.setInAppEnabled(username, enabled);
        return ResponseEntity.ok(new NotificationPreferences.NotificationPreferencesResponse(prefs));
    }

    // ============================================================
    // SLA ENDPOINTS
    // ============================================================
    
    /**
     * Get SLA status for a ticket.
     */
    @GetMapping("/sla/{ticketId}")
    public ResponseEntity<SlaSchedulerService.SlaStatus> getTicketSlaStatus(@PathVariable Long ticketId) {
        SlaSchedulerService.SlaStatus status = slaSchedulerService.checkTicketSla(ticketId);
        return ResponseEntity.ok(status);
    }

    // ============================================================
    // LEGACY ENDPOINTS (for frontend compatibility)
    // ============================================================
    
    /**
     * Create a new notification (for frontend to save to backend).
     */
    @PostMapping
    public ResponseEntity<Notification.NotificationResponse> createNotification(
            Authentication authentication,
            @RequestBody CreateNotificationRequest request) {
        String username = authentication.getName();
        
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

    public static class CreateNotificationRequest {
        public String type;
        public String title;
        public String message;
        public Long userId;
    }
}

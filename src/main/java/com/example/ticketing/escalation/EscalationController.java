package com.example.ticketing.escalation;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.ticketing.ticket.TicketTypes.TicketPriority;

/**
 * REST Controller cho Escalation Management.
 */
@RestController
@RequestMapping("/api/escalation")
@CrossOrigin(origins = "*")
public class EscalationController {

    private static final Logger log = LoggerFactory.getLogger(EscalationController.class);

    private final EscalationService escalationService;

    public EscalationController(EscalationService escalationService) {
        this.escalationService = escalationService;
    }

    // ==================== Escalation Rules ====================

    /**
     * Lấy tất cả escalation rules.
     * GET /api/escalation/rules
     */
    @GetMapping("/rules")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<List<EscalationRuleDto>> getAllRules() {
        log.info("GET /api/escalation/rules");
        List<EscalationRule> rules = escalationService.getAllRules();
        return ResponseEntity.ok(rules.stream().map(EscalationRuleDto::fromEntity).toList());
    }

    /**
     * Lấy enabled escalation rules.
     * GET /api/escalation/rules/enabled
     */
    @GetMapping("/rules/enabled")
    public ResponseEntity<List<EscalationRuleDto>> getEnabledRules() {
        log.info("GET /api/escalation/rules/enabled");
        List<EscalationRule> rules = escalationService.getEnabledRules();
        return ResponseEntity.ok(rules.stream().map(EscalationRuleDto::fromEntity).toList());
    }

    /**
     * Tạo escalation rule.
     * POST /api/escalation/rules
     */
    @PostMapping("/rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EscalationRuleDto> createRule(@RequestBody EscalationRuleRequest request) {
        log.info("POST /api/escalation/rules - Creating: {}", request.getName());

        EscalationRule rule = mapRequestToEntity(request);
        EscalationRule created = escalationService.createRule(rule, "admin");

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(EscalationRuleDto.fromEntity(created));
    }

    /**
     * Cập nhật escalation rule.
     * PUT /api/escalation/rules/{id}
     */
    @PutMapping("/rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EscalationRuleDto> updateRule(
            @PathVariable Long id,
            @RequestBody EscalationRuleRequest request) {
        log.info("PUT /api/escalation/rules/{}", id);

        EscalationRule updates = mapRequestToEntity(request);
        EscalationRule updated = escalationService.updateRule(id, updates, "admin");

        return ResponseEntity.ok(EscalationRuleDto.fromEntity(updated));
    }

    /**
     * Xóa escalation rule.
     * DELETE /api/escalation/rules/{id}
     */
    @DeleteMapping("/rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        log.info("DELETE /api/escalation/rules/{}", id);
        escalationService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Toggle enabled status.
     * PATCH /api/escalation/rules/{id}/toggle
     */
    @PatchMapping("/rules/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EscalationRuleDto> toggleRule(
            @PathVariable Long id,
            @RequestBody ToggleEnabledRequest request) {
        log.info("PATCH /api/escalation/rules/{}/toggle - enabled: {}", id, request.isEnabled());
        EscalationRule toggled = escalationService.toggleRule(id, request.isEnabled(), "admin");
        return ResponseEntity.ok(EscalationRuleDto.fromEntity(toggled));
    }

    // ==================== Escalation History ====================

    /**
     * Lấy lịch sử escalation của ticket.
     * GET /api/escalation/history/{ticketId}
     */
    @GetMapping("/history/{ticketId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<List<EscalationHistoryDto>> getTicketHistory(@PathVariable Long ticketId) {
        log.info("GET /api/escalation/history/{}", ticketId);
        List<EscalationHistory> history = escalationService.getTicketEscalationHistory(ticketId);
        return ResponseEntity.ok(history.stream().map(EscalationHistoryDto::fromEntity).toList());
    }

    /**
     * Manual escalate ticket.
     * POST /api/escalation/escalate/{ticketId}
     */
    @PostMapping("/escalate/{ticketId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'NHAN_VIEN')")
    public ResponseEntity<EscalationHistoryDto> manualEscalate(
            @PathVariable Long ticketId,
            @RequestBody ManualEscalateRequest request) {
        log.info("POST /api/escalation/escalate/{}", ticketId);
        EscalationHistory history = escalationService.manualEscalate(ticketId, request.getReason(), "admin");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(EscalationHistoryDto.fromEntity(history));
    }

    // ==================== Exception Handlers ====================

    @ExceptionHandler(EscalationService.EscalationRuleNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleNotFound(EscalationService.EscalationRuleNotFoundException ex) {
        log.error("Escalation rule not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        log.error("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("BAD_REQUEST", ex.getMessage()));
    }

    // ==================== Helper Methods ====================

    private EscalationRule mapRequestToEntity(EscalationRuleRequest request) {
        EscalationRule rule = new EscalationRule();
        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setTriggerType(request.getTriggerType());
        rule.setMinPriority(request.getMinPriority());
        rule.setMinutesThreshold(request.getMinutesThreshold() != null ? request.getMinutesThreshold() : 0);
        rule.setEscalateToUserId(request.getEscalateToUserId());
        rule.setEscalateToTeamId(request.getEscalateToTeamId());
        rule.setEscalationLevel(request.getEscalationLevel());
        rule.setActionType(request.getActionType());
        rule.setNotificationMessage(request.getNotificationMessage());
        rule.setMaxEscalations(request.getMaxEscalations() != null ? request.getMaxEscalations() : 3);
        rule.setEnabled(request.isEnabled());
        rule.setPriority(request.getPriority() != null ? request.getPriority() : 0);
        return rule;
    }

    // ==================== DTOs ====================

    public static class ErrorResponse {
        private String code;
        private String message;

        public ErrorResponse(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() { return code; }
        public String getMessage() { return message; }
    }

    public static class EscalationRuleRequest {
        private String name;
        private String description;
        private EscalationRule.EscalationTrigger triggerType;
        private TicketPriority minPriority;
        private Integer minutesThreshold;
        private Long escalateToUserId;
        private Long escalateToTeamId;
        private EscalationRule.EscalationLevel escalationLevel;
        private EscalationRule.EscalationAction actionType;
        private String notificationMessage;
        private Integer maxEscalations;
        private boolean enabled = true;
        private Integer priority;

        // Getters
        public String getName() { return name; }
        public String getDescription() { return description; }
        public EscalationRule.EscalationTrigger getTriggerType() { return triggerType; }
        public TicketPriority getMinPriority() { return minPriority; }
        public Integer getMinutesThreshold() { return minutesThreshold; }
        public Long getEscalateToUserId() { return escalateToUserId; }
        public Long getEscalateToTeamId() { return escalateToTeamId; }
        public EscalationRule.EscalationLevel getEscalationLevel() { return escalationLevel; }
        public EscalationRule.EscalationAction getActionType() { return actionType; }
        public String getNotificationMessage() { return notificationMessage; }
        public Integer getMaxEscalations() { return maxEscalations; }
        public boolean isEnabled() { return enabled; }
        public Integer getPriority() { return priority; }
    }

    public static class ToggleEnabledRequest {
        private boolean enabled;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class ManualEscalateRequest {
        private String reason;

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class EscalationRuleDto {
        private Long id;
        private String name;
        private String description;
        private String triggerType;
        private String triggerLabel;
        private String minPriority;
        private Integer minutesThreshold;
        private Long escalateToUserId;
        private Long escalateToTeamId;
        private String escalationLevel;
        private String actionType;
        private String actionLabel;
        private String notificationMessage;
        private Integer maxEscalations;
        private boolean enabled;
        private Integer priority;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static EscalationRuleDto fromEntity(EscalationRule rule) {
            EscalationRuleDto dto = new EscalationRuleDto();
            dto.setId(rule.getId());
            dto.setName(rule.getName());
            dto.setDescription(rule.getDescription());
            dto.setTriggerType(rule.getTriggerType() != null ? rule.getTriggerType().name() : null);
            dto.setTriggerLabel(rule.getTriggerLabel());
            dto.setMinPriority(rule.getMinPriority() != null ? rule.getMinPriority().name() : null);
            dto.setMinutesThreshold(rule.getMinutesThreshold());
            dto.setEscalateToUserId(rule.getEscalateToUserId());
            dto.setEscalateToTeamId(rule.getEscalateToTeamId());
            dto.setEscalationLevel(rule.getEscalationLevel() != null ? rule.getEscalationLevel().name() : null);
            dto.setActionType(rule.getActionType() != null ? rule.getActionType().name() : null);
            dto.setActionLabel(rule.getActionLabel());
            dto.setNotificationMessage(rule.getNotificationMessage());
            dto.setMaxEscalations(rule.getMaxEscalations());
            dto.setEnabled(rule.getEnabled());
            dto.setPriority(rule.getPriority());
            dto.setCreatedAt(rule.getCreatedAt());
            dto.setUpdatedAt(rule.getUpdatedAt());
            return dto;
        }

        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getTriggerType() { return triggerType; }
        public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
        public String getTriggerLabel() { return triggerLabel; }
        public void setTriggerLabel(String triggerLabel) { this.triggerLabel = triggerLabel; }
        public String getMinPriority() { return minPriority; }
        public void setMinPriority(String minPriority) { this.minPriority = minPriority; }
        public Integer getMinutesThreshold() { return minutesThreshold; }
        public void setMinutesThreshold(Integer minutesThreshold) { this.minutesThreshold = minutesThreshold; }
        public Long getEscalateToUserId() { return escalateToUserId; }
        public void setEscalateToUserId(Long escalateToUserId) { this.escalateToUserId = escalateToUserId; }
        public Long getEscalateToTeamId() { return escalateToTeamId; }
        public void setEscalateToTeamId(Long escalateToTeamId) { this.escalateToTeamId = escalateToTeamId; }
        public String getEscalationLevel() { return escalationLevel; }
        public void setEscalationLevel(String escalationLevel) { this.escalationLevel = escalationLevel; }
        public String getActionType() { return actionType; }
        public void setActionType(String actionType) { this.actionType = actionType; }
        public String getActionLabel() { return actionLabel; }
        public void setActionLabel(String actionLabel) { this.actionLabel = actionLabel; }
        public String getNotificationMessage() { return notificationMessage; }
        public void setNotificationMessage(String notificationMessage) { this.notificationMessage = notificationMessage; }
        public Integer getMaxEscalations() { return maxEscalations; }
        public void setMaxEscalations(Integer maxEscalations) { this.maxEscalations = maxEscalations; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Integer getPriority() { return priority; }
        public void setPriority(Integer priority) { this.priority = priority; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class EscalationHistoryDto {
        private Long id;
        private Long ticketId;
        private String ticketNumber;
        private Long ruleId;
        private String ruleName;
        private String escalationLevel;
        private String escalationReason;
        private String actionTaken;
        private Boolean notificationSent;
        private String previousAssignee;
        private String newAssignee;
        private String previousStatus;
        private String newStatus;
        private LocalDateTime escalatedAt;
        private LocalDateTime resolvedAt;
        private String notes;

        public static EscalationHistoryDto fromEntity(EscalationHistory history) {
            EscalationHistoryDto dto = new EscalationHistoryDto();
            dto.setId(history.getId());
            dto.setTicketId(history.getTicketId());
            dto.setTicketNumber(history.getTicketNumber());
            dto.setRuleId(history.getRuleId());
            dto.setRuleName(history.getRuleName());
            dto.setEscalationLevel(history.getEscalationLevel());
            dto.setEscalationReason(history.getEscalationReason());
            dto.setActionTaken(history.getActionTaken());
            dto.setNotificationSent(history.getNotificationSent());
            dto.setPreviousAssignee(history.getPreviousAssignee());
            dto.setNewAssignee(history.getNewAssignee());
            dto.setPreviousStatus(history.getPreviousStatus());
            dto.setNewStatus(history.getNewStatus());
            dto.setEscalatedAt(history.getEscalatedAt());
            dto.setResolvedAt(history.getResolvedAt());
            dto.setNotes(history.getNotes());
            return dto;
        }

        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTicketId() { return ticketId; }
        public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
        public String getTicketNumber() { return ticketNumber; }
        public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }
        public Long getRuleId() { return ruleId; }
        public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
        public String getRuleName() { return ruleName; }
        public void setRuleName(String ruleName) { this.ruleName = ruleName; }
        public String getEscalationLevel() { return escalationLevel; }
        public void setEscalationLevel(String escalationLevel) { this.escalationLevel = escalationLevel; }
        public String getEscalationReason() { return escalationReason; }
        public void setEscalationReason(String escalationReason) { this.escalationReason = escalationReason; }
        public String getActionTaken() { return actionTaken; }
        public void setActionTaken(String actionTaken) { this.actionTaken = actionTaken; }
        public Boolean getNotificationSent() { return notificationSent; }
        public void setNotificationSent(Boolean notificationSent) { this.notificationSent = notificationSent; }
        public String getPreviousAssignee() { return previousAssignee; }
        public void setPreviousAssignee(String previousAssignee) { this.previousAssignee = previousAssignee; }
        public String getNewAssignee() { return newAssignee; }
        public void setNewAssignee(String newAssignee) { this.newAssignee = newAssignee; }
        public String getPreviousStatus() { return previousStatus; }
        public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }
        public String getNewStatus() { return newStatus; }
        public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
        public LocalDateTime getEscalatedAt() { return escalatedAt; }
        public void setEscalatedAt(LocalDateTime escalatedAt) { this.escalatedAt = escalatedAt; }
        public LocalDateTime getResolvedAt() { return resolvedAt; }
        public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }
}

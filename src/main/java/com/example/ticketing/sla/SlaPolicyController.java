package com.example.ticketing.sla;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.example.ticketing.ticket.TicketTypes.TicketPriority;

/**
 * REST Controller cho SLA Policy Management.
 * Cung cấp API để CRUD operations và xem SLA deadlines.
 */
@RestController
@RequestMapping("/api/sla-policies")
@CrossOrigin(origins = "*")
public class SlaPolicyController {

    private static final Logger log = LoggerFactory.getLogger(SlaPolicyController.class);

    private final SlaPolicyService slaPolicyService;

    public SlaPolicyController(SlaPolicyService slaPolicyService) {
        this.slaPolicyService = slaPolicyService;
    }

    // ==================== CRUD Operations ====================

    /**
     * Lấy tất cả SLA policies.
     * GET /api/sla-policies
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<List<SlaPolicyDtos.SlaPolicyResponse>> getAllPolicies() {
        log.info("GET /api/sla-policies - Fetching all SLA policies");
        List<SlaPolicy> policies = slaPolicyService.getAllPolicies();
        List<SlaPolicyDtos.SlaPolicyResponse> response = 
                SlaPolicyDtos.SlaPolicyResponse.fromEntities(policies);
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy tất cả enabled SLA policies.
     * GET /api/sla-policies/enabled
     */
    @GetMapping("/enabled")
    public ResponseEntity<List<SlaPolicyDtos.SlaPolicyResponse>> getEnabledPolicies() {
        log.info("GET /api/sla-policies/enabled");
        List<SlaPolicy> policies = slaPolicyService.getEnabledPolicies();
        List<SlaPolicyDtos.SlaPolicyResponse> response = 
                SlaPolicyDtos.SlaPolicyResponse.fromEntities(policies);
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy SLA policy theo ID.
     * GET /api/sla-policies/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<SlaPolicyDtos.SlaPolicyResponse> getPolicyById(@PathVariable Long id) {
        log.info("GET /api/sla-policies/{}", id);
        SlaPolicy policy = slaPolicyService.getPolicyByIdOrThrow(id);
        return ResponseEntity.ok(SlaPolicyDtos.SlaPolicyResponse.fromEntity(policy));
    }

    /**
     * Tạo mới SLA policy.
     * POST /api/sla-policies
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SlaPolicyDtos.SlaPolicyResponse> createPolicy(
            @RequestBody SlaPolicyDtos.SlaPolicyRequest request) {
        log.info("POST /api/sla-policies - Creating new SLA policy: {}", request.getName());

        SlaPolicy policy = mapRequestToEntity(request);
        SlaPolicy created = slaPolicyService.createPolicy(policy, "admin"); // TODO: Get actual user

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SlaPolicyDtos.SlaPolicyResponse.fromEntity(created));
    }

    /**
     * Cập nhật SLA policy.
     * PUT /api/sla-policies/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SlaPolicyDtos.SlaPolicyResponse> updatePolicy(
            @PathVariable Long id,
            @RequestBody SlaPolicyDtos.SlaPolicyRequest request) {
        log.info("PUT /api/sla-policies/{}", id);

        SlaPolicy updates = mapRequestToEntity(request);
        SlaPolicy updated = slaPolicyService.updatePolicy(id, updates, "admin"); // TODO: Get actual user

        return ResponseEntity.ok(SlaPolicyDtos.SlaPolicyResponse.fromEntity(updated));
    }

    /**
     * Xóa SLA policy.
     * DELETE /api/sla-policies/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletePolicy(@PathVariable Long id) {
        log.info("DELETE /api/sla-policies/{}", id);
        slaPolicyService.deletePolicy(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Toggle enabled status.
     * PATCH /api/sla-policies/{id}/toggle
     */
    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SlaPolicyDtos.SlaPolicyResponse> toggleEnabled(
            @PathVariable Long id,
            @RequestBody SlaPolicyDtos.ToggleEnabledRequest request) {
        log.info("PATCH /api/sla-policies/{}/toggle - enabled: {}", id, request.getEnabled());
        SlaPolicy toggled = slaPolicyService.toggleEnabled(id, request.getEnabled(), "admin");
        return ResponseEntity.ok(SlaPolicyDtos.SlaPolicyResponse.fromEntity(toggled));
    }

    /**
     * Set policy làm default.
     * POST /api/sla-policies/{id}/set-default
     */
    @PostMapping("/{id}/set-default")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SlaPolicyDtos.SlaPolicyResponse> setAsDefault(@PathVariable Long id) {
        log.info("POST /api/sla-policies/{}/set-default", id);
        SlaPolicy policy = slaPolicyService.setAsDefault(id, "admin");
        return ResponseEntity.ok(SlaPolicyDtos.SlaPolicyResponse.fromEntity(policy));
    }

    // ==================== SLA Deadline Endpoints ====================

    /**
     * Lấy SLA deadline cho một priority.
     * GET /api/sla-policies/deadline?priority=CRITICAL
     */
    @GetMapping("/deadline")
    public ResponseEntity<SlaPolicyDtos.SlaDeadlineResponse> getSlaDeadline(
            @RequestParam String priority) {
        log.info("GET /api/sla-policies/deadline?priority={}", priority);
        
        TicketPriority ticketPriority = TicketPriority.valueOf(priority.toUpperCase());
        SlaPolicyService.SlaDeadline deadline = slaPolicyService.calculateSlaDeadlines(
                ticketPriority, 
                java.time.LocalDateTime.now()
        );
        
        return ResponseEntity.ok(SlaPolicyDtos.SlaDeadlineResponse.fromDeadline(deadline));
    }

    /**
     * Lấy tất cả default policies summary.
     * GET /api/sla-policies/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<List<SlaPolicyDtos.PrioritySlaSummary>> getSlaSummary() {
        log.info("GET /api/sla-policies/summary");
        
        List<SlaPolicyDtos.PrioritySlaSummary> summary = java.util.Arrays.stream(TicketPriority.values())
                .map(priority -> {
                    SlaPolicy policy = slaPolicyService.getEffectivePolicyForPriority(priority);
                    SlaPolicyDtos.PrioritySlaSummary s = new SlaPolicyDtos.PrioritySlaSummary();
                    s.setPriority(priority.name());
                    s.setResponseTimeFormatted(policy.getResponseTimeFormatted());
                    s.setResolutionTimeFormatted(policy.getResolutionTimeFormatted());
                    s.setWarningThreshold(policy.getWarningThreshold());
                    return s;
                })
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(summary);
    }

    /**
     * Refresh SLA policy cache.
     * POST /api/sla-policies/refresh-cache
     */
    @PostMapping("/refresh-cache")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> refreshCache() {
        log.info("POST /api/sla-policies/refresh-cache");
        slaPolicyService.refreshDefaultPolicyCache();
        return ResponseEntity.ok().build();
    }

    // ==================== Exception Handlers ====================

    @ExceptionHandler(SlaPolicyNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleNotFound(SlaPolicyNotFoundException ex) {
        log.error("SLA Policy not found: {}", ex.getMessage());
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

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalStateException ex) {
        log.error("Conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONFLICT", ex.getMessage()));
    }

    /**
     * Error response DTO.
     */
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

    // ==================== Helper Methods ====================

    private SlaPolicy mapRequestToEntity(SlaPolicyDtos.SlaPolicyRequest request) {
        SlaPolicy policy = new SlaPolicy();
        policy.setName(request.getName());
        policy.setDescription(request.getDescription());
        policy.setPriority(request.getPriority());
        policy.setResponseMinutes(request.getResponseMinutes());
        policy.setResolutionMinutes(request.getResolutionMinutes());
        policy.setBusinessHoursOnly(request.getBusinessHoursOnly() != null ? request.getBusinessHoursOnly() : false);
        policy.setWarningThreshold(request.getWarningThreshold() != null ? request.getWarningThreshold() : 75);
        policy.setSecondResponseMinutes(request.getSecondResponseMinutes());
        policy.setIsDefault(request.getIsDefault() != null ? request.getIsDefault() : false);
        policy.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        return policy;
    }
}

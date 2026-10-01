package com.example.ticketing.servicerequest;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller cho Service Request Management.
 */
@RestController
@RequestMapping("/api/service-requests")
@CrossOrigin(origins = "*")
public class ServiceRequestController {

    private static final Logger log = LoggerFactory.getLogger(ServiceRequestController.class);

    private final ServiceRequestService serviceRequestService;

    public ServiceRequestController(ServiceRequestService serviceRequestService) {
        this.serviceRequestService = serviceRequestService;
    }

    // ==================== Service Catalog ====================

    /**
     * Lấy service catalog.
     * GET /api/service-requests/catalog
     */
    @GetMapping("/catalog")
    public ResponseEntity<List<ServiceCatalogDto>> getServiceCatalog() {
        log.info("GET /api/service-requests/catalog");
        List<ServiceCatalogItem> services = serviceRequestService.getPublicServices();
        return ResponseEntity.ok(services.stream().map(ServiceCatalogDto::fromEntity).toList());
    }

    /**
     * Lấy categories.
     * GET /api/service-requests/categories
     */
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        log.info("GET /api/service-requests/categories");
        List<String> categories = serviceRequestService.getCategories();
        return ResponseEntity.ok(categories);
    }

    // ==================== Service Requests CRUD ====================

    /**
     * Lấy tất cả service requests.
     * GET /api/service-requests
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<Page<ServiceRequestDto>> getAllRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/service-requests - status: {}, search: {}", status, search);

        Pageable pageable = Pageable.ofSize(size).withPage(page);
        Page<ServiceRequest> requests;

        if (search != null && !search.isBlank()) {
            requests = serviceRequestService.searchRequests(search, pageable);
        } else if (status != null && !status.isBlank()) {
            ServiceRequest.ServiceStatus serviceStatus = ServiceRequest.ServiceStatus.valueOf(status);
            requests = serviceRequestService.getAllRequests(pageable);
            // Filter by status in memory for simplicity
        } else {
            requests = serviceRequestService.getAllRequests(pageable);
        }

        return ResponseEntity.ok(requests.map(ServiceRequestDto::fromEntity));
    }

    /**
     * Lấy requests của user hiện tại.
     * GET /api/service-requests/my
     */
    @GetMapping("/my")
    public ResponseEntity<Page<ServiceRequestDto>> getMyRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/service-requests/my");
        // In real app, get from SecurityContext
        Pageable pageable = Pageable.ofSize(size).withPage(page);
        Page<ServiceRequest> requests = serviceRequestService.getMyRequests("current_user", pageable);
        return ResponseEntity.ok(requests.map(ServiceRequestDto::fromEntity));
    }

    /**
     * Lấy request theo ID.
     * GET /api/service-requests/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ServiceRequestDto> getRequestById(@PathVariable Long id) {
        log.info("GET /api/service-requests/{}", id);
        ServiceRequest request = serviceRequestService.getRequestById(id);
        return ResponseEntity.ok(ServiceRequestDto.fromEntity(request));
    }

    /**
     * Tạo service request mới.
     * POST /api/service-requests
     */
    @PostMapping
    public ResponseEntity<ServiceRequestDto> createRequest(@RequestBody ServiceRequestRequest request) {
        log.info("POST /api/service-requests - Creating: {}", request.getTitle());

        ServiceRequest serviceRequest = new ServiceRequest();
        serviceRequest.setTitle(request.getTitle());
        serviceRequest.setDescription(request.getDescription());
        serviceRequest.setServiceId(request.getServiceId());
        serviceRequest.setPriority(request.getPriority());
        serviceRequest.setFormData(request.getFormData());

        ServiceRequest created = serviceRequestService.createRequest(
                serviceRequest,
                request.getRequesterUsername(),
                request.getRequesterName(),
                request.getRequesterEmail(),
                request.getRequesterDepartment()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ServiceRequestDto.fromEntity(created));
    }

    /**
     * Cập nhật request.
     * PUT /api/service-requests/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<ServiceRequestDto> updateRequest(
            @PathVariable Long id,
            @RequestBody ServiceRequestRequest request) {
        log.info("PUT /api/service-requests/{}", id);

        ServiceRequest updates = new ServiceRequest();
        updates.setTitle(request.getTitle());
        updates.setDescription(request.getDescription());
        updates.setPriority(request.getPriority());

        ServiceRequest updated = serviceRequestService.updateRequest(id, updates, "admin");

        return ResponseEntity.ok(ServiceRequestDto.fromEntity(updated));
    }

    /**
     * Xóa request.
     * DELETE /api/service-requests/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRequest(@PathVariable Long id) {
        log.info("DELETE /api/service-requests/{}", id);
        serviceRequestService.deleteRequest(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== Status Management ====================

    /**
     * Cập nhật trạng thái.
     * PATCH /api/service-requests/{id}/status
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<ServiceRequestDto> updateStatus(
            @PathVariable Long id,
            @RequestBody StatusUpdateRequest request) {
        log.info("PATCH /api/service-requests/{}/status - status: {}", id, request.getStatus());

        ServiceRequest updated = serviceRequestService.updateStatus(
                id,
                ServiceRequest.ServiceStatus.valueOf(request.getStatus()),
                "admin"
        );

        return ResponseEntity.ok(ServiceRequestDto.fromEntity(updated));
    }

    /**
     * Phê duyệt request.
     * PATCH /api/service-requests/{id}/approve
     */
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<ServiceRequestDto> approveRequest(
            @PathVariable Long id,
            @RequestBody ApprovalRequest request) {
        log.info("PATCH /api/service-requests/{}/approve", id);

        ServiceRequest updated = serviceRequestService.approveRequest(
                id,
                request.getApprover(),
                request.getNotes()
        );

        return ResponseEntity.ok(ServiceRequestDto.fromEntity(updated));
    }

    /**
     * Từ chối request.
     * PATCH /api/service-requests/{id}/reject
     */
    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<ServiceRequestDto> rejectRequest(
            @PathVariable Long id,
            @RequestBody ApprovalRequest request) {
        log.info("PATCH /api/service-requests/{}/reject", id);

        ServiceRequest updated = serviceRequestService.rejectRequest(
                id,
                request.getApprover(),
                request.getNotes()
        );

        return ResponseEntity.ok(ServiceRequestDto.fromEntity(updated));
    }

    /**
     * Assign request.
     * PATCH /api/service-requests/{id}/assign
     */
    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<ServiceRequestDto> assignRequest(
            @PathVariable Long id,
            @RequestBody AssignRequest request) {
        log.info("PATCH /api/service-requests/{}/assign - to: {}", id, request.getAssignee());

        ServiceRequest updated = serviceRequestService.assignRequest(id, request.getAssignee(), "admin");

        return ResponseEntity.ok(ServiceRequestDto.fromEntity(updated));
    }

    // ==================== Comments ====================

    /**
     * Lấy comments.
     * GET /api/service-requests/{id}/comments
     */
    @GetMapping("/{id}/comments")
    public ResponseEntity<List<ServiceRequestCommentDto>> getComments(@PathVariable Long id) {
        log.info("GET /api/service-requests/{}/comments", id);
        List<ServiceRequestComment> comments = serviceRequestService.getComments(id);
        return ResponseEntity.ok(comments.stream()
                .map(ServiceRequestCommentDto::fromEntity)
                .toList());
    }

    /**
     * Thêm comment.
     * POST /api/service-requests/{id}/comments
     */
    @PostMapping("/{id}/comments")
    public ResponseEntity<ServiceRequestCommentDto> addComment(
            @PathVariable Long id,
            @RequestBody CommentRequest request) {
        log.info("POST /api/service-requests/{}/comments", id);

        ServiceRequestComment comment = serviceRequestService.addComment(
                id,
                request.getBody(),
                request.getAuthorName(),
                request.getAuthorUsername(),
                request.isInternal()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ServiceRequestCommentDto.fromEntity(comment));
    }

    // ==================== Exception Handlers ====================

    @ExceptionHandler(ServiceRequestService.ServiceRequestNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleNotFound(ServiceRequestService.ServiceRequestNotFoundException ex) {
        log.error("Service request not found: {}", ex.getMessage());
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

    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponse> handleNullPointer(NullPointerException ex) {
        log.error("NullPointerException: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "Lỗi hệ thống: " + ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("General error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "Lỗi không xác định: " + ex.getMessage()));
    }

    /**
     * Tạo service request mới (cho End User - tự động lấy user info từ header).
     * POST /api/service-requests/my
     */
    @PostMapping("/my")
    public ResponseEntity<ServiceRequestDto> createRequestForCurrentUser(
            @RequestBody ServiceRequestRequest request,
            @RequestHeader(value = "X-User-Username", required = false) String username,
            @RequestHeader(value = "X-User-Name", required = false) String userName,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestHeader(value = "X-User-Department", required = false) String department) {
        log.info("POST /api/service-requests/my - by: {}", username);

        ServiceRequest serviceRequest = new ServiceRequest();
        serviceRequest.setTitle(request.getTitle());
        serviceRequest.setDescription(request.getDescription());
        serviceRequest.setServiceId(request.getServiceId());
        serviceRequest.setPriority(request.getPriority());
        serviceRequest.setFormData(request.getFormData());

        ServiceRequest created = serviceRequestService.createRequest(
                serviceRequest,
                username,
                userName,
                email,
                department
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ServiceRequestDto.fromEntity(created));
    }

    /**
     * IT Staff nhận request (Take Ownership).
     * PATCH /api/service-requests/{id}/take-ownership
     */
    @PatchMapping("/{id}/take-ownership")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<ServiceRequestDto> takeOwnership(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Username", required = false) String username) {
        log.info("PATCH /api/service-requests/{}/take-ownership - by: {}", id, username);

        ServiceRequest updated = serviceRequestService.takeOwnership(id, username);

        return ResponseEntity.ok(ServiceRequestDto.fromEntity(updated));
    }

    /**
     * IT Staff hoàn thành request.
     * PATCH /api/service-requests/{id}/complete
     */
    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'GIAM_DOC', 'TRUONG_PHONG', 'NHAN_VIEN')")
    public ResponseEntity<ServiceRequestDto> completeRequest(
            @PathVariable Long id,
            @RequestBody CompletionRequest request,
            @RequestHeader(value = "X-User-Username", required = false) String username) {
        log.info("PATCH /api/service-requests/{}/complete - by: {}", id, username);

        ServiceRequest updated = serviceRequestService.completeRequest(id, request.getResolution(), username);

        return ResponseEntity.ok(ServiceRequestDto.fromEntity(updated));
    }

    /**
     * User xác nhận hoàn thành.
     * PATCH /api/service-requests/{id}/confirm
     */
    @PatchMapping("/{id}/confirm")
    public ResponseEntity<ServiceRequestDto> confirmCompletion(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Username", required = false) String username) {
        log.info("PATCH /api/service-requests/{}/confirm - by: {}", id, username);

        ServiceRequest updated = serviceRequestService.confirmCompletion(id, username);

        return ResponseEntity.ok(ServiceRequestDto.fromEntity(updated));
    }

    /**
     * User yêu cầu mở lại.
     * PATCH /api/service-requests/{id}/reopen
     */
    @PatchMapping("/{id}/reopen")
    public ResponseEntity<ServiceRequestDto> reopenRequest(
            @PathVariable Long id,
            @RequestBody ReopenRequest request,
            @RequestHeader(value = "X-User-Username", required = false) String username) {
        log.info("PATCH /api/service-requests/{}/reopen - by: {}", id, username);

        ServiceRequest updated = serviceRequestService.reopenRequest(id, request.getReason(), username);

        return ResponseEntity.ok(ServiceRequestDto.fromEntity(updated));
    }

    // ==================== DTOs ====================

    public static class CompletionRequest {
        private String resolution;

        public String getResolution() { return resolution; }
    }

    public static class ReopenRequest {
        private String reason;

        public String getReason() { return reason; }
    }

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

    public static class ServiceRequestRequest {
        private String title;
        private String description;
        private Long serviceId;
        private ServiceRequest.Priority priority;
        private String formData;
        private String requesterUsername;
        private String requesterName;
        private String requesterEmail;
        private String requesterDepartment;

        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public Long getServiceId() { return serviceId; }
        public ServiceRequest.Priority getPriority() { return priority; }
        public String getFormData() { return formData; }
        public String getRequesterUsername() { return requesterUsername; }
        public String getRequesterName() { return requesterName; }
        public String getRequesterEmail() { return requesterEmail; }
        public String getRequesterDepartment() { return requesterDepartment; }
    }

    public static class StatusUpdateRequest {
        private String status;

        public String getStatus() { return status; }
    }

    public static class ApprovalRequest {
        private String approver;
        private String notes;

        public String getApprover() { return approver; }
        public String getNotes() { return notes; }
    }

    public static class AssignRequest {
        private String assignee;

        public String getAssignee() { return assignee; }
    }

    public static class CommentRequest {
        private String body;
        private String authorName;
        private String authorUsername;
        private boolean internal;

        public String getBody() { return body; }
        public String getAuthorName() { return authorName; }
        public String getAuthorUsername() { return authorUsername; }
        public boolean isInternal() { return internal; }
    }

    public static class ServiceCatalogDto {
        private Long id;
        private String name;
        private String description;
        private String category;
        private String icon;
        private String color;
        private Integer responseSlaMinutes;
        private Integer resolutionSlaMinutes;
        private Boolean requiresApproval;
        private Boolean billable;

        public static ServiceCatalogDto fromEntity(ServiceCatalogItem item) {
            ServiceCatalogDto dto = new ServiceCatalogDto();
            dto.setId(item.getId());
            dto.setName(item.getName());
            dto.setDescription(item.getDescription());
            dto.setCategory(item.getCategory());
            dto.setIcon(item.getIcon());
            dto.setColor(item.getColor());
            dto.setResponseSlaMinutes(item.getResponseSlaMinutes());
            dto.setResolutionSlaMinutes(item.getResolutionSlaMinutes());
            dto.setRequiresApproval(item.getRequiresApproval());
            dto.setBillable(item.getBillable());
            return dto;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getIcon() { return icon; }
        public void setIcon(String icon) { this.icon = icon; }
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        public Integer getResponseSlaMinutes() { return responseSlaMinutes; }
        public void setResponseSlaMinutes(Integer responseSlaMinutes) { this.responseSlaMinutes = responseSlaMinutes; }
        public Integer getResolutionSlaMinutes() { return resolutionSlaMinutes; }
        public void setResolutionSlaMinutes(Integer resolutionSlaMinutes) { this.resolutionSlaMinutes = resolutionSlaMinutes; }
        public Boolean getRequiresApproval() { return requiresApproval; }
        public void setRequiresApproval(Boolean requiresApproval) { this.requiresApproval = requiresApproval; }
        public Boolean getBillable() { return billable; }
        public void setBillable(Boolean billable) { this.billable = billable; }
    }

    public static class ServiceRequestDto {
        private Long id;
        private String requestNumber;
        private Long serviceId;
        private String serviceName;
        private String title;
        private String description;
        private String requesterName;
        private String requesterUsername;
        private String status;
        private String statusLabel;
        private String priority;
        private String assignedTo;
        private java.time.LocalDateTime slaResponseAt;
        private java.time.LocalDateTime slaResolutionAt;
        private Boolean approvalRequired;
        private Boolean billable;
        private java.time.LocalDateTime completedAt;
        private java.time.LocalDateTime createdAt;

        public static ServiceRequestDto fromEntity(ServiceRequest sr) {
            ServiceRequestDto dto = new ServiceRequestDto();
            dto.setId(sr.getId());
            dto.setRequestNumber(sr.getRequestNumber());
            dto.setServiceId(sr.getServiceId());
            dto.setServiceName(sr.getServiceName());
            dto.setTitle(sr.getTitle());
            dto.setDescription(sr.getDescription());
            dto.setRequesterName(sr.getRequesterName());
            dto.setRequesterUsername(sr.getRequesterUsername());
            dto.setStatus(sr.getStatus() != null ? sr.getStatus().name() : null);
            dto.setStatusLabel(sr.getStatus() != null ? sr.getStatus().getLabel() : null);
            dto.setPriority(sr.getPriority() != null ? sr.getPriority().name() : null);
            dto.setAssignedTo(sr.getAssignedTo());
            dto.setSlaResponseAt(sr.getSlaResponseAt());
            dto.setSlaResolutionAt(sr.getSlaResolutionAt());
            dto.setApprovalRequired(sr.getApprovalRequired());
            dto.setBillable(sr.getBillable());
            dto.setCompletedAt(sr.getCompletedAt());
            dto.setCreatedAt(sr.getCreatedAt());
            return dto;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getRequestNumber() { return requestNumber; }
        public void setRequestNumber(String requestNumber) { this.requestNumber = requestNumber; }
        public Long getServiceId() { return serviceId; }
        public void setServiceId(Long serviceId) { this.serviceId = serviceId; }
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getRequesterName() { return requesterName; }
        public void setRequesterName(String requesterName) { this.requesterName = requesterName; }
        public String getRequesterUsername() { return requesterUsername; }
        public void setRequesterUsername(String requesterUsername) { this.requesterUsername = requesterUsername; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getStatusLabel() { return statusLabel; }
        public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public String getAssignedTo() { return assignedTo; }
        public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
        public java.time.LocalDateTime getSlaResponseAt() { return slaResponseAt; }
        public void setSlaResponseAt(java.time.LocalDateTime slaResponseAt) { this.slaResponseAt = slaResponseAt; }
        public java.time.LocalDateTime getSlaResolutionAt() { return slaResolutionAt; }
        public void setSlaResolutionAt(java.time.LocalDateTime slaResolutionAt) { this.slaResolutionAt = slaResolutionAt; }
        public Boolean getApprovalRequired() { return approvalRequired; }
        public void setApprovalRequired(Boolean approvalRequired) { this.approvalRequired = approvalRequired; }
        public Boolean getBillable() { return billable; }
        public void setBillable(Boolean billable) { this.billable = billable; }
        public java.time.LocalDateTime getCompletedAt() { return completedAt; }
        public void setCompletedAt(java.time.LocalDateTime completedAt) { this.completedAt = completedAt; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    public static class ServiceRequestCommentDto {
        private Long id;
        private String authorName;
        private String authorUsername;
        private String visibility;
        private String body;
        private java.time.LocalDateTime createdAt;

        public static ServiceRequestCommentDto fromEntity(ServiceRequestComment comment) {
            ServiceRequestCommentDto dto = new ServiceRequestCommentDto();
            dto.setId(comment.getId());
            dto.setAuthorName(comment.getAuthorName());
            dto.setAuthorUsername(comment.getAuthorUsername());
            dto.setVisibility(comment.getVisibility() != null ? comment.getVisibility().name() : null);
            dto.setBody(comment.getBody());
            dto.setCreatedAt(comment.getCreatedAt());
            return dto;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getAuthorName() { return authorName; }
        public void setAuthorName(String authorName) { this.authorName = authorName; }
        public String getAuthorUsername() { return authorUsername; }
        public void setAuthorUsername(String authorUsername) { this.authorUsername = authorUsername; }
        public String getVisibility() { return visibility; }
        public void setVisibility(String visibility) { this.visibility = visibility; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
}

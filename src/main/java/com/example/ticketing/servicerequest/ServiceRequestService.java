package com.example.ticketing.servicerequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.ticketing.auth.Notification;
import com.example.ticketing.auth.NotificationRepository;
import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.auth.UserRole;
import com.example.ticketing.department.ItDepartmentResolver;

/**
 * Service cho Service Request Management.
 */
@Service
@Transactional
public class ServiceRequestService {

    private static final Logger log = LoggerFactory.getLogger(ServiceRequestService.class);

    private final ServiceRequestRepository serviceRequestRepository;
    private final ServiceRequestCommentRepository commentRepository;
    private final ServiceCatalogRepository catalogRepository;
    private final NotificationRepository notificationRepository;
    private final UserAccountRepository userAccountRepository;
    private final ItDepartmentResolver itDepartmentResolver;

    public ServiceRequestService(
            ServiceRequestRepository serviceRequestRepository,
            ServiceRequestCommentRepository commentRepository,
            ServiceCatalogRepository catalogRepository,
            NotificationRepository notificationRepository,
            UserAccountRepository userAccountRepository,
            ItDepartmentResolver itDepartmentResolver) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.commentRepository = commentRepository;
        this.catalogRepository = catalogRepository;
        this.notificationRepository = notificationRepository;
        this.userAccountRepository = userAccountRepository;
        this.itDepartmentResolver = itDepartmentResolver;
    }

    // ==================== Service Catalog ====================

    /**
     * Lấy tất cả services trong catalog.
     */
    @Transactional(readOnly = true)
    public List<ServiceCatalogItem> getAllServices() {
        return catalogRepository.findByEnabledTrueOrderByCategoryAscNameAsc();
    }

    /**
     * Lấy services public.
     */
    @Transactional(readOnly = true)
    public List<ServiceCatalogItem> getPublicServices() {
        return catalogRepository.findByEnabledTrueAndIsPublicTrue();
    }

    /**
     * Lấy services theo category.
     */
    @Transactional(readOnly = true)
    public List<ServiceCatalogItem> getServicesByCategory(String category) {
        return catalogRepository.findByCategory(category);
    }

    /**
     * Lấy categories.
     */
    @Transactional(readOnly = true)
    public List<String> getCategories() {
        return catalogRepository.findDistinctCategories();
    }

    // ==================== Service Request CRUD ====================

    /**
     * Tạo service request mới.
     */
    public ServiceRequest createRequest(
            ServiceRequest request,
            String requesterUsername,
            String requesterName,
            String requesterEmail,
            String requesterDepartment) {
        log.info("Creating service request: {}", request.getTitle());

        // Generate request number
        request.setRequestNumber(generateRequestNumber());

        // Set requester info
        request.setRequesterUsername(requesterUsername);
        request.setRequesterName(requesterName);
        request.setRequesterEmail(requesterEmail);
        request.setRequesterDepartment(requesterDepartment);

        // Calculate SLA based on service
        if (request.getServiceId() != null) {
            catalogRepository.findById(request.getServiceId()).ifPresent(service -> {
                LocalDateTime now = LocalDateTime.now();
                request.setSlaResponseAt(now.plusMinutes(service.getResponseSlaMinutes()));
                request.setSlaResolutionAt(now.plusMinutes(service.getResolutionSlaMinutes()));
                request.setServiceName(service.getName());
                request.setApprovalRequired(service.getRequiresApproval());
                request.setBillable(service.getBillable());
            });
        }

        // Set initial status
        if (Boolean.TRUE.equals(request.getApprovalRequired())) {
            request.setStatus(ServiceRequest.ServiceStatus.PENDING_APPROVAL);
        } else {
            request.setStatus(ServiceRequest.ServiceStatus.SUBMITTED);
        }

        request.setCreatedBy(requesterUsername);

        ServiceRequest saved = serviceRequestRepository.save(request);
        notifyRequestCreated(saved);
        return saved;
    }

    /** Admins + IT operators (who process/approve SRs) get notified; the requester does not. */
    private void notifyRequestCreated(ServiceRequest sr) {
        String title = sr.getStatus() == ServiceRequest.ServiceStatus.PENDING_APPROVAL
                ? "Yêu cầu dịch vụ mới cần duyệt" : "Yêu cầu dịch vụ mới";
        String message = sr.getRequestNumber() + " - " + sr.getTitle()
                + " (từ " + (sr.getRequesterName() != null ? sr.getRequesterName() : sr.getRequesterUsername()) + ")";
        for (UserAccount user : userAccountRepository.findAll()) {
            boolean itOperator = (user.getRole() == UserRole.Role.TRUONG_PHONG || user.getRole() == UserRole.Role.NHAN_VIEN)
                    && itDepartmentResolver.isITDepartment(user.getDepartment());
            if (!user.isEnabled() || !(user.isAdmin() || itOperator)
                    || user.getUsername().equalsIgnoreCase(sr.getRequesterUsername())) {
                continue;
            }
            Notification n = new Notification(user.getUsername(),
                    Notification.NotificationType.SERVICE_REQUEST_CREATED, title, message);
            n.setTicketId(sr.getId());
            n.setTicketNumber(sr.getRequestNumber());
            n.setActorUsername(sr.getRequesterUsername());
            notificationRepository.save(n);
        }
    }

    /**
     * Lấy request theo ID.
     */
    @Transactional(readOnly = true)
    public ServiceRequest getRequestById(Long id) {
        return serviceRequestRepository.findById(id)
                .orElseThrow(() -> new ServiceRequestNotFoundException(id));
    }

    /**
     * Lấy request theo request number.
     */
    @Transactional(readOnly = true)
    public ServiceRequest getRequestByNumber(String requestNumber) {
        return serviceRequestRepository.findByRequestNumber(requestNumber)
                .orElseThrow(() -> new ServiceRequestNotFoundException(requestNumber));
    }

    /**
     * Cập nhật request.
     */
    public ServiceRequest updateRequest(Long id, ServiceRequest updates, String updatedBy) {
        ServiceRequest existing = getRequestById(id);

        requireStatus(existing, "sửa", ServiceRequest.ServiceStatus.SUBMITTED,
                ServiceRequest.ServiceStatus.PENDING_APPROVAL, ServiceRequest.ServiceStatus.APPROVED,
                ServiceRequest.ServiceStatus.IN_PROGRESS);
        existing.setTitle(updates.getTitle());
        existing.setDescription(updates.getDescription());
        if (updates.getPriority() != null) {
            existing.setPriority(updates.getPriority());
        }
        existing.setUpdatedBy(updatedBy);

        return serviceRequestRepository.save(existing);
    }

    /**
     * Xóa request.
     */
    public void deleteRequest(Long id) {
        log.info("Deleting service request: {}", id);
        serviceRequestRepository.deleteById(id);
    }

    // ==================== Status Management ====================

    /**
     * Cập nhật trạng thái request.
     */
    public ServiceRequest updateStatus(Long id, ServiceRequest.ServiceStatus newStatus, String actorName) {
        ServiceRequest request = getRequestById(id);

        request.setStatus(newStatus);
        request.setUpdatedBy(actorName);

        // Track completion time
        if (newStatus == ServiceRequest.ServiceStatus.COMPLETED) {
            request.setCompletedAt(LocalDateTime.now());
        }

        return serviceRequestRepository.save(request);
    }

    /**
     * Phê duyệt request.
     */
    public ServiceRequest approveRequest(Long id, String approverName, String notes) {
        ServiceRequest request = getRequestById(id);
        requireStatus(request, "duyệt", ServiceRequest.ServiceStatus.PENDING_APPROVAL);

        request.setStatus(ServiceRequest.ServiceStatus.APPROVED);
        request.setApprovedBy(approverName);
        request.setApprovedAt(LocalDateTime.now());
        request.setApprovalNotes(notes);

        ServiceRequest saved = serviceRequestRepository.save(request);
        notifyRequester(saved, "Yêu cầu dịch vụ đã được duyệt", approverName);
        return saved;
    }

    /**
     * Từ chối request.
     */
    public ServiceRequest rejectRequest(Long id, String approverName, String reason) {
        ServiceRequest request = getRequestById(id);
        requireStatus(request, "từ chối", ServiceRequest.ServiceStatus.PENDING_APPROVAL);

        request.setStatus(ServiceRequest.ServiceStatus.REJECTED);
        request.setApprovedBy(approverName);
        request.setApprovedAt(LocalDateTime.now());
        request.setApprovalNotes(reason);

        ServiceRequest saved = serviceRequestRepository.save(request);
        notifyRequester(saved, "Yêu cầu dịch vụ bị từ chối"
                + (reason != null && !reason.isBlank() ? ": " + reason : ""), approverName);
        return saved;
    }

    /**
     * Assign request cho IT staff.
     */
    public ServiceRequest assignRequest(Long id, String assignee, String actorName) {
        ServiceRequest request = getRequestById(id);
        requireStatus(request, "phân công", ServiceRequest.ServiceStatus.SUBMITTED,
                ServiceRequest.ServiceStatus.APPROVED, ServiceRequest.ServiceStatus.IN_PROGRESS);
        UserAccount target = assignee == null ? null : userAccountRepository.findByUsername(assignee).orElse(null);
        if (target == null || !target.isEnabled() || !itDepartmentResolver.isITDepartment(target.getDepartment())) {
            throw new IllegalArgumentException("Chỉ được phân công cho nhân viên IT đang hoạt động.");
        }
        request.setAssignedTo(target.getUsername());
        request.setUpdatedBy(actorName);

        // Set to IN_PROGRESS if currently SUBMITTED
        if (request.getStatus() == ServiceRequest.ServiceStatus.SUBMITTED) {
            request.setStatus(ServiceRequest.ServiceStatus.IN_PROGRESS);
            request.setFirstResponseAt(LocalDateTime.now());
        }

        ServiceRequest saved = serviceRequestRepository.save(request);
        if (!target.getUsername().equalsIgnoreCase(actorName)) {
            Notification n = new Notification(target.getUsername(),
                    Notification.NotificationType.SERVICE_REQUEST_UPDATED, "Bạn được giao yêu cầu dịch vụ",
                    saved.getRequestNumber() + " - " + saved.getTitle());
            n.setTicketId(saved.getId());
            n.setTicketNumber(saved.getRequestNumber());
            n.setActorUsername(actorName);
            notificationRepository.save(n);
        }
        notifyRequester(saved, "Yêu cầu dịch vụ đang được xử lý", actorName);
        return saved;
    }

    /**
     * IT Staff nhận request (Take Ownership).
     */
    public ServiceRequest takeOwnership(Long id, String username) {
        ServiceRequest request = getRequestById(id);
        // PENDING_APPROVAL must be approved first; taking it would bypass the approval step.
        requireStatus(request, "nhận xử lý", ServiceRequest.ServiceStatus.SUBMITTED, ServiceRequest.ServiceStatus.APPROVED);
        request.setAssignedTo(username);
        request.setStatus(ServiceRequest.ServiceStatus.IN_PROGRESS);
        request.setFirstResponseAt(LocalDateTime.now());
        request.setUpdatedBy(username);
        ServiceRequest saved = serviceRequestRepository.save(request);
        notifyRequester(saved, "Yêu cầu dịch vụ đang được xử lý", username);
        return saved;
    }

    /**
     * IT Staff hoàn thành request.
     */
    public ServiceRequest completeRequest(Long id, String resolution, String username) {
        ServiceRequest request = getRequestById(id);
        requireStatus(request, "hoàn thành", ServiceRequest.ServiceStatus.IN_PROGRESS);
        request.setStatus(ServiceRequest.ServiceStatus.COMPLETED);
        request.setCompletedAt(LocalDateTime.now());
        if (resolution != null) {
            request.setResolution(resolution);
        }
        request.setUpdatedBy(username);
        ServiceRequest saved = serviceRequestRepository.save(request);
        notifyRequester(saved, "Yêu cầu dịch vụ đã hoàn thành, vui lòng xác nhận", username);
        return saved;
    }

    /**
     * User xác nhận hoàn thành (đóng request).
     */
    public ServiceRequest confirmCompletion(Long id, String username) {
        ServiceRequest request = getRequestById(id);
        requireStatus(request, "xác nhận", ServiceRequest.ServiceStatus.COMPLETED);
        request.setStatus(ServiceRequest.ServiceStatus.CLOSED);
        request.setUpdatedBy(username);
        return serviceRequestRepository.save(request);
    }

    /**
     * Hủy request (người yêu cầu hoặc IT) khi chưa hoàn thành.
     */
    public ServiceRequest cancelRequest(Long id, String username) {
        ServiceRequest request = getRequestById(id);
        requireStatus(request, "hủy", ServiceRequest.ServiceStatus.SUBMITTED,
                ServiceRequest.ServiceStatus.PENDING_APPROVAL, ServiceRequest.ServiceStatus.APPROVED,
                ServiceRequest.ServiceStatus.IN_PROGRESS);
        request.setStatus(ServiceRequest.ServiceStatus.CANCELLED);
        request.setUpdatedBy(username);
        ServiceRequest saved = serviceRequestRepository.save(request);
        notifyRequester(saved, "Yêu cầu dịch vụ đã bị hủy", username);
        return saved;
    }

    /**
     * User yêu cầu mở lại.
     */
    public ServiceRequest reopenRequest(Long id, String reason, String username) {
        ServiceRequest request = getRequestById(id);
        requireStatus(request, "mở lại", ServiceRequest.ServiceStatus.COMPLETED, ServiceRequest.ServiceStatus.CLOSED);
        request.setStatus(ServiceRequest.ServiceStatus.IN_PROGRESS);
        request.setCompletedAt(null);
        request.setUpdatedBy(username);
        return serviceRequestRepository.save(request);
    }

    // ==================== Comments ====================

    /**
     * Thêm comment.
     */
    public ServiceRequestComment addComment(Long requestId, String body, String authorName,
            String authorUsername, boolean isInternal) {
        ServiceRequest request = getRequestById(requestId);

        ServiceRequestComment comment = new ServiceRequestComment(request, authorName, authorUsername, body);
        comment.setAuthorRole("IT_STAFF");
        comment.setVisibility(
                isInternal ? ServiceRequestComment.Visibility.INTERNAL : ServiceRequestComment.Visibility.PUBLIC);

        return commentRepository.save(comment);
    }

    /**
     * Lấy comments của request.
     */
    @Transactional(readOnly = true)
    public List<ServiceRequestComment> getComments(Long requestId) {
        return commentRepository.findByServiceRequestIdOrderByCreatedAtDesc(requestId);
    }

    // ==================== Query Methods ====================

    /**
     * Danh sách theo phạm vi quyền của actor (scope từ ServiceRequestAuthorization.readScope).
     */
    @Transactional(readOnly = true)
    public Page<ServiceRequest> listRequests(Specification<ServiceRequest> scope,
                                             ServiceRequest.ServiceStatus status,
                                             String search,
                                             Pageable pageable) {
        List<Specification<ServiceRequest>> parts = new ArrayList<>();
        parts.add(scope);
        if (status != null) {
            parts.add((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        if (search != null && !search.isBlank()) {
            String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            parts.add((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("description")), like),
                    cb.like(cb.lower(root.get("requestNumber")), like)));
        }
        return serviceRequestRepository.findAll(Specification.allOf(parts), pageable);
    }

    /**
     * Lấy tất cả requests.
     */
    @Transactional(readOnly = true)
    public Page<ServiceRequest> getAllRequests(Pageable pageable) {
        return serviceRequestRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * Lấy requests của requester.
     */
    @Transactional(readOnly = true)
    public Page<ServiceRequest> getMyRequests(String username, Pageable pageable) {
        return serviceRequestRepository.findByRequesterOrAssignee(username, pageable);
    }

    /**
     * Lấy requests đang chờ.
     */
    @Transactional(readOnly = true)
    public Page<ServiceRequest> getPendingRequests(Pageable pageable) {
        return getRequestsByStatus(ServiceRequest.ServiceStatus.PENDING_APPROVAL, pageable);
    }

    /**
     * Lấy requests theo trạng thái.
     */
    @Transactional(readOnly = true)
    public Page<ServiceRequest> getRequestsByStatus(ServiceRequest.ServiceStatus status, Pageable pageable) {
        return serviceRequestRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    /**
     * Tìm kiếm requests.
     */
    @Transactional(readOnly = true)
    public Page<ServiceRequest> searchRequests(String search, Pageable pageable) {
        return serviceRequestRepository.searchServiceRequests(search, pageable);
    }

    // ==================== Helper Methods ====================

    private static void requireStatus(ServiceRequest sr, String action, ServiceRequest.ServiceStatus... allowed) {
        for (ServiceRequest.ServiceStatus s : allowed) {
            if (sr.getStatus() == s) return;
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Không thể " + action + " yêu cầu ở trạng thái " + sr.getStatus().getLabel() + ".");
    }

    /** Tell the requester their request moved; skip when they did it themselves. */
    private void notifyRequester(ServiceRequest sr, String title, String actor) {
        String requester = sr.getRequesterUsername();
        if (requester == null || requester.equalsIgnoreCase(actor)) return;
        Notification n = new Notification(requester, Notification.NotificationType.SERVICE_REQUEST_UPDATED,
                title, sr.getRequestNumber() + " - " + sr.getTitle());
        n.setTicketId(sr.getId());
        n.setTicketNumber(sr.getRequestNumber());
        n.setActorUsername(actor);
        notificationRepository.save(n);
    }

    private String generateRequestNumber() {
        String uuid = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "SR-" + uuid;
    }

    // ==================== Exception ====================

    public static class ServiceRequestNotFoundException extends RuntimeException {
        public ServiceRequestNotFoundException(Long id) {
            super("Không tìm thấy Service Request với ID: " + id);
        }

        public ServiceRequestNotFoundException(String requestNumber) {
            super("Không tìm thấy Service Request với số: " + requestNumber);
        }
    }
}

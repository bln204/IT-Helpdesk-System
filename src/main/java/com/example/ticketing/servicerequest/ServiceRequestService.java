package com.example.ticketing.servicerequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.department.Department;
import com.example.ticketing.department.DepartmentRepository;

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
    private final DepartmentRepository departmentRepository;

    public ServiceRequestService(
            ServiceRequestRepository serviceRequestRepository,
            ServiceRequestCommentRepository commentRepository,
            ServiceCatalogRepository catalogRepository,
            DepartmentRepository departmentRepository) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.commentRepository = commentRepository;
        this.catalogRepository = catalogRepository;
        this.departmentRepository = departmentRepository;
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

        return serviceRequestRepository.save(request);
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

        existing.setTitle(updates.getTitle());
        existing.setDescription(updates.getDescription());
        existing.setPriority(updates.getPriority());
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
        ServiceRequest.ServiceStatus oldStatus = request.getStatus();

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

        request.setStatus(ServiceRequest.ServiceStatus.APPROVED);
        request.setApprovedBy(approverName);
        request.setApprovedAt(LocalDateTime.now());
        request.setApprovalNotes(notes);

        return serviceRequestRepository.save(request);
    }

    /**
     * Từ chối request.
     */
    public ServiceRequest rejectRequest(Long id, String approverName, String reason) {
        ServiceRequest request = getRequestById(id);

        request.setStatus(ServiceRequest.ServiceStatus.REJECTED);
        request.setApprovedBy(approverName);
        request.setApprovedAt(LocalDateTime.now());
        request.setApprovalNotes(reason);

        return serviceRequestRepository.save(request);
    }

    /**
     * Assign request cho IT staff.
     */
    public ServiceRequest assignRequest(Long id, String assignee, String actorName) {
        ServiceRequest request = getRequestById(id);
        request.setAssignedTo(assignee);
        request.setUpdatedBy(actorName);

        // Set to IN_PROGRESS if currently SUBMITTED
        if (request.getStatus() == ServiceRequest.ServiceStatus.SUBMITTED) {
            request.setStatus(ServiceRequest.ServiceStatus.IN_PROGRESS);
            request.setFirstResponseAt(LocalDateTime.now());
        }

        return serviceRequestRepository.save(request);
    }

    /**
     * IT Staff nhận request (Take Ownership).
     */
    public ServiceRequest takeOwnership(Long id, String username) {
        ServiceRequest request = getRequestById(id);
        request.setAssignedTo(username);
        request.setStatus(ServiceRequest.ServiceStatus.IN_PROGRESS);
        request.setFirstResponseAt(LocalDateTime.now());
        request.setUpdatedBy(username);
        return serviceRequestRepository.save(request);
    }

    /**
     * IT Staff hoàn thành request.
     */
    public ServiceRequest completeRequest(Long id, String resolution, String username) {
        ServiceRequest request = getRequestById(id);
        request.setStatus(ServiceRequest.ServiceStatus.COMPLETED);
        request.setCompletedAt(LocalDateTime.now());
        if (resolution != null) {
            request.setResolution(resolution);
        }
        request.setUpdatedBy(username);
        return serviceRequestRepository.save(request);
    }

    /**
     * User xác nhận hoàn thành (đóng request).
     */
    public ServiceRequest confirmCompletion(Long id, String username) {
        ServiceRequest request = getRequestById(id);
        request.setStatus(ServiceRequest.ServiceStatus.CLOSED);
        request.setUpdatedBy(username);
        return serviceRequestRepository.save(request);
    }

    /**
     * User yêu cầu mở lại.
     */
    public ServiceRequest reopenRequest(Long id, String reason, String username) {
        ServiceRequest request = getRequestById(id);
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
        comment.setVisibility(isInternal ? 
                ServiceRequestComment.Visibility.INTERNAL : 
                ServiceRequestComment.Visibility.PUBLIC);

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
        return serviceRequestRepository.findByStatusOrderByCreatedAtDesc(
                ServiceRequest.ServiceStatus.PENDING_APPROVAL, pageable);
    }

    /**
     * Tìm kiếm requests.
     */
    @Transactional(readOnly = true)
    public Page<ServiceRequest> searchRequests(String search, Pageable pageable) {
        return serviceRequestRepository.searchServiceRequests(search, pageable);
    }

    // ==================== Helper Methods ====================

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

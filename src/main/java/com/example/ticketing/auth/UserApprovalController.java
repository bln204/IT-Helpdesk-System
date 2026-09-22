package com.example.ticketing.auth;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;

import jakarta.validation.Valid;

/**
 * Controller for user approval management.
 * - ADMIN: full access (approve, reject, view all pending)
 * - TRUONG_PHONG: can view pending users in their department (cannot approve/reject)
 */
@RestController
@RequestMapping("/api/admin/users")
@Validated
public class UserApprovalController {
    
    private final UserAdminService userAdminService;
    private final UserAccountRepository userAccountRepository;
    private final NotificationService notificationService;

    public UserApprovalController(
        UserAdminService userAdminService,
        UserAccountRepository userAccountRepository,
        NotificationService notificationService
    ) {
        this.userAdminService = userAdminService;
        this.userAccountRepository = userAccountRepository;
        this.notificationService = notificationService;
    }

    /**
     * Get all users pending approval.
     * ADMIN: all pending users
     * TRUONG_PHONG: pending users in their department only
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRUONG_PHONG')")
    public List<UserDtos.UserResponse> getPendingUsers(Authentication authentication) {
        UserAccount actor = getCurrentUser(authentication);

        // TRUONG_PHONG can only see pending users in their department
        if ("TRUONG_PHONG".equals(actor.getRole().name())) {
            return userAdminService.listPendingApprovalByDepartment(actor.getDepartmentId()).stream()
                .map(UserDtos.UserResponse::from)
                .toList();
        }

        // ADMIN can see all pending users
        return userAdminService.listPendingApproval().stream()
            .map(UserDtos.UserResponse::from)
            .toList();
    }

    /**
     * Get count of pending approval users.
     * ADMIN only (badge in nav)
     */
    @GetMapping("/pending/count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PendingCountResponse> getPendingCount() {
        long count = userAdminService.countPendingApproval();
        return ResponseEntity.ok(new PendingCountResponse(count));
    }

    /**
     * Get users pending approval by department.
     * ADMIN only
     */
    @GetMapping("/pending/by-department/{departmentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserDtos.UserResponse> getPendingUsersByDepartment(@PathVariable Long departmentId) {
        return userAdminService.listPendingApprovalByDepartment(departmentId).stream()
            .map(UserDtos.UserResponse::from)
            .toList();
    }

    // ============ Delete Request Endpoints ============

    /**
     * Get all users with pending delete requests.
     * ADMIN - can see all pending delete requests
     * TRUONG_PHONG - can only see pending delete requests in their department
     * GET /api/admin/users/delete-requests
     */
    @GetMapping("/delete-requests")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRUONG_PHONG')")
    public List<UserDtos.UserResponse> getPendingDeleteRequests(Authentication authentication) {
        UserAccount actor = getCurrentUser(authentication);

        if ("ADMIN".equals(actor.getRole().name())) {
            return userAdminService.listPendingDeleteRequests().stream()
                .map(UserDtos.UserResponse::from)
                .toList();
        } else {
            // TRUONG_PHONG can only see their department's pending delete requests
            return userAdminService.listPendingDeleteRequestsByDepartment(actor.getDepartmentId()).stream()
                .map(UserDtos.UserResponse::from)
                .toList();
        }
    }

    /**
     * Get count of pending delete requests.
     * ADMIN only
     * GET /api/admin/users/delete-requests/count
     */
    @GetMapping("/delete-requests/count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PendingCountResponse> getPendingDeleteRequestsCount() {
        long count = userAdminService.countPendingDeleteRequests();
        return ResponseEntity.ok(new PendingCountResponse(count));
    }

    /**
     * Approve a pending user account.
     * ADMIN only
     * POST /api/admin/users/{id}/approve
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDtos.UserResponse approveUser(
        @PathVariable Long id,
        @Valid @RequestBody(required = false) UserDtos.ApproveUserRequest request,
        Authentication authentication
    ) {
        UserAccount actor = getCurrentUser(authentication);

        // Get user before approval for notification
        UserAccount userBefore = userAdminService.getUser(id);
        String creatorUsername = userBefore.getCreatedBy();
        
        UserAccount user = userAdminService.approveUser(
            id,
            actor.getUsername(),
            actor.getRole().name()
        );

        // Send notification to creator if exists
        if (creatorUsername != null && !creatorUsername.isBlank()) {
            notificationService.notifyCreatorOfApproval(
                user.getId(),
                creatorUsername,
                user.getUsername(),
                user.getDisplayName(),
                actor.getUsername()
            );
        }

        return UserDtos.UserResponse.from(user);
    }

    /**
     * Reject a pending user account.
     * ADMIN only
     * POST /api/admin/users/{id}/reject
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDtos.UserResponse rejectUser(
        @PathVariable Long id,
        @Valid @RequestBody UserDtos.RejectUserRequest request,
        Authentication authentication
    ) {
        UserAccount actor = getCurrentUser(authentication);

        // Get user before rejection for notification
        UserAccount userBefore = userAdminService.getUser(id);
        String creatorUsername = userBefore.getCreatedBy();
        String rejectionReason = request.getReason();
        
        UserAccount user = userAdminService.rejectUser(
            id,
            rejectionReason,
            actor.getUsername(),
            actor.getRole().name()
        );

        // Send notification to creator if exists
        if (creatorUsername != null && !creatorUsername.isBlank()) {
            notificationService.notifyCreatorOfRejection(
                user.getId(),
                creatorUsername,
                user.getUsername(),
                user.getDisplayName(),
                actor.getUsername(),
                rejectionReason
            );
        }

        return UserDtos.UserResponse.from(user);
    }

    /**
     * Enable a disabled user account.
     * ADMIN only
     * POST /api/admin/users/{id}/enable
     */
    @PostMapping("/{id}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDtos.UserResponse enableUser(
        @PathVariable Long id,
        Authentication authentication
    ) {
        UserAccount actor = getCurrentUser(authentication);

        UserAccount user = userAdminService.updateEnabled(
            id,
            true, // enabled = true
            actor.getUsername(),
            actor.getRole().name(),
            actor.getDepartmentId()
        );

        return UserDtos.UserResponse.from(user);
    }

    /**
     * Disable a user account.
     * ADMIN only
     * POST /api/admin/users/{id}/disable
     */
    @PostMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDtos.UserResponse disableUser(
        @PathVariable Long id,
        Authentication authentication
    ) {
        UserAccount actor = getCurrentUser(authentication);

        UserAccount user = userAdminService.updateEnabled(
            id,
            false, // enabled = false
            actor.getUsername(),
            actor.getRole().name(),
            actor.getDepartmentId()
        );

        return UserDtos.UserResponse.from(user);
    }

    /**
     * Request delete a user account.
     * TRUONG_PHONG only - requests admin to delete NHAN_VIEN in their department
     * POST /api/admin/users/{id}/request-delete
     */
    @PostMapping("/{id}/request-delete")
    @PreAuthorize("hasRole('TRUONG_PHONG')")
    public ResponseEntity<DeleteRequestResponse> requestDeleteUser(
        @PathVariable Long id,
        Authentication authentication
    ) {
        UserAccount actor = getCurrentUser(authentication);
        UserAccount targetUser = userAdminService.getUser(id);

        // TRUONG_PHONG can only request delete for NHAN_VIEN in their department
        if (!"NHAN_VIEN".equals(targetUser.getRole().name())) {
            throw new ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "Chỉ có thể yêu cầu xóa tài khoản nhân viên."
            );
        }
        if (!actor.getDepartmentId().equals(targetUser.getDepartmentId())) {
            throw new ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "Chỉ có thể yêu cầu xóa tài khoản trong phòng ban của bạn."
            );
        }

        // Mark user as pending deletion - admin will review and delete
        userAdminService.requestDeleteUser(
            id,
            actor.getUsername(),
            actor.getRole().name(),
            actor.getDepartmentId()
        );

        // Notification is already sent inside requestDeleteUser() to avoid duplication

        return ResponseEntity.ok(new DeleteRequestResponse(true, "Yêu cầu xóa đã được gửi cho Admin."));
    }

    /**
     * Approve delete request and actually delete user.
     * ADMIN only
     * DELETE /api/admin/users/{id}/delete-request
     */
    @DeleteMapping("/{id}/delete-request")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> approveDeleteRequest(
        @PathVariable Long id,
        Authentication authentication
    ) {
        UserAccount actor = getCurrentUser(authentication);
        UserAccount targetUser = userAdminService.getUser(id);

        // Actually delete the user
        userAdminService.deleteUser(id);

        // Notify the TRUONG_PHONG who requested the deletion
        if (targetUser.getCreatedBy() != null) {
            notificationService.notifyUserDeleted(
                targetUser.getId(),
                targetUser.getCreatedBy(),
                targetUser.getUsername(),
                targetUser.getDisplayName(),
                actor.getUsername()
            );
        }

        return ResponseEntity.noContent().build();
    }

    /**
     * Cancel a pending delete request.
     * ADMIN - can cancel any delete request
     * TRUONG_PHONG - can only cancel delete requests in their department
     * POST /api/admin/users/{id}/cancel-delete-request
     */
    @PostMapping("/{id}/cancel-delete-request")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRUONG_PHONG')")
    public UserDtos.UserResponse cancelDeleteRequest(
        @PathVariable Long id,
        Authentication authentication
    ) {
        UserAccount actor = getCurrentUser(authentication);

        UserAccount user = userAdminService.cancelDeleteRequest(
            id,
            actor.getUsername(),
            actor.getRole().name(),
            actor.getDepartmentId()
        );

        return UserDtos.UserResponse.from(user);
    }

    /**
     * Response DTO for delete request.
     */
    public static class DeleteRequestResponse {
        private boolean success;
        private String message;

        public DeleteRequestResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Get user account status details.
     * ADMIN only
     * GET /api/admin/users/{id}/status
     */
    @GetMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDtos.AccountStatusResponse getUserStatus(
        @PathVariable Long id,
        Authentication authentication
    ) {
        UserAccount user = userAdminService.getUser(id);
        return UserDtos.AccountStatusResponse.from(user);
    }

    /**
     * Response DTO for pending count.
     */
    public static class PendingCountResponse {
        private long count;

        public PendingCountResponse(long count) {
            this.count = count;
        }

        public long getCount() {
            return count;
        }
    }

    private UserAccount getCurrentUser(Authentication authentication) {
        return userAccountRepository.findByUsername(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "User not found."
            ));
    }
}

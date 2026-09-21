package com.example.ticketing.auth;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
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

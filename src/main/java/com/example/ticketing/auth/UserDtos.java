package com.example.ticketing.auth;

import com.example.ticketing.validation.DepartmentRequired;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class UserDtos {
    private UserDtos() {
    }

    /**
     * DTO for creating a new user.
     * Validation: departmentId is required for all roles except ADMIN.
     */
    @DepartmentRequired(message = "Phòng ban không được để trống")
    public static class UserCreateRequest {
        @NotBlank
        @Size(max = 80)
        private String username;

        @NotBlank
        @Size(min = 8, max = 128)
        private String password;

        @NotNull
        private UserRole.Role role;

        private Boolean enabled;

        @NotBlank
        @Size(max = 120)
        private String displayName;

        @NotBlank
        @Size(max = 120)
        private String title;

        @Size(max = 255)
        private String avatarUrl;

        @Email
        @Size(max = 160)
        private String email;

        /**
         * Department ID - required for all roles except ADMIN.
         * For ADMIN role, this field can be null since admin doesn't belong to any department.
         */
        private Long departmentId;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public UserRole.Role getRole() {
            return role;
        }

        public void setRole(UserRole.Role role) {
            this.role = role;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public void setAvatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public Long getDepartmentId() {
            return departmentId;
        }

        public void setDepartmentId(Long departmentId) {
            this.departmentId = departmentId;
        }
    }

    public static class UserEnabledRequest {
        @NotNull
        private Boolean enabled;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class UserRoleUpdateRequest {
        @NotNull
        private UserRole.Role role;

        public UserRole.Role getRole() {
            return role;
        }

        public void setRole(UserRole.Role role) {
            this.role = role;
        }
    }

    public static class UserProfileUpdateRequest {
        @NotBlank
        @Size(max = 120)
        private String displayName;

        @NotBlank
        @Size(max = 120)
        private String title;

        @Size(max = 255)
        private String avatarUrl;

        @Email
        @Size(max = 160)
        private String email;

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public void setAvatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public static class UserPasswordResetRequest {
        @NotBlank
        @Size(min = 8, max = 128)
        private String password;

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
    
    /**
     * Request to update profile and/or reset password in a single operation.
     * Sends one combined email notification for all changes.
     */
    public static class UserProfileAndPasswordUpdateRequest {
        @Size(max = 120)
        private String displayName;

        @Size(max = 120)
        private String title;

        @Size(max = 255)
        private String avatarUrl;

        @Email
        @Size(max = 160)
        private String email;
        
        // Optional: if provided, password will be reset to this value
        @Size(min = 8, max = 128)
        private String newPassword;

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public void setAvatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
        
        public String getNewPassword() {
            return newPassword;
        }
        
        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
        
        public boolean hasNewPassword() {
            return newPassword != null && !newPassword.isBlank();
        }
    }

    public static class UserPasswordChangeRequest {
        @NotBlank
        private String currentPassword;

        @NotBlank
        @Size(min = 8, max = 128)
        private String newPassword;

        public String getCurrentPassword() {
            return currentPassword;
        }

        public void setCurrentPassword(String currentPassword) {
            this.currentPassword = currentPassword;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
    }

    public static class UserResponse {
        private Long id;
        private String username;
        private UserRole.Role role;
        private Long departmentId;
        private String departmentCode;
        private String departmentName;
        private boolean enabled;
        private boolean approved;  // NEW
        private String displayName;
        private String title;
        private String avatarUrl;
        private String email;
        private java.time.LocalDateTime approvedAt;  // NEW
        private String approvedBy;  // NEW
        private String rejectionReason;  // NEW
        private String createdBy;  // NEW

        public static UserResponse from(UserAccount user) {
            UserResponse response = new UserResponse();
            response.id = user.getId();
            response.username = user.getUsername();
            response.role = user.getRole();
            response.departmentId = user.getDepartmentId();
            response.departmentCode = user.getDepartmentCode();
            response.departmentName = user.getDepartmentName();
            response.enabled = user.isEnabled();
            response.approved = user.isApproved();  // NEW
            response.displayName = user.getDisplayName();
            response.title = user.getTitle();
            response.avatarUrl = user.getAvatarUrl();
            response.email = user.getEmail();
            response.approvedAt = user.getApprovedAt();  // NEW
            response.approvedBy = user.getApprovedBy();  // NEW
            response.rejectionReason = user.getRejectionReason();  // NEW
            response.createdBy = user.getCreatedBy();  // NEW
            return response;
        }

        public Long getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public UserRole.Role getRole() {
            return role;
        }

        public Long getDepartmentId() {
            return departmentId;
        }

        public String getDepartmentCode() {
            return departmentCode;
        }

        public String getDepartmentName() {
            return departmentName;
        }

        public boolean isEnabled() {
            return enabled;
        }

        // NEW getters
        public boolean isApproved() {
            return approved;
        }
        
        public String getDisplayName() {
            return displayName;
        }

        public String getTitle() {
            return title;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public String getEmail() {
            return email;
        }
        
        public java.time.LocalDateTime getApprovedAt() {
            return approvedAt;
        }
        
        public String getApprovedBy() {
            return approvedBy;
        }
        
        public String getRejectionReason() {
            return rejectionReason;
        }

        public String getCreatedBy() {
            return createdBy;
        }
    }

    public static class UserAuditResponse {
        private Long id;
        private UserAuditAction action;
        private String actorUsername;
        private String actorRole;
        private String targetUsername;
        private java.time.LocalDateTime createdAt;
        private String details;  // NEW
        private String oldValue;  // NEW
        private String newValue;  // NEW

        public static UserAuditResponse from(UserAudit audit) {
            UserAuditResponse response = new UserAuditResponse();
            response.id = audit.getId();
            response.action = audit.getAction();
            response.actorUsername = audit.getActorUsername();
            response.actorRole = audit.getActorRole();
            response.targetUsername = audit.getTargetUsername();
            response.createdAt = audit.getCreatedAt();
            response.details = audit.getDetails();  // NEW
            response.oldValue = audit.getOldValue();  // NEW
            response.newValue = audit.getNewValue();  // NEW
            return response;
        }

        public Long getId() {
            return id;
        }

        public UserAuditAction getAction() {
            return action;
        }

        public String getActorUsername() {
            return actorUsername;
        }

        public String getActorRole() {
            return actorRole;
        }

        public String getTargetUsername() {
            return targetUsername;
        }

        public java.time.LocalDateTime getCreatedAt() {
            return createdAt;
        }
        
        // NEW getters
        public String getDetails() {
            return details;
        }
        
        public String getOldValue() {
            return oldValue;
        }
        
        public String getNewValue() {
            return newValue;
        }
    }
    
    // ============ NEW: Approval Request DTOs ============
    
    /**
     * Request to create a new user (from TRUONG_PHONG).
     * This creates a pending request that requires ADMIN approval.
     */
    public static class UserApprovalRequest {
        @NotBlank
        @Size(max = 80)
        private String username;

        @NotBlank
        @Size(min = 8, max = 128)
        private String password;

        @NotBlank
        @Size(max = 120)
        private String displayName;

        @NotBlank
        @Size(max = 120)
        private String title;

        @Email
        @Size(max = 160)
        private String email;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }
    
    /**
     * Request to approve a user account.
     */
    public static class ApproveUserRequest {
        // Optional notes for approval
        private String notes;
        
        public String getNotes() {
            return notes;
        }
        
        public void setNotes(String notes) {
            this.notes = notes;
        }
    }
    
    /**
     * Request to reject a user account.
     */
    public static class RejectUserRequest {
        @NotBlank(message = "Lý do từ chối không được để trống")
        @Size(max = 255, message = "Lý do từ chối không được vượt quá 255 ký tự")
        private String reason;
        
        public String getReason() {
            return reason;
        }
        
        public void setReason(String reason) {
            this.reason = reason;
        }
    }
    
    /**
     * Request to enable/disable a user (from TRUONG_PHONG).
     */
    public static class EnableDisableRequest {
        private String reason;
        
        public String getReason() {
            return reason;
        }
        
        public void setReason(String reason) {
            this.reason = reason;
        }
    }
    
    /**
     * Response for pending approval request.
     */
    public static class PendingUserResponse {
        private Long id;
        private String username;
        private String displayName;
        private String title;
        private String email;
        private String departmentName;
        private String requestedBy;
        private java.time.LocalDateTime requestedAt;
        private String status;
        
        public static PendingUserResponse fromPendingRequest(Object[] result) {
            PendingUserResponse response = new PendingUserResponse();
            // Handle the projection result
            return response;
        }

        public Long getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getTitle() {
            return title;
        }

        public String getEmail() {
            return email;
        }

        public String getDepartmentName() {
            return departmentName;
        }

        public String getRequestedBy() {
            return requestedBy;
        }

        public java.time.LocalDateTime getRequestedAt() {
            return requestedAt;
        }

        public String getStatus() {
            return status;
        }
    }
    
    /**
     * Account status information for users.
     */
    public static class AccountStatusResponse {
        private Long userId;
        private String username;
        private boolean approved;
        private boolean enabled;
        private String status;  // PENDING, ACTIVE, DISABLED, REJECTED
        private String statusMessage;
        private java.time.LocalDateTime approvedAt;
        private String approvedBy;
        private String rejectionReason;
        
        public static AccountStatusResponse from(UserAccount user) {
            AccountStatusResponse response = new AccountStatusResponse();
            response.userId = user.getId();
            response.username = user.getUsername();
            response.approved = user.isApproved();
            response.enabled = user.isEnabled();
            
            // Calculate status
            if (!user.isApproved() && user.getRejectionReason() != null) {
                response.status = "REJECTED";
                response.statusMessage = "Tài khoản đã bị từ chối";
            } else if (!user.isApproved()) {
                response.status = "PENDING";
                response.statusMessage = "Tài khoản đang chờ phê duyệt";
            } else if (!user.isEnabled()) {
                response.status = "DISABLED";
                response.statusMessage = "Tài khoản đang bị vô hiệu hoá";
            } else {
                response.status = "ACTIVE";
                response.statusMessage = "Tài khoản đang hoạt động";
            }
            
            response.approvedAt = user.getApprovedAt();
            response.approvedBy = user.getApprovedBy();
            response.rejectionReason = user.getRejectionReason();
            
            return response;
        }

        public Long getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public boolean isApproved() {
            return approved;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getStatus() {
            return status;
        }

        public String getStatusMessage() {
            return statusMessage;
        }

        public java.time.LocalDateTime getApprovedAt() {
            return approvedAt;
        }

        public String getApprovedBy() {
            return approvedBy;
        }

        public String getRejectionReason() {
            return rejectionReason;
        }
    }
}

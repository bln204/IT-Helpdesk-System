package com.example.ticketing.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class UserDtos {
    private UserDtos() {
    }

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

        @NotNull
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
        private String displayName;
        private String title;
        private String avatarUrl;
        private String email;

        public static UserResponse from(UserAccount user) {
            UserResponse response = new UserResponse();
            response.id = user.getId();
            response.username = user.getUsername();
            response.role = user.getRole();
            response.departmentId = user.getDepartmentId();
            response.departmentCode = user.getDepartmentCode();
            response.departmentName = user.getDepartmentName();
            response.enabled = user.isEnabled();
            response.displayName = user.getDisplayName();
            response.title = user.getTitle();
            response.avatarUrl = user.getAvatarUrl();
            response.email = user.getEmail();
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
    }

    public static class UserAuditResponse {
        private Long id;
        private UserAuditAction action;
        private String actorUsername;
        private String actorRole;
        private String targetUsername;
        private java.time.LocalDateTime createdAt;

        public static UserAuditResponse from(UserAudit audit) {
            UserAuditResponse response = new UserAuditResponse();
            response.id = audit.getId();
            response.action = audit.getAction();
            response.actorUsername = audit.getActorUsername();
            response.actorRole = audit.getActorRole();
            response.targetUsername = audit.getTargetUsername();
            response.createdAt = audit.getCreatedAt();
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
    }
}

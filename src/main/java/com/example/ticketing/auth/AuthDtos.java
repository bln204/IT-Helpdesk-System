package com.example.ticketing.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public static class LoginRequest {
        @NotBlank
        @Size(max = 80)
        private String username;

        @NotBlank
        @Size(min = 7, max = 128)
        private String password;

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
    }

    public static class LoginResponse {
        private String token;
        private String tokenType;
        private long expiresInSeconds;
        private UserInfo user;
        private boolean passwordMustChange;

        public LoginResponse(String token, long expiresInSeconds, UserInfo user) {
            this.token = token;
            this.tokenType = "Bearer";
            this.expiresInSeconds = expiresInSeconds;
            this.user = user;
            this.passwordMustChange = false;
        }
        
        public LoginResponse(String token, long expiresInSeconds, UserInfo user, boolean passwordMustChange) {
            this.token = token;
            this.tokenType = "Bearer";
            this.expiresInSeconds = expiresInSeconds;
            this.user = user;
            this.passwordMustChange = passwordMustChange;
        }

        public String getToken() {
            return token;
        }

        public String getTokenType() {
            return tokenType;
        }

        public long getExpiresInSeconds() {
            return expiresInSeconds;
        }
        
        public boolean isPasswordMustChange() {
            return passwordMustChange;
        }
        
        public void setPasswordMustChange(boolean passwordMustChange) {
            this.passwordMustChange = passwordMustChange;
        }

        public UserInfo getUser() {
            return user;
        }
    }

    public static class UserInfo {
        private Long id;
        private String username;
        private String role;
        private Long departmentId;
        private String departmentCode;
        private String departmentName;
        private String displayName;
        private String email;

        public UserInfo(
            Long id,
            String username,
            String role,
            Long departmentId,
            String departmentCode,
            String departmentName,
            String displayName,
            String email
        ) {
            this.id = id;
            this.username = username;
            this.role = role;
            this.departmentId = departmentId;
            this.departmentCode = departmentCode;
            this.departmentName = departmentName;
            this.displayName = displayName;
            this.email = email;
        }

        public Long getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public String getRole() {
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

        public String getDisplayName() {
            return displayName;
        }

        public String getEmail() {
            return email;
        }
    }
    
    /**
     * Request to force change password (for first login).
     */
    public static class ForcePasswordChangeRequest {
        @NotBlank
        @Size(min = 7, max = 128)
        private String newPassword;
        
        @NotBlank
        @Size(min = 7, max = 128)
        private String confirmPassword;
        
        public String getNewPassword() {
            return newPassword;
        }
        
        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
        
        public String getConfirmPassword() {
            return confirmPassword;
        }
        
        public void setConfirmPassword(String confirmPassword) {
            this.confirmPassword = confirmPassword;
        }
    }
}

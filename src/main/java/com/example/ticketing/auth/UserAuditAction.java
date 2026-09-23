package com.example.ticketing.auth;

/**
 * Enum representing all actions that can be audited in the user management system.
 */
public enum UserAuditAction {
    // Authentication actions
    LOGIN,
    LOGOUT,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
    TOKEN_REVOKED,
    
    // Basic user management actions
    USER_CREATED,
    USER_DELETED,
    USER_DISABLED,
    USER_ENABLED,
    ROLE_CHANGED,
    PROFILE_UPDATED,
    
    // ============ NEW: Approval System Actions ============
    
    /**
     * User account created by TRUONG_PHONG - pending approval.
     */
    USER_PENDING_CREATED,
    
    /**
     * User account approved by ADMIN.
     */
    USER_APPROVED,
    
    /**
     * User account rejected by ADMIN.
     */
    USER_REJECTED,
    
    /**
     * TRUONG_PHONG requested to enable a disabled user.
     */
    ENABLE_REQUESTED,
    
    /**
     * TRUONG_PHONG requested to disable a user.
     */
    DISABLE_REQUESTED,
    
    /**
     * ADMIN approved enable/disable request.
     */
    ENABLE_REQUEST_APPROVED,
    
    /**
     * ADMIN rejected enable/disable request.
     */
    ENABLE_REQUEST_REJECTED,
    
    /**
     * User attempted to edit profile but account not yet approved.
     */
    PROFILE_EDIT_REJECTED,
    
    /**
     * User viewing their own account status.
     */
    STATUS_VIEWED
}

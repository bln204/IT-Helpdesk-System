package com.example.ticketing.exception;

/**
 * Exception thrown when there's an issue with a pending user approval request.
 * Used when TRUONG_PHONG tries to create a duplicate request or similar scenarios.
 */
public class ApprovalRequestException extends UserAccountException {
    
    private static final String ERROR_CODE = "APPROVAL_REQUEST_ERROR";
    
    public ApprovalRequestException(String message) {
        super(ERROR_CODE, message);
    }
    
    public ApprovalRequestException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}

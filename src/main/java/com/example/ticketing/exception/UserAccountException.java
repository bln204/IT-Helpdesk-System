package com.example.ticketing.exception;

/**
 * Base exception for all user-related authentication and authorization errors.
 * This provides a consistent way to handle user-specific exceptions.
 */
public abstract class UserAccountException extends RuntimeException {
    
    private final String errorCode;
    
    protected UserAccountException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    protected UserAccountException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}

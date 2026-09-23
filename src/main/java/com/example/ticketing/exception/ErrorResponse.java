package com.example.ticketing.exception;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard error response format for all API errors.
 * Provides consistent error information across all endpoints.
 */
public class ErrorResponse {
    
    private String errorCode;
    private String message;
    private LocalDateTime timestamp;
    private String path;
    private Map<String, Object> details;
    
    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }
    
    public ErrorResponse(String errorCode, String message) {
        this();
        this.errorCode = errorCode;
        this.message = message;
    }
    
    public ErrorResponse(String errorCode, String message, String path) {
        this(errorCode, message);
        this.path = path;
    }
    
    public ErrorResponse(String errorCode, String message, String path, Map<String, Object> details) {
        this(errorCode, message, path);
        this.details = details;
    }
    
    // Getters and Setters
    public String getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public Map<String, Object> getDetails() {
        return details;
    }
    
    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }
}

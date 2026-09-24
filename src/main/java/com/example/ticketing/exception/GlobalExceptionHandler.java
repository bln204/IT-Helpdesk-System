package com.example.ticketing.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for the entire application.
 * Provides consistent error responses for all types of exceptions.
 * 
 * This centralizes all exception handling to ensure:
 * 1. Consistent error response format
 * 2. User-friendly messages in Vietnamese
 * 3. Proper HTTP status codes
 * 4. Detailed information for debugging (in non-production)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    // ==================== User Account Exceptions ====================
    
    /**
     * Handle UserNotApprovedException - account not yet approved by admin
     */
    @ExceptionHandler(UserNotApprovedException.class)
    public ResponseEntity<ErrorResponse> handleUserNotApproved(
            UserNotApprovedException ex, 
            HttpServletRequest request) {
        
        log.warn("Login attempt with unapproved account: {}", ex.getMessage());
        
        Map<String, Object> details = new HashMap<>();
        details.put("suggestion", "Vui lòng liên hệ Trưởng phòng hoặc Admin để được phê duyệt.");
        details.put("supportContact", "admin@company.com");
        details.put("canRetry", false);
        
        ErrorResponse error = new ErrorResponse(
            ex.getErrorCode(),
            ex.getMessage(),
            request.getRequestURI(),
            details
        );
        
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
            .body(error);
    }
    
    /**
     * Handle UserDisabledException - account has been disabled
     */
    @ExceptionHandler(UserDisabledException.class)
    public ResponseEntity<ErrorResponse> handleUserDisabled(
            UserDisabledException ex, 
            HttpServletRequest request) {
        
        log.warn("Login attempt with disabled account: {}", ex.getMessage());
        
        Map<String, Object> details = new HashMap<>();
        details.put("suggestion", "Vui lòng liên hệ Trưởng phòng hoặc Admin để được hỗ trợ.");
        details.put("supportContact", "admin@company.com");
        details.put("canRetry", false);
        
        ErrorResponse error = new ErrorResponse(
            ex.getErrorCode(),
            ex.getMessage(),
            request.getRequestURI(),
            details
        );
        
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
            .body(error);
    }
    
    /**
     * Handle UserRejectedException - account was rejected during approval
     */
    @ExceptionHandler(UserRejectedException.class)
    public ResponseEntity<ErrorResponse> handleUserRejected(
            UserRejectedException ex, 
            HttpServletRequest request) {
        
        log.warn("Login attempt with rejected account: {}", ex.getMessage());
        
        Map<String, Object> details = new HashMap<>();
        details.put("suggestion", "Vui lòng liên hệ Admin để biết thêm chi tiết.");
        details.put("supportContact", "admin@company.com");
        details.put("canRetry", false);
        
        if (ex.getRejectionReason() != null) {
            details.put("rejectionReason", ex.getRejectionReason());
        }
        
        ErrorResponse error = new ErrorResponse(
            ex.getErrorCode(),
            ex.getMessage(),
            request.getRequestURI(),
            details
        );
        
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
            .body(error);
    }
    
    /**
     * Handle UserNotApprovedActionException - action blocked due to unapproved account
     */
    @ExceptionHandler(UserNotApprovedActionException.class)
    public ResponseEntity<ErrorResponse> handleUserNotApprovedAction(
            UserNotApprovedActionException ex, 
            HttpServletRequest request) {
        
        log.warn("Action blocked for unapproved account: {}", ex.getMessage());
        
        Map<String, Object> details = new HashMap<>();
        details.put("suggestion", "Vui lòng đợi tài khoản được phê duyệt trước khi thực hiện thao tác này.");
        details.put("canRetry", false);
        
        ErrorResponse error = new ErrorResponse(
            ex.getErrorCode(),
            ex.getMessage(),
            request.getRequestURI(),
            details
        );
        
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(error);
    }
    
    /**
     * Handle ApprovalRequestException - issues with approval workflow
     */
    @ExceptionHandler(ApprovalRequestException.class)
    public ResponseEntity<ErrorResponse> handleApprovalRequest(
            ApprovalRequestException ex, 
            HttpServletRequest request) {
        
        log.warn("Approval request error: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(
            ex.getErrorCode(),
            ex.getMessage(),
            request.getRequestURI()
        );
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(error);
    }
    
    // ==================== Authentication Exceptions ====================
    
    /**
     * Handle BadCredentialsException - invalid username or password
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, 
            HttpServletRequest request) {
        
        log.warn("Login failed with bad credentials from IP: {}", request.getRemoteAddr());
        
        Map<String, Object> details = new HashMap<>();
        details.put("suggestion", "Vui lòng kiểm tra lại tên đăng nhập và mật khẩu.");
        details.put("canRetry", true);
        
        ErrorResponse error = new ErrorResponse(
            "AUTH_INVALID_CREDENTIALS",
            "Tên đăng nhập hoặc mật khẩu không đúng.",
            request.getRequestURI(),
            details
        );
        
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
            .body(error);
    }
    
    /**
     * Handle AccessDeniedException - insufficient permissions
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, 
            HttpServletRequest request) {
        
        log.warn("Access denied for user: {} on path: {}", 
            request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous",
            request.getRequestURI());
        
        Map<String, Object> details = new HashMap<>();
        details.put("suggestion", "Bạn không có quyền thực hiện thao tác này. Vui lòng liên hệ Admin nếu bạn cần quyền truy cập.");
        
        ErrorResponse error = new ErrorResponse(
            "ACCESS_DENIED",
            "Bạn không có quyền thực hiện thao tác này.",
            request.getRequestURI(),
            details
        );
        
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(error);
    }
    
    // ==================== ResponseStatusException Handler ====================
    
    /**
     * Handle ResponseStatusException - programmatic status exceptions
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException ex, 
            HttpServletRequest request) {
        
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        
        log.warn("ResponseStatusException: {} - {} on path: {}", 
            status, ex.getReason(), request.getRequestURI());
        
        String errorCode = mapStatusToErrorCode(status);
        
        ErrorResponse error = new ErrorResponse(
            errorCode,
            ex.getReason() != null ? ex.getReason() : "Đã xảy ra lỗi.",
            request.getRequestURI()
        );
        
        return ResponseEntity
            .status(status)
            .body(error);
    }
    
    // ==================== Validation Exceptions ====================
    
    /**
     * Handle validation errors from @Valid annotations (field-level validation)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String errors = ex.getBindingResult().getFieldErrors().stream()
            .map(this::formatFieldError)
            .collect(Collectors.joining("; "));

        log.warn("Validation failed: {} on path: {}", errors, request.getRequestURI());

        Map<String, Object> details = new HashMap<>();
        details.put("validationErrors", ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                this::formatFieldError,
                (e1, e2) -> e1 + "; " + e2
            ))
        );

        ErrorResponse error = new ErrorResponse(
            "VALIDATION_ERROR",
            "Vui lòng kiểm tra lại các trường thông tin: " + errors,
            request.getRequestURI(),
            details
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(error);
    }

    /**
     * Format field error message in Vietnamese
     */
    private String formatFieldError(FieldError error) {
        String message = error.getDefaultMessage();
        String field = translateFieldName(error.getField());

        if (message == null || message.isEmpty()) {
            return field + " không được để trống";
        }

        // Make messages more user-friendly
        if (message.contains("must not be blank")) {
            return field + " không được để trống";
        }
        if (message.contains("must not be null")) {
            return field + " không được để trống";
        }
        if (message.contains("must not be empty")) {
            return field + " không được để trống";
        }
        if (message.contains("must be a valid email")) {
            return field + " phải là địa chỉ email hợp lệ";
        }
        if (message.contains("size must be between")) {
            return field + " có độ dài không hợp lệ";
        }

        // If message already contains the field name, return as-is
        if (message.toLowerCase().contains(field.toLowerCase())) {
            return message;
        }

        return field + ": " + message;
    }

    /**
     * Translate field names to Vietnamese
     */
    private String translateFieldName(String field) {
        return switch (field) {
            case "username" -> "Tên đăng nhập";
            case "password" -> "Mật khẩu";
            case "role" -> "Vai trò";
            case "displayName" -> "Họ tên";
            case "title" -> "Chức danh";
            case "avatarUrl" -> "URL avatar";
            case "email" -> "Email";
            case "departmentId" -> "Phòng ban";
            case "enabled" -> "Trạng thái";
            case "currentPassword" -> "Mật khẩu hiện tại";
            case "newPassword" -> "Mật khẩu mới";
            case "reason" -> "Lý do";
            case "notes" -> "Ghi chú";
            default -> field;
        };
    }
    
    /**
     * Handle validation errors from @Valid annotations (object-level validation)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        String errors = ex.getConstraintViolations().stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.joining("; "));

        log.warn("Constraint violation: {} on path: {}", errors, request.getRequestURI());

        Map<String, Object> details = new HashMap<>();
        details.put("validationErrors", ex.getConstraintViolations().stream()
            .collect(Collectors.toMap(
                v -> {
                    String path = v.getPropertyPath().toString();
                    // Extract field name from property path (e.g., "createUser.request.departmentId" -> "departmentId")
                    String[] parts = path.split("\\.");
                    return parts.length > 0 ? parts[parts.length - 1] : path;
                },
                ConstraintViolation::getMessage,
                (e1, e2) -> e1 + "; " + e2
            ))
        );

        ErrorResponse error = new ErrorResponse(
            "VALIDATION_ERROR",
            errors,
            request.getRequestURI(),
            details
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(error);
    }
    
    // ==================== Generic Exception Handler ====================
    
    /**
     * Handle all other uncaught exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex, 
            HttpServletRequest request) {
        
        log.error("Unhandled exception on path: {}: {}", 
            request.getRequestURI(), ex.getMessage(), ex);
        
        ErrorResponse error = new ErrorResponse(
            "INTERNAL_ERROR",
            "Đã xảy ra lỗi nội bộ. Vui lòng thử lại sau.",
            request.getRequestURI()
        );
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(error);
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Map HTTP status to error code
     */
    private String mapStatusToErrorCode(HttpStatus status) {
        return switch (status.value()) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 409 -> "CONFLICT";
            case 422 -> "UNPROCESSABLE_ENTITY";
            case 429 -> "TOO_MANY_REQUESTS";
            default -> "HTTP_" + status.value();
        };
    }
}

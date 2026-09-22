package com.example.ticketing.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a user tries to login but their account has been rejected.
 * 
 * This provides a natural, user-friendly message explaining that the account was rejected
 * and suggesting they contact admin for more information.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UserRejectedException extends UserAccountException {
    
    private static final String ERROR_CODE = "AUTH_USER_REJECTED";
    private static final String DEFAULT_MESSAGE = 
        "Tài khoản đã bị từ chối. Vui lòng liên hệ Admin để biết thêm chi tiết.";
    
    private String rejectionReason;
    
    public UserRejectedException() {
        super(ERROR_CODE, DEFAULT_MESSAGE);
    }
    
    public UserRejectedException(String username) {
        super(ERROR_CODE, String.format(
            "Tài khoản '%s' đã bị từ chối. Vui lòng liên hệ Admin để biết thêm chi tiết.",
            username
        ));
    }
    
    public UserRejectedException(String username, String rejectionReason) {
        super(ERROR_CODE, String.format(
            "Tài khoản '%s' đã bị từ chối: %s. Vui lòng liên hệ Admin để biết thêm chi tiết.",
            username, rejectionReason != null ? rejectionReason : "không có lý do cụ thể"
        ));
        this.rejectionReason = rejectionReason;
    }
    
    public UserRejectedException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
    
    public String getRejectionReason() {
        return rejectionReason;
    }
}

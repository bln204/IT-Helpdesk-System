package com.example.ticketing.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a user tries to login but their account has not been approved yet.
 * 
 * This provides a natural, user-friendly message explaining that the account is pending approval
 * and suggesting they contact their department head or admin.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UserNotApprovedException extends UserAccountException {
    
    private static final String ERROR_CODE = "AUTH_USER_NOT_APPROVED";
    private static final String DEFAULT_MESSAGE = 
        "Tài khoản chưa được phê duyệt. Vui lòng liên hệ Trưởng phòng hoặc Admin để được hỗ trợ.";
    
    public UserNotApprovedException() {
        super(ERROR_CODE, DEFAULT_MESSAGE);
    }
    
    public UserNotApprovedException(String username) {
        super(ERROR_CODE, String.format(
            "Tài khoản '%s' chưa được phê duyệt. Vui lòng liên hệ Trưởng phòng hoặc Admin để được hỗ trợ.",
            username
        ));
    }
    
    public UserNotApprovedException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}

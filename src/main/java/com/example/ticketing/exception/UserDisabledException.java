package com.example.ticketing.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a user tries to login but their account has been disabled.
 * 
 * This provides a natural, user-friendly message explaining that the account is disabled
 * and suggesting they contact their department head or admin for reactivation.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UserDisabledException extends UserAccountException {
    
    private static final String ERROR_CODE = "AUTH_USER_DISABLED";
    private static final String DEFAULT_MESSAGE = 
        "Tài khoản đang bị vô hiệu hoá. Vui lòng liên hệ Trưởng phòng hoặc Admin để được hỗ trợ.";
    
    public UserDisabledException() {
        super(ERROR_CODE, DEFAULT_MESSAGE);
    }
    
    public UserDisabledException(String username) {
        super(ERROR_CODE, String.format(
            "Tài khoản '%s' đang bị vô hiệu hoá. Vui lòng liên hệ Trưởng phòng hoặc Admin để được hỗ trợ.",
            username
        ));
    }
    
    public UserDisabledException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}

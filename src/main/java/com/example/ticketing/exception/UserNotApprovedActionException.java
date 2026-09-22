package com.example.ticketing.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a user tries to perform an action that requires approval
 * but their account has not been approved yet.
 * 
 * This is used for actions like editing profile before account is approved.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class UserNotApprovedActionException extends UserAccountException {
    
    private static final String ERROR_CODE = "ACTION_USER_NOT_APPROVED";
    
    public UserNotApprovedActionException() {
        super(ERROR_CODE, "Tài khoản chưa được phê duyệt. Bạn không thể thực hiện thao tác này.");
    }
    
    public UserNotApprovedActionException(String action) {
        super(ERROR_CODE, String.format(
            "Tài khoản chưa được phê duyệt. Bạn không thể %s cho đến khi tài khoản được phê duyệt.",
            action
        ));
    }
    
    public UserNotApprovedActionException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}

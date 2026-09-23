package com.example.ticketing.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exception thrown when a user or IP is locked out due to too many failed attempts.
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class TooManyRequestsException extends ResponseStatusException {
    
    private final long retryAfterSeconds;
    
    public TooManyRequestsException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, 
            "Bạn đã nhập sai quá nhiều lần. Vui lòng thử lại sau: " + retryAfterSeconds + " giây.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
    
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

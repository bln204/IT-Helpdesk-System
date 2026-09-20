package com.example.ticketing.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

/**
 * Global exception handler for security-related exceptions.
 */
@RestControllerAdvice
public class SecurityExceptionHandler {

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Map<String, Object>> handleTooManyRequests(TooManyRequestsException ex, HttpServletRequest request) {
        // Add Retry-After header
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(Map.of(
                "Bạn đã nhập sai quá nhiều lần",
                "Vui lòng thử lại sau: " + ex.getRetryAfterSeconds() + " giây"
            ));
    }
}

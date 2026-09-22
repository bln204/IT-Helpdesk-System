package com.example.ticketing.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ticketing.exception.UserDisabledException;
import com.example.ticketing.exception.UserNotApprovedException;
import com.example.ticketing.exception.UserRejectedException;
import com.example.ticketing.security.RateLimiterService;
import com.example.ticketing.security.TooManyRequestsException;
import com.example.ticketing.security.JwtBlacklistService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }
    
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserAuditService userAuditService;
    private final RateLimiterService rateLimiterService;
    private final JwtBlacklistService jwtBlacklistService;

    public AuthController(
        AuthenticationManager authenticationManager,
        JwtService jwtService,
        UserAuditService userAuditService,
        RateLimiterService rateLimiterService,
        JwtBlacklistService jwtBlacklistService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userAuditService = userAuditService;
        this.rateLimiterService = rateLimiterService;
        this.jwtBlacklistService = jwtBlacklistService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.LoginResponse> login(
        @Valid @RequestBody AuthDtos.LoginRequest request,
        HttpServletRequest httpRequest
    ) {
        String clientIp = getClientIp(httpRequest);
        String rateLimitKey = clientIp; // Rate limit by IP
        
        log.info("Login attempt for user: {} from IP: {}", request.getUsername(), clientIp);
        
        // Check if IP is locked out
        if (rateLimiterService.isLockedOut(rateLimitKey)) {
            long remainingSeconds = rateLimiterService.getRemainingLockoutSeconds(rateLimitKey);
            log.warn("Login blocked for IP {} - locked out for {} more seconds", clientIp, remainingSeconds);
            throw new TooManyRequestsException(remainingSeconds);
        }
        
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            log.info("Authentication successful for user: {}", request.getUsername());

            // Clear failed attempts on success
            rateLimiterService.clearFailedAttempts(rateLimitKey);
            
            UserAccount account = (UserAccount) authentication.getPrincipal();

            // ============ NEW: Check Approval Status ============
            
            // First, check if the account has been rejected
            if (account.getRejectionReason() != null) {
                log.warn("Login attempt with rejected account: {}", request.getUsername());
                throw new UserRejectedException(request.getUsername(), account.getRejectionReason());
            }
            
            // Check if account has been approved
            if (!account.isApproved()) {
                log.warn("Login attempt with unapproved account: {}", request.getUsername());
                throw new UserNotApprovedException(request.getUsername());
            }
            
            // Check if account is enabled
            if (!account.isEnabled()) {
                log.warn("Login attempt with disabled account: {}", request.getUsername());
                throw new UserDisabledException(request.getUsername());
            }
            
            // ============ END Approval Check ============

            // Log audit
            userAuditService.log(
                UserAuditAction.LOGIN,
                account.getUsername(),
                account.getRole().name(),
                account.getUsername()
            );

            // Generate token với department info
            String token = jwtService.generateTokenFromUserAccount(account);

            // Build user info
            AuthDtos.UserInfo userInfo = new AuthDtos.UserInfo(
                account.getId(),
                account.getUsername(),
                account.getRole().name(),
                account.getDepartmentId(),
                account.getDepartmentCode(),
                account.getDepartmentName(),
                account.getDisplayName(),
                account.getEmail()
            );

            return ResponseEntity.ok(new AuthDtos.LoginResponse(token, jwtService.getExpirationSeconds(), userInfo));
        } catch (UserNotApprovedException | UserDisabledException | UserRejectedException e) {
            // Re-throw approval-related exceptions to be handled by GlobalExceptionHandler
            throw e;
        } catch (BadCredentialsException e) {
            log.warn("Login failed for user {} from IP {}: Bad credentials", request.getUsername(), clientIp);
            
            // Record failed attempt
            boolean nowLocked = rateLimiterService.recordFailedAttempt(rateLimitKey);
            
            int remainingAttempts = rateLimiterService.getRemainingAttempts(rateLimitKey);
            
            if (nowLocked) {
                log.warn("IP {} has been locked out due to too many failed attempts", clientIp);
                throw new TooManyRequestsException(900); // 15 minutes
            }
            
            throw new BadCredentialsException(
                "Invalid username or password. " + remainingAttempts + " attempts remaining before lockout."
            );
        } catch (Exception e) {
            log.error("Login failed for user {} from IP {}: {}", request.getUsername(), clientIp, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Extract client IP address, considering proxy headers.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Take the first IP (original client)
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
    
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                String jti = jwtService.extractJti(token);
                long expiresAt = jwtService.extractExpiration(token);
                
                // Blacklist the token
                jwtBlacklistService.blacklist(jti, expiresAt);
                
                // Get username for audit log
                String username = jwtService.parseClaims(token).getSubject();
                
                // Log audit
                userAuditService.log(
                    UserAuditAction.LOGOUT,
                    username,
                    null,
                    username
                );
                
                log.info("User {} logged out, token blacklisted", username);
                
                return ResponseEntity.ok(Map.of(
                    "message", "Logged out successfully",
                    "tokenRevoked", true
                ));
            } catch (Exception e) {
                log.warn("Failed to blacklist token during logout: {}", e.getMessage());
                // Still return success - the token may just be invalid
                return ResponseEntity.ok(Map.of(
                    "message", "Logged out",
                    "tokenRevoked", false,
                    "reason", "Token was already invalid or expired"
                ));
            }
        }
        
        return ResponseEntity.ok(Map.of(
            "message", "No active session"
        ));
    }
}

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

import jakarta.validation.Valid;

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

    public AuthController(
        AuthenticationManager authenticationManager,
        JwtService jwtService,
        UserAuditService userAuditService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userAuditService = userAuditService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.LoginResponse> login(
        @Valid @RequestBody AuthDtos.LoginRequest request
    ) {
        log.info("Login attempt for user: {}", request.getUsername());
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            log.info("Authentication successful for user: {}", request.getUsername());

            UserAccount account = (UserAccount) authentication.getPrincipal();

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
        } catch (BadCredentialsException e) {
            log.warn("Login failed for user {}: Bad credentials", request.getUsername());
            throw e;
        } catch (Exception e) {
            log.error("Login failed for user {}: {}", request.getUsername(), e.getMessage(), e);
            throw e;
        }
    }
}

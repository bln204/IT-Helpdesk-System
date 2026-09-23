package com.example.ticketing.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {
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
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        UserDetails user = (UserDetails) authentication.getPrincipal();
        if (user instanceof UserAccount account) {
            userAuditService.log(
                UserAuditAction.LOGIN,
                account.getUsername(),
                account.getRole(),
                account.getUsername()
            );
        }
        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(new AuthDtos.LoginResponse(token, jwtService.getExpirationSeconds()));
    }
}

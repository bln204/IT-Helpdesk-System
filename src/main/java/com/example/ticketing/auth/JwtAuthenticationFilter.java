package com.example.ticketing.auth;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.ticketing.security.JwtBlacklistService;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtService jwtService;
    private final JwtBlacklistService jwtBlacklistService;
    private final UserAccountRepository userAccountRepository;

    /** Endpoint vẫn dùng được khi user phải đổi mật khẩu (spec auth-foundation §3.3). */
    private static final java.util.Set<String> PASSWORD_CHANGE_ALLOWED = java.util.Set.of(
        "POST /api/auth/logout",
        "GET /api/users/me",
        "GET /api/users/me/status",
        "POST /api/users/me/force-password-change",
        "PATCH /api/users/me/password"
    );

    public JwtAuthenticationFilter(
        JwtService jwtService, 
        JwtBlacklistService jwtBlacklistService,
        UserAccountRepository userAccountRepository
    ) {
        this.jwtService = jwtService;
        this.jwtBlacklistService = jwtBlacklistService;
        this.userAccountRepository = userAccountRepository;
    }
    
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        
        try {
            Claims claims = jwtService.parseClaims(token);
            String jti = claims.getId();
            String username = claims.getSubject();
            
            // Check if token is blacklisted
            if (jti != null && jwtBlacklistService.isBlacklisted(jti)) {
                logger.warn("Rejected blacklisted token: jti={}", jti);
                SecurityContextHolder.clearContext();
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Token has been revoked\",\"message\":\"Please login again\"}");
                return;
            }
            
            // Load user from database for real-time checks
            UserAccount user = null;
            if (username != null) {
                user = userAccountRepository.findByUsername(username).orElse(null);

                if (user == null) {
                    logger.warn("Rejected token - user no longer exists: {}", username);
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Account no longer exists\"}");
                    return;
                }

                // Check if user's all tokens have been revoked (e.g., user was deactivated)
                if (user != null && jwtBlacklistService.isUserRevoked(username)) {
                    logger.warn("Rejected token - user has been revoked: {}", username);
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Account disabled\",\"message\":\"Your account has been disabled. Please contact your administrator.\"}");
                    return;
                }
                
                // Check if user account is disabled
                if (user != null && !user.isEnabled()) {
                    logger.warn("Rejected token - user account is disabled: {}", username);
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Account disabled\",\"message\":\"Your account has been disabled. Please contact your administrator.\"}");
                    return;
                }
                
                // Mật khẩu tạm: chỉ cho gọi các endpoint đổi mật khẩu/đăng xuất (spec auth-foundation §3.3).
                // Body có passwordMustChange=true để static/js/core/api.js tự mở modal đổi mật khẩu.
                if (user.isPasswordMustChange()
                        && !PASSWORD_CHANGE_ALLOWED.contains(request.getMethod() + " " + request.getRequestURI())) {
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"error\":\"PASSWORD_CHANGE_REQUIRED\",\"errorCode\":\"PASSWORD_CHANGE_REQUIRED\","
                        + "\"message\":\"Bạn cần đổi mật khẩu trước khi tiếp tục.\",\"passwordMustChange\":true}");
                    return;
                }
                
                // Check if password was changed after token was issued
                if (user != null && user.getPasswordChangedAt() != null) {
                    java.time.Instant tokenIssuedAt = claims.getIssuedAt().toInstant();
                    java.time.Instant passwordChangedAtInstant = user.getPasswordChangedAt()
                        .atZone(java.time.ZoneId.systemDefault()).toInstant();
                    
                    // If password was changed after token was issued, reject the token
                    if (passwordChangedAtInstant.isAfter(tokenIssuedAt)) {
                        logger.warn("Token rejected - password changed after token was issued for user: {}", username);
                        SecurityContextHolder.clearContext();
                        response.setStatus(HttpStatus.UNAUTHORIZED.value());
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"Session expired\",\"message\":\"Please login again - your password was changed\"}");
                        return;
                    }
                }
            }
            
            if (user != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Role đọc từ DB để đổi/hạ role có hiệu lực ngay, không chờ token hết hạn.
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    username,
                    null,
                    user.getAuthorities()
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            logger.error("JWT validation failed: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid or expired token\"}");
            return; // Stop filter chain
        }

        filterChain.doFilter(request, response);
    }
}

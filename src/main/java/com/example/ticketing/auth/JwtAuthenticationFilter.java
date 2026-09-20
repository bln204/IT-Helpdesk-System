package com.example.ticketing.auth;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.ticketing.security.JwtBlacklistService;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final JwtBlacklistService jwtBlacklistService;
    private final UserAccountRepository userAccountRepository;

    public JwtAuthenticationFilter(
        JwtService jwtService, 
        UserDetailsService userDetailsService,
        JwtBlacklistService jwtBlacklistService,
        UserAccountRepository userAccountRepository
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
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
            
            // Check if password was changed after token was issued
            if (username != null) {
                UserAccount user = userAccountRepository.findByUsername(username).orElse(null);
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
            
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            logger.warn("JWT validation failed: " + ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}

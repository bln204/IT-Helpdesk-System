package com.example.ticketing.auth;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.example.ticketing.department.Department;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
    private static final int MIN_SECRET_LENGTH_BYTES = 32; // 256 bits minimum for HMAC-SHA256
    private final byte[] secret;
    private final String issuer;
    private final long expirationSeconds;

    public JwtService(
        @Value("${security.jwt.secret}") String secret,
        @Value("${security.jwt.issuer}") String issuer,
        @Value("${security.jwt.expiration-seconds}") long expirationSeconds
    ) {
        // Load .env file if exists (for local development)
        secret = loadEnvSecret(secret);
        
        validateSecret(secret);
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.issuer = issuer;
        this.expirationSeconds = expirationSeconds;
    }

    /**
     * Load secret from .env file if available.
     * This allows .env to override system properties/env variables for local dev.
     */
    private String loadEnvSecret(String defaultSecret) {
        try {
            java.nio.file.Path envPath = java.nio.file.Paths.get(".env");
            if (java.nio.file.Files.exists(envPath)) {
                for (String line : java.nio.file.Files.readAllLines(envPath)) {
                    line = line.trim();
                    if (line.startsWith("SECURITY_JWT_SECRET=") && !line.contains("#")) {
                        String envSecret = line.substring("SECURITY_JWT_SECRET=".length()).trim();
                        if (!envSecret.isEmpty()) {
                            return envSecret;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore - use default/system property
        }
        return defaultSecret;
    }

    private void validateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "JWT secret is required. Set SECURITY_JWT_SECRET environment variable with at least 32 characters."
            );
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_LENGTH_BYTES) {
            throw new IllegalStateException(
                "JWT secret is too weak. Minimum " + MIN_SECRET_LENGTH_BYTES + " characters (256 bits) required for HMAC-SHA256. " +
                "Current secret length: " + secretBytes.length + " characters. " +
                "Set a stronger SECURITY_JWT_SECRET environment variable."
            );
        }
        // Check for common weak secrets
        String lowerSecret = secret.toLowerCase();
        if (lowerSecret.contains("change") || 
            lowerSecret.contains("secret") ||
            lowerSecret.contains("password") ||
            lowerSecret.contains("123456") ||
            lowerSecret.contains("test")) {
            throw new IllegalStateException(
                "JWT secret contains common weak patterns. Please use a cryptographically random value."
            );
        }
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(userDetails, null);
    }

    public String generateToken(UserDetails userDetails, Department department) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationSeconds * 1000);
        String jti = UUID.randomUUID().toString();

        var claimsBuilder = Map.<String, Object>of(
            "roles", userDetails.getAuthorities().stream()
                .map(Object::toString)
                .toList(),
            "jti", jti
        );

        // Thêm department info nếu có
        if (department != null) {
            claimsBuilder = new java.util.HashMap<>(claimsBuilder);
            claimsBuilder.put("departmentId", department.getId());
            claimsBuilder.put("departmentCode", department.getCode());
            claimsBuilder.put("departmentName", department.getName());
        }

        return Jwts.builder()
            .issuer(issuer)
            .subject(userDetails.getUsername())
            .id(jti)  // Set JTI claim
            .issuedAt(now)
            .expiration(expiry)
            .claims(claimsBuilder)
            .signWith(Keys.hmacShaKeyFor(secret))
            .compact();
    }

    public String generateTokenFromUserAccount(UserAccount user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationSeconds * 1000);
        String jti = UUID.randomUUID().toString();

        var claimsBuilder = Map.<String, Object>of(
            "roles", user.getAuthorities().stream()
                .map(Object::toString)
                .toList(),
            "jti", jti
        );

        // Thêm department info nếu có
        if (user.getDepartment() != null) {
            claimsBuilder = new java.util.HashMap<>(claimsBuilder);
            claimsBuilder.put("departmentId", user.getDepartmentId());
            claimsBuilder.put("departmentCode", user.getDepartmentCode());
            claimsBuilder.put("departmentName", user.getDepartmentName());
        }

        return Jwts.builder()
            .issuer(issuer)
            .subject(user.getUsername())
            .id(jti)  // Set JTI claim
            .issuedAt(now)
            .expiration(expiry)
            .claims(claimsBuilder)
            .signWith(Keys.hmacShaKeyFor(secret))
            .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(secret))
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
    
    /**
     * Extract JWT ID (jti) from token.
     */
    public String extractJti(String token) {
        Claims claims = parseClaims(token);
        return claims.getId();
    }
    
    /**
     * Extract expiration timestamp from token.
     */
    public long extractExpiration(String token) {
        Claims claims = parseClaims(token);
        return claims.getExpiration().getTime() / 1000; // Return as epoch seconds
    }

    public Long getExpirationSeconds() {
        return expirationSeconds;
    }
}

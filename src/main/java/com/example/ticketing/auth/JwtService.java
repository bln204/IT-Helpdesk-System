package com.example.ticketing.auth;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
    private final byte[] secret;
    private final String issuer;
    private final long expirationSeconds;

    public JwtService(
        @Value("${security.jwt.secret}") String secret,
        @Value("${security.jwt.issuer}") String issuer,
        @Value("${security.jwt.expiration-seconds}") long expirationSeconds
    ) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.issuer = issuer;
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationSeconds * 1000);
        return Jwts.builder()
            .issuer(issuer)
            .subject(userDetails.getUsername())
            .issuedAt(now)
            .expiration(expiry)
            .claims(Map.of("roles", userDetails.getAuthorities().stream()
                .map(Object::toString)
                .toList()))
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

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}

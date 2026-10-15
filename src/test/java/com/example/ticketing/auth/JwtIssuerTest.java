package com.example.ticketing.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtIssuerTest {

    private static final String SECRET = "Zq8vN2kLw5pR7tY1uI3oA6sD9fG4hJ0xC";

    @Test
    void tokenWithForeignIssuerIsRejected() {
        JwtService service = new JwtService(SECRET, "ticketing", 3600);
        // Ký bằng đúng key của service (constructor có thể nạp SECURITY_JWT_SECRET từ .env) để chỉ issuer là sai.
        byte[] key = (byte[]) ReflectionTestUtils.getField(service, "secret");
        String foreign = Jwts.builder().issuer("other-app").subject("alice").issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(key)).compact();
        assertThatThrownBy(() -> service.parseClaims(foreign)).isInstanceOf(io.jsonwebtoken.JwtException.class);
    }
}

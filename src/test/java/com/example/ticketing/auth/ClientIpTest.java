package com.example.ticketing.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** IP dùng cho rate limit: phần tử cuối X-Forwarded-For (proxy thêm), không thì remoteAddr. */
class ClientIpTest {

    @Test
    void usesLastForwardedEntry() {
        assertThat(AuthController.clientIp("1.1.1.1, 9.9.9.9", "10.0.0.1")).isEqualTo("9.9.9.9");
    }

    @Test
    void noHeaderUsesRemoteAddr() {
        assertThat(AuthController.clientIp(null, "10.0.0.1")).isEqualTo("10.0.0.1");
    }

    @Test
    void malformedHeaderFallsBackToRemoteAddr() {
        assertThat(AuthController.clientIp("", "10.0.0.1")).isEqualTo("10.0.0.1");
        assertThat(AuthController.clientIp("a,", "10.0.0.1")).isEqualTo("10.0.0.1");
        assertThat(AuthController.clientIp(" , ", "10.0.0.1")).isEqualTo("10.0.0.1");
    }
}

package com.example.ticketing.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.ticketing.security.JwtBlacklistService;

import io.jsonwebtoken.Jwts;

class JwtPasswordMustChangeTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final JwtBlacklistService blacklist = mock(JwtBlacklistService.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, blacklist, users);

    @BeforeEach
    void setUp() {
        when(jwtService.parseClaims("tok")).thenReturn(Jwts.claims().subject("temp").id("j").issuedAt(new Date())
                .add("roles", List.of("ROLE_NHAN_VIEN")).build());
        UserAccount u = new UserAccount();
        u.setUsername("temp");
        u.setRole(UserRole.Role.NHAN_VIEN);
        u.setEnabled(true);
        u.setPasswordMustChange(true);
        when(users.findByUsername("temp")).thenReturn(Optional.of(u));
        when(blacklist.isBlacklisted(anyString())).thenReturn(false);
        when(blacklist.isUserRevoked("temp")).thenReturn(false);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse call(String method, String uri, String query) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        if (query != null) req.setQueryString(query);
        req.addHeader("Authorization", "Bearer tok");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void ordinaryApiIsBlocked() throws Exception {
        MockHttpServletResponse res = call("GET", "/api/tickets", null);
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("PASSWORD_CHANGE_REQUIRED").contains("\"passwordMustChange\":true");
    }

    @Test
    void forcePasswordChangeIsAllowed() throws Exception {
        assertThat(call("POST", "/api/users/me/force-password-change", null).getStatus()).isEqualTo(200);
    }

    @Test
    void allowedEndpointWithQueryStringPasses() throws Exception {
        assertThat(call("GET", "/api/users/me/status", "ts=1").getStatus()).isEqualTo(200);
    }

    @Test
    void logoutAllowed() throws Exception {
        assertThat(call("POST", "/api/auth/logout", null).getStatus()).isEqualTo(200);
    }

    @Test
    void wrongMethodOnAllowedPathIsBlocked() throws Exception {
        assertThat(call("DELETE", "/api/users/me", null).getStatus()).isEqualTo(403);
    }
}

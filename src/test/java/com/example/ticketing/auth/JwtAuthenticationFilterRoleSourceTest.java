package com.example.ticketing.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.ticketing.security.JwtBlacklistService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

class JwtAuthenticationFilterRoleSourceTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final JwtBlacklistService blacklist = mock(JwtBlacklistService.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, blacklist, users);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithToken() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/tickets");
        req.addHeader("Authorization", "Bearer tok");
        return req;
    }

    private Claims claims(String username, String roleInToken) {
        return Jwts.claims().subject(username).id("jti-1").issuedAt(new Date())
                .add("roles", List.of("ROLE_" + roleInToken)).build();
    }

    @Test
    void authoritiesComeFromDatabaseNotFromTokenClaim() throws Exception {
        when(jwtService.parseClaims("tok")).thenReturn(claims("alice", "ADMIN"));
        UserAccount alice = new UserAccount();
        alice.setUsername("alice");
        alice.setRole(UserRole.Role.NHAN_VIEN);
        alice.setEnabled(true);
        alice.setPasswordMustChange(false);
        when(users.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(blacklist.isBlacklisted(anyString())).thenReturn(false);
        when(blacklist.isUserRevoked("alice")).thenReturn(false);

        filter.doFilter(requestWithToken(), new MockHttpServletResponse(), new MockFilterChain());

        List<String> roles = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().map(GrantedAuthority::getAuthority).toList();
        assertThat(roles).containsExactly("ROLE_NHAN_VIEN");
    }

    @Test
    void deletedUserIsRejected() throws Exception {
        when(jwtService.parseClaims("tok")).thenReturn(claims("ghost", "ADMIN"));
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());
        when(blacklist.isBlacklisted(anyString())).thenReturn(false);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithToken(), res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}

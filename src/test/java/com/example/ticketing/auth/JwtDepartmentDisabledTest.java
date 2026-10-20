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
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.ticketing.department.Department;
import com.example.ticketing.security.JwtBlacklistService;

import io.jsonwebtoken.Jwts;

/** Phòng ban bị vô hiệu hoá thì token của mọi account thuộc phòng đó bị từ chối. */
class JwtDepartmentDisabledTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final JwtBlacklistService blacklist = mock(JwtBlacklistService.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, blacklist, users);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse call(UserRole.Role role, Boolean departmentEnabled) throws Exception {
        when(jwtService.parseClaims("tok")).thenReturn(Jwts.claims().subject("u").id("j").issuedAt(new Date())
                .add("roles", List.of("ROLE_" + role.name())).build());
        UserAccount u = new UserAccount();
        u.setUsername("u");
        u.setRole(role);
        u.setEnabled(true);
        u.setPasswordMustChange(false);
        if (departmentEnabled != null) {
            Department d = new Department();
            d.setCode("FIN");
            d.setEnabled(departmentEnabled);
            u.setDepartment(d);
        }
        when(users.findByUsername("u")).thenReturn(Optional.of(u));
        when(blacklist.isBlacklisted(anyString())).thenReturn(false);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/tickets");
        req.addHeader("Authorization", "Bearer tok");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void employeeOfDisabledDepartmentIsRejected() throws Exception {
        MockHttpServletResponse res = call(UserRole.Role.NHAN_VIEN, false);
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("Department disabled").doesNotContain("AUTH_USER");
    }

    @Test
    void leadOfDisabledDepartmentIsRejected() throws Exception {
        assertThat(call(UserRole.Role.TRUONG_PHONG, false).getStatus()).isEqualTo(401);
    }

    @Test
    void enabledDepartmentPasses() throws Exception {
        assertThat(call(UserRole.Role.TRUONG_PHONG, true).getStatus()).isEqualTo(200);
    }

    @Test
    void userWithoutDepartmentPasses() throws Exception {
        assertThat(call(UserRole.Role.ADMIN, null).getStatus()).isEqualTo(200);
    }
}

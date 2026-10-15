package com.example.ticketing.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.security.RateLimiterService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UnlockLoginEndpointTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository users;
    @Autowired private RateLimiterService rateLimiter;

    private UserAccount account(String username, UserRole.Role role) {
        UserAccount u = new UserAccount();
        u.setUsername(username);
        u.setPasswordHash("x");
        u.setRole(role);
        u.setApproved(true);
        u.setEnabled(true);
        return users.save(u);
    }

    @Test
    void adminUnlocksLockedAccount() throws Exception {
        account("ul_admin", UserRole.Role.ADMIN);
        UserAccount victim = account("ul_victim", UserRole.Role.NHAN_VIEN);
        for (int i = 0; i < 5; i++) {
            rateLimiter.recordFailedAttempt(RateLimiterService.userKey("ul_victim"));
        }
        assertThat(rateLimiter.isLockedOut(RateLimiterService.userKey("ul_victim"))).isTrue();

        mockMvc.perform(post("/api/admin/users/{id}/unlock-login", victim.getId())
                        .with(user("ul_admin").roles("ADMIN")))
                .andExpect(status().isOk());

        assertThat(rateLimiter.isLockedOut(RateLimiterService.userKey("ul_victim"))).isFalse();
    }

    @Test
    void unlockingNeverLockedUserIsNoOp() throws Exception {
        account("ul_admin2", UserRole.Role.ADMIN);
        UserAccount fine = account("ul_fine", UserRole.Role.NHAN_VIEN);
        mockMvc.perform(post("/api/admin/users/{id}/unlock-login", fine.getId())
                        .with(user("ul_admin2").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void unknownUserIs404() throws Exception {
        account("ul_admin3", UserRole.Role.ADMIN);
        mockMvc.perform(post("/api/admin/users/{id}/unlock-login", 987654321L)
                        .with(user("ul_admin3").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminIsForbidden() throws Exception {
        account("ul_tp", UserRole.Role.TRUONG_PHONG);
        UserAccount victim = account("ul_victim2", UserRole.Role.NHAN_VIEN);
        mockMvc.perform(post("/api/admin/users/{id}/unlock-login", victim.getId())
                        .with(user("ul_tp").roles("TRUONG_PHONG")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSeesLockedAccounts() throws Exception {
        account("ul_admin4", UserRole.Role.ADMIN);
        for (int i = 0; i < 5; i++) {
            rateLimiter.recordFailedAttempt(RateLimiterService.userKey("UL_Listed"));
            rateLimiter.recordFailedAttempt(RateLimiterService.ipKey("10.8.0.1"));
        }
        mockMvc.perform(get("/api/admin/users/login-locks").with(user("ul_admin4").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ul_listed").isNumber())
                .andExpect(jsonPath("$['10.8.0.1']").doesNotExist());
        mockMvc.perform(get("/api/admin/users/login-locks").with(user("ul_tp4").roles("TRUONG_PHONG")))
                .andExpect(status().isForbidden());
    }
}

package com.example.ticketing.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.ticketing.security.RateLimiterService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginRateLimitTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserAccountRepository users;
    @Autowired private PasswordEncoder encoder;

    private void user(String username) {
        if (users.findByUsername(username).isPresent()) return;
        UserAccount u = new UserAccount();
        u.setUsername(username);
        u.setPasswordHash(encoder.encode("Correct-Pass-1"));
        u.setRole(UserRole.Role.NHAN_VIEN);
        u.setApproved(true);
        u.setEnabled(true);
        users.save(u);
    }

    private MockHttpServletRequestBuilder login(String username, String password, String remoteAddr, String xff) {
        MockHttpServletRequestBuilder b = post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                .with(r -> { r.setRemoteAddr(remoteAddr); return r; });
        return xff == null ? b : b.header("X-Forwarded-For", xff);
    }

    @Test
    void rotatingForwardedHeaderDoesNotBypassIpLimit() throws Exception {
        user("rl_ip_victim");
        for (int i = 0; i < RateLimiterService.IP_MAX_ATTEMPTS; i++) {
            // Mỗi lần đổi phần đầu X-Forwarded-For, nhưng IP thật (phần cuối) giữ nguyên.
            mockMvc.perform(login("rl_ip_victim_" + i, "wrong-pass", "10.1.1.1", "6.6.6." + i + ", 10.9.9.9"));
        }
        mockMvc.perform(login("rl_ip_victim", "Correct-Pass-1", "10.1.1.1", "7.7.7.7, 10.9.9.9"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void lockedAccountDoesNotLockOthersOnSameIp() throws Exception {
        user("rl_shared_bad");
        user("rl_shared_ok");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login("rl_shared_bad", "wrong-pass", "10.4.0.1", null));
        }
        mockMvc.perform(login("rl_shared_bad", "Correct-Pass-1", "10.4.0.1", null))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(login("rl_shared_ok", "Correct-Pass-1", "10.4.0.1", null))
                .andExpect(status().isOk());
    }

    @Test
    void accountLockedAcrossDifferentIps() throws Exception {
        user("rl_acct");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login("rl_acct", "wrong-pass", "10.2.0." + i, null));
        }
        mockMvc.perform(login("rl_acct", "Correct-Pass-1", "10.2.0.99", null))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void successClearsCounters() throws Exception {
        user("rl_ok");
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(login("rl_ok", "wrong-pass", "10.3.0.1", null));
        }
        mockMvc.perform(login("rl_ok", "Correct-Pass-1", "10.3.0.1", null)).andExpect(status().isOk());
        mockMvc.perform(login("rl_ok", "wrong-pass", "10.3.0.1", null)).andExpect(status().isUnauthorized());
    }

    @Test
    void lockoutKeyIgnoresCaseAndWhitespace() {
        assertThat(RateLimiterService.userKey(" Admin ")).isEqualTo(RateLimiterService.userKey("admin"));
    }
}

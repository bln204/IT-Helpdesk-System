package com.example.ticketing.servicerequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.ticketing.auth.JwtService;
import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.auth.UserRole;
import com.example.ticketing.config.SecurityConfig;
import com.example.ticketing.security.JwtBlacklistService;

@WebMvcTest(controllers = ServiceRequestController.class)
@Import({SecurityConfig.class})
class ServiceRequestControllerIdentityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ServiceRequestService serviceRequestService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private JwtBlacklistService jwtBlacklistService;
    @MockitoBean private UserAccountRepository userAccountRepository;

    private static UsernamePasswordAuthenticationToken alice() {
        return new UsernamePasswordAuthenticationToken("alice", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_NHAN_VIEN")));
    }

    @BeforeEach
    void setUp() {
        UserAccount a = new UserAccount();
        a.setUsername("alice");
        a.setDisplayName("Alice");
        a.setEmail("alice@corp.vn");
        a.setRole(UserRole.Role.NHAN_VIEN);
        when(userAccountRepository.findByUsername("alice")).thenReturn(Optional.of(a));
        ServiceRequest sr = new ServiceRequest();
        sr.setRequesterUsername("alice");
        when(serviceRequestService.confirmCompletion(anyLong(), any())).thenReturn(sr);
        when(serviceRequestService.createRequest(any(), any(), any(), any(), any())).thenReturn(sr);
    }

    @Test
    void confirmIgnoresSpoofedHeader() throws Exception {
        mockMvc.perform(patch("/api/service-requests/1/confirm")
                        .header("X-User-Username", "mallory")
                        .with(authentication(alice())))
                .andExpect(status().isOk());
        verify(serviceRequestService).confirmCompletion(1L, "alice");
    }

    @Test
    void createMyRequestUsesAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/service-requests/my")
                        .header("X-User-Username", "mallory")
                        .with(authentication(alice()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"VPN\"}"))
                .andExpect(status().isCreated());
        verify(serviceRequestService).createRequest(any(), eq("alice"), eq("Alice"), eq("alice@corp.vn"), any());
    }

    @Test
    void createIgnoresRequesterInBody() throws Exception {
        mockMvc.perform(post("/api/service-requests")
                        .with(authentication(alice()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"VPN\",\"requesterUsername\":\"mallory\"}"))
                .andExpect(status().isCreated());
        verify(serviceRequestService).createRequest(any(), eq("alice"), eq("Alice"), eq("alice@corp.vn"), any());
    }

    @Test
    void forbiddenIsNot500() throws Exception {
        mockMvc.perform(delete("/api/service-requests/1").with(authentication(alice())))
                .andExpect(status().isForbidden());
    }
}

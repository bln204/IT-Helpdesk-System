package com.example.ticketing.change;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.ticketing.auth.JwtService;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.auth.UserRole;
import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.authorization.ActorContextService;
import com.example.ticketing.authorization.ChangeAuthorization;
import com.example.ticketing.config.SecurityConfig;
import com.example.ticketing.security.JwtBlacklistService;

@WebMvcTest(controllers = ChangeRequestController.class)
@Import({SecurityConfig.class, ChangeAuthorization.class})
class ChangeRequestControllerAuthorizationTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ChangeRequestService changeService;
    @MockitoBean private ActorContextService actorContextService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private JwtBlacklistService jwtBlacklistService;
    @MockitoBean private UserAccountRepository userAccountRepository;

    private Authentication as(String username, UserRole.Role role, String dept, boolean it) {
        when(actorContextService.fromAuthentication(any())).thenReturn(ActorContext.of(username, role, dept, it));
        return new UsernamePasswordAuthenticationToken(username, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    @Test
    void businessUserCannotReadOthersChange() throws Exception {
        ChangeRequest c = new ChangeRequest();
        c.setRequesterUsername("bob");
        when(changeService.getChangeById(1L)).thenReturn(c);
        mockMvc.perform(get("/api/changes/1").with(authentication(as("alice", UserRole.Role.NHAN_VIEN, "MKT", false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void itStaffCannotApprove() throws Exception {
        mockMvc.perform(post("/api/changes/1/approve")
                        .with(authentication(as("nv_it", UserRole.Role.NHAN_VIEN, "IT", true)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"ok\"}"))
                .andExpect(status().isForbidden());
        verify(changeService, never()).approveChange(anyLong(), any(), any(), any());
    }

    @Test
    void itLeadApprovesAsHimself() throws Exception {
        when(changeService.approveChange(anyLong(), any(), any(), any())).thenReturn(new ChangeRequest());
        mockMvc.perform(post("/api/changes/1/approve")
                        .with(authentication(as("tp_it", UserRole.Role.TRUONG_PHONG, "IT", true)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approver\":\"mallory\",\"notes\":\"ok\"}"))
                .andExpect(status().isOk());
        verify(changeService).approveChange(1L, "tp_it", "ok", "tp_it");
    }

    @Test
    void businessUserCannotCreate() throws Exception {
        mockMvc.perform(post("/api/changes")
                        .with(authentication(as("alice", UserRole.Role.TRUONG_PHONG, "MKT", false)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"))
                .andExpect(status().isForbidden());
    }
}

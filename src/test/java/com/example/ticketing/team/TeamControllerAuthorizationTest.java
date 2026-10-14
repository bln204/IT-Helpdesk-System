package com.example.ticketing.team;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.ticketing.auth.JwtService;
import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.auth.UserRole;
import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.authorization.ActorContextService;
import com.example.ticketing.config.SecurityConfig;
import com.example.ticketing.security.JwtBlacklistService;

@WebMvcTest(controllers = TeamController.class)
@Import({SecurityConfig.class})
class TeamControllerAuthorizationTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TeamService teamService;
    @MockitoBean private ActorContextService actorContextService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private JwtBlacklistService jwtBlacklistService;
    @MockitoBean private UserAccountRepository userAccountRepository;

    private Authentication as(String username, UserRole.Role role, String dept, boolean it) {
        when(actorContextService.fromAuthentication(any())).thenReturn(ActorContext.of(username, role, dept, it));
        UserAccount u = new UserAccount();
        u.setUsername(username);
        u.setRole(role);
        when(userAccountRepository.findByUsername(username)).thenReturn(Optional.of(u));
        return new UsernamePasswordAuthenticationToken(username, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    @Test
    void businessUserCannotReadTeamMembers() throws Exception {
        mockMvc.perform(get("/api/teams/1/members").with(authentication(as("alice", UserRole.Role.NHAN_VIEN, "MKT", false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void businessLeadCannotListTeams() throws Exception {
        mockMvc.perform(get("/api/teams").with(authentication(as("lead", UserRole.Role.TRUONG_PHONG, "MKT", false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void itStaffCanListTeams() throws Exception {
        when(teamService.getITTeams()).thenReturn(List.of());
        mockMvc.perform(get("/api/teams").with(authentication(as("nv_it", UserRole.Role.NHAN_VIEN, "IT", true))))
                .andExpect(status().isOk());
    }

    @Test
    void itStaffCannotGetNextAgent() throws Exception {
        mockMvc.perform(get("/api/teams/1/next-agent").with(authentication(as("nv_it", UserRole.Role.NHAN_VIEN, "IT", true))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anyoneCanReadOwnTeams() throws Exception {
        when(teamService.getUserTeams(any())).thenReturn(List.of());
        mockMvc.perform(get("/api/teams/user/me").with(authentication(as("alice", UserRole.Role.NHAN_VIEN, "MKT", false))))
                .andExpect(status().isOk());
    }
}

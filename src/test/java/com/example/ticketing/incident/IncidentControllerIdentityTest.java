package com.example.ticketing.incident;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.config.SecurityConfig;
import com.example.ticketing.security.JwtBlacklistService;

@WebMvcTest(controllers = IncidentController.class)
@Import({SecurityConfig.class})
class IncidentControllerIdentityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private IncidentService incidentService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private JwtBlacklistService jwtBlacklistService;
    @MockitoBean private UserAccountRepository userAccountRepository;

    @Test
    void createIgnoresReporterInBody() throws Exception {
        when(incidentService.createIncident(any(), any())).thenReturn(new Incident());
        mockMvc.perform(post("/api/incidents")
                        .with(authentication(new UsernamePasswordAuthenticationToken("alice", "n/a",
                                List.of(new SimpleGrantedAuthority("ROLE_NHAN_VIEN")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Mất mạng\",\"reportedByUsername\":\"giamdoc\"}"))
                .andExpect(status().isCreated());
        verify(incidentService).createIncident(any(), eq("alice"));
    }
}

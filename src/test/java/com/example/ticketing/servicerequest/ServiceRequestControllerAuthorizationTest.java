package com.example.ticketing.servicerequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.example.ticketing.authorization.DepartmentKeys;
import com.example.ticketing.authorization.ServiceRequestAuthorization;
import com.example.ticketing.config.SecurityConfig;
import com.example.ticketing.department.DepartmentRepository;
import com.example.ticketing.security.JwtBlacklistService;

@WebMvcTest(controllers = ServiceRequestController.class)
@Import({SecurityConfig.class, ServiceRequestAuthorization.class, DepartmentKeys.class})
class ServiceRequestControllerAuthorizationTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ServiceRequestService serviceRequestService;
    @MockitoBean private ActorContextService actorContextService;
    @MockitoBean private DepartmentRepository departmentRepository;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private JwtBlacklistService jwtBlacklistService;
    @MockitoBean private UserAccountRepository userAccountRepository;

    private Authentication as(String username, UserRole.Role role, String dept, boolean it) {
        Authentication auth = new UsernamePasswordAuthenticationToken(username, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        when(actorContextService.fromAuthentication(any())).thenReturn(ActorContext.of(username, role, dept, it));
        return auth;
    }

    private void existing(String requester) {
        ServiceRequest sr = new ServiceRequest();
        sr.setRequesterUsername(requester);
        sr.setRequesterDepartment("MKT");
        when(serviceRequestService.getRequestById(1L)).thenReturn(sr);
    }

    @Test
    void businessUserCannotReadOthersRequest() throws Exception {
        existing("bob");
        mockMvc.perform(get("/api/service-requests/1")
                        .with(authentication(as("alice", UserRole.Role.NHAN_VIEN, "MKT", false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void businessUserCannotApprove() throws Exception {
        existing("alice");
        mockMvc.perform(patch("/api/service-requests/1/approve")
                        .with(authentication(as("alice", UserRole.Role.NHAN_VIEN, "MKT", false)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"ok\"}"))
                .andExpect(status().isForbidden());
        verify(serviceRequestService, never()).approveRequest(anyLong(), any(), any());
    }

    @Test
    void itLeadApprovesAsHimself() throws Exception {
        existing("alice");
        when(serviceRequestService.approveRequest(anyLong(), any(), any())).thenReturn(new ServiceRequest());
        mockMvc.perform(patch("/api/service-requests/1/approve")
                        .with(authentication(as("tp_it", UserRole.Role.TRUONG_PHONG, "IT", true)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approver\":\"mallory\",\"notes\":\"ok\"}"))
                .andExpect(status().isOk());
        verify(serviceRequestService).approveRequest(1L, "tp_it", "ok");
    }

    @Test
    void adminCannotApprove() throws Exception {
        existing("alice");
        mockMvc.perform(patch("/api/service-requests/1/approve")
                        .with(authentication(as("root", UserRole.Role.ADMIN, null, false)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"ok\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyRequesterConfirms() throws Exception {
        existing("bob");
        mockMvc.perform(patch("/api/service-requests/1/confirm")
                        .with(authentication(as("tp_it", UserRole.Role.TRUONG_PHONG, "IT", true))))
                .andExpect(status().isForbidden());
    }

    @Test
    void businessUserListIsScoped() throws Exception {
        when(serviceRequestService.listRequests(any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        mockMvc.perform(get("/api/service-requests")
                        .with(authentication(as("alice", UserRole.Role.NHAN_VIEN, "MKT", false))))
                .andExpect(status().isOk());
        verify(serviceRequestService).listRequests(any(), any(), any(), any());
        verify(serviceRequestService, never()).getAllRequests(any());
    }
}

package com.example.ticketing.servicerequest;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.ticketing.auth.JwtService;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.authorization.ActorContextService;
import com.example.ticketing.authorization.DepartmentKeys;
import com.example.ticketing.authorization.ServiceRequestAuthorization;
import com.example.ticketing.config.SecurityConfig;
import com.example.ticketing.department.DepartmentRepository;
import com.example.ticketing.security.JwtBlacklistService;

@WebMvcTest(controllers = ServiceRequestController.class)
@Import({SecurityConfig.class, ServiceRequestAuthorization.class, DepartmentKeys.class})
class ServiceRequestErrorLeakTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ServiceRequestService serviceRequestService;
    @MockitoBean private ActorContextService actorContextService;
    @MockitoBean private DepartmentRepository departmentRepository;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private JwtBlacklistService jwtBlacklistService;
    @MockitoBean private UserAccountRepository userAccountRepository;

    @Test
    void internalErrorDoesNotLeakMessage() throws Exception {
        when(serviceRequestService.getPublicServices())
                .thenThrow(new IllegalStateException("could not execute statement; SQL [select secret_column]"));
        mockMvc.perform(get("/api/service-requests/catalog").with(user("alice").roles("NHAN_VIEN")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(Matchers.not(Matchers.containsString("secret_column"))));
    }

    @Test
    void nullPointerDoesNotLeakMessage() throws Exception {
        when(serviceRequestService.getPublicServices()).thenThrow(new NullPointerException("field internalThing is null"));
        mockMvc.perform(get("/api/service-requests/catalog").with(user("alice").roles("NHAN_VIEN")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(Matchers.not(Matchers.containsString("internalThing"))));
    }
}

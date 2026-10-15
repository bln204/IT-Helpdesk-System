package com.example.ticketing.asset;

import static org.mockito.ArgumentMatchers.any;
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
import com.example.ticketing.authorization.AssetAuthorization;
import com.example.ticketing.authorization.DepartmentKeys;
import com.example.ticketing.config.SecurityConfig;
import com.example.ticketing.department.DepartmentRepository;
import com.example.ticketing.security.JwtBlacklistService;

@WebMvcTest(controllers = AssetController.class)
@Import({SecurityConfig.class, AssetAuthorization.class, DepartmentKeys.class})
class AssetControllerAuthorizationTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AssetService assetService;
    @MockitoBean private ActorContextService actorContextService;
    @MockitoBean private DepartmentRepository departmentRepository;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private JwtBlacklistService jwtBlacklistService;
    @MockitoBean private UserAccountRepository userAccountRepository;

    private Authentication as(String username, UserRole.Role role, String dept, boolean it) {
        when(actorContextService.fromAuthentication(any())).thenReturn(ActorContext.of(username, role, dept, it));
        return new UsernamePasswordAuthenticationToken(username, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    @Test
    void businessUserCannotReadOthersAsset() throws Exception {
        Asset a = new Asset();
        a.setAssignedTo("bob");
        when(assetService.getAssetById(1L)).thenReturn(a);
        mockMvc.perform(get("/api/assets/1").with(authentication(as("alice", UserRole.Role.NHAN_VIEN, "MKT", false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void businessUserCannotSeeFleetStats() throws Exception {
        mockMvc.perform(get("/api/assets/stats").with(authentication(as("alice", UserRole.Role.TRUONG_PHONG, "MKT", false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void oversightCannotAssign() throws Exception {
        mockMvc.perform(patch("/api/assets/1/assign")
                        .with(authentication(as("gd", UserRole.Role.GIAM_DOC, "EXEC", false)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"assignedTo\":\"bob\"}"))
                .andExpect(status().isForbidden());
    }
}

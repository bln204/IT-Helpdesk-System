package com.example.ticketing.problem;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.ticketing.auth.JwtService;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.auth.UserRole;
import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.authorization.ActorContextService;
import com.example.ticketing.authorization.ProblemAuthorization;
import com.example.ticketing.config.SecurityConfig;
import com.example.ticketing.security.JwtBlacklistService;

/** Ma trận spec phase1-rbac-hardening §4.3: Known Error chỉ Trưởng phòng IT ghi; đọc mở cho mọi role. */
@WebMvcTest(controllers = ProblemController.class)
@Import({SecurityConfig.class, ProblemAuthorization.class})
class ProblemControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProblemService problemService;

    @MockitoBean
    private ActorContextService actorContextService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtBlacklistService jwtBlacklistService;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    private static List<GrantedAuthority> auth(String role) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private UsernamePasswordAuthenticationToken actor(String name, String role, String dept, boolean it) {
        when(actorContextService.fromAuthentication(any()))
                .thenReturn(ActorContext.of(name, UserRole.Role.valueOf(role), dept, it));
        return new UsernamePasswordAuthenticationToken(name, "n/a", auth(role));
    }

    private static final String PROBLEM_ID = "1";
    private static final String KE_ID = "2";
    private static final String KE_BODY = "{\"title\":\"KE\"}";

    @Test
    @DisplayName("POST createKnownErrorForProblem - NHAN_VIEN nghiệp vụ bị 403")
    void nhanVien_cannotCreateKnownErrorForProblem() throws Exception {
        mockMvc.perform(post("/api/problems/{id}/known-error", PROBLEM_ID)
                        .with(authentication(actor("nv_user", "NHAN_VIEN", "MKT", false)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(KE_BODY))
                .andExpect(status().isForbidden());
        verify(problemService, never())
                .createKnownErrorForProblem(any(), any(KnownError.class), any());
    }

    @Test
    @DisplayName("POST createKnownErrorForProblem - ADMIN bị 403 (không phải operator IT)")
    void admin_cannotCreateKnownErrorForProblem() throws Exception {
        mockMvc.perform(post("/api/problems/{id}/known-error", PROBLEM_ID)
                        .with(authentication(actor("admin_user", "ADMIN", null, false)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(KE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST createKnownErrorForProblem - TRUONG_PHONG nghiệp vụ bị 403")
    void truongPhongBusiness_cannotCreateKnownErrorForProblem() throws Exception {
        mockMvc.perform(post("/api/problems/{id}/known-error", PROBLEM_ID)
                        .with(authentication(actor("tp_user", "TRUONG_PHONG", "MKT", false)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(KE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST createKnownErrorForProblem - Trưởng phòng IT được phép")
    void tpIt_canCreateKnownErrorForProblem() throws Exception {
        when(problemService.createKnownErrorForProblem(any(), any(KnownError.class), any()))
                .thenReturn(new KnownError());
        mockMvc.perform(post("/api/problems/{id}/known-error", PROBLEM_ID)
                        .with(authentication(actor("tp_it", "TRUONG_PHONG", "IT", true)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(KE_BODY))
                .andExpect(status().isCreated());
        verify(problemService).createKnownErrorForProblem(eq(1L), any(KnownError.class), eq("tp_it"));
    }

    @Test
    @DisplayName("POST createKnownErrorForProblem - chưa đăng nhập bị 401")
    void unauth_cannotCreateKnownErrorForProblem() throws Exception {
        mockMvc.perform(post("/api/problems/{id}/known-error", PROBLEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(KE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST linkKnownErrorToProblem - GIAM_DOC bị 403")
    void giamDoc_cannotLinkKnownErrorToProblem() throws Exception {
        mockMvc.perform(post("/api/problems/{id}/known-error/link", PROBLEM_ID)
                        .with(authentication(actor("gd_user", "GIAM_DOC", "EXEC", false)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knownErrorId\":" + KE_ID + "}"))
                .andExpect(status().isForbidden());
        verify(problemService, never()).linkExistingKnownError(any(), any(), any());
    }

    @Test
    @DisplayName("DELETE unlinkKnownErrorFromProblem - NHAN_VIEN IT bị 403")
    void nhanVienIt_cannotUnlinkKnownErrorFromProblem() throws Exception {
        mockMvc.perform(delete("/api/problems/{id}/known-error", PROBLEM_ID)
                        .with(authentication(actor("nv_it", "NHAN_VIEN", "IT", true))))
                .andExpect(status().isForbidden());
        verify(problemService, never()).unlinkKnownError(any(), any());
    }

    @Test
    @DisplayName("DELETE unlinkKnownErrorFromProblem - Trưởng phòng IT được phép")
    void tpIt_canUnlinkKnownErrorFromProblem() throws Exception {
        mockMvc.perform(delete("/api/problems/{id}/known-error", PROBLEM_ID)
                        .with(authentication(actor("tp_it", "TRUONG_PHONG", "IT", true))))
                .andExpect(status().isNoContent());
        verify(problemService).unlinkKnownError(eq(1L), any());
    }

    @Test
    @DisplayName("POST /known-errors - NHAN_VIEN bị 403")
    void nhanVien_cannotCreateLegacyKnownError() throws Exception {
        mockMvc.perform(post("/api/problems/known-errors")
                        .with(authentication(actor("nv_user", "NHAN_VIEN", "MKT", false)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(KE_BODY))
                .andExpect(status().isForbidden());
        verify(problemService, never()).createKnownError(any(KnownError.class), any());
    }

    @Test
    @DisplayName("POST /api/problems - người dùng nghiệp vụ nhận 403, không phải 500")
    void businessUserWriteIs403Not500() throws Exception {
        mockMvc.perform(post("/api/problems")
                        .with(authentication(actor("nv_user", "NHAN_VIEN", "MKT", false)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"P\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /known-errors - người dùng nghiệp vụ đọc được (self-service)")
    void businessUserCanReadKnownErrors() throws Exception {
        when(problemService.getKnownErrors(any())).thenReturn(List.of());
        mockMvc.perform(get("/api/problems/known-errors")
                        .with(authentication(actor("nv_user", "NHAN_VIEN", "MKT", false))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /{id}/notes - người dùng nghiệp vụ bị 403")
    void businessUserCannotReadInternalNotes() throws Exception {
        mockMvc.perform(get("/api/problems/{id}/notes", PROBLEM_ID)
                        .with(authentication(actor("nv_user", "NHAN_VIEN", "MKT", false))))
                .andExpect(status().isForbidden());
    }
}

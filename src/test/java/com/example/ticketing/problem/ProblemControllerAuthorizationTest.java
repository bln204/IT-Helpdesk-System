package com.example.ticketing.problem;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.example.ticketing.config.SecurityConfig;
import com.example.ticketing.security.JwtBlacklistService;

@WebMvcTest(controllers = ProblemController.class)
@Import({SecurityConfig.class})
class ProblemControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProblemService problemService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtBlacklistService jwtBlacklistService;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    private static List<GrantedAuthority> auth(String role) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static UsernamePasswordAuthenticationToken principal(String name, String role) {
        return new UsernamePasswordAuthenticationToken(name, "n/a", auth(role));
    }

    private static final String PROBLEM_ID = "1";
    private static final String KE_ID = "2";

    @Test
    @DisplayName("POST createKnownErrorForProblem - NHAN_VIEN is rejected with 403")
    void nhanVien_cannotCreateKnownErrorForProblem() throws Exception {
        String body = "{\"title\":\"KE\"}";
        mockMvc.perform(post("/api/problems/{id}/known-error", PROBLEM_ID)
                        .with(authentication(principal("nv_user", "NHAN_VIEN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        verify(problemService, never())
                .createKnownErrorForProblem(any(), any(KnownError.class), any());
    }

    @Test
    @DisplayName("POST createKnownErrorForProblem - ADMIN is allowed")
    void admin_canCreateKnownErrorForProblem() throws Exception {
        when(problemService.createKnownErrorForProblem(any(), any(KnownError.class), any()))
                .thenReturn(new KnownError());
        String body = "{\"title\":\"KE\"}";
        mockMvc.perform(post("/api/problems/{id}/known-error", PROBLEM_ID)
                        .with(authentication(principal("admin_user", "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST createKnownErrorForProblem - TRUONG_PHONG is allowed")
    void truongPhong_canCreateKnownErrorForProblem() throws Exception {
        when(problemService.createKnownErrorForProblem(any(), any(KnownError.class), any()))
                .thenReturn(new KnownError());
        String body = "{\"title\":\"KE\"}";
        mockMvc.perform(post("/api/problems/{id}/known-error", PROBLEM_ID)
                        .with(authentication(principal("tp_user", "TRUONG_PHONG")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST createKnownErrorForProblem - unauthenticated is rejected with 401")
    void unauth_cannotCreateKnownErrorForProblem() throws Exception {
        String body = "{\"title\":\"KE\"}";
        mockMvc.perform(post("/api/problems/{id}/known-error", PROBLEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST linkKnownErrorToProblem - NHAN_VIEN is rejected with 403")
    void nhanVien_cannotLinkKnownErrorToProblem() throws Exception {
        String body = "{\"knownErrorId\":" + KE_ID + "}";
        mockMvc.perform(post("/api/problems/{id}/known-error/link", PROBLEM_ID)
                        .with(authentication(principal("nv_user", "NHAN_VIEN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        verify(problemService, never())
                .linkExistingKnownError(any(), any(), any());
    }

    @Test
    @DisplayName("POST linkKnownErrorToProblem - GIAM_DOC is allowed")
    void giamDoc_canLinkKnownErrorToProblem() throws Exception {
        when(problemService.linkExistingKnownError(any(), any(), any()))
                .thenReturn(new KnownError());
        String body = "{\"knownErrorId\":" + KE_ID + "}";
        mockMvc.perform(post("/api/problems/{id}/known-error/link", PROBLEM_ID)
                        .with(authentication(principal("gd_user", "GIAM_DOC")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE unlinkKnownErrorFromProblem - NHAN_VIEN is rejected with 403")
    void nhanVien_cannotUnlinkKnownErrorFromProblem() throws Exception {
        mockMvc.perform(delete("/api/problems/{id}/known-error", PROBLEM_ID)
                        .with(authentication(principal("nv_user", "NHAN_VIEN"))))
                .andExpect(status().isForbidden());
        verify(problemService, never())
                .unlinkKnownError(any(), any());
    }

    @Test
    @DisplayName("DELETE unlinkKnownErrorFromProblem - TRUONG_PHONG is allowed")
    void truongPhong_canUnlinkKnownErrorFromProblem() throws Exception {
        mockMvc.perform(delete("/api/problems/{id}/known-error", PROBLEM_ID)
                        .with(authentication(principal("tp_user", "TRUONG_PHONG"))))
                .andExpect(status().isNoContent());
        verify(problemService).unlinkKnownError(eq(1L), any());
    }

    @Test
    @DisplayName("Legacy POST /known-errors - NHAN_VIEN is now rejected (write path tightened)")
    void nhanVien_cannotCreateLegacyKnownError() throws Exception {
        String body = "{\"title\":\"KE\"}";
        mockMvc.perform(post("/api/problems/known-errors")
                        .with(authentication(principal("nv_user", "NHAN_VIEN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        verify(problemService, never())
                .createKnownError(any(KnownError.class), any());
    }
}
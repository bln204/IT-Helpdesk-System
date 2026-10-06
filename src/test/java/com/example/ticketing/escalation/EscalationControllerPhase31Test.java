package com.example.ticketing.escalation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.ticketing.auth.JwtService;
import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.auth.UserRole;
import com.example.ticketing.authorization.ActorContextService;
import com.example.ticketing.authorization.TicketAuthorization;
import com.example.ticketing.config.SecurityConfig;
import com.example.ticketing.department.Department;
import com.example.ticketing.department.ItDepartmentResolver;
import com.example.ticketing.security.JwtBlacklistService;
import com.example.ticketing.ticket.TicketExceptionHandler;

/**
 * PHASE 3.1 - HTTP-level controller tests for the manual escalation endpoint.
 *
 * <p>Verifies the canonical authorization fix:
 *
 * <ul>
 *   <li>{@code POST /api/escalation/escalate/{ticketId}} requires the actor to satisfy
 *       {@link TicketAuthorization#canAssignOthers(actor)}.</li>
 *   <li>The recorded actor on the escalation history is the actual authenticated
 *       principal, not the legacy hardcoded {@code "admin"}.</li>
 *   <li>Unauthenticated requests return 401.</li>
 * </ul>
 */
@WebMvcTest(controllers = EscalationController.class)
@Import({SecurityConfig.class, ItDepartmentResolver.class,
    ActorContextService.class, TicketAuthorization.class,
    TicketExceptionHandler.class})
class EscalationControllerPhase31Test {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EscalationService escalationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtBlacklistService jwtBlacklistService;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    private static Department dept(String code) {
        Department d = new Department();
        d.setCode(code);
        d.setName(code + " department");
        d.setEnabled(true);
        return d;
    }

    private static UserAccount actor(String username, UserRole.Role role, Department department) {
        UserAccount u = new UserAccount();
        u.setUsername(username);
        u.setRole(role);
        u.setDepartment(department);
        u.setDisplayName(username);
        u.setEmail(username + "@example.internal");
        u.setEnabled(true);
        u.setApproved(true);
        return u;
    }

    private static final Department IT = dept("IT");
    private static final Department MKT = dept("MKT");

    private static final UserAccount ADMIN = actor("admin_p31", UserRole.Role.ADMIN, MKT);
    private static final UserAccount GIAM_DOC = actor("gd_p31", UserRole.Role.GIAM_DOC, MKT);
    private static final UserAccount TRUONG_PHONG_IT = actor("tp_p31", UserRole.Role.TRUONG_PHONG, IT);
    private static final UserAccount NHAN_VIEN_IT = actor("nv_p31", UserRole.Role.NHAN_VIEN, IT);
    private static final UserAccount TRUONG_PHONG_MKT = actor("tp_mkt_p31", UserRole.Role.TRUONG_PHONG, MKT);
    private static final UserAccount NHAN_VIEN_MKT = actor("nv_mkt_p31", UserRole.Role.NHAN_VIEN, MKT);

    private static UsernamePasswordAuthenticationToken stringPrincipal(UserAccount user) {
        return new UsernamePasswordAuthenticationToken(
            user.getUsername(), null,
            List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }

    private void givenUserExists(UserAccount user) {
        when(userAccountRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
    }

    private void givenManualEscalateReturns(EscalationHistory history) {
        when(escalationService.manualEscalate(anyLong(), any(), any())).thenReturn(history);
    }

    private static EscalationHistory historyFor(Long ticketId) {
        EscalationHistory h = new EscalationHistory(ticketId, "PHASE31-CTRL");
        h.setEscalationReason("Manual escalation: SLA breach");
        h.setNewStatus("ESCALATED");
        h.setActionTaken("MANUAL_ESCALATION");
        return h;
    }

    private static String escalatePayload() {
        return "{\"reason\":\"SLA breach\"}";
    }

    private static final Long TICKET_ID = 3131L;

    // ========================================================================
    // Authorization - only TRUONG_PHONG + IT can manually escalate
    // ========================================================================

    @Nested
    @DisplayName("Manual escalation - actor authorization (canAssignOthers)")
    class ActorAuthorization {

        @Test
        @DisplayName("TRUONG_PHONG + IT: 201 created")
        void truongPhongItAllowed() throws Exception {
            givenUserExists(TRUONG_PHONG_IT);
            givenManualEscalateReturns(historyFor(TICKET_ID));

            mockMvc.perform(post("/api/escalation/escalate/{id}", TICKET_ID)
                    .with(authentication(stringPrincipal(TRUONG_PHONG_IT)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(escalatePayload()))
                .andExpect(status().isCreated());

            // PHASE 3.1: the actual authenticated username must be passed to the
            // service as the audit actor, NOT the legacy hardcoded "admin".
            verify(escalationService).manualEscalate(eq(TICKET_ID), any(),
                eq(TRUONG_PHONG_IT.getUsername()));
        }

        @Test
        @DisplayName("ADMIN: forbidden (canAssignOthers = false)")
        void adminForbidden() throws Exception {
            givenUserExists(ADMIN);

            mockMvc.perform(post("/api/escalation/escalate/{id}", TICKET_ID)
                    .with(authentication(stringPrincipal(ADMIN)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(escalatePayload()))
                .andExpect(status().isForbidden());

            verify(escalationService, never()).manualEscalate(anyLong(), any(), any());
        }

        @Test
        @DisplayName("GIAM_DOC: forbidden (canAssignOthers = false)")
        void giamDocForbidden() throws Exception {
            givenUserExists(GIAM_DOC);

            mockMvc.perform(post("/api/escalation/escalate/{id}", TICKET_ID)
                    .with(authentication(stringPrincipal(GIAM_DOC)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(escalatePayload()))
                .andExpect(status().isForbidden());

            verify(escalationService, never()).manualEscalate(anyLong(), any(), any());
        }

        @Test
        @DisplayName("TRUONG_PHONG non-IT: forbidden (canAssignOthers = false)")
        void truongPhongNonItForbidden() throws Exception {
            givenUserExists(TRUONG_PHONG_MKT);

            mockMvc.perform(post("/api/escalation/escalate/{id}", TICKET_ID)
                    .with(authentication(stringPrincipal(TRUONG_PHONG_MKT)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(escalatePayload()))
                .andExpect(status().isForbidden());

            verify(escalationService, never()).manualEscalate(anyLong(), any(), any());
        }

        @Test
        @DisplayName("NHAN_VIEN non-IT: forbidden (canAssignOthers = false)")
        void nhanVienNonItForbidden() throws Exception {
            givenUserExists(NHAN_VIEN_MKT);

            mockMvc.perform(post("/api/escalation/escalate/{id}", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_MKT)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(escalatePayload()))
                .andExpect(status().isForbidden());

            verify(escalationService, never()).manualEscalate(anyLong(), any(), any());
        }

        @Test
        @DisplayName("NHAN_VIEN + IT: forbidden (canAssignOthers = false; only self-receive allowed)")
        void nhanVienItForbidden() throws Exception {
            givenUserExists(NHAN_VIEN_IT);

            mockMvc.perform(post("/api/escalation/escalate/{id}", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_IT)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(escalatePayload()))
                .andExpect(status().isForbidden());

            verify(escalationService, never()).manualEscalate(anyLong(), any(), any());
        }

        @Test
        @DisplayName("Unauthenticated: 401")
        void unauthenticatedRejected() throws Exception {
            mockMvc.perform(post("/api/escalation/escalate/{id}", TICKET_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(escalatePayload()))
                .andExpect(status().isUnauthorized());

            verify(escalationService, never()).manualEscalate(anyLong(), any(), any());
        }
    }

    // ========================================================================
    // \u00a716 - manual escalation records the actual authenticated principal
    // ========================================================================

    @Nested
    @DisplayName("Manual escalation - audit actor is the real principal")
    class AuditActor {

        @Test
        @DisplayName("Authorized actor: the actual username is passed to the service, not 'admin'")
        void realUsernamePassedToService() throws Exception {
            givenUserExists(TRUONG_PHONG_IT);
            givenManualEscalateReturns(historyFor(TICKET_ID));

            mockMvc.perform(post("/api/escalation/escalate/{id}", TICKET_ID)
                    .with(authentication(stringPrincipal(TRUONG_PHONG_IT)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(escalatePayload()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketId").value(TICKET_ID));

            // The controller must pass the real principal, not the legacy "admin" string.
            verify(escalationService).manualEscalate(
                eq(TICKET_ID),
                any(),
                eq(TRUONG_PHONG_IT.getUsername()));
        }
    }
}

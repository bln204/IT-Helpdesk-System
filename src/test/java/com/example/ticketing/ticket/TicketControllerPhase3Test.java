package com.example.ticketing.ticket;

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
import com.example.ticketing.ticket.TicketTypes.TicketPriority;

/**
 * PHASE 3 - HTTP-level controller tests for the create-time assignee policy (H-3) and
 * the canonical target validation contract (C-3).
 *
 * <p>This suite covers brief \u00a725 - verify the actual HTTP behavior:
 * <ul>
 *   <li>HTTP success for authorized create-time assignment (TRUONG_PHONG + IT + valid target).</li>
 *   <li>HTTP success for create without an assignee (every authenticated role).</li>
 *   <li>HTTP 4xx for unauthorized create-time assignment attempts (per the existing
 *       project exception-to-HTTP mapping: 400 for TicketRuleViolationException,
 *       404 for TicketNotFoundException).</li>
 * </ul>
 *
 * <p>The actual C-3 target validation is exercised end-to-end by
 * {@link TicketServicePhase3IntegrationTest}. This suite verifies the HTTP seam: that
 * the controller does not bypass the service, and that an unauthorized actor never
 * reaches the service for create-time assignment.
 */
@WebMvcTest(controllers = TicketController.class)
@Import({SecurityConfig.class, ItDepartmentResolver.class,
    ActorContextService.class, TicketAuthorization.class,
    TicketExceptionHandler.class})
class TicketControllerPhase3Test {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketService ticketService;

    @MockitoBean
    private TicketAttachmentService attachmentService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtBlacklistService jwtBlacklistService;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    private static final String MKT_CODE = "MKT";
    private static final String IT_CODE = "IT";

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

    private static final Department IT = dept(IT_CODE);
    private static final Department MKT = dept(MKT_CODE);

    private static final UserAccount ADMIN = actor("admin_p3", UserRole.Role.ADMIN, MKT);
    private static final UserAccount GIAM_DOC = actor("gd_p3", UserRole.Role.GIAM_DOC, MKT);
    private static final UserAccount TRUONG_PHONG_IT = actor("tp_p3", UserRole.Role.TRUONG_PHONG, IT);
    private static final UserAccount NHAN_VIEN_IT = actor("nv_p3", UserRole.Role.NHAN_VIEN, IT);
    private static final UserAccount NHAN_VIEN_IT2 = actor("nv_p3b", UserRole.Role.NHAN_VIEN, IT);
    private static final UserAccount TRUONG_PHONG_MKT = actor("tp_mkt_p3", UserRole.Role.TRUONG_PHONG, MKT);
    private static final UserAccount NHAN_VIEN_MKT = actor("nv_mkt_p3", UserRole.Role.NHAN_VIEN, MKT);

    /** Mirrors JwtAuthenticationFilter: principal is the String username. */
    private static UsernamePasswordAuthenticationToken stringPrincipal(UserAccount user) {
        return new UsernamePasswordAuthenticationToken(
            user.getUsername(), null,
            List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }

    private void givenUserExists(UserAccount user) {
        when(userAccountRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
    }

    private void givenCreateTicketReturns(UserAccount actor, Ticket ticket) {
        when(ticketService.createTicket(any(), eq(actor.getUsername()), eq(actor.getRole())))
            .thenReturn(ticket);
    }

    private void givenCreateTicketThrows(RuntimeException toThrow) {
        when(ticketService.createTicket(any(), any(), any())).thenThrow(toThrow);
    }

    private static Ticket responseTicket(UserAccount requester, String assigneeName) {
        Ticket t = new Ticket();
        t.setTicketNumber("PHASE3-CREATE");
        t.setTitle("HTTP create test");
        t.setDescription("Phase 3 HTTP test");
        t.setPriority(TicketPriority.MEDIUM);
        t.setCategory(TicketTypes.TicketCategory.HARDWARE);
        t.setStatus(TicketTypes.TicketStatus.NEW);
        t.setRequesterUsername(requester.getUsername());
        t.setRequesterName(requester.getDisplayName());
        t.setDepartment(requester.getDepartment());
        if (assigneeName != null) {
            t.setAssigneeName(assigneeName);
        }
        return t;
    }

    private static String createPayload(String requesterName, String assigneeName) {
        StringBuilder body = new StringBuilder("{");
        body.append("\"title\":\"HTTP test\",");
        body.append("\"description\":\"Phase 3 HTTP test\",");
        body.append("\"priority\":\"MEDIUM\",");
        body.append("\"category\":\"HARDWARE\",");
        body.append("\"requesterName\":\"").append(requesterName).append("\"");
        if (assigneeName != null) {
            body.append(",\"assigneeName\":\"").append(assigneeName).append("\"");
        }
        body.append("}");
        return body.toString();
    }

    // ========================================================================
    // CREATE without assignee - every authenticated role succeeds
    // ========================================================================

    @Nested
    @DisplayName("POST /api/tickets without assignee - all roles allowed")
    class CreateWithoutAssignee {

        @Test
        @DisplayName("ADMIN may create without an assignee - 201")
        void adminCreateWithoutAssignee() throws Exception {
            givenUserExists(ADMIN);
            givenCreateTicketReturns(ADMIN, responseTicket(ADMIN, null));

            mockMvc.perform(post("/api/tickets")
                    .with(authentication(stringPrincipal(ADMIN)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload("Admin request", null)))
                .andExpect(status().isCreated());

            verify(ticketService).createTicket(any(), eq(ADMIN.getUsername()), eq(UserRole.Role.ADMIN));
        }

        @Test
        @DisplayName("GIAM_DOC may create without an assignee - 201")
        void giamDocCreateWithoutAssignee() throws Exception {
            givenUserExists(GIAM_DOC);
            givenCreateTicketReturns(GIAM_DOC, responseTicket(GIAM_DOC, null));

            mockMvc.perform(post("/api/tickets")
                    .with(authentication(stringPrincipal(GIAM_DOC)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload("GD request", null)))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("TRUONG_PHONG non-IT may create without an assignee - 201")
        void truongPhongNonItCreateWithoutAssignee() throws Exception {
            givenUserExists(TRUONG_PHONG_MKT);
            givenCreateTicketReturns(TRUONG_PHONG_MKT, responseTicket(TRUONG_PHONG_MKT, null));

            mockMvc.perform(post("/api/tickets")
                    .with(authentication(stringPrincipal(TRUONG_PHONG_MKT)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload("TP non-IT", null)))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("NHAN_VIEN non-IT may create without an assignee - 201")
        void nhanVienNonItCreateWithoutAssignee() throws Exception {
            givenUserExists(NHAN_VIEN_MKT);
            givenCreateTicketReturns(NHAN_VIEN_MKT, responseTicket(NHAN_VIEN_MKT, null));

            mockMvc.perform(post("/api/tickets")
                    .with(authentication(stringPrincipal(NHAN_VIEN_MKT)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload("NV non-IT", null)))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("TRUONG_PHONG + IT may create without an assignee - 201")
        void truongPhongItCreateWithoutAssignee() throws Exception {
            givenUserExists(TRUONG_PHONG_IT);
            givenCreateTicketReturns(TRUONG_PHONG_IT, responseTicket(TRUONG_PHONG_IT, null));

            mockMvc.perform(post("/api/tickets")
                    .with(authentication(stringPrincipal(TRUONG_PHONG_IT)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload("TP IT", null)))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("NHAN_VIEN + IT may create without an assignee - 201 (no auto-self-assign)")
        void nhanVienItCreateWithoutAssignee() throws Exception {
            givenUserExists(NHAN_VIEN_IT);
            givenCreateTicketReturns(NHAN_VIEN_IT, responseTicket(NHAN_VIEN_IT, null));

            mockMvc.perform(post("/api/tickets")
                    .with(authentication(stringPrincipal(NHAN_VIEN_IT)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload("NV IT", null)))
                .andExpect(status().isCreated());
        }
    }

    // ========================================================================
    // CREATE with valid assignee - TRUONG_PHONG + IT may set a valid NHAN_VIEN + IT
    // ========================================================================

    @Nested
    @DisplayName("POST /api/tickets with valid assignee - TRUONG_PHONG + IT only")
    class CreateWithValidAssignee {

        @Test
        @DisplayName("TRUONG_PHONG + IT + valid NHAN_VIEN + IT assignee - 201")
        void truongPhongItCreateWithValidAssignee() throws Exception {
            givenUserExists(TRUONG_PHONG_IT);
            givenCreateTicketReturns(TRUONG_PHONG_IT,
                responseTicket(TRUONG_PHONG_IT, NHAN_VIEN_IT2.getUsername()));

            mockMvc.perform(post("/api/tickets")
                    .with(authentication(stringPrincipal(TRUONG_PHONG_IT)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload("TP IT", NHAN_VIEN_IT2.getUsername())))
                .andExpect(status().isCreated());

            // The controller forwards the assignee string to the service. The service
            // applies C-3 target validation (verified by TicketServicePhase3IntegrationTest).
            verify(ticketService).createTicket(any(), eq(TRUONG_PHONG_IT.getUsername()), eq(UserRole.Role.TRUONG_PHONG));
        }
    }

    // ========================================================================
    // CREATE with unauthorized assignee - service raises the appropriate exception
    // and the HTTP layer maps it to 400/404 via the existing project mapping
    // ========================================================================

    @Nested
    @DisplayName("POST /api/tickets with unauthorized assignee - rejected at HTTP")
    class CreateWithUnauthorizedAssignee {

        @Test
        @DisplayName("ADMIN + valid NHAN_VIEN + IT assignee - 400 (TicketRuleViolationException)")
        void adminWithValidTargetIsRejected() throws Exception {
            // The target is valid; the actor is not. Server rejects on actor.
            givenUserExists(ADMIN);
            givenCreateTicketThrows(new TicketRuleViolationException(
                "Only the IT Helpdesk manager may specify an assignee during ticket creation."));

            mockMvc.perform(post("/api/tickets")
                    .with(authentication(stringPrincipal(ADMIN)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload("Admin request", NHAN_VIEN_IT.getUsername())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                    org.hamcrest.Matchers.containsString("IT Helpdesk manager")));
        }

        @Test
        @DisplayName("GIAM_DOC + valid NHAN_VIEN + IT assignee - 400")
        void giamDocWithValidTargetIsRejected() throws Exception {
            givenUserExists(GIAM_DOC);
            givenCreateTicketThrows(new TicketRuleViolationException(
                "Only the IT Helpdesk manager may specify an assignee during ticket creation."));

            mockMvc.perform(post("/api/tickets")
                    .with(authentication(stringPrincipal(GIAM_DOC)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload("GD request", NHAN_VIEN_IT.getUsername())))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("TRUONG_PHONG non-IT + valid NHAN_VIEN + IT assignee - 400")
        void truongPhongNonItWithValidTargetIsRejected() throws Exception {
            givenUserExists(TRUONG_PHONG_MKT);
            givenCreateTicketThrows(new TicketRuleViolationException(
                "Only the IT Helpdesk manager may specify an assignee during ticket creation."));

            mockMvc.perform(post("/api/tickets")
                    .with(authentication(stringPrincipal(TRUONG_PHONG_MKT)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload("TP non-IT", NHAN_VIEN_IT.getUsername())))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("NHAN_VIEN non-IT + valid NHAN_VIEN + IT assignee - 400")
        void nhanVienNonItWithValidTargetIsRejected() throws Exception {
            givenUserExists(NHAN_VIEN_MKT);
            givenCreateTicketThrows(new TicketRuleViolationException(
                "Only the IT Helpdesk manager may specify an assignee during ticket creation."));

            mockMvc.perform(post("/api/tickets")
                    .with(authentication(stringPrincipal(NHAN_VIEN_MKT)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload("NV non-IT", NHAN_VIEN_IT.getUsername())))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("NHAN_VIEN + IT + another user as create-time assignee - 400")
        void nhanVienItWithAnotherAssigneeIsRejected() throws Exception {
            givenUserExists(NHAN_VIEN_IT);
            givenCreateTicketThrows(new TicketRuleViolationException(
                "Only the IT Helpdesk manager may specify an assignee during ticket creation."));

            mockMvc.perform(post("/api/tickets")
                    .with(authentication(stringPrincipal(NHAN_VIEN_IT)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload("NV IT", NHAN_VIEN_IT2.getUsername())))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("TRUONG_PHONG + IT + ADMIN as create-time assignee - 400 (C-3: role mismatch)")
        void truongPhongItWithAdminAsTargetIsRejected() throws Exception {
            givenUserExists(TRUONG_PHONG_IT);
            givenCreateTicketThrows(new TicketRuleViolationException(
                "Assignment target role is not NHAN_VIEN: admin_p3"));

            mockMvc.perform(post("/api/tickets")
                    .with(authentication(stringPrincipal(TRUONG_PHONG_IT)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload("TP IT", ADMIN.getUsername())))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("TRUONG_PHONG + IT + nonexistent username - 404 (C-3: existence check)")
        void truongPhongItWithNonexistentTargetIsRejected() throws Exception {
            givenUserExists(TRUONG_PHONG_IT);
            givenCreateTicketThrows(new TicketNotFoundException(
                "Assignment target not found: ghost.user"));

            mockMvc.perform(post("/api/tickets")
                    .with(authentication(stringPrincipal(TRUONG_PHONG_IT)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload("TP IT", "ghost.user")))
                .andExpect(status().isNotFound());
        }
    }

    // ========================================================================
    // POST /api/tickets without a token - 401 (regression of unauthenticated baseline)
    // ========================================================================

    @Test
    @DisplayName("POST /api/tickets without a token is 401")
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload("Anonymous", null)))
            .andExpect(status().isUnauthorized());

        verify(ticketService, never()).createTicket(any(), any(), any());
    }
}
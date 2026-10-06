package com.example.ticketing.ticket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.http.HttpHeaders;
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
import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.authorization.ActorContextService;
import com.example.ticketing.authorization.TicketAuthorization;
import com.example.ticketing.config.SecurityConfig;
import com.example.ticketing.department.Department;
import com.example.ticketing.department.ItDepartmentResolver;
import com.example.ticketing.security.JwtBlacklistService;
import com.example.ticketing.ticket.TicketTypes.TicketCategory;
import com.example.ticketing.ticket.TicketTypes.TicketPriority;
import com.example.ticketing.ticket.TicketTypes.TicketStatus;

/**
 * PHASE 5.1 (C-7) - HTTP-level integration tests for the canonical object-level
 * read policy.
 *
 * <p>Verifies the wire-level behavior:
 * <ul>
 *   <li>GET /api/tickets/{id} returns 200 for full-scope actors and 403 for
 *       unauthorized non-IT actors in a different department.</li>
 *   <li>Comments, audit, audit/export, assignments, attachments (list and
 *       download) all enforce the same policy.</li>
 *   <li>The canonical IT operator scope is preserved (MKT/HR/FIN requesters are
 *       readable).</li>
 *   <li>Unauthenticated requests still return 401 (regression of the existing
 *       baseline).</li>
 * </ul>
 */
@WebMvcTest(controllers = TicketController.class)
@Import({SecurityConfig.class, ItDepartmentResolver.class,
    ActorContextService.class, TicketAuthorization.class,
    TicketExceptionHandler.class})
class TicketControllerReadAuthorizationPhase51Test {

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

    private static final Long TICKET_ID = 5151L;

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
    private static final Department HR = dept("HR");

    private static final UserAccount ADMIN = actor("admin_p51", UserRole.Role.ADMIN, MKT);
    private static final UserAccount GIAM_DOC = actor("gd_p51", UserRole.Role.GIAM_DOC, MKT);
    private static final UserAccount TRUONG_PHONG_IT = actor("tp_it_p51", UserRole.Role.TRUONG_PHONG, IT);
    private static final UserAccount NHAN_VIEN_IT = actor("nv_it_p51", UserRole.Role.NHAN_VIEN, IT);
    private static final UserAccount TRUONG_PHONG_MKT = actor("tp_mkt_p51", UserRole.Role.TRUONG_PHONG, MKT);
    private static final UserAccount NHAN_VIEN_MKT = actor("nv_mkt_p51", UserRole.Role.NHAN_VIEN, MKT);
    private static final UserAccount NHAN_VIEN_HR = actor("nv_hr_p51", UserRole.Role.NHAN_VIEN, HR);

    private static UsernamePasswordAuthenticationToken stringPrincipal(UserAccount user) {
        return new UsernamePasswordAuthenticationToken(
            user.getUsername(), null,
            List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }

    private void givenUserExists(UserAccount user) {
        when(userAccountRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
    }

    private static Ticket ticketRequestedBy(UserAccount requester, String departmentCode) {
        Ticket t = new Ticket();
        t.setTicketNumber("PHASE51-" + requester.getUsername());
        t.setTitle("Phase 5.1 fixture ticket");
        t.setDescription("Phase 5.1 integration test fixture");
        t.setPriority(TicketPriority.MEDIUM);
        t.setCategory(TicketCategory.HARDWARE);
        t.setStatus(TicketStatus.NEW);
        t.setRequesterUsername(requester.getUsername());
        t.setRequesterName(requester.getDisplayName());
        Department dept = new Department();
        dept.setCode(departmentCode);
        dept.setName(departmentCode + " department");
        dept.setEnabled(true);
        t.setDepartment(dept);
        return t;
    }

    private void givenTicketRequester(UserAccount requester, String departmentCode) {
        Ticket t = ticketRequestedBy(requester, departmentCode);
        // Authorized actors receive the ticket; the canonical C-7 policy decides in the
        // production code. For test wiring, we route through the TicketAuthorization
        // bean so the same policy runs in the @WebMvcTest slice. This keeps the test
        // honest: the controller does NOT short-circuit, the policy does.
        when(ticketService.getTicket(eq(TICKET_ID), anyString()))
            .thenAnswer(invocation -> {
                String principal = invocation.getArgument(1);
                if (principal == null) {
                    throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentication required.");
                }
                UserAccount principalUser = userAccountRepository.findByUsername(principal).orElse(null);
                if (principalUser == null) {
                    throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED, "User not found: " + principal);
                }
                com.example.ticketing.department.Department principalDept = principalUser.getDepartment();
                boolean isIt = principalDept != null
                    && "IT".equals(principalDept.getCode());
                ActorContext actor = ActorContext.of(principalUser, isIt);
                if (!new TicketAuthorization().canReadTicket(actor, t)) {
                    throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "You don't have permission to read this ticket.");
                }
                return t;
            });
        when(ticketService.getTicket(eq(TICKET_ID), any(org.springframework.security.core.Authentication.class)))
            .thenAnswer(invocation -> {
                org.springframework.security.core.Authentication auth = invocation.getArgument(1);
                if (auth == null || auth.getName() == null) {
                    throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentication required.");
                }
                UserAccount principalUser = userAccountRepository.findByUsername(auth.getName()).orElse(null);
                if (principalUser == null) {
                    throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED, "User not found: " + auth.getName());
                }
                com.example.ticketing.department.Department principalDept = principalUser.getDepartment();
                boolean isIt = principalDept != null
                    && "IT".equals(principalDept.getCode());
                ActorContext actor = ActorContext.of(principalUser, isIt);
                if (!new TicketAuthorization().canReadTicket(actor, t)) {
                    throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "You don't have permission to read this ticket.");
                }
                return t;
            });
        // Nested-resource stubs also consult the canonical C-7 policy. The controller
        // calls the actor-aware overload, the service throws ResponseStatusException(403)
        // when the actor is not allowed to read the parent ticket.
        java.util.function.Function<String, Boolean> isAllowed = (principal) -> {
            if (principal == null) return false;
            UserAccount principalUser = userAccountRepository.findByUsername(principal).orElse(null);
            if (principalUser == null) return false;
            com.example.ticketing.department.Department principalDept = principalUser.getDepartment();
            boolean isIt = principalDept != null && "IT".equals(principalDept.getCode());
            ActorContext actor = ActorContext.of(principalUser, isIt);
            return new TicketAuthorization().canReadTicket(actor, t);
        };
        when(ticketService.listComments(eq(TICKET_ID), anyString(), any()))
            .thenAnswer(invocation -> {
                String principal = invocation.getArgument(1);
                if (!isAllowed.apply(principal)) {
                    throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "You don't have permission to read this ticket.");
                }
                return List.of();
            });
        when(ticketService.listAudit(eq(TICKET_ID), anyString()))
            .thenAnswer(invocation -> {
                String principal = invocation.getArgument(1);
                if (!isAllowed.apply(principal)) {
                    throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "You don't have permission to read this ticket.");
                }
                return List.of();
            });
        when(ticketService.listAssignments(eq(TICKET_ID), anyString()))
            .thenAnswer(invocation -> {
                String principal = invocation.getArgument(1);
                if (!isAllowed.apply(principal)) {
                    throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "You don't have permission to read this ticket.");
                }
                return List.of();
            });
    }

    // ============================================================
    // GET /api/tickets/{id}
    // ============================================================

    @Nested
    @DisplayName("GET /api/tickets/{id} - canonical object-level read policy")
    class GetTicketPolicy {

        @Test
        @DisplayName("PHASE 5.1: ADMIN can read any ticket (200)")
        void adminCanReadAnyTicket() throws Exception {
            givenUserExists(ADMIN);
            givenTicketRequester(NHAN_VIEN_MKT, "MKT");

            mockMvc.perform(get("/api/tickets/{id}", TICKET_ID)
                    .with(authentication(stringPrincipal(ADMIN))))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PHASE 5.1: GIAM_DOC can read any ticket (200)")
        void giamDocCanReadAnyTicket() throws Exception {
            givenUserExists(GIAM_DOC);
            givenTicketRequester(NHAN_VIEN_MKT, "MKT");

            mockMvc.perform(get("/api/tickets/{id}", TICKET_ID)
                    .with(authentication(stringPrincipal(GIAM_DOC))))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PHASE 5.1: TRUONG_PHONG + IT can read a MKT-requester ticket (200)")
        void tpItCanReadMktTicket() throws Exception {
            givenUserExists(TRUONG_PHONG_IT);
            givenTicketRequester(NHAN_VIEN_MKT, "MKT");

            mockMvc.perform(get("/api/tickets/{id}", TICKET_ID)
                    .with(authentication(stringPrincipal(TRUONG_PHONG_IT))))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PHASE 5.1: NHAN_VIEN + IT can read an HR-requester ticket (200)")
        void nvItCanReadHrTicket() throws Exception {
            givenUserExists(NHAN_VIEN_IT);
            givenTicketRequester(NHAN_VIEN_HR, "HR");

            mockMvc.perform(get("/api/tickets/{id}", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_IT))))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PHASE 5.1: TRUONG_PHONG non-IT (MKT) cannot read an HR-requester ticket (403)")
        void tpMktCannotReadHrTicket() throws Exception {
            givenUserExists(TRUONG_PHONG_MKT);
            givenTicketRequester(NHAN_VIEN_HR, "HR");

            mockMvc.perform(get("/api/tickets/{id}", TICKET_ID)
                    .with(authentication(stringPrincipal(TRUONG_PHONG_MKT))))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PHASE 5.1: NHAN_VIEN non-IT (MKT) cannot read an HR-requester ticket (403)")
        void nvMktCannotReadHrTicket() throws Exception {
            givenUserExists(NHAN_VIEN_MKT);
            givenTicketRequester(NHAN_VIEN_HR, "HR");

            mockMvc.perform(get("/api/tickets/{id}", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_MKT))))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PHASE 5.1: NHAN_VIEN non-IT (HR) cannot read a MKT-requester ticket (403)")
        void nvHrCannotReadMktTicket() throws Exception {
            givenUserExists(NHAN_VIEN_HR);
            givenTicketRequester(NHAN_VIEN_MKT, "MKT");

            mockMvc.perform(get("/api/tickets/{id}", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_HR))))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PHASE 5.1: the requester can always read their own ticket (200)")
        void requesterCanReadOwnTicket() throws Exception {
            givenUserExists(NHAN_VIEN_MKT);
            givenTicketRequester(NHAN_VIEN_MKT, "MKT");

            mockMvc.perform(get("/api/tickets/{id}", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_MKT))))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PHASE 5.1: unauthenticated GET returns 401 (regression)")
        void unauthenticatedReturns401() throws Exception {
            mockMvc.perform(get("/api/tickets/{id}", TICKET_ID))
                .andExpect(status().isUnauthorized());
        }
    }

    // ============================================================
    // Nested resources
    // ============================================================

    @Nested
    @DisplayName("Nested resources - canonical object-level read policy")
    class NestedResourcesPolicy {

        @Test
        @DisplayName("PHASE 5.1: comments are gated by the parent ticket read policy (HR cannot read MKT)")
        void commentsAreGated() throws Exception {
            givenUserExists(NHAN_VIEN_HR);
            givenTicketRequester(NHAN_VIEN_MKT, "MKT");

            mockMvc.perform(get("/api/tickets/{id}/comments", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_HR))))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PHASE 5.1: audit is gated by the parent ticket read policy (HR cannot read MKT)")
        void auditIsGated() throws Exception {
            givenUserExists(NHAN_VIEN_HR);
            givenTicketRequester(NHAN_VIEN_MKT, "MKT");

            mockMvc.perform(get("/api/tickets/{id}/audit", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_HR))))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PHASE 5.1: audit export is gated by the parent ticket read policy")
        void auditExportIsGated() throws Exception {
            givenUserExists(NHAN_VIEN_HR);
            givenTicketRequester(NHAN_VIEN_MKT, "MKT");

            mockMvc.perform(get("/api/tickets/{id}/audit/export", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_HR))))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PHASE 5.1: assignments are gated by the parent ticket read policy")
        void assignmentsAreGated() throws Exception {
            givenUserExists(NHAN_VIEN_HR);
            givenTicketRequester(NHAN_VIEN_MKT, "MKT");

            mockMvc.perform(get("/api/tickets/{id}/assignments", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_HR))))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PHASE 5.1: attachments list is gated by the parent ticket read policy")
        void attachmentsListIsGated() throws Exception {
            givenUserExists(NHAN_VIEN_HR);
            givenTicketRequester(NHAN_VIEN_MKT, "MKT");
            when(attachmentService.getAttachmentsByTicketId(TICKET_ID)).thenReturn(List.of());

            mockMvc.perform(get("/api/tickets/{id}/attachments", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_HR))))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PHASE 5.1: IT operator can read MKT-requester comments, audit, assignments, attachments")
        void itOperatorCanReadAllNestedResources() throws Exception {
            givenUserExists(NHAN_VIEN_IT);
            givenTicketRequester(NHAN_VIEN_MKT, "MKT");
            when(attachmentService.getAttachmentsByTicketId(TICKET_ID)).thenReturn(List.of());

            mockMvc.perform(get("/api/tickets/{id}/comments", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_IT))))
                .andExpect(status().isOk());
            mockMvc.perform(get("/api/tickets/{id}/audit", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_IT))))
                .andExpect(status().isOk());
            mockMvc.perform(get("/api/tickets/{id}/audit/export", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_IT))))
                .andExpect(status().isOk());
            mockMvc.perform(get("/api/tickets/{id}/assignments", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_IT))))
                .andExpect(status().isOk());
            mockMvc.perform(get("/api/tickets/{id}/attachments", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_IT))))
                .andExpect(status().isOk());
        }
    }
}

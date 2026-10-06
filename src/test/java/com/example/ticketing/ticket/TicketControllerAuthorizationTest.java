package com.example.ticketing.ticket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.authorization.ActorContextService;
import com.example.ticketing.authorization.TicketAuthorization;
import com.example.ticketing.config.SecurityConfig;
import com.example.ticketing.department.Department;
import com.example.ticketing.department.ItDepartmentResolver;
import com.example.ticketing.security.JwtBlacklistService;
import com.example.ticketing.ticket.TicketTypes.CommentVisibility;
import com.example.ticketing.ticket.TicketTypes.TicketPriority;
import com.example.ticketing.ticket.TicketTypes.TicketStatus;

/**
 * PHASE 0 - ENDPOINT-LEVEL AUTHORIZATION CHARACTERIZATION.
 *
 * <p>Records which actors reach which ticket endpoints today. TicketController holds a SECOND
 * authorization gate ({@code requireAssignPermission}) that is independent of the service-layer
 * predicate, and this suite pins its current behavior at the HTTP boundary.
 *
 * <p>Principal shape: a String username, exactly as {@code JwtAuthenticationFilter} produces.
 * {@code Authentication.getName()} is the only identity source used. No UserDetails is introduced.
 *
 * <p>Tests named {@code current...} or {@code ..._isPolicyGap} record behavior that CONFLICTS with
 * the canonical IT helpdesk policy. Those conflicts are the intended output of Phase 0 and are
 * recorded, not fixed.
 */
@WebMvcTest(controllers = TicketController.class)
@Import({SecurityConfig.class, ItDepartmentResolver.class,
    ActorContextService.class, TicketAuthorization.class})
class TicketControllerAuthorizationTest {

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

    private static final Long TICKET_ID = 4242L;

    // ========================================================================
    // Actor construction
    // ========================================================================

    private static Department dept(String code) {
        Department d = new Department();
        // Department exposes no id setter and must not be modified for test convenience.
        // Authorization decisions depend on the department CODE, so that is what is asserted.
        d.setCode(code);
        d.setName(code + " department");
        d.setEnabled(true);
        return d;
    }

    private static UserAccount actor(String username, UserRole.Role role, Department department) {
        UserAccount u = new UserAccount();
        // No id setter exists on the entity and none is added for test convenience. The
        // authorization decisions under test resolve from username -> role -> department code.
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

    private static final UserAccount ADMIN = actor("admin_it", UserRole.Role.ADMIN, MKT);
    private static final UserAccount GIAM_DOC = actor("giamdoc_it", UserRole.Role.GIAM_DOC, MKT);
    private static final UserAccount TRUONG_PHONG_IT = actor("tp_it", UserRole.Role.TRUONG_PHONG, IT);
    private static final UserAccount NHAN_VIEN_IT = actor("nv_it", UserRole.Role.NHAN_VIEN, IT);
    private static final UserAccount NHAN_VIEN_IT2 = actor("nv_it2", UserRole.Role.NHAN_VIEN, IT);
    private static final UserAccount TRUONG_PHONG_MKT = actor("tp_mkt", UserRole.Role.TRUONG_PHONG, MKT);
    private static final UserAccount NHAN_VIEN_MKT = actor("nv_mkt", UserRole.Role.NHAN_VIEN, MKT);

    /** Mirrors JwtAuthenticationFilter: principal is the String username. */
    private static UsernamePasswordAuthenticationToken stringPrincipal(UserAccount user) {
        return new UsernamePasswordAuthenticationToken(
            user.getUsername(), null,
            List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }

    private void givenUserExists(UserAccount user) {
        when(userAccountRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
    }

    private void givenTicket() {
        Ticket t = new Ticket();
        // The entity exposes no id setter. Response mapping only needs the descriptive fields;
        // the id under test is the path variable TICKET_ID.
        t.setTicketNumber("PHASE0-00001");
        t.setTitle("Printer offline");
        t.setDescription("Characterization fixture");
        t.setPriority(TicketPriority.MEDIUM);
        t.setCategory(TicketTypes.TicketCategory.HARDWARE);
        t.setStatus(TicketStatus.NEW);
        t.setRequesterUsername(NHAN_VIEN_MKT.getUsername());
        t.setRequesterName("MKT requester");
        t.setDepartment(MKT);
        when(ticketService.getTicket(TICKET_ID)).thenReturn(t);
        when(ticketService.assignTicket(anyLong(), any(), any(), any(), any())).thenReturn(t);
        when(ticketService.unassignTicket(anyLong(), any(), any(), any())).thenReturn(t);
        when(ticketService.startProgress(anyLong(), any(), any(), any())).thenReturn(t);
        when(ticketService.updateStatus(anyLong(), any(), any(), any(), any(), any(), any()))
            .thenReturn(t);
        when(ticketService.updatePriority(anyLong(), any(), any(), any(), any())).thenReturn(t);
        when(ticketService.cancelTicket(anyLong(), any(), any(), any())).thenReturn(t);
        when(ticketService.closeTicket(anyLong(), any(), any())).thenReturn(t);
        // addComment must be stubbed: returning null makes TicketCommentResponse.from(...) NPE.
        TicketComment comment = new TicketComment();
        comment.setTicketId(TICKET_ID);
        comment.setBody("characterization comment");
        comment.setVisibility(CommentVisibility.PUBLIC);
        when(ticketService.addComment(anyLong(), any(), any(), any(), any())).thenReturn(comment);
    }

    // ========================================================================
    // Unauthenticated baseline
    // ========================================================================

    @Nested
    @DisplayName("Unauthenticated requests are rejected before any authorization logic")
    class Unauthenticated {

        @Test
        @DisplayName("CURRENT: GET /{id} without a token is 401")
        void getTicketRequiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/tickets/{id}", TICKET_ID))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("CURRENT: POST /{id}/assign/me without a token is 401")
        void assignToMeRequiresAuthentication() throws Exception {
            mockMvc.perform(post("/api/tickets/{id}/assign/me", TICKET_ID))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("CURRENT: PATCH /{id}/status without a token is 401")
        void updateStatusRequiresAuthentication() throws Exception {
            mockMvc.perform(patch("/api/tickets/{id}/status", TICKET_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isUnauthorized());
        }
    }

    // ========================================================================
    // Assignment gate: TicketController.requireAssignPermission
    // ========================================================================

    @Nested
    @DisplayName("POST /{id}/assign/me - canonical TicketAuthorization.canReceiveTicket")
    class AssignToMeGate {

        @Test
        @DisplayName("CANONICAL: ADMIN is NOT an IT Helpdesk operator - cannot self-receive (C-1 fixed)")
        void adminCannotSelfReceive_canonicalPolicy() throws Exception {
            givenUserExists(ADMIN);
            givenTicket();

            mockMvc.perform(post("/api/tickets/{id}/assign/me", TICKET_ID)
                    .with(authentication(stringPrincipal(ADMIN))))
                .andExpect(status().isForbidden());

            verify(ticketService, never()).assignTicket(anyLong(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("CANONICAL: GIAM_DOC is NOT an IT Helpdesk operator - cannot self-receive (C-1 fixed)")
        void giamDocCannotSelfReceive_canonicalPolicy() throws Exception {
            givenUserExists(GIAM_DOC);
            givenTicket();

            mockMvc.perform(post("/api/tickets/{id}/assign/me", TICKET_ID)
                    .with(authentication(stringPrincipal(GIAM_DOC))))
                .andExpect(status().isForbidden());

            verify(ticketService, never()).assignTicket(anyLong(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("CANONICAL: TRUONG_PHONG + IT may self-receive (matches intended policy)")
        void truongPhongItMayAssignToMe() throws Exception {
            givenUserExists(TRUONG_PHONG_IT);
            givenTicket();

            mockMvc.perform(post("/api/tickets/{id}/assign/me", TICKET_ID)
                    .with(authentication(stringPrincipal(TRUONG_PHONG_IT))))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("CANONICAL: NHAN_VIEN + IT may self-receive (canReceiveTicket = true)")
        void nhanVienItMaySelfAssign() throws Exception {
            givenUserExists(NHAN_VIEN_IT);
            givenTicket();

            mockMvc.perform(post("/api/tickets/{id}/assign/me", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_IT))))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("CANONICAL: TRUONG_PHONG non-IT is forbidden")
        void nonItTruongPhongIsForbidden() throws Exception {
            givenUserExists(TRUONG_PHONG_MKT);
            givenTicket();

            mockMvc.perform(post("/api/tickets/{id}/assign/me", TICKET_ID)
                    .with(authentication(stringPrincipal(TRUONG_PHONG_MKT))))
                .andExpect(status().isForbidden());

            verify(ticketService, never()).assignTicket(anyLong(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("CANONICAL: NHAN_VIEN non-IT is forbidden")
        void nonItNhanVienIsForbidden() throws Exception {
            givenUserExists(NHAN_VIEN_MKT);
            givenTicket();

            mockMvc.perform(post("/api/tickets/{id}/assign/me", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_MKT))))
                .andExpect(status().isForbidden());

            verify(ticketService, never()).assignTicket(anyLong(), any(), any(), any(), any());
        }
    }

    // ========================================================================
    // Assignment target is forwarded unvalidated
    // ========================================================================

    @Nested
    @DisplayName("PATCH /{id}/assignee - current target handling")
    class UpdateAssigneeTarget {

        @Test
        @DisplayName("PHASE 3: the controller forwards the target string to the service; the service applies C-3 target validation")
        void controllerForwardsAssigneeToService_canonicalPolicy() throws Exception {
            // Phase 3: the controller still forwards the client-supplied target string, but
            // C-3 target validation lives in TicketService. The controller preserves the
            // existing wire format (assigneeName) so the rejection is decided server-side
            // and the client cannot bypass the policy.
            givenUserExists(TRUONG_PHONG_IT);
            givenTicket();

            mockMvc.perform(patch("/api/tickets/{id}/assignee", TICKET_ID)
                    .with(authentication(stringPrincipal(TRUONG_PHONG_IT)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"assigneeName\":\"ghost.user\"}"))
                .andExpect(status().isOk());

            // The controller passes the target string through. The service applies C-3
            // target validation (verified by the service-level test suite).
            verify(ticketService).assignTicket(
                eq(TICKET_ID), eq("ghost.user"), eq(UserRole.Role.TRUONG_PHONG), isNull(), eq("tp_it"));
        }

        @Test
        @DisplayName("CANONICAL: an IT-department ADMIN is NOT an IT Helpdesk operator - cannot set assignee (C-1 fixed)")
        void adminInItCannotSetAssignee_canonicalPolicy() throws Exception {
            UserAccount adminInIt = actor("admin_it", UserRole.Role.ADMIN, IT);
            givenUserExists(adminInIt);
            givenTicket();

            mockMvc.perform(patch("/api/tickets/{id}/assignee", TICKET_ID)
                    .with(authentication(stringPrincipal(adminInIt)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"assigneeName\":\"anyone.at.all\"}"))
                .andExpect(status().isForbidden());

            verify(ticketService, never()).assignTicket(anyLong(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("CURRENT: a forbidden actor is rejected before the service is reached")
        void forbiddenActorNeverReachesTheService() throws Exception {
            givenUserExists(NHAN_VIEN_MKT);
            givenTicket();

            mockMvc.perform(patch("/api/tickets/{id}/assignee", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_MKT)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"assigneeName\":\"someone\"}"))
                .andExpect(status().isForbidden());

            verify(ticketService, never()).assignTicket(anyLong(), any(), any(), any(), any());
        }
    }

    // ========================================================================
    // Status endpoints
    // ========================================================================

    @Nested
    @DisplayName("Status endpoints - controller forwards to service, canonical gate is at the service")
    class StatusEndpoints {

        @Test
        @DisplayName("CANONICAL: ADMIN's PATCH /{id}/status reaches the service, which now denies (C-4 fixed at the service)")
        void adminMayPatchStatusReachesServiceWhichDenies_canonicalPolicy() throws Exception {
            // The controller does not gate status updates itself; the canonical processing gate
            // is enforced inside TicketService.updateStatus. This test pins the controller's
            // forwarding contract. The service-level denial is covered by the service suite.
            givenUserExists(ADMIN);
            givenTicket();

            mockMvc.perform(patch("/api/tickets/{id}/status", TICKET_ID)
                    .with(authentication(stringPrincipal(ADMIN)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());

            verify(ticketService).updateStatus(eq(TICKET_ID), eq(TicketStatus.IN_PROGRESS),
                eq(UserRole.Role.ADMIN), isNull(), eq("admin_it"), eq("admin_it"), eq(null));
        }

        @Test
        @DisplayName("CANONICAL: a non-IT TRUONG_PHONG's PATCH /{id}/status reaches the service, which now denies (C-5 fixed at the service)")
        void nonItTruongPhongReachesUpdateStatusWhichDenies_canonicalPolicy() throws Exception {
            // The controller intentionally forwards status requests to the service. The canonical
            // processing gate inside TicketService.updateStatus denies non-IT TRUONG_PHONG.
            givenUserExists(TRUONG_PHONG_MKT);
            givenTicket();

            mockMvc.perform(patch("/api/tickets/{id}/status", TICKET_ID)
                    .with(authentication(stringPrincipal(TRUONG_PHONG_MKT)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());

            verify(ticketService).updateStatus(eq(TICKET_ID), eq(TicketStatus.IN_PROGRESS),
                eq(UserRole.Role.TRUONG_PHONG), isNull(), eq("tp_mkt"), eq("tp_mkt"), eq(null));
        }

        @Test
        @DisplayName("CANONICAL: POST /{id}/start is reachable by an IT staff account (matches intended policy)")
        void itStaffMayStartProgress() throws Exception {
            givenUserExists(NHAN_VIEN_IT);
            givenTicket();

            mockMvc.perform(post("/api/tickets/{id}/start", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_IT))))
                .andExpect(status().isOk());

            verify(ticketService).startProgress(
                eq(TICKET_ID), eq(UserRole.Role.NHAN_VIEN), isNull(), eq("nv_it"));
        }

        @Test
        @DisplayName("CANONICAL: POST /{id}/cancel remains reachable; ADMIN-only rule is preserved at the service")
        void cancelReachesServiceAndServiceOwnsTheAdminOnlyRule() throws Exception {
            givenUserExists(NHAN_VIEN_IT);
            givenTicket();

            // The endpoint itself is authenticated-only. The ADMIN-only rule is enforced inside
            // TicketService.cancelTicket, so a non-ADMIN call fails there, not here.
            mockMvc.perform(post("/api/tickets/{id}/cancel", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_IT))))
                .andExpect(status().isOk());

            verify(ticketService).cancelTicket(
                eq(TICKET_ID), eq(UserRole.Role.NHAN_VIEN), eq("nv_it"), eq(null));
        }
    }

    // ========================================================================
    // Object-level read authorization
    // ========================================================================

    @Nested
    @DisplayName("Object-level read - CURRENT behavior")
    class ObjectLevelRead {

        @Test
        @DisplayName("CURRENT: GET /{id} accepts no actor and enforces no ownership check")
        void getTicketEnforcesNoObjectLevelCheck_isPolicyGapC7() throws Exception {
            // The handler signature takes only @PathVariable Long id - no Authentication at all.
            givenUserExists(NHAN_VIEN_MKT);
            givenTicket();

            mockMvc.perform(get("/api/tickets/{id}", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_MKT))))
                .andExpect(status().isOk());

            // A non-IT staff account reads a ticket belonging to the IT department's workflow.
            verify(ticketService).getTicket(TICKET_ID);
        }

        @Test
        @DisplayName("CURRENT: any authenticated role may read any ticket by id")
        void everyAuthenticatedRoleMayReadAnyTicket() throws Exception {
            for (UserAccount reader : List.of(
                    ADMIN, GIAM_DOC, TRUONG_PHONG_IT, NHAN_VIEN_IT, TRUONG_PHONG_MKT, NHAN_VIEN_MKT)) {
                givenUserExists(reader);
                givenTicket();

                mockMvc.perform(get("/api/tickets/{id}", TICKET_ID)
                        .with(authentication(stringPrincipal(reader))))
                    .andExpect(status().isOk());
            }
        }
    }

    // ========================================================================
    // Comments
    // ========================================================================

    @Nested
    @DisplayName("Comment endpoints - CURRENT behavior")
    class Comments {

        @Test
        @DisplayName("CURRENT: NHAN_VIEN + IT may post a PUBLIC comment")
        void itStaffMayPostPublicComment() throws Exception {
            givenUserExists(NHAN_VIEN_IT);
            givenTicket();

            mockMvc.perform(post("/api/tickets/{id}/comments", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_IT)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"visibility\":\"PUBLIC\",\"body\":\"On it\"}"))
                .andExpect(status().isCreated());

            verify(ticketService).addComment(eq(TICKET_ID), eq(CommentVisibility.PUBLIC),
                eq("On it"), eq(UserRole.Role.NHAN_VIEN), eq("nv_it"));
        }

        @Test
        @DisplayName("CANONICAL: the controller forwards INTERNAL comments; the canonical policy (canAddInternalComment) is the gate at the service layer")
        void itStaffPostingInternalCommentReachesServiceWhichAuthorizes() throws Exception {
            // H-4 was fixed in Phase 2.1: NHAN_VIEN + IT may now post INTERNAL comments. The
            // controller does not gate comments itself; the canonical canAddInternalComment is
            // enforced inside TicketService.addComment. The controller test asserts the
            // forwarding contract.
            givenUserExists(NHAN_VIEN_IT);
            givenTicket();

            mockMvc.perform(post("/api/tickets/{id}/comments", TICKET_ID)
                    .with(authentication(stringPrincipal(NHAN_VIEN_IT)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"visibility\":\"INTERNAL\",\"body\":\"internal\"}"))
                .andExpect(status().isCreated());

            verify(ticketService).addComment(eq(TICKET_ID), eq(CommentVisibility.INTERNAL),
                eq("internal"), eq(UserRole.Role.NHAN_VIEN), eq("nv_it"));
        }
    }
}

package com.example.ticketing.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.ticket.TicketTypes.CommentVisibility;
import com.example.ticketing.ticket.TicketTypes.TicketPriority;
import com.example.ticketing.ticket.TicketTypes.TicketStatus;

/**
 * PHASE 2.1 - PRODUCTION-LEVEL INTEGRATION TESTS for the canonical authorization policy.
 *
 * <p>This suite exercises the canonical {@link com.example.ticketing.authorization.ActorContextService}
 * and {@link com.example.ticketing.authorization.TicketAuthorization} components through the real
 * {@code TicketService} production code path. Each test asserts an end-to-end outcome (a successful
 * state change or a {@link TicketRuleViolationException}) so the wiring of Phase 2.1 is verified
 * for the matrix documented in {@code TicketAuthorization}.
 *
 * <p>Coverage matrix (per the brief):
 * <ul>
 *   <li>{@code §23 - Processing integration test}: ADMIN and GIAM_DOC cannot process.</li>
 *   <li>{@code §24 - Assignment integration test}: TRUONG_PHONG + IT may assign others;
 *       NHAN_VIEN + IT may self-receive; everyone else is denied.</li>
 *   <li>{@code §25 - Cross-department ticket integration test}: an IT operator can process
 *       a requester-from-MKT ticket (the canonical "department = requester, not operator").</li>
 *   <li>{@code §22 - Self-receive integration test}: the canonical distinction.</li>
 *   <li>{@code §26 - Internal comments integration test}: H-4 fix is end-to-end.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketServicePhase21IntegrationTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketAuthorizationFixtures fixtures;

    // ========================================================================
    // §23 - Processing integration test
    // ADMIN / GIAM_DOC cannot process tickets
    // ========================================================================

    @Nested
    @DisplayName("§23 Processing integration: ADMIN / GIAM_DOC are NOT helpdesk operators")
    class ProcessingIntegration {

        @Test
        @DisplayName("ADMIN: attempt to start progress on a MKT-requester ticket - expected denial (TicketRuleViolationException)")
        void adminCannotStartProgress_onMktRequesterTicket() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount admin = fixtures.admin();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.startProgress(
                    saved.getId(), admin.getRole(),
                    admin.getDepartmentId(), admin.getUsername()),
                "ADMIN is not an IT Helpdesk operator and must be denied at the canonical gate");
        }

        @Test
        @DisplayName("GIAM_DOC: attempt to start progress on a MKT-requester ticket - expected denial")
        void giamDocCannotStartProgress_onMktRequesterTicket() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount giamDoc = fixtures.giamDoc();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.startProgress(
                    saved.getId(), giamDoc.getRole(),
                    giamDoc.getDepartmentId(), giamDoc.getUsername()),
                "GIAM_DOC is not an IT Helpdesk operator and must be denied at the canonical gate");
        }

        @Test
        @DisplayName("NHAN_VIEN + IT may process a MKT-requester ticket end-to-end (department is requester's, not operator's)")
        void nhanVienItMayProcessMktRequesterTicketEndToEnd() {
            // This single test exercises §25 + §23 + the full happy path of an IT operator
            // working on a cross-department ticket.
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount itStaff = fixtures.nhanVienIT();

            Ticket inProgress = ticketService.startProgress(
                saved.getId(), itStaff.getRole(),
                itStaff.getDepartmentId(), itStaff.getUsername());
            assertEquals(TicketStatus.IN_PROGRESS, inProgress.getStatus(),
                "NHAN_VIEN + IT must be permitted to start progress on a MKT ticket");

            Ticket resolved = ticketService.resolveTicket(
                saved.getId(), itStaff.getRole(),
                itStaff.getDepartmentId(), itStaff.getUsername(),
                "Replaced the cable");
            assertEquals(TicketStatus.RESOLVED, resolved.getStatus(),
                "NHAN_VIEN + IT must be permitted to resolve");
        }

        @Test
        @DisplayName("NHAN_VIEN non-IT is NOT an IT Helpdesk operator - denied")
        void nhanVienNonItCannotStartProgress() {
            Ticket saved = ticketRepository.save(fixtures.itRequestedTicket());
            UserAccount nonItStaff = fixtures.nhanVienMkt();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.startProgress(
                    saved.getId(), nonItStaff.getRole(),
                    nonItStaff.getDepartmentId(), nonItStaff.getUsername()),
                "NHAN_VIEN non-IT must be denied at the canonical canProcessTickets gate");
        }

        @Test
        @DisplayName("TRUONG_PHONG non-IT is NOT an IT Helpdesk operator - denied")
        void truongPhongNonItCannotStartProgress() {
            Ticket saved = ticketRepository.save(fixtures.itRequestedTicket());
            UserAccount nonItManager = fixtures.truongPhongMkt();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.startProgress(
                    saved.getId(), nonItManager.getRole(),
                    nonItManager.getDepartmentId(), nonItManager.getUsername()),
                "TRUONG_PHONG non-IT must be denied at the canonical canProcessTickets gate");
        }
    }

    // ========================================================================
    // §24 - Assignment integration test
    // TRUONG_PHONG + IT can assign others; NHAN_VIEN + IT can self-receive only;
    // ADMIN / GIAM_DOC / non-IT denied
    // ========================================================================

    @Nested
    @DisplayName("§24 Assignment integration: only IT Helpdesk operators may receive; only TRUONG_PHONG + IT may assign others")
    class AssignmentIntegration {

        @Test
        @DisplayName("TRUONG_PHONG + IT assigns to NHAN_VIEN + IT - the canonical assignment path")
        void truongPhongItAssignsNhanVienIt() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount itManager = fixtures.truongPhongIT();
            UserAccount itStaff = fixtures.nhanVienIT();

            Ticket assigned = ticketService.assignTicket(
                saved.getId(), itStaff.getUsername(), itManager.getRole(),
                itManager.getDepartmentId(), itManager.getUsername());

            assertEquals(TicketStatus.ASSIGNED, assigned.getStatus());
            assertEquals(itStaff.getUsername(), assigned.getAssigneeName(),
                "TRUONG_PHONG + IT assigns NHAN_VIEN + IT - matches intended behavior");
        }

        @Test
        @DisplayName("NHAN_VIEN + IT self-assigns (canReceiveTicket) - allowed")
        void nhanVienItSelfReceives() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount itStaff = fixtures.nhanVienIT();

            Ticket self = ticketService.assignTicket(
                saved.getId(), itStaff.getUsername(), itStaff.getRole(),
                itStaff.getDepartmentId(), itStaff.getUsername());

            assertEquals(TicketStatus.ASSIGNED, self.getStatus());
            assertEquals(itStaff.getUsername(), self.getAssigneeName());
        }

        @Test
        @DisplayName("NHAN_VIEN + IT assigns to a peer - denied by canAssignOthers")
        void nhanVienItCannotAssignPeer() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.nhanVienIT();
            UserAccount peer = fixtures.nhanVienIT2();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), peer.getUsername(), actor.getRole(),
                    actor.getDepartmentId(), actor.getUsername()),
                "NHAN_VIEN + IT is denied under canAssignOthers (peer-assignment is denied)");
        }

        @Test
        @DisplayName("ADMIN cannot assign others - denied by canAssignOthers")
        void adminCannotAssignOthers() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount admin = fixtures.admin();
            UserAccount itStaff = fixtures.nhanVienIT();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), itStaff.getUsername(), admin.getRole(),
                    admin.getDepartmentId(), admin.getUsername()),
                "ADMIN must be denied under canAssignOthers");
        }

        @Test
        @DisplayName("GIAM_DOC cannot assign others - denied by canAssignOthers")
        void giamDocCannotAssignOthers() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount giamDoc = fixtures.giamDoc();
            UserAccount itStaff = fixtures.nhanVienIT();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), itStaff.getUsername(), giamDoc.getRole(),
                    giamDoc.getDepartmentId(), giamDoc.getUsername()),
                "GIAM_DOC must be denied under canAssignOthers");
        }

        @Test
        @DisplayName("Non-IT TRUONG_PHONG cannot assign - denied by canAssignOthers")
        void nonItTruongPhongCannotAssign() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount nonItManager = fixtures.truongPhongMkt();
            UserAccount itStaff = fixtures.nhanVienIT();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), itStaff.getUsername(), nonItManager.getRole(),
                    nonItManager.getDepartmentId(), nonItManager.getUsername()),
                "TRUONG_PHONG non-IT must be denied under canAssignOthers");
        }

        @Test
        @DisplayName("Non-IT NHAN_VIEN cannot self-receive - denied by canReceiveTicket")
        void nonItNhanVienCannotSelfReceive() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount nonItStaff = fixtures.nhanVienMkt();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), nonItStaff.getUsername(), nonItStaff.getRole(),
                    nonItStaff.getDepartmentId(), nonItStaff.getUsername()),
                "NHAN_VIEN non-IT must be denied under canReceiveTicket");
        }
    }

    // ========================================================================
    // §25 - Cross-department ticket integration test
    // An IT Helpdesk operator can process a ticket from any company's department
    // ========================================================================

    @Nested
    @DisplayName("§25 Cross-department: IT operators process tickets from any department; department is the requester's, not the operator's")
    class CrossDepartmentIntegration {

        @Test
        @DisplayName("NHAN_VIEN + IT processes a MKT-requester ticket end-to-end")
        void itOperatorProcessesMktTicket() {
            // MKT-requester, IT-operator: the canonical "department is the requester's"
            // invariant. The IT operator must NOT be blocked by requester.department.
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            assertEquals("MKT", saved.getDepartment().getCode(),
                "sanity: ticket.department == requester.department == MKT");

            UserAccount itStaff = fixtures.nhanVienIT();
            assertEquals("IT", itStaff.getDepartment().getCode(),
                "sanity: operator is in IT");

            Ticket updated = ticketService.updatePriority(
                saved.getId(), TicketPriority.HIGH, itStaff.getRole(),
                itStaff.getDepartmentId(), itStaff.getUsername());
            assertEquals(TicketPriority.HIGH, updated.getPriority(),
                "IT operator may raise priority on a MKT-requester ticket");

            Ticket advanced = ticketService.startProgress(
                saved.getId(), itStaff.getRole(),
                itStaff.getDepartmentId(), itStaff.getUsername());
            assertEquals(TicketStatus.IN_PROGRESS, advanced.getStatus(),
                "IT operator may start progress on a MKT-requester ticket");
        }

        @Test
        @DisplayName("TRUONG_PHONG + IT assigns a NHAN_VIEN + IT to a MKT-requester ticket")
        void itManagerAssignsItStaffForMktTicket() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());

            UserAccount itManager = fixtures.truongPhongIT();
            UserAccount itStaff = fixtures.nhanVienIT();

            Ticket assigned = ticketService.assignTicket(
                saved.getId(), itStaff.getUsername(), itManager.getRole(),
                itManager.getDepartmentId(), itManager.getUsername());

            assertEquals(itStaff.getUsername(), assigned.getAssigneeName(),
                "IT Helpdesk manager assigns IT staff to a MKT ticket");
        }
    }

    // ========================================================================
    // §22 - Self-receive vs assign-others integration test
    // ========================================================================

    @Nested
    @DisplayName("§22 Self-receive vs assign-others: the canonical distinction")
    class SelfReceiveVsAssignOthersIntegration {

        @Test
        @DisplayName("NHAN_VIEN + IT: self-receive allowed, assign-others denied - the two paths are distinct")
        void nhanVienItSelfReceiveVsAssignOthers() {
            UserAccount actor = fixtures.nhanVienIT();
            UserAccount peer = fixtures.nhanVienIT2();

            Ticket selfTicket = ticketRepository.save(fixtures.mktRequestedTicket());
            Ticket peerTicket = ticketRepository.save(fixtures.mktRequestedTicket());

            Ticket self = ticketService.assignTicket(
                selfTicket.getId(), actor.getUsername(), actor.getRole(),
                actor.getDepartmentId(), actor.getUsername());
            assertEquals(actor.getUsername(), self.getAssigneeName(),
                "self-receive is permitted under canReceiveTicket");

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    peerTicket.getId(), peer.getUsername(), actor.getRole(),
                    actor.getDepartmentId(), actor.getUsername()),
                "assign-to-another is denied under canAssignOthers");
        }
    }

    // ========================================================================
    // §26 - Internal comments integration test
    // H-4 fixed: NHAN_VIEN + IT may add AND view INTERNAL comments
    // ========================================================================

    @Nested
    @DisplayName("§26 Internal comments: H-4 fixed end-to-end")
    class InternalCommentsIntegration {

        @Test
        @DisplayName("NHAN_VIEN + IT may add an INTERNAL comment")
        void nhanVienItMayAddInternalComment() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount itStaff = fixtures.nhanVienIT();

            TicketComment comment = ticketService.addComment(
                saved.getId(), CommentVisibility.INTERNAL,
                "IT staff internal note",
                itStaff.getRole(), itStaff.getUsername());

            assertNotNull(comment.getId(), "comment must be persisted");
            assertEquals(CommentVisibility.INTERNAL, comment.getVisibility(),
                "the visibility is preserved as INTERNAL");
        }

        @Test
        @DisplayName("NHAN_VIEN + IT may view INTERNAL comments authored by a TRUONG_PHONG + IT")
        void nhanVienItMayViewInternalComments() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount itManager = fixtures.truongPhongIT();
            UserAccount itStaff = fixtures.nhanVienIT();

            ticketService.addComment(
                saved.getId(), CommentVisibility.INTERNAL,
                "manager-only internal note",
                itManager.getRole(), itManager.getUsername());

            List<TicketComment> asItStaff = ticketService.listComments(
                saved.getId(), itStaff.getUsername(), itStaff.getRole(), null);

            assertTrue(asItStaff.stream().anyMatch(c -> c.getVisibility() == CommentVisibility.INTERNAL),
                "NHAN_VIEN + IT must now see INTERNAL comments under canViewInternalComments");
        }

        @Test
        @DisplayName("NHAN_VIEN non-IT is denied adding an INTERNAL comment")
        void nonItNhanVienCannotAddInternalComment() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount nonItStaff = fixtures.nhanVienMkt();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.addComment(
                    saved.getId(), CommentVisibility.INTERNAL,
                    "non-IT staff internal note",
                    nonItStaff.getRole(), nonItStaff.getUsername()),
                "NHAN_VIEN non-IT must be denied adding INTERNAL comments");
        }

        @Test
        @DisplayName("NHAN_VIEN non-IT is filtered out from INTERNAL comments on list")
        void nonItNhanVienCannotViewInternalComments() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount itManager = fixtures.truongPhongIT();
            UserAccount nonItStaff = fixtures.nhanVienMkt();

            ticketService.addComment(
                saved.getId(), CommentVisibility.INTERNAL,
                "internal note from manager",
                itManager.getRole(), itManager.getUsername());

            List<TicketComment> asNonIt = ticketService.listComments(
                saved.getId(), nonItStaff.getUsername(), nonItStaff.getRole(), null);

            assertTrue(asNonIt.stream().noneMatch(c -> c.getVisibility() == CommentVisibility.INTERNAL),
                "NHAN_VIEN non-IT must NOT see INTERNAL comments");
        }
    }
}
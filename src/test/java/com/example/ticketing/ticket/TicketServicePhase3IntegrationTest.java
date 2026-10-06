package com.example.ticketing.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.auth.UserRole;
import com.example.ticketing.department.Department;
import com.example.ticketing.department.DepartmentRepository;
import com.example.ticketing.department.ItDepartmentResolver;
import com.example.ticketing.ticket.TicketService.AssignmentTargetValidation;
import com.example.ticketing.ticket.TicketTypes.TicketPriority;

/**
 * PHASE 3 - CANONICAL ASSIGNMENT TARGET VALIDATION (C-3) and CREATE-TIME
 * ASSIGNEE POLICY (H-3).
 *
 * <p>This suite covers the Phase 3 brief \u00a719 test matrix:
 *
 * <ul>
 *   <li>\u00a719.1 CREATE: every authenticated actor may create without an assignee.</li>
 *   <li>\u00a719.2 CREATE + ASSIGNEE: TRUONG_PHONG + IT may create with a target, but only
 *       when the target is an enabled NHAN_VIEN in IT.</li>
 *   <li>\u00a719.3 Unauthorized create-time assignee: every other role is denied.</li>
 *   <li>\u00a720 RECEIVE: NHAN_VIEN + IT may self-receive; everyone else is denied.</li>
 *   <li>\u00a721 ASSIGN-OTHERS: TRUONG_PHONG + IT may assign others (enabled NHAN_VIEN + IT
 *       only); every other role is denied.</li>
 *   <li>\u00a722 ASSIGNEE CONSISTENCY: assignee_id and assignee_name stay synchronized.</li>
 *   <li>\u00a723 CROSS-DEPARTMENT: IT Helpdesk may handle tickets from any requester
 *       department (MKT, HR, Finance, etc.).</li>
 *   <li>\u00a724 NEGATIVE SECURITY: a malicious client cannot bypass the policy by
 *       supplying a valid target with an invalid actor.</li>
 * </ul>
 *
 * <p>All assertions use the real TicketService production code path; this is an
 * end-to-end integration test that exercises the Phase 3 implementation as the
 * application would.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketServicePhase3IntegrationTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketAuthorizationFixtures fixtures;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ItDepartmentResolver itDepartmentResolver;

    // ========================================================================
    // \u00a719.1 - CREATE: every authenticated actor may create without an assignee
    // ========================================================================

    @Nested
    @DisplayName("\u00a719.1 CREATE without assignee - all roles allowed")
    class CreateWithoutAssignee {

        @Test
        @DisplayName("ADMIN may create a ticket without an assignee")
        void adminCreateWithoutAssignee() {
            Ticket ticket = fixtures.mktRequestedTicket("Admin request", TicketPriority.LOW);
            Ticket created = ticketService.createTicket(
                ticket, fixtures.admin().getUsername(), fixtures.admin().getRole());
            assertNotNull(created.getId());
            assertNull(created.getAssigneeName(), "ADMIN must not be auto-assigned");
            assertNull(created.getAssignee(), "ADMIN must not be auto-assigned (FK stays null)");
        }

        @Test
        @DisplayName("GIAM_DOC may create a ticket without an assignee")
        void giamDocCreateWithoutAssignee() {
            Ticket ticket = fixtures.mktRequestedTicket("GD request", TicketPriority.LOW);
            Ticket created = ticketService.createTicket(
                ticket, fixtures.giamDoc().getUsername(), fixtures.giamDoc().getRole());
            assertNotNull(created.getId());
            assertNull(created.getAssigneeName(), "GIAM_DOC must not be auto-assigned");
            assertNull(created.getAssignee(), "GIAM_DOC must not be auto-assigned (FK stays null)");
        }

        @Test
        @DisplayName("TRUONG_PHONG non-IT may create a ticket without an assignee")
        void truongPhongNonItCreateWithoutAssignee() {
            Ticket ticket = fixtures.mktRequestedTicket("TP non-IT request", TicketPriority.LOW);
            Ticket created = ticketService.createTicket(
                ticket, fixtures.truongPhongMkt().getUsername(), fixtures.truongPhongMkt().getRole());
            assertNotNull(created.getId());
            assertNull(created.getAssigneeName());
        }

        @Test
        @DisplayName("NHAN_VIEN non-IT may create a ticket without an assignee")
        void nhanVienNonItCreateWithoutAssignee() {
            Ticket ticket = fixtures.mktRequestedTicket("NV non-IT request", TicketPriority.LOW);
            Ticket created = ticketService.createTicket(
                ticket, fixtures.nhanVienMkt().getUsername(), fixtures.nhanVienMkt().getRole());
            assertNotNull(created.getId());
            assertNull(created.getAssigneeName());
        }

        @Test
        @DisplayName("TRUONG_PHONG + IT may create a ticket without an assignee (starts unassigned)")
        void truongPhongItCreateWithoutAssignee() {
            Ticket ticket = fixtures.mktRequestedTicket("TP IT request", TicketPriority.LOW);
            Ticket created = ticketService.createTicket(
                ticket, fixtures.truongPhongIT().getUsername(), fixtures.truongPhongIT().getRole());
            assertNotNull(created.getId());
            // Phase 3: TRUONG_PHONG + IT is not auto-self-assigned at creation time.
            // Self-receive (and assignment of others) happens through /assign/me and
            // /assignee respectively.
            assertNull(created.getAssigneeName(),
                "TRUONG_PHONG + IT must NOT be auto-self-assigned at creation time");
            assertNull(created.getAssignee(),
                "TRUONG_PHONG + IT auto-self-assignment FK must be null at creation time");
        }

        @Test
        @DisplayName("NHAN_VIEN + IT may create a ticket without an assignee - ticket starts unassigned (H-3 fix)")
        void nhanVienItCreateWithoutAssignee() {
            // Phase 3: no auto-self-assign on create. The canonical self-receive flow is
            // /assign/me (POST \u00a76). This test pins the canonical behavior at creation
            // time; the corresponding self-receive test asserts the operator can still
            // take the ticket via /assign/me.
            Ticket ticket = fixtures.mktRequestedTicket("NV IT request", TicketPriority.LOW);
            Ticket created = ticketService.createTicket(
                ticket, fixtures.nhanVienIT().getUsername(), fixtures.nhanVienIT().getRole());
            assertNotNull(created.getId());
            assertNull(created.getAssigneeName(),
                "NHAN_VIEN + IT must NOT be auto-self-assigned at creation time");
            assertNull(created.getAssignee(),
                "NHAN_VIEN + IT auto-self-assignment FK must be null at creation time");
        }
    }

    // ========================================================================
    // \u00a719.2 - CREATE + ASSIGNEE: only TRUONG_PHONG + IT may supply one, and only
    // to an enabled NHAN_VIEN + IT.
    // ========================================================================

    @Nested
    @DisplayName("\u00a719.2 TRUONG_PHONG + IT create + assignee policy")
    class TruongPhongItCreateWithAssignee {

        @Test
        @DisplayName("TRUONG_PHONG + IT + valid NHAN_VIEN + IT assignee - ALLOWED")
        void truongPhongItCanCreateWithValidAssignee() {
            UserAccount manager = fixtures.truongPhongIT();
            UserAccount staff = fixtures.nhanVienIT2();
            Ticket ticket = fixtures.mktRequestedTicket("Assign on create", TicketPriority.HIGH);
            ticket.setAssigneeName(staff.getUsername());

            Ticket created = ticketService.createTicket(
                ticket, manager.getUsername(), manager.getRole());

            assertEquals(staff.getUsername(), created.getAssigneeName());
            assertNotNull(created.getAssignee());
            assertEquals(staff.getId(), created.getAssignee().getId(),
                "create-time assignee_id and assigneeName must refer to the same user");
        }

        @Test
        @DisplayName("TRUONG_PHONG + IT + NHAN_VIEN non-IT - DENIED (department check)")
        void truongPhongItCannotCreateWithNonItNhanVien() {
            UserAccount manager = fixtures.truongPhongIT();
            UserAccount target = fixtures.nhanVienMkt();
            Ticket ticket = fixtures.mktRequestedTicket("Bad assign on create", TicketPriority.HIGH);
            ticket.setAssigneeName(target.getUsername());

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.createTicket(
                    ticket, manager.getUsername(), manager.getRole()),
                "TRUONG_PHONG + IT must not assign to NHAN_VIEN outside IT");
        }

        @Test
        @DisplayName("TRUONG_PHONG + IT + TRUONG_PHONG (even in IT) - DENIED (role check)")
        void truongPhongItCannotCreateWithTruongPhong() {
            // The brief \u00a78 is explicit: a TRUONG_PHONG + IT target is denied even though
            // they belong to IT - only NHAN_VIEN + IT is a valid target.
            UserAccount manager = fixtures.truongPhongIT();
            Ticket ticket = fixtures.mktRequestedTicket("Self assign on create", TicketPriority.HIGH);
            ticket.setAssigneeName(manager.getUsername());

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.createTicket(
                    ticket, manager.getUsername(), manager.getRole()),
                "TRUONG_PHONG + IT must not assign to themselves at create time");
        }

        @Test
        @DisplayName("TRUONG_PHONG + IT + ADMIN - DENIED (role check)")
        void truongPhongItCannotCreateWithAdmin() {
            UserAccount manager = fixtures.truongPhongIT();
            UserAccount target = fixtures.admin();
            Ticket ticket = fixtures.mktRequestedTicket("Bad admin assign", TicketPriority.HIGH);
            ticket.setAssigneeName(target.getUsername());

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.createTicket(
                    ticket, manager.getUsername(), manager.getRole()));
        }

        @Test
        @DisplayName("TRUONG_PHONG + IT + GIAM_DOC - DENIED (role check)")
        void truongPhongItCannotCreateWithGiamDoc() {
            UserAccount manager = fixtures.truongPhongIT();
            UserAccount target = fixtures.giamDoc();
            Ticket ticket = fixtures.mktRequestedTicket("Bad GD assign", TicketPriority.HIGH);
            ticket.setAssigneeName(target.getUsername());

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.createTicket(
                    ticket, manager.getUsername(), manager.getRole()));
        }

        @Test
        @DisplayName("TRUONG_PHONG + IT + disabled NHAN_VIEN + IT - DENIED (enabled check)")
        void truongPhongItCannotCreateWithDisabledItStaff() {
            UserAccount manager = fixtures.truongPhongIT();
            UserAccount target = fixtures.nhanVienIT2();
            target.setEnabled(false);
            // approved stays true so the only blocked dimension is enabled=false.
            userAccountRepository.save(target);

            Ticket ticket = fixtures.mktRequestedTicket("Disabled target", TicketPriority.HIGH);
            ticket.setAssigneeName(target.getUsername());

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.createTicket(
                    ticket, manager.getUsername(), manager.getRole()),
                "a disabled NHAN_VIEN + IT must not be a valid create-time assignee");
        }

        @Test
        @DisplayName("TRUONG_PHONG + IT + nonexistent username - DENIED (existence check)")
        void truongPhongItCannotCreateWithNonexistentUsername() {
            UserAccount manager = fixtures.truongPhongIT();
            Ticket ticket = fixtures.mktRequestedTicket("Ghost target", TicketPriority.HIGH);
            ticket.setAssigneeName("ghost.user");

            assertThrows(TicketNotFoundException.class,
                () -> ticketService.createTicket(
                    ticket, manager.getUsername(), manager.getRole()),
                "a nonexistent username must raise TicketNotFoundException");
        }
    }

    // ========================================================================
    // \u00a719.3 - Unauthorized create-time assignee
    // ========================================================================

    @Nested
    @DisplayName("\u00a719.3 Non-TRUONG_PHONG+IT create-time assignee is rejected")
    class UnauthorizedCreateTimeAssignee {

        @Test
        @DisplayName("ADMIN + assignee - DENIED")
        void adminWithAssigneeDenied() {
            Ticket ticket = fixtures.mktRequestedTicket("admin assigns", TicketPriority.LOW);
            ticket.setAssigneeName(fixtures.nhanVienIT().getUsername());

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.createTicket(
                    ticket, fixtures.admin().getUsername(), fixtures.admin().getRole()),
                "ADMIN must NOT be allowed to specify a create-time assignee");
        }

        @Test
        @DisplayName("GIAM_DOC + assignee - DENIED")
        void giamDocWithAssigneeDenied() {
            Ticket ticket = fixtures.mktRequestedTicket("gd assigns", TicketPriority.LOW);
            ticket.setAssigneeName(fixtures.nhanVienIT().getUsername());

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.createTicket(
                    ticket, fixtures.giamDoc().getUsername(), fixtures.giamDoc().getRole()));
        }

        @Test
        @DisplayName("TRUONG_PHONG non-IT + assignee - DENIED")
        void truongPhongNonItWithAssigneeDenied() {
            Ticket ticket = fixtures.mktRequestedTicket("TP non-IT assigns", TicketPriority.LOW);
            ticket.setAssigneeName(fixtures.nhanVienIT().getUsername());

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.createTicket(
                    ticket, fixtures.truongPhongMkt().getUsername(), fixtures.truongPhongMkt().getRole()));
        }

        @Test
        @DisplayName("NHAN_VIEN non-IT + assignee - DENIED")
        void nhanVienNonItWithAssigneeDenied() {
            Ticket ticket = fixtures.mktRequestedTicket("NV non-IT assigns", TicketPriority.LOW);
            ticket.setAssigneeName(fixtures.nhanVienIT().getUsername());

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.createTicket(
                    ticket, fixtures.nhanVienMkt().getUsername(), fixtures.nhanVienMkt().getRole()));
        }

        @Test
        @DisplayName("NHAN_VIEN + IT + another user as create-time assignee - DENIED")
        void nhanVienItWithAnotherAssigneeDenied() {
            Ticket ticket = fixtures.mktRequestedTicket("NV IT assigns peer", TicketPriority.LOW);
            ticket.setAssigneeName(fixtures.nhanVienIT2().getUsername());

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.createTicket(
                    ticket, fixtures.nhanVienIT().getUsername(), fixtures.nhanVienIT().getRole()),
                "NHAN_VIEN + IT must NOT bypass self-receive by pre-populating assignee");
        }
    }

    // ========================================================================
    // \u00a720 - RECEIVE: NHAN_VIEN + IT self-receive allowed; everyone else denied
    // ========================================================================

    @Nested
    @DisplayName("\u00a720 Receive policy - canonical self-receive")
    class ReceivePolicy {

        @Test
        @DisplayName("NHAN_VIEN + IT self-receives an unassigned ticket - ALLOWED, assignee = self")
        void nhanVienItSelfReceives() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.nhanVienIT();

            Ticket updated = ticketService.assignTicket(
                saved.getId(), actor.getUsername(), actor.getRole(),
                actor.getDepartmentId(), actor.getUsername());

            assertEquals(actor.getUsername(), updated.getAssigneeName());
            assertNotNull(updated.getAssignee());
            assertEquals(actor.getId(), updated.getAssignee().getId(),
                "self-receive must set the FK alongside the name");
        }

        @Test
        @DisplayName("NHAN_VIEN + IT attempts to receive on behalf of another - DENIED")
        void nhanVienItCannotReceiveOnBehalf() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.nhanVienIT();
            UserAccount peer = fixtures.nhanVienIT2();

            // The actor passes themselves as actor but supplies peer as the new assignee.
            // The isSelfReceive check (peer == actor) is false, so canAssignOthers is consulted
            // and that returns false for NHAN_VIEN + IT.
            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), peer.getUsername(), actor.getRole(),
                    actor.getDepartmentId(), actor.getUsername()));
        }

        @Test
        @DisplayName("NHAN_VIEN non-IT cannot receive - DENIED")
        void nhanVienNonItCannotReceive() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.nhanVienMkt();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), actor.getUsername(), actor.getRole(),
                    actor.getDepartmentId(), actor.getUsername()));
        }

        @Test
        @DisplayName("ADMIN cannot receive - DENIED")
        void adminCannotReceive() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.admin();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), actor.getUsername(), actor.getRole(),
                    actor.getDepartmentId(), actor.getUsername()));
        }

        @Test
        @DisplayName("GIAM_DOC cannot receive - DENIED")
        void giamDocCannotReceive() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.giamDoc();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), actor.getUsername(), actor.getRole(),
                    actor.getDepartmentId(), actor.getUsername()));
        }

        @Test
        @DisplayName("TRUONG_PHONG non-IT cannot receive - DENIED")
        void truongPhongNonItCannotReceive() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.truongPhongMkt();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), actor.getUsername(), actor.getRole(),
                    actor.getDepartmentId(), actor.getUsername()));
        }

        @Test
        @DisplayName("TRUONG_PHONG + IT self-receive - ALLOWED (preserves Phase 2.1 canReceiveTicket)")
        void truongPhongItCanSelfReceive() {
            // Per brief \u00a720: TRUONG_PHONG + IT must follow the existing canonical
            // canReceiveTicket policy. canReceiveTicket returns true for any IT operator
            // (TRUONG_PHONG + IT or NHAN_VIEN + IT), so self-receive is allowed.
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.truongPhongIT();

            Ticket updated = ticketService.assignTicket(
                saved.getId(), actor.getUsername(), actor.getRole(),
                actor.getDepartmentId(), actor.getUsername());

            assertEquals(actor.getUsername(), updated.getAssigneeName());
        }
    }

    // ========================================================================
    // \u00a721 - ASSIGN-OTHERS: TRUONG_PHONG + IT may assign others (NHAN_VIEN + IT only)
    // ========================================================================

    @Nested
    @DisplayName("\u00a721 Assign-others - canonical target validation")
    class AssignOthersPolicy {

        @Test
        @DisplayName("TRUONG_PHONG + IT assigns to enabled NHAN_VIEN + IT - ALLOWED")
        void truongPhongItAssignsValidItStaff() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount manager = fixtures.truongPhongIT();
            UserAccount staff = fixtures.nhanVienIT();

            Ticket updated = ticketService.assignTicket(
                saved.getId(), staff.getUsername(), manager.getRole(),
                manager.getDepartmentId(), manager.getUsername());

            assertEquals(staff.getUsername(), updated.getAssigneeName());
            assertNotNull(updated.getAssignee());
            assertEquals(staff.getId(), updated.getAssignee().getId());
        }

        @Test
        @DisplayName("TRUONG_PHONG + IT assigns to ADMIN - DENIED")
        void truongPhongItCannotAssignAdmin() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount manager = fixtures.truongPhongIT();
            UserAccount target = fixtures.admin();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), target.getUsername(), manager.getRole(),
                    manager.getDepartmentId(), manager.getUsername()));
        }

        @Test
        @DisplayName("TRUONG_PHONG + IT assigns to NHAN_VIEN non-IT - DENIED")
        void truongPhongItCannotAssignNonItNhanVien() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount manager = fixtures.truongPhongIT();
            UserAccount target = fixtures.nhanVienMkt();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), target.getUsername(), manager.getRole(),
                    manager.getDepartmentId(), manager.getUsername()));
        }

        @Test
        @DisplayName("TRUONG_PHONG + IT assigns to disabled user - DENIED")
        void truongPhongItCannotAssignDisabledUser() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount manager = fixtures.truongPhongIT();
            UserAccount staff = fixtures.nhanVienIT2();
            staff.setEnabled(false);
            userAccountRepository.save(staff);

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), staff.getUsername(), manager.getRole(),
                    manager.getDepartmentId(), manager.getUsername()));
        }

        @Test
        @DisplayName("TRUONG_PHONG + IT assigns to nonexistent user - DENIED")
        void truongPhongItCannotAssignNonexistentUser() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount manager = fixtures.truongPhongIT();

            assertThrows(TicketNotFoundException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), "no.such.user", manager.getRole(),
                    manager.getDepartmentId(), manager.getUsername()));
        }

        @Test
        @DisplayName("NHAN_VIEN + IT cannot assign to another NHAN_VIEN + IT - DENIED")
        void nhanVienItCannotAssignPeer() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.nhanVienIT();
            UserAccount peer = fixtures.nhanVienIT2();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), peer.getUsername(), actor.getRole(),
                    actor.getDepartmentId(), actor.getUsername()));
        }

        @Test
        @DisplayName("ADMIN cannot assign others - DENIED")
        void adminCannotAssignOthers() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount admin = fixtures.admin();
            UserAccount staff = fixtures.nhanVienIT();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), staff.getUsername(), admin.getRole(),
                    admin.getDepartmentId(), admin.getUsername()));
        }

        @Test
        @DisplayName("GIAM_DOC cannot assign others - DENIED")
        void giamDocCannotAssignOthers() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount giamDoc = fixtures.giamDoc();
            UserAccount staff = fixtures.nhanVienIT();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), staff.getUsername(), giamDoc.getRole(),
                    giamDoc.getDepartmentId(), giamDoc.getUsername()));
        }

        @Test
        @DisplayName("non-IT TRUONG_PHONG cannot assign others - DENIED")
        void truongPhongNonItCannotAssign() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount tpMkt = fixtures.truongPhongMkt();
            UserAccount staff = fixtures.nhanVienIT();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), staff.getUsername(), tpMkt.getRole(),
                    tpMkt.getDepartmentId(), tpMkt.getUsername()));
        }
    }

    // ========================================================================
    // \u00a722 - ASSIGNEE CONSISTENCY: assignee_id and assignee_name must refer to the
    // SAME user after assignment, both cleared after unassignment
    // ========================================================================

    @Nested
    @DisplayName("\u00a722 Assignee consistency - assignee_id and assignee_name synchronized")
    class AssigneeConsistency {

        @Test
        @DisplayName("After create-time assignment: assignee_id and assignee_name refer to the same user")
        void createTimeAssignmentIsConsistent() {
            UserAccount manager = fixtures.truongPhongIT();
            UserAccount staff = fixtures.nhanVienIT2();
            Ticket ticket = fixtures.mktRequestedTicket("Create-time consistency", TicketPriority.HIGH);
            ticket.setAssigneeName(staff.getUsername());

            Ticket created = ticketService.createTicket(
                ticket, manager.getUsername(), manager.getRole());

            assertEquals(staff.getUsername(), created.getAssigneeName(),
                "assigneeName must be the target username");
            assertNotNull(created.getAssignee(),
                "assignee FK must be populated");
            assertEquals(staff.getId(), created.getAssignee().getId(),
                "assignee_id must equal the target user id");
            assertEquals(staff.getUsername(), created.getAssignee().getUsername(),
                "assignee FK.username must equal assignee_name");
        }

        @Test
        @DisplayName("After PATCH /assignee: assignee_id and assignee_name refer to the same user")
        void assignTicketIsConsistent() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount manager = fixtures.truongPhongIT();
            UserAccount staff = fixtures.nhanVienIT();

            Ticket updated = ticketService.assignTicket(
                saved.getId(), staff.getUsername(), manager.getRole(),
                manager.getDepartmentId(), manager.getUsername());

            assertEquals(staff.getUsername(), updated.getAssigneeName());
            assertNotNull(updated.getAssignee());
            assertEquals(staff.getId(), updated.getAssignee().getId(),
                "assignee_id must equal the target user id after assignTicket");
        }

        @Test
        @DisplayName("After self-receive: assignee_id and assignee_name refer to the same user (the actor)")
        void selfReceiveIsConsistent() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.nhanVienIT();

            Ticket updated = ticketService.assignTicket(
                saved.getId(), actor.getUsername(), actor.getRole(),
                actor.getDepartmentId(), actor.getUsername());

            assertEquals(actor.getUsername(), updated.getAssigneeName());
            assertNotNull(updated.getAssignee());
            assertEquals(actor.getId(), updated.getAssignee().getId(),
                "self-receive: assignee_id must equal the actor's user id");
        }

        @Test
        @DisplayName("After unassign: both representations are cleared")
        void unassignClearsBothRepresentations() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount manager = fixtures.truongPhongIT();
            UserAccount staff = fixtures.nhanVienIT();

            // Assign first.
            Ticket assigned = ticketService.assignTicket(
                saved.getId(), staff.getUsername(), manager.getRole(),
                manager.getDepartmentId(), manager.getUsername());
            assertEquals(staff.getUsername(), assigned.getAssigneeName());
            assertNotNull(assigned.getAssignee());

            // Unassign.
            Ticket unassigned = ticketService.unassignTicket(
                assigned.getId(), manager.getRole(),
                manager.getDepartmentId(), manager.getUsername());
            assertNull(unassigned.getAssigneeName(), "unassign must null out the legacy name");
            assertNull(unassigned.getAssignee(), "unassign must null out the FK");
        }

        @Test
        @DisplayName("Validation rejects a target whose assignee_id and assignee_name would diverge")
        void assignmentTargetValidationCannotLeaveFieldsDiverged() {
            // Indirect: a non-existent username is rejected (TicketNotFoundException) BEFORE
            // any state mutation, so assigneeName can never be set to an unknown name with
            // a null FK. The previous code path silently set assigneeName = "ghost.user"
            // while leaving assignee = null; that is now impossible.
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount manager = fixtures.truongPhongIT();

            assertThrows(TicketNotFoundException.class, () -> ticketService.assignTicket(
                saved.getId(), "ghost.user", manager.getRole(),
                manager.getDepartmentId(), manager.getUsername()));

            // Confirm the ticket was not mutated.
            Ticket reread = ticketService.getTicket(saved.getId());
            assertNull(reread.getAssigneeName(), "rejected assignment leaves assigneeName null");
            assertNull(reread.getAssignee(), "rejected assignment leaves assignee FK null");
        }
    }

    // ========================================================================
    // \u00a723 - CROSS-DEPARTMENT: IT Helpdesk may process tickets from any requester
    // department. The requester department is NOT an authorization boundary.
    // ========================================================================

    @Nested
    @DisplayName("\u00a723 Cross-department - requester.department is not an authorization boundary")
    class CrossDepartment {

        @Test
        @DisplayName("MKT requester + TRUONG_PHONG + IT assignment - ALLOWED")
        void mktRequesterAssignedByTruongPhongIt() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            assertEquals(TicketAuthorizationFixtures.MKT_CODE, saved.getDepartment().getCode(),
                "sanity: ticket.department mirrors requester (MKT)");

            UserAccount manager = fixtures.truongPhongIT();
            UserAccount staff = fixtures.nhanVienIT();
            Ticket assigned = ticketService.assignTicket(
                saved.getId(), staff.getUsername(), manager.getRole(),
                manager.getDepartmentId(), manager.getUsername());

            assertEquals(staff.getUsername(), assigned.getAssigneeName(),
                "IT manager may assign IT staff to a MKT-requester ticket");
            // Department is the REQUESTER's department, not the operator's.
            assertEquals(TicketAuthorizationFixtures.MKT_CODE, assigned.getDepartment().getCode(),
                "ticket.department must remain MKT (requester) - not switch to operator");
        }

        @Test
        @DisplayName("MKT requester + NHAN_VIEN + IT self-receive - ALLOWED")
        void mktRequesterReceivedByNhanVienIt() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            assertEquals(TicketAuthorizationFixtures.MKT_CODE, saved.getDepartment().getCode());

            UserAccount staff = fixtures.nhanVienIT();
            Ticket self = ticketService.assignTicket(
                saved.getId(), staff.getUsername(), staff.getRole(),
                staff.getDepartmentId(), staff.getUsername());

            assertEquals(staff.getUsername(), self.getAssigneeName(),
                "IT staff may self-receive a MKT-requester ticket");
            assertEquals(TicketAuthorizationFixtures.MKT_CODE, self.getDepartment().getCode(),
                "ticket.department must remain MKT - not become IT");
        }

        @Test
        @DisplayName("A second non-IT department (HR) requester - assignment still ALLOWED")
        void hrRequesterAssignedByTruongPhongIt() {
            Department hr = persistDepartment("HR", "Human Resources");
            UserAccount hrRequester = persistUser("phase3_hr_user", UserRole.Role.NHAN_VIEN, hr);
            Ticket ticket = new Ticket();
            ticket.setTicketNumber("PHASE3-HR-001");
            ticket.setTitle("HR ticket");
            ticket.setDescription("created by HR requester");
            ticket.setPriority(com.example.ticketing.ticket.TicketTypes.TicketPriority.LOW);
            ticket.setCategory(com.example.ticketing.ticket.TicketTypes.TicketCategory.HARDWARE);
            ticket.setStatus(com.example.ticketing.ticket.TicketTypes.TicketStatus.NEW);
            ticket.setRequesterUsername(hrRequester.getUsername());
            ticket.setRequesterName(hrRequester.getDisplayName());
            ticket.setDepartment(hrRequester.getDepartment());
            Ticket saved = ticketRepository.save(ticket);
            assertEquals("HR", saved.getDepartment().getCode());

            UserAccount manager = fixtures.truongPhongIT();
            UserAccount staff = fixtures.nhanVienIT();
            Ticket assigned = ticketService.assignTicket(
                saved.getId(), staff.getUsername(), manager.getRole(),
                manager.getDepartmentId(), manager.getUsername());

            assertEquals(staff.getUsername(), assigned.getAssigneeName());
            assertEquals("HR", assigned.getDepartment().getCode(),
                "HR requester ticket.department stays HR");
        }
    }

    // ========================================================================
    // \u00a724 - NEGATIVE SECURITY: client-supplied valid target cannot bypass actor policy
    // ========================================================================

    @Nested
    @DisplayName("\u00a724 Negative security - client-side authorization bypass")
    class NegativeSecurity {

        @Test
        @DisplayName("A non-IT user supplies a valid NHAN_VIEN + IT assignee on create - REJECTED")
        void nonItClientCannotBypassWithValidTarget() {
            // The target is genuinely valid, but the actor is NOT TRUONG_PHONG + IT.
            // The server must reject based on the ACTOR, not the TARGET.
            Ticket ticket = fixtures.mktRequestedTicket("Bypass attempt", TicketPriority.HIGH);
            ticket.setAssigneeName(fixtures.nhanVienIT().getUsername());

            for (UserAccount actor : List.of(
                    fixtures.admin(),
                    fixtures.giamDoc(),
                    fixtures.truongPhongMkt(),
                    fixtures.nhanVienMkt())) {
                Ticket attempt = fixtures.mktRequestedTicket(
                    "Bypass attempt " + actor.getRole(), TicketPriority.HIGH);
                attempt.setAssigneeName(fixtures.nhanVienIT().getUsername());

                assertThrows(TicketRuleViolationException.class,
                    () -> ticketService.createTicket(
                        attempt, actor.getUsername(), actor.getRole()),
                    actor.getRole() + " must NOT bypass create-time authorization via a valid target");
            }
        }

        @Test
        @DisplayName("NHAN_VIEN + IT supplies another NHAN_VIEN + IT as create-time assignee - REJECTED")
        void nhanVienItCannotBypassAndMakeAnotherAssignee() {
            Ticket ticket = fixtures.mktRequestedTicket("Peer bypass attempt", TicketPriority.HIGH);
            ticket.setAssigneeName(fixtures.nhanVienIT2().getUsername());

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.createTicket(
                    ticket, fixtures.nhanVienIT().getUsername(), fixtures.nhanVienIT().getRole()),
                "NHAN_VIEN + IT must NOT bypass self-receive via create-time assignment");
        }

        @Test
        @DisplayName("A non-IT user supplies a valid NHAN_VIEN + IT assignee on PATCH /assignee - REJECTED")
        void nonItClientCannotAssignWithValidTarget() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount validTarget = fixtures.nhanVienIT();

            // The actor policy is the gate, regardless of how valid the target looks.
            for (UserAccount actor : List.of(
                    fixtures.admin(),
                    fixtures.giamDoc(),
                    fixtures.truongPhongMkt(),
                    fixtures.nhanVienMkt())) {
                Ticket toAssign = ticketRepository.save(fixtures.mktRequestedTicket());
                assertThrows(TicketRuleViolationException.class,
                    () -> ticketService.assignTicket(
                        toAssign.getId(), validTarget.getUsername(), actor.getRole(),
                        actor.getDepartmentId(), actor.getUsername()),
                    actor.getRole() + " must NOT bypass assignment authorization via a valid target");
            }
        }
    }

    // ========================================================================
    // \u00a77 - TARGET VALIDATION ENUM (pure-function test)
    // ========================================================================

    @Nested
    @DisplayName("\u00a77 Target validation enum - pure-function checks")
    class TargetValidationEnum {

        @Test
        @DisplayName("validateAssignmentTarget returns TARGET_NOT_FOUND for an unknown username")
        void nullTarget() {
            assertEquals(AssignmentTargetValidation.TARGET_NOT_FOUND,
                ticketService.validateAssignmentTarget(null));
            assertEquals(AssignmentTargetValidation.TARGET_NOT_FOUND,
                ticketService.validateAssignmentTarget(""));
            assertEquals(AssignmentTargetValidation.TARGET_NOT_FOUND,
                ticketService.validateAssignmentTarget("ghost.user"));
        }

        @Test
        @DisplayName("validateAssignmentTarget returns TARGET_DISABLED for a disabled user")
        void disabledTarget() {
            UserAccount target = fixtures.nhanVienIT2();
            target.setEnabled(false);
            userAccountRepository.save(target);

            assertEquals(AssignmentTargetValidation.TARGET_DISABLED,
                ticketService.validateAssignmentTarget(target.getUsername()));
        }

        @Test
        @DisplayName("validateAssignmentTarget returns INVALID_ROLE for ADMIN/GIAM_DOC/TRUONG_PHONG")
        void wrongRoleTargets() {
            // ADMIN/GIAM_DOC/TRUONG_PHONG are rejected because their role is not NHAN_VIEN.
            // The role check is the first gate after the existence/enabled gates, so these
            // actors never reach the department check (role takes precedence by design).
            assertEquals(AssignmentTargetValidation.INVALID_ROLE,
                ticketService.validateAssignmentTarget(fixtures.admin().getUsername()));
            assertEquals(AssignmentTargetValidation.INVALID_ROLE,
                ticketService.validateAssignmentTarget(fixtures.giamDoc().getUsername()));
            assertEquals(AssignmentTargetValidation.INVALID_ROLE,
                ticketService.validateAssignmentTarget(fixtures.truongPhongIT().getUsername()));
            assertEquals(AssignmentTargetValidation.INVALID_ROLE,
                ticketService.validateAssignmentTarget(fixtures.truongPhongMkt().getUsername()));
        }

        @Test
        @DisplayName("validateAssignmentTarget returns INVALID_DEPARTMENT for NHAN_VIEN non-IT")
        void wrongDepartmentTargets() {
            // NHAN_VIEN has the right role, but the wrong department. The role check
            // passes and the department check fails. (NHAN_VIEN non-IT is the canonical
            // "wrong department" target.)
            assertEquals(AssignmentTargetValidation.INVALID_DEPARTMENT,
                ticketService.validateAssignmentTarget(fixtures.nhanVienMkt().getUsername()));
        }

        @Test
        @DisplayName("validateAssignmentTarget returns VALID for enabled NHAN_VIEN + IT")
        void validTarget() {
            assertEquals(AssignmentTargetValidation.VALID,
                ticketService.validateAssignmentTarget(fixtures.nhanVienIT().getUsername()));
            assertEquals(AssignmentTargetValidation.VALID,
                ticketService.validateAssignmentTarget(fixtures.nhanVienIT2().getUsername()));
        }

        @Test
        @DisplayName("IT department code is NOT hardcoded - it comes from ItDepartmentResolver")
        void itCodeIsNotHardcoded() {
            // Sanity check that the resolver is in the wiring path. We do not flip the
            // resolver here because that would require a profile; the unit test for the
            // custom code path is ItDepartmentResolverCustomCodeTest.
            assertNotNull(itDepartmentResolver);
            assertNotNull(itDepartmentResolver.getItDepartmentCode());
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private Department persistDepartment(String code, String name) {
        return departmentRepository.findByCode(code).orElseGet(() -> {
            Department d = new Department();
            d.setCode(code);
            d.setName(name);
            d.setEnabled(true);
            return departmentRepository.save(d);
        });
    }

    private UserAccount persistUser(String username, UserRole.Role role, Department dept) {
        return userAccountRepository.findByUsername(username).orElseGet(() -> {
            UserAccount u = new UserAccount();
            u.setUsername(username);
            u.setPasswordHash("{noop}phase3-fixture");
            u.setRole(role);
            u.setDepartment(dept);
            u.setDisplayName(username);
            u.setEmail(username + "@example.internal");
            u.setEnabled(true);
            u.setApproved(true);
            return userAccountRepository.save(u);
        });
    }
}
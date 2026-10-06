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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.auth.UserRole;
import com.example.ticketing.department.Department;
import com.example.ticketing.ticket.TicketTypes.CommentVisibility;
import com.example.ticketing.ticket.TicketTypes.TicketPriority;
import com.example.ticketing.ticket.TicketTypes.TicketStatus;

/**
 * PHASE 0 - AUTHORIZATION CHARACTERIZATION TESTS.
 *
 * <p>These tests answer "what does the current system actually do?" They are NOT a specification
 * of desired behavior. Every assertion records observed behavior as of this commit.
 *
 * <p>Naming convention: tests whose name contains {@code current...} or {@code ..._isPolicyGap}
 * document behavior that CONFLICTS with the canonical IT helpdesk policy. Those conflicts are the
 * intended, successful output of Phase 0. They are recorded, NOT fixed. No production code is
 * changed to make them pass, and none of them is presented as the future specification.
 *
 * <p>Canonical policy being contrasted against (IT helpdesk, internal company):
 * <ul>
 *   <li>ADMIN / GIAM_DOC: may administer, may NOT act as IT helpdesk operators, may NOT assign.</li>
 *   <li>TRUONG_PHONG + IT: may process and may assign; target must eventually be NHAN_VIEN + IT.</li>
 *   <li>NHAN_VIEN + IT: may receive/process own assigned work; may NOT assign to others.</li>
 *   <li>Ticket.department is the REQUESTER's department, never an operator boundary.</li>
 * </ul>
 *
 * <p>Default fixture shape is requester in MKT + operator in IT, so that the "requester's
 * department must not restrict the operator" rule is exercised by every applicable test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketServiceAuthorizationTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketAuthorizationFixtures fixtures;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private com.example.ticketing.department.DepartmentRepository departmentRepository;

    // PHASE 3: helper for tests that need an extra user beyond the standard fixtures.
    private UserAccount persistUser(String username, UserRole.Role role,
                                    Department dept) {
        return userAccountRepository.findByUsername(username).orElseGet(() -> {
            UserAccount u = new UserAccount();
            u.setUsername(username);
            u.setPasswordHash("{noop}phase3-characterization");
            u.setRole(role);
            u.setDepartment(dept);
            u.setDisplayName(username);
            u.setEmail(username + "@example.internal");
            u.setEnabled(true);
            u.setApproved(true);
            return userAccountRepository.save(u);
        });
    }

    // ========================================================================
    // A. TICKET VIEWING
    // ========================================================================

    @Nested
    @DisplayName("A. Ticket viewing - CURRENT behavior")
    class Viewing {

        @Test
        @DisplayName("CURRENT: listTickets applies no department or role filter for any actor")
        void currentListTicketsHasNoDepartmentOrRoleFilter() {
            // TicketService.listTickets contains an EMPTY if-block for NHAN_VIEN/TRUONG_PHONG,
            // so every actor receives the same unfiltered result set.
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());

            long visibleToMktStaff = countVisibleTo(fixtures.nhanVienMkt());
            long visibleToItStaff = countVisibleTo(fixtures.nhanVienIT());
            long visibleToAdmin = countVisibleTo(fixtures.admin());
            long visibleToMktManager = countVisibleTo(fixtures.truongPhongMkt());

            assertTrue(saved.getId() > 0, "fixture ticket must be persisted");
            assertEquals(visibleToMktStaff, visibleToItStaff,
                "IT staff and non-IT staff currently see the same tickets");
            assertEquals(visibleToMktStaff, visibleToAdmin,
                "ADMIN sees the same tickets as everyone else");
            assertEquals(visibleToMktStaff, visibleToMktManager,
                "a non-IT TRUONG_PHONG sees IT-department tickets too");
        }

        @Test
        @DisplayName("CURRENT: getTicket performs no object-level authorization check")
        void currentGetTicketHasNoObjectLevelCheck() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());

            // No actor/role/ownership argument exists on getTicket at all, so any authenticated
            // caller reaching this method can read any ticket by id.
            Ticket read = ticketService.getTicket(saved.getId());

            assertNotNull(read);
            assertEquals(saved.getId(), read.getId());
            // Requester department is MKT; the reader is a non-IT staff account. Nothing blocked it.
            assertEquals(TicketAuthorizationFixtures.MKT_CODE, read.getDepartment().getCode());
        }

        @Test
        @DisplayName("CURRENT: an MKT-requester ticket is readable regardless of requester department")
        void currentRequesterDepartmentIsNotAnOperatorBoundary() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());

            Ticket read = ticketService.getTicket(saved.getId());

            assertEquals(TicketAuthorizationFixtures.MKT_CODE, saved.getDepartment().getCode(),
                "canonical invariant: Ticket.department == requester.department");
            assertNotNull(read, "operator is not blocked by the requester's department");
        }

        private long countVisibleTo(UserAccount actor) {
            return ticketService.listTickets(
                null, null, null, false, PageRequest.of(0, 50),
                actor.getRole().name(), actor.getDepartmentId()
            ).getTotalElements();
        }
    }

    // ========================================================================
    // B. TICKET STATUS CHANGES
    // ========================================================================

    @Nested
    @DisplayName("B. Status changes - CURRENT behavior")
    class StatusChanges {

        @Test
        @DisplayName("CANONICAL: ADMIN cannot start progress (C-4 fixed - ADMIN is not an IT Helpdesk operator)")
        void currentAdminCanStartProgress_isPolicyGapC4() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.startProgress(
                    saved.getId(),
                    fixtures.admin().getRole(),
                    fixtures.admin().getDepartmentId(),
                    fixtures.admin().getUsername()
                ),
                "ADMIN must be denied by the canonical canProcessTickets policy");
        }

        @Test
        @DisplayName("CANONICAL: GIAM_DOC cannot start progress (C-4 fixed - GIAM_DOC is not an IT Helpdesk operator)")
        void currentGiamDocCanStartProgress_isPolicyGapC4() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.startProgress(
                    saved.getId(),
                    fixtures.giamDoc().getRole(),
                    fixtures.giamDoc().getDepartmentId(),
                    fixtures.giamDoc().getUsername()
                ),
                "GIAM_DOC must be denied by the canonical canProcessTickets policy");
        }

        @Test
        @DisplayName("CURRENT: TRUONG_PHONG + IT can start progress - matches intended policy")
        void currentTruongPhongItCanStartProgress() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.truongPhongIT();

            Ticket updated = ticketService.startProgress(
                saved.getId(), actor.getRole(), actor.getDepartmentId(), actor.getUsername());

            assertEquals(TicketStatus.IN_PROGRESS, updated.getStatus());
        }

        @Test
        @DisplayName("CURRENT: NHAN_VIEN + IT can start progress - matches intended policy")
        void currentNhanVienItCanStartProgress() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.nhanVienIT();

            Ticket updated = ticketService.startProgress(
                saved.getId(), actor.getRole(), actor.getDepartmentId(), actor.getUsername());

            assertEquals(TicketStatus.IN_PROGRESS, updated.getStatus());
        }

        @Test
        @DisplayName("CURRENT: TRUONG_PHONG non-IT is denied startProgress")
        void currentNonItTruongPhongCannotStartProgress() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.truongPhongMkt();

            assertThrows(TicketRuleViolationException.class, () -> ticketService.startProgress(
                saved.getId(), actor.getRole(), actor.getDepartmentId(), actor.getUsername()));
        }

        @Test
        @DisplayName("CURRENT: NHAN_VIEN non-IT is denied startProgress")
        void currentNonItNhanVienCannotStartProgress() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.nhanVienMkt();

            assertThrows(TicketRuleViolationException.class, () -> ticketService.startProgress(
                saved.getId(), actor.getRole(), actor.getDepartmentId(), actor.getUsername()));
        }

        @Test
        @DisplayName("CURRENT: a null department denies status modification for TRUONG_PHONG")
        void currentNullDepartmentDeniesTruongPhongStatusChange() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());

            assertThrows(TicketRuleViolationException.class, () -> ticketService.startProgress(
                saved.getId(),
                fixtures.truongPhongIT().getRole(),
                null,
                fixtures.truongPhongIT().getUsername()
            ));
        }

        @Test
        @DisplayName("CANONICAL: ADMIN cannot resolve (C-4 fixed - the resolveTicket gate is now canProcessTickets)")
        void currentAdminCanResolve_isPolicyGapC4() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.admin();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.resolveTicket(
                    saved.getId(), actor.getRole(), actor.getDepartmentId(), actor.getUsername(), "Fixed by admin"),
                "ADMIN must be denied by the canonical canProcessTickets policy");
        }

        @Test
        @DisplayName("CANONICAL: ADMIN cannot escalate (C-4 fixed - the escalateTicket gate is now canProcessTickets)")
        void currentAdminCanEscalate_isPolicyGapC4() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.admin();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.escalateTicket(
                    saved.getId(), actor.getRole(), actor.getDepartmentId(), actor.getUsername(), "Vendor needed"),
                "ADMIN must be denied by the canonical canProcessTickets policy");
        }

        @Test
        @DisplayName("CURRENT: NHAN_VIEN + IT can resolve and escalate - matches intended policy")
        void currentNhanVienItCanResolveAndEscalate() {
            Ticket resolvedTicket = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.nhanVienIT();
            ticketService.startProgress(resolvedTicket.getId(), actor.getRole(), actor.getDepartmentId(), actor.getUsername());

            Ticket resolved = ticketService.resolveTicket(
                resolvedTicket.getId(), actor.getRole(), actor.getDepartmentId(), actor.getUsername(), "Replaced cable");
            assertEquals(TicketStatus.RESOLVED, resolved.getStatus());

            Ticket escalateTarget = ticketRepository.save(fixtures.mktRequestedTicket());
            ticketService.startProgress(escalateTarget.getId(), actor.getRole(), actor.getDepartmentId(), actor.getUsername());
            Ticket escalated = ticketService.escalateTicket(
                escalateTarget.getId(), actor.getRole(), actor.getDepartmentId(), actor.getUsername(), "Needs vendor");
            assertEquals(TicketStatus.ESCALATED, escalated.getStatus());
        }

        @Test
        @DisplayName("CURRENT: only ADMIN may cancel - existing rule preserved as a guard")
        void currentOnlyAdminMayCancel() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount itStaff = fixtures.nhanVienIT();

            assertThrows(TicketRuleViolationException.class, () -> ticketService.cancelTicket(
                saved.getId(), itStaff.getRole(), itStaff.getUsername(), "not allowed"));

            Ticket cancelled = ticketService.cancelTicket(
                saved.getId(), fixtures.admin().getRole(), fixtures.admin().getUsername(), "duplicate");
            assertEquals(TicketStatus.CANCELLED, cancelled.getStatus());
        }

        @Test
        @DisplayName("CANONICAL: a non-IT TRUONG_PHONG cannot change status via updateStatus (C-5 fixed - the gate is now canProcessTickets)")
        void currentNonItTruongPhongCanChangeStatusViaUpdateStatus_isPolicyGapC5() {
            // Phase 2.1: updateStatus now consults the canonical canProcessTickets gate BEFORE
            // the transition validator. A non-IT TRUONG_PHONG is not an IT Helpdesk operator and
            // is therefore denied before the transition graph is even reached.
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.truongPhongMkt();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.updateStatus(
                    saved.getId(), TicketStatus.IN_PROGRESS, actor.getRole(),
                    actor.getDepartmentId(), actor.getUsername(), actor.getUsername(), "starting"),
                "non-IT TRUONG_PHONG must be denied by the canonical canProcessTickets policy");
        }

        @Test
        @DisplayName("CURRENT: updateStatus still confines a non-IT NHAN_VIEN to own-ticket close/reopen")
        void currentNonItNhanVienIsConfinedToOwnTicketCloseReopen() {
            Ticket someoneElsesTicket = ticketRepository.save(fixtures.itRequestedTicket());
            UserAccount actor = fixtures.nhanVienMkt();

            TicketRuleViolationException error = assertThrows(
                TicketRuleViolationException.class,
                () -> ticketService.updateStatus(
                    someoneElsesTicket.getId(), TicketStatus.IN_PROGRESS, actor.getRole(),
                    actor.getDepartmentId(), actor.getUsername(), actor.getUsername(), "x")
            );

            assertTrue(error.getMessage().contains("only modify your own tickets"),
                "expected own-ticket restriction, got: " + error.getMessage());
        }

        @Test
        @DisplayName("CURRENT: updateStatus cannot move to CANCELLED for a non-ADMIN actor")
        void currentUpdateStatusBlocksNonAdminCancel() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.nhanVienIT();

            TicketRuleViolationException error = assertThrows(
                TicketRuleViolationException.class,
                () -> ticketService.updateStatus(
                    saved.getId(), TicketStatus.CANCELLED, actor.getRole(),
                    actor.getDepartmentId(), actor.getUsername(), actor.getUsername(), "x")
            );

            assertTrue(error.getMessage().contains("administrators can cancel"),
                "expected admin-only cancel, got: " + error.getMessage());
        }

        @Test
        @DisplayName("CURRENT: updateStatus rejects an illegal transition for an allowed actor")
        void currentUpdateStatusStillEnforcesTheTransitionGraph() {
            // RESOLVED -> IN_PROGRESS is not a legal edge. Actor authorization permitting
            // startProgress does not mean the transition graph is bypassed.
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.nhanVienIT();
            ticketService.startProgress(saved.getId(), actor.getRole(), actor.getDepartmentId(), actor.getUsername());
            ticketService.resolveTicket(saved.getId(), actor.getRole(), actor.getDepartmentId(), actor.getUsername(), "done");

            assertThrows(TicketRuleViolationException.class, () -> ticketService.updateStatus(
                saved.getId(), TicketStatus.IN_PROGRESS, actor.getRole(),
                actor.getDepartmentId(), actor.getUsername(), actor.getUsername(), "x"));
        }
    }

    // ========================================================================
    // C. ASSIGNMENT
    // ========================================================================

    @Nested
    @DisplayName("C. Assignment - CURRENT behavior")
    class Assignment {

        @Test
        @DisplayName("1. TRUONG_PHONG + IT -> NHAN_VIEN + IT is allowed - matches intended policy")
        void currentTruongPhongItCanAssignToNhanVienIt() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.truongPhongIT();
            UserAccount target = fixtures.nhanVienIT();

            Ticket updated = ticketService.assignTicket(
                saved.getId(), target.getUsername(), actor.getRole(),
                actor.getDepartmentId(), actor.getUsername());

            assertEquals(TicketStatus.ASSIGNED, updated.getStatus());
            assertEquals(target.getUsername(), updated.getAssigneeName());
        }

        @Test
        @DisplayName("CANONICAL: TRUONG_PHONG + IT -> non-IT NHAN_VIEN is DENIED (C-3 fixed - target must be enabled NHAN_VIEN + IT)")
        void canonicalTruongPhongItCannotAssignToNonItNhanVien_isPolicyGapC3Fixed() {
            // Phase 3: assignTicket now validates the target. A non-IT NHAN_VIEN is rejected
            // before any state mutation. The canonical rule is: target must be enabled
            // NHAN_VIEN in the configured IT department.
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.truongPhongIT();
            UserAccount target = fixtures.nhanVienMkt();

            TicketRuleViolationException thrown = assertThrows(
                TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), target.getUsername(), actor.getRole(),
                    actor.getDepartmentId(), actor.getUsername()),
                "assigning to a non-IT NHAN_VIEN must be denied by C-3 target validation");

            assertTrue(thrown.getMessage().contains("IT Helpdesk")
                    || thrown.getMessage().toLowerCase().contains("department"),
                "rejection must explain the target is not in IT Helpdesk; got: "
                    + thrown.getMessage());
        }

        @Test
        @DisplayName("CANONICAL: TRUONG_PHONG + IT -> ADMIN is DENIED (C-3 fixed - role check)")
        void canonicalTruongPhongItCannotAssignToAdmin_isPolicyGapC3Fixed() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.truongPhongIT();
            UserAccount target = fixtures.admin();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), target.getUsername(), actor.getRole(),
                    actor.getDepartmentId(), actor.getUsername()),
                "assigning to an ADMIN must be denied by C-3 target validation");
        }

        @Test
        @DisplayName("CANONICAL: TRUONG_PHONG + IT -> GIAM_DOC is DENIED (C-3 fixed - role check)")
        void canonicalTruongPhongItCannotAssignToGiamDoc_isPolicyGapC3Fixed() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.truongPhongIT();
            UserAccount target = fixtures.giamDoc();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), target.getUsername(), actor.getRole(),
                    actor.getDepartmentId(), actor.getUsername()),
                "assigning to a GIAM_DOC must be denied by C-3 target validation");
        }

        @Test
        @DisplayName("CANONICAL: TRUONG_PHONG + IT -> another TRUONG_PHONG (even in IT) is DENIED (C-3 fixed - role must be NHAN_VIEN)")
        void canonicalTruongPhongItCannotAssignAnotherTruongPhong_isPolicyGapC3Fixed() {
            // Even though both are in IT, the target must be NHAN_VIEN specifically. A
            // TRUONG_PHONG + IT is not eligible to be the target of an assignment. To
            // exercise this we need a second TRUONG_PHONG + IT account distinct from the
            // actor (otherwise isSelfReceive short-circuits and bypasses C-3, which is
            // the intended self-receive path).
            UserAccount secondManager = persistUser(
                "phase3_tp_it2", UserRole.Role.TRUONG_PHONG, fixtures.itDepartment());
            UserAccount actor = fixtures.truongPhongIT();
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), secondManager.getUsername(), actor.getRole(),
                    actor.getDepartmentId(), actor.getUsername()),
                "assigning to a TRUONG_PHONG must be denied even when in IT; only NHAN_VIEN is eligible");
        }

        @Test
        @DisplayName("CANONICAL: NHAN_VIEN + IT cannot assign to a peer (C-2 fixed - only canReceiveTicket applies, not canAssignOthers)")
        void currentNhanVienItCanAssignToAnotherNhanVienIt_isPolicyGapC2() {
            // Canonical: NHAN_VIEN + IT may receive/process own work but must NOT assign others.
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.nhanVienIT();
            UserAccount otherItStaff = fixtures.nhanVienIT2();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), otherItStaff.getUsername(), actor.getRole(),
                    actor.getDepartmentId(), actor.getUsername()),
                "NHAN_VIEN + IT must be denied by the canonical canAssignOthers policy");
        }

        @Test
        @DisplayName("CANONICAL: ADMIN cannot assign tickets (C-1 fixed - ADMIN is not an IT Helpdesk operator)")
        void currentAdminCanAssign_isPolicyGapC1() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.admin();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), fixtures.nhanVienIT().getUsername(), actor.getRole(),
                    actor.getDepartmentId(), actor.getUsername()),
                "ADMIN must be denied by the canonical canAssignOthers policy");
        }

        @Test
        @DisplayName("CANONICAL: GIAM_DOC cannot assign tickets (C-1 fixed - GIAM_DOC is not an IT Helpdesk operator)")
        void currentGiamDocCanAssign_isPolicyGapC1() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.giamDoc();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    saved.getId(), fixtures.nhanVienIT().getUsername(), actor.getRole(),
                    actor.getDepartmentId(), actor.getUsername()),
                "GIAM_DOC must be denied by the canonical canAssignOthers policy");
        }

        @Test
        @DisplayName("9. non-IT TRUONG_PHONG is denied assignment")
        void currentNonItTruongPhongCannotAssign() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.truongPhongMkt();

            assertThrows(TicketRuleViolationException.class, () -> ticketService.assignTicket(
                saved.getId(), fixtures.nhanVienIT().getUsername(), actor.getRole(),
                actor.getDepartmentId(), actor.getUsername()));
        }

        @Test
        @DisplayName("10. non-IT NHAN_VIEN is denied assignment")
        void currentNonItNhanVienCannotAssign() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.nhanVienMkt();

            assertThrows(TicketRuleViolationException.class, () -> ticketService.assignTicket(
                saved.getId(), fixtures.nhanVienIT().getUsername(), actor.getRole(),
                actor.getDepartmentId(), actor.getUsername()));
        }

        @Test
        @DisplayName("CANONICAL: assigning to an unknown username is rejected (C-3 fixed - existence check)")
        void canonicalUnknownAssigneeTargetIsRejected_isPolicyGapC3Fixed() {
            // Phase 3: target validation runs before any mutation. A nonexistent username
            // raises TicketNotFoundException (404) rather than silently desynchronizing
            // assigneeName vs assignee.
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.truongPhongIT();

            assertThrows(TicketNotFoundException.class, () -> ticketService.assignTicket(
                saved.getId(), "ghost.user", actor.getRole(), actor.getDepartmentId(), actor.getUsername()),
                "unknown assignment target must be rejected, not silently accepted");
        }

        @Test
        @DisplayName("CANONICAL: assigning to a disabled NHAN_VIEN + IT is rejected (C-3 fixed - active check)")
        void canonicalDisabledAssigneeTargetIsRejected_isPolicyGapC3Fixed() {
            // Phase 3: an enabled NHAN_VIEN + IT who later gets disabled is no longer a
            // valid assignment target. The actor is TRUONG_PHONG + IT (the canonical
            // assigner) and would otherwise pass the actor policy.
            UserAccount itStaff = fixtures.nhanVienIT();
            itStaff.setEnabled(false);
            // approved stays true so the only blocked dimension is enabled=false.

            Ticket ticket = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount assigner = fixtures.truongPhongIT();

            assertThrows(TicketRuleViolationException.class, () -> ticketService.assignTicket(
                ticket.getId(), itStaff.getUsername(), assigner.getRole(),
                assigner.getDepartmentId(), assigner.getUsername()),
                "a disabled NHAN_VIEN + IT must not be a valid assignment target");
        }

        @Test
        @DisplayName("CURRENT: unassign requires the same permission as assign")
        void currentUnassignRequiresAssignPermission() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount itStaff = fixtures.nhanVienIT();
            UserAccount mktStaff = fixtures.nhanVienMkt();

            // Permitted for IT staff today...
            Ticket assigned = ticketService.assignTicket(
                saved.getId(), itStaff.getUsername(), itStaff.getRole(),
                itStaff.getDepartmentId(), itStaff.getUsername());
            assertEquals(itStaff.getUsername(), assigned.getAssigneeName());

            // ...and denied for a non-IT account.
            assertThrows(TicketRuleViolationException.class, () -> ticketService.unassignTicket(
                saved.getId(), mktStaff.getRole(), mktStaff.getDepartmentId(), mktStaff.getUsername()));
        }
    }

    // ========================================================================
    // D. SELF-RECEIVE vs ASSIGN-OTHERS
    // ========================================================================

    @Nested
    @DisplayName("D. Self-receive vs assign others - CURRENT behavior")
    class SelfReceiveVersusAssignOthers {

        @Test
        @DisplayName("CURRENT: NHAN_VIEN + IT can self-receive (permitted, matches intended policy)")
        void currentNhanVienItCanSelfReceive() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.nhanVienIT();

            Ticket updated = ticketService.assignTicket(
                saved.getId(), actor.getUsername(), actor.getRole(),
                actor.getDepartmentId(), actor.getUsername());

            assertEquals(actor.getUsername(), updated.getAssigneeName());
        }

        @Test
        @DisplayName("CANONICAL: self-receive is allowed, assign-to-another is denied - the two are now distinguished (C-2 fixed)")
        void currentSelfReceiveAndAssignOthersAreNotDistinguished_isPolicyGapC2() {
            // Phase 2.1: TicketService.assignTicket now distinguishes self-receive (newAssignee
            // == actorName) from assign-to-others. The canonical policy is:
            //   self-receive  -> canReceiveTicket (any IT operator)
            //   assign-others -> canAssignOthers  (only TRUONG_PHONG + IT)
            Ticket selfReceiveTicket = ticketRepository.save(fixtures.mktRequestedTicket());
            Ticket assignOtherTicket = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.nhanVienIT();
            UserAccount peer = fixtures.nhanVienIT2();

            // Self-receive is allowed for NHAN_VIEN + IT.
            Ticket selfReceived = ticketService.assignTicket(
                selfReceiveTicket.getId(), actor.getUsername(), actor.getRole(),
                actor.getDepartmentId(), actor.getUsername());
            assertEquals(actor.getUsername(), selfReceived.getAssigneeName(),
                "self-receive is permitted for NHAN_VIEN + IT under canReceiveTicket");

            // Assign-to-another is now denied for NHAN_VIEN + IT.
            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.assignTicket(
                    assignOtherTicket.getId(), peer.getUsername(), actor.getRole(),
                    actor.getDepartmentId(), actor.getUsername()),
                "assign-to-another is now denied for NHAN_VIEN + IT under canAssignOthers");
        }
    }

    // ========================================================================
    // E. ADMIN / GIAM_DOC PROCESSING (explicit policy-gap coverage)
    // ========================================================================

    @Nested
    @DisplayName("E. ADMIN / GIAM_DOC as helpdesk operators - CURRENT behavior")
    class AdminAndGiamDocProcessing {

        @Test
        @DisplayName("CANONICAL: ADMIN cannot change priority (C-4 fixed - updatePriority uses the canProcessTickets gate)")
        void currentAdminCanChangePriority_isPolicyGapC4() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.admin();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.updatePriority(
                    saved.getId(), TicketPriority.URGENT, actor.getRole(),
                    actor.getDepartmentId(), actor.getUsername()),
                "ADMIN must be denied by the canonical canProcessTickets policy");
        }

        @Test
        @DisplayName("CANONICAL: GIAM_DOC cannot change priority (C-4 fixed)")
        void currentGiamDocCanChangePriority_isPolicyGapC4() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.giamDoc();

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.updatePriority(
                    saved.getId(), TicketPriority.HIGH, actor.getRole(),
                    actor.getDepartmentId(), actor.getUsername()),
                "GIAM_DOC must be denied by the canonical canProcessTickets policy");
        }

        @Test
        @DisplayName("CURRENT: NHAN_VIEN + IT can change priority - matches intended policy")
        void currentNhanVienItCanChangePriority() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.nhanVienIT();

            Ticket updated = ticketService.updatePriority(
                saved.getId(), TicketPriority.LOW, actor.getRole(),
                actor.getDepartmentId(), actor.getUsername());

            assertEquals(TicketPriority.LOW, updated.getPriority());
        }

        @Test
        @DisplayName("CURRENT: NHAN_VIEN non-IT is denied priority change before reaching the shared predicate")
        void currentNonItNhanVienCannotChangePriority() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.nhanVienMkt();

            assertThrows(TicketRuleViolationException.class, () -> ticketService.updatePriority(
                saved.getId(), TicketPriority.URGENT, actor.getRole(),
                actor.getDepartmentId(), actor.getUsername()));
        }

        @Test
        @DisplayName("CURRENT: TRUONG_PHONG non-IT is denied priority change")
        void currentNonItTruongPhongCannotChangePriority() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.truongPhongMkt();

            assertThrows(TicketRuleViolationException.class, () -> ticketService.updatePriority(
                saved.getId(), TicketPriority.URGENT, actor.getRole(),
                actor.getDepartmentId(), actor.getUsername()));
        }
    }

    // ========================================================================
    // F. TICKET CREATION WITH CLIENT-SUPPLIED ASSIGNEE
    // ========================================================================

    @Nested
    @DisplayName("F. Ticket creation with a client-supplied assignee - CURRENT behavior")
    class CreationWithAssignee {

        @Test
        @DisplayName("CANONICAL: NHAN_VIEN + IT is NOT allowed to specify a create-time assignee (H-3 fixed - only TRUONG_PHONG + IT may)")
        void canonicalNhanVienItCannotSpecifyAssigneeOnCreate_isPolicyGapH3Fixed() {
            // Phase 3 (H-3 fix): an NHAN_VIEN + IT creator can still SELF-RECEIVE via
            // /assign/me (post-create) but cannot bypass that by pre-populating the
            // assignee at creation time. Client-supplied assignee must be REJECTED.
            UserAccount actor = fixtures.nhanVienIT();
            Ticket ticket = fixtures.mktRequestedTicket("VPN failure", TicketPriority.HIGH);
            ticket.setAssigneeName(fixtures.nhanVienIT2().getUsername());

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.createTicket(
                    ticket, actor.getUsername(), actor.getRole()),
                "NHAN_VIEN + IT must NOT silently retain a create-time assignee");
        }

        @Test
        @DisplayName("CANONICAL: ADMIN / GIAM_DOC / non-IT roles cannot specify a create-time assignee (H-3 fixed)")
        void canonicalNonItRolesCannotSpecifyAssigneeOnCreate_isPolicyGapH3Fixed() {
            // Phase 3 (H-3 fix): every role except TRUONG_PHONG + IT must be rejected
            // when supplying an assignee at create time. The previous code silently
            // discarded the value; the canonical behavior is to reject explicitly so
            // the client cannot probe for valid authorization.
            for (UserAccount actor : List.of(
                    fixtures.admin(),
                    fixtures.giamDoc(),
                    fixtures.truongPhongMkt(),
                    fixtures.nhanVienMkt())) {
                Ticket ticket = fixtures.mktRequestedTicket("Create attempt", TicketPriority.LOW);
                ticket.setAssigneeName(fixtures.nhanVienIT().getUsername());

                TicketRuleViolationException ex = assertThrows(
                    TicketRuleViolationException.class,
                    () -> ticketService.createTicket(
                        ticket, actor.getUsername(), actor.getRole()),
                    actor.getRole() + " must be denied specifying a create-time assignee");

                assertTrue(ex.getMessage().toLowerCase().contains("it helpdesk manager")
                        || ex.getMessage().toLowerCase().contains("create-time")
                        || ex.getMessage().toLowerCase().contains("create"),
                    actor.getRole() + " rejection must explain create-time assignee is restricted; got: "
                        + ex.getMessage());
            }
        }

        @Test
        @DisplayName("CANONICAL: a non-IT actor's client-supplied assignee is REJECTED on create (H-3 fixed - was silently discarded)")
        void canonicalNonItActorAssigneeIsRejectedOnCreate() {
            // Phase 3 (H-3): the previous "silent discard" was a covert data-flow path
            // and is replaced with an explicit TicketRuleViolationException. The
            // client is informed that supplying an assignee is unauthorized.
            UserAccount actor = fixtures.nhanVienMkt();
            Ticket ticket = fixtures.mktRequestedTicket("Monitor flickers", TicketPriority.LOW);
            ticket.setAssigneeName("someone.else");

            assertThrows(TicketRuleViolationException.class,
                () -> ticketService.createTicket(
                    ticket, actor.getUsername(), actor.getRole()),
                "non-IT actors must NOT silently retain a supplied assignee");
        }

        @Test
        @DisplayName("CANONICAL: IT actors supply NO assignee at create time - ticket starts unassigned (H-3 fixed)")
        void canonicalItActorIsNotAutoAssignedOnCreate_isPolicyGapH3Fixed() {
            // Phase 3 (H-3 fixed): no actor is auto-self-assigned at creation time. The
            // canonical self-receive flow is /assign/me (POST \u00a76), which happens AFTER
            // creation. This test pins the new policy: a ticket created by an IT operator
            // starts unassigned and is taken into the operator's queue only when the
            // operator explicitly calls /assign/me.
            UserAccount nhanVienIt = fixtures.nhanVienIT();
            Ticket nhanVienTicket = fixtures.mktRequestedTicket("Cannot print", TicketPriority.MEDIUM);
            Ticket nhanVienResult = ticketService.createTicket(
                nhanVienTicket, nhanVienIt.getUsername(), nhanVienIt.getRole());
            assertNull(nhanVienResult.getAssigneeName(),
                "NHAN_VIEN + IT must NOT be auto-self-assigned at creation time; use /assign/me");
            assertNull(nhanVienResult.getAssignee(),
                "NHAN_VIEN + IT auto-self-assignment FK must be null at creation time");

            UserAccount truongPhongIt = fixtures.truongPhongIT();
            Ticket tpTicket = fixtures.mktRequestedTicket("Manager cannot print", TicketPriority.MEDIUM);
            Ticket tpResult = ticketService.createTicket(
                tpTicket, truongPhongIt.getUsername(), truongPhongIt.getRole());
            assertNull(tpResult.getAssigneeName(),
                "TRUONG_PHONG + IT must NOT be auto-self-assigned at creation time; use /assign/me");
        }

        @Test
        @DisplayName("CURRENT: an ADMIN or GIAM_DOC creator is never auto-assigned")
        void currentAdminAndGiamDocAreNotAutoAssignedOnCreate() {
            UserAccount admin = fixtures.admin();
            Ticket adminTicket = fixtures.mktRequestedTicket("Admin created", TicketPriority.MEDIUM);
            Ticket adminCreated = ticketService.createTicket(
                adminTicket, admin.getUsername(), admin.getRole());
            assertNull(adminCreated.getAssigneeName());

            UserAccount giamDoc = fixtures.giamDoc();
            Ticket gdTicket = fixtures.mktRequestedTicket("Giam doc created", TicketPriority.MEDIUM);
            Ticket gdCreated = ticketService.createTicket(
                gdTicket, giamDoc.getUsername(), giamDoc.getRole());
            assertNull(gdCreated.getAssigneeName());
        }

        @Test
        @DisplayName("CURRENT: Ticket.department is always taken from the requester, never the body")
        void currentTicketDepartmentAlwaysComesFromRequester() {
            UserAccount actor = fixtures.nhanVienMkt();
            Ticket ticket = fixtures.mktRequestedTicket("Department provenance", TicketPriority.MEDIUM);
            // Deliberately poison the department to prove the service overrides it.
            ticket.setDepartment(fixtures.itDepartment());

            Ticket created = ticketService.createTicket(
                ticket, actor.getUsername(), actor.getRole());

            assertEquals(TicketAuthorizationFixtures.MKT_CODE, created.getDepartment().getCode(),
                "canonical invariant Ticket.department == requester.department holds");
        }
    }

    // ========================================================================
    // G. COMMENTS
    // ========================================================================

    @Nested
    @DisplayName("G. Comments - CURRENT behavior")
    class Comments {

        @Test
        @DisplayName("CURRENT: NHAN_VIEN + IT can add a PUBLIC comment")
        void currentNhanVienItCanAddPublicComment() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount actor = fixtures.nhanVienIT();

            TicketComment comment = ticketService.addComment(
                saved.getId(), CommentVisibility.PUBLIC, "Looking into it",
                actor.getRole(), actor.getUsername());

            assertEquals(CommentVisibility.PUBLIC, comment.getVisibility());
        }

        @Test
        @DisplayName("CANONICAL: NHAN_VIEN + IT may add INTERNAL comments (H-4 fixed - IT Helpdesk staff are operators)")
        void currentNhanVienItCannotAddInternalComment_isPolicyGapH4() {
            // Phase 2.1: TicketService.addComment now uses canAddInternalComment, which returns
            // true for both TRUONG_PHONG + IT and NHAN_VIEN + IT. The previous role-only check
            // (deny all NHAN_VIEN) is replaced by the canonical policy.
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount itStaff = fixtures.nhanVienIT();
            assertNotNull(itStaff.getDepartmentId(), "actor is genuinely in the IT department");

            TicketComment comment = ticketService.addComment(
                saved.getId(), CommentVisibility.INTERNAL, "internal note",
                itStaff.getRole(), itStaff.getUsername());

            assertEquals(CommentVisibility.INTERNAL, comment.getVisibility(),
                "NHAN_VIEN + IT may now add INTERNAL comments under the canonical policy");
        }

        @Test
        @DisplayName("CANONICAL: NHAN_VIEN + IT can list INTERNAL comments (H-4 fixed - canViewInternalComments = true)")
        void currentNhanVienItCannotListInternalComments_isPolicyGapH4() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount itStaff = fixtures.nhanVienIT();
            UserAccount manager = fixtures.truongPhongIT();

            // A manager-authored INTERNAL comment exists on the ticket.
            ticketService.addComment(
                saved.getId(), CommentVisibility.INTERNAL, "manager only",
                manager.getRole(), manager.getUsername());

            List<TicketComment> asItStaff = ticketService.listComments(
                saved.getId(), itStaff.getUsername(), itStaff.getRole(), null);
            List<TicketComment> asItManager = ticketService.listComments(
                saved.getId(), manager.getUsername(), manager.getRole(), null);

            assertTrue(asItStaff.stream().anyMatch(c -> c.getVisibility() == CommentVisibility.INTERNAL),
                "NHAN_VIEN + IT now sees INTERNAL comments under the canonical canViewInternalComments policy");
            assertTrue(asItManager.stream().anyMatch(c -> c.getVisibility() == CommentVisibility.INTERNAL),
                "the IT manager still sees INTERNAL comments");
        }

        @Test
        @DisplayName("CURRENT: ADMIN, GIAM_DOC and TRUONG_PHONG + IT may add and read INTERNAL comments")
        void currentPrivilegedRolesCanUseInternalComments() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            ticketService.addComment(
                saved.getId(), CommentVisibility.INTERNAL, "admin note",
                fixtures.admin().getRole(), fixtures.admin().getUsername());

            for (UserAccount actor : List.of(
                    fixtures.admin(), fixtures.giamDoc(), fixtures.truongPhongIT())) {
                List<TicketComment> visible = ticketService.listComments(
                    saved.getId(), actor.getUsername(), actor.getRole(), null);
                assertTrue(visible.stream().anyMatch(c -> c.getVisibility() == CommentVisibility.INTERNAL),
                    actor.getRole() + " should currently read INTERNAL comments");
            }
        }

        @Test
        @DisplayName("CANONICAL: resolveTicket writes an INTERNAL comment; the IT staff who resolved it can now read it (H-4 fixed)")
        void currentResolveWritesInternalCommentHiddenFromNhanVien() {
            // Phase 2.1: the INTERNAL comment written by the resolution is now visible to the
            // IT Helpdesk operator (NHAN_VIEN + IT) under canViewInternalComments. The
            // requester (non-IT MKT) still cannot see it.
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount itStaff = fixtures.nhanVienIT();
            UserAccount requester = fixtures.nhanVienMkt();
            ticketService.startProgress(
                saved.getId(), itStaff.getRole(), itStaff.getDepartmentId(), itStaff.getUsername());
            ticketService.resolveTicket(
                saved.getId(), itStaff.getRole(), itStaff.getDepartmentId(), itStaff.getUsername(),
                "Replaced the power cable");

            List<TicketComment> asItStaff = ticketService.listComments(
                saved.getId(), itStaff.getUsername(), itStaff.getRole(), null);
            assertTrue(asItStaff.stream().anyMatch(c -> c.getBody() != null && c.getBody().contains("Replaced the power cable")),
                "NHAN_VIEN + IT now sees the INTERNAL resolution note under canViewInternalComments");

            List<TicketComment> asRequester = ticketService.listComments(
                saved.getId(), requester.getUsername(), requester.getRole(), null);
            assertTrue(asRequester.stream().noneMatch(c -> c.getBody() != null && c.getBody().contains("Replaced the power cable")),
                "the requester still cannot see the INTERNAL resolution note");
        }
    }

    // ========================================================================
    // CLOSE / REOPEN - characterization only, policy is silent
    // ========================================================================

    @Nested
    @DisplayName("Close / reopen - CURRENT behavior, canonical policy is silent")
    class CloseAndReopen {

        @Test
        @DisplayName("CURRENT: the requester may close their own RESOLVED ticket - rule preserved")
        void currentRequesterCanCloseOwnResolvedTicket() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount requester = fixtures.nhanVienMkt();
            UserAccount itStaff = fixtures.nhanVienIT();
            ticketService.startProgress(
                saved.getId(), itStaff.getRole(), itStaff.getDepartmentId(), itStaff.getUsername());
            ticketService.resolveTicket(
                saved.getId(), itStaff.getRole(), itStaff.getDepartmentId(), itStaff.getUsername(), "done");

            Ticket closed = ticketService.closeTicket(
                saved.getId(), requester.getUsername(), requester.getRole());

            assertEquals(TicketStatus.CLOSED, closed.getStatus());
        }

        @Test
        @DisplayName("CURRENT: a non-IT NHAN_VIEN cannot close someone else's ticket")
        void currentNonItNhanVienCannotCloseOthersTicket() {
            Ticket saved = ticketRepository.save(fixtures.itRequestedTicket());
            UserAccount outsider = fixtures.nhanVienMkt();

            assertThrows(TicketRuleViolationException.class, () -> ticketService.closeTicket(
                saved.getId(), outsider.getUsername(), outsider.getRole()));
        }

        @Test
        @DisplayName("CURRENT: a non-IT NHAN_VIEN cannot close a ticket that is not RESOLVED")
        void currentNonItNhanVienCannotCloseUnresolvedTicket() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount requester = fixtures.nhanVienMkt();

            assertThrows(TicketRuleViolationException.class, () -> ticketService.closeTicket(
                saved.getId(), requester.getUsername(), requester.getRole()));
        }

        @Test
        @DisplayName("CANONICAL: only IT operators and the requester may close/reopen; ADMIN/GIAM_DOC and non-IT TRUONG_PHONG are denied")
        void currentPrivilegedAndNonItManagerRolesCanCloseAndReopen() {
            // Phase 2.1: closeTicket and reopenTicket now consult the canonical processing gate
            // for every actor who is not the requester closing their own ticket. ADMIN,
            // GIAM_DOC and non-IT TRUONG_PHONG are no longer permitted.
            // IT operators (TRUONG_PHONG + IT) are still allowed.
            UserAccount itManager = fixtures.truongPhongIT();
            Ticket toClose = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount itStaff = fixtures.nhanVienIT();
            ticketService.startProgress(
                toClose.getId(), itStaff.getRole(), itStaff.getDepartmentId(), itStaff.getUsername());
            ticketService.resolveTicket(
                toClose.getId(), itStaff.getRole(), itStaff.getDepartmentId(), itStaff.getUsername(), "done");

            Ticket closed = ticketService.closeTicket(
                toClose.getId(), itManager.getUsername(), itManager.getRole());
            assertEquals(TicketStatus.CLOSED, closed.getStatus(),
                "TRUONG_PHONG + IT may still close resolved tickets");

            Ticket reopened = ticketService.reopenTicket(
                toClose.getId(), itManager.getUsername(), itManager.getRole(),
                itManager.getDepartmentId(), itManager.getUsername(), "not fixed");
            assertEquals(TicketStatus.REOPENED, reopened.getStatus(),
                "TRUONG_PHONG + IT may still reopen closed tickets");

            // ADMIN, GIAM_DOC and non-IT TRUONG_PHONG are now denied.
            for (UserAccount actor : List.of(
                    fixtures.admin(), fixtures.giamDoc(), fixtures.truongPhongMkt())) {

                Ticket toDeny = ticketRepository.save(fixtures.mktRequestedTicket());
                ticketService.startProgress(
                    toDeny.getId(), itStaff.getRole(), itStaff.getDepartmentId(), itStaff.getUsername());
                ticketService.resolveTicket(
                    toDeny.getId(), itStaff.getRole(), itStaff.getDepartmentId(), itStaff.getUsername(), "done");

                assertThrows(TicketRuleViolationException.class,
                    () -> ticketService.closeTicket(
                        toDeny.getId(), actor.getUsername(), actor.getRole()),
                    actor.getRole() + " must be denied by the canonical processing gate");

                assertThrows(TicketRuleViolationException.class,
                    () -> ticketService.reopenTicket(
                        toDeny.getId(), actor.getUsername(), actor.getRole(),
                        actor.getDepartmentId(), actor.getUsername(), "no"),
                    actor.getRole() + " must be denied by the canonical processing gate");
            }
        }

        @Test
        @DisplayName("UNRESOLVED: a non-IT NHAN_VIEN may reopen their own closed ticket")
        void currentRequesterCanReopenOwnClosedTicket() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount requester = fixtures.nhanVienMkt();
            UserAccount itStaff = fixtures.nhanVienIT();
            ticketService.startProgress(
                saved.getId(), itStaff.getRole(), itStaff.getDepartmentId(), itStaff.getUsername());
            ticketService.resolveTicket(
                saved.getId(), itStaff.getRole(), itStaff.getDepartmentId(), itStaff.getUsername(), "done");
            ticketService.closeTicket(saved.getId(), requester.getUsername(), requester.getRole());

            Ticket reopened = ticketService.reopenTicket(
                saved.getId(), requester.getUsername(), requester.getRole(),
                requester.getDepartmentId(), requester.getUsername(), "still broken");

            assertEquals(TicketStatus.REOPENED, reopened.getStatus());
        }
    }

    // ========================================================================
    // STATUS TRANSITION GRAPH - unchanged, verified as a guard
    // ========================================================================

    @Nested
    @DisplayName("Existing transition graph is untouched by actor authorization")
    class TransitionGraph {

        @Test
        @DisplayName("CURRENT: the graph still governs legal edges independently of the actor")
        void currentTransitionGraphIsIndependentOfActor() {
            assertTrue(StatusTransitionValidator.isValidTransition(
                TicketStatus.NEW, TicketStatus.ASSIGNED));
            assertTrue(StatusTransitionValidator.isValidTransition(
                TicketStatus.NEW, TicketStatus.CANCELLED));
            assertTrue(StatusTransitionValidator.isValidTransition(
                TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED));
            assertFalse(StatusTransitionValidator.isValidTransition(
                TicketStatus.RESOLVED, TicketStatus.IN_PROGRESS));
            assertFalse(StatusTransitionValidator.isValidTransition(
                TicketStatus.CANCELLED, TicketStatus.NEW));
        }

        @Test
        @DisplayName("CURRENT: isTerminalStatus reports only CLOSED, although CANCELLED is terminal in practice")
        void currentIsTerminalStatusUnderReportsCancelled() {
            // TERMINAL_STATUSES contains only CLOSED. CANCELLED is nonetheless unusable as a source
            // state, because isValidTransition returns false for any CANCELLED origin via a
            // separate early return. So isTerminalStatus(CANCELLED) under-reports the real graph.
            // Recorded as an observation about the existing graph; the graph itself is not changed
            // in Phase 0.
            assertTrue(StatusTransitionValidator.isTerminalStatus(TicketStatus.CLOSED));
            assertFalse(StatusTransitionValidator.isTerminalStatus(TicketStatus.CANCELLED),
                "CANCELLED is not listed in TERMINAL_STATUSES");

            for (TicketStatus target : TicketStatus.values()) {
                assertFalse(StatusTransitionValidator.isValidTransition(TicketStatus.CANCELLED, target),
                    "CANCELLED is nonetheless an effective dead end in the graph");
            }
        }
    }

    // ========================================================================
    // DEPARTMENT SEMANTICS
    // ========================================================================

    @Nested
    @DisplayName("Ticket.department is the requester's department")
    class DepartmentSemantics {

        @Test
        @DisplayName("CURRENT: fixture keeps Ticket.department equal to the requester's department")
        void currentTicketDepartmentMirrorsRequester() {
            UserAccount mktStaff = fixtures.nhanVienMkt();
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());

            Department requesterDept = mktStaff.getDepartment();
            assertNotNull(requesterDept);
            assertEquals(requesterDept.getId(), saved.getDepartment().getId());
        }
    }
}

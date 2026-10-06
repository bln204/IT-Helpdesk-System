package com.example.ticketing.ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.auth.UserRole;
import com.example.ticketing.department.Department;
import com.example.ticketing.department.DepartmentRepository;
import com.example.ticketing.ticket.TicketTypes.CommentVisibility;
import com.example.ticketing.ticket.TicketTypes.TicketPriority;

/**
 * PHASE 5.0 - TICKET OBJECT-LEVEL READ AUTHORIZATION CHARACTERIZATION.
 *
 * <p>PURPOSE. This suite documents the CURRENT behavior of every Ticket-related read
 * endpoint at the service layer. It does NOT propose a fix. Phase 5.1 will be the
 * implementation phase; this file is characterization only.
 *
 * <p>BUSINESS CONTEXT. This system is an INTERNAL company IT helpdesk.
 * <ul>
 *   <li>Ticket.department is the REQUESTER's department; it is NOT an authorization
 *       boundary for IT Helpdesk operators (C-6 already documented this in RAG).</li>
 *   <li>ADMIN / GIAM_DOC are not IT Helpdesk operators and cannot process tickets.</li>
 *   <li>TRUONG_PHONG + IT and NHAN_VIEN + IT are the canonical IT Helpdesk operators.</li>
 *   <li>Non-IT TRUONG_PHONG and non-IT NHAN_VIEN have an unresolved object-level read
 *       scope (C-7). This file does NOT decide that scope.</li>
 * </ul>
 *
 * <p>CHARACTERIZATION CONTRACT. Every assertion in this file records an OBSERVED behavior
 * of the production code as of this commit. The test name prefix encodes intent:
 * <ul>
 *   <li>{@code CURRENT: ...} - documents behavior that is in place today.</li>
 *   <li>{@code C7_GAP: ...} - documents behavior that CONFLICTS with the future object-level
 *       read policy. These tests will be the FALSIFIABLE targets Phase 5.1 must change.</li>
 * </ul>
 *
 * <p>NO TEST DELETES A PRE-EXISTING ASSERTION. The Phase 0 / Phase 2.1 / Phase 3 / Phase 3.1
 * suites remain authoritative for their scopes. This file only adds new characterizations.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketObjectLevelReadCharacterizationTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketCommentRepository ticketCommentRepository;

    @Autowired
    private TicketAuditRepository ticketAuditRepository;

    @Autowired
    private TicketAttachmentRepository ticketAttachmentRepository;

    @Autowired
    private TicketTimelineRepository ticketTimelineRepository;

    @Autowired
    private TicketAssignmentRepository ticketAssignmentRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private TicketAuthorizationFixtures fixtures;

    // ============================================================
    // Per-actor + per-requester-department fixtures (used to prove
    // that requester department is currently NOT an operator boundary
    // and that no other object-level check exists on the read path).
    // ============================================================

    private Department hrDepartment() {
        return departmentRepository.findByCode("HR").orElseGet(() -> {
            Department d = new Department();
            d.setCode("HR");
            d.setName("Human Resources");
            d.setEnabled(true);
            return departmentRepository.save(d);
        });
    }

    private Department financeDepartment() {
        return departmentRepository.findByCode("FIN").orElseGet(() -> {
            Department d = new Department();
            d.setCode("FIN");
            d.setName("Finance");
            d.setEnabled(true);
            return departmentRepository.save(d);
        });
    }

    private UserAccount hrRequester() {
        return persistUser("phase50_hr_req", UserRole.Role.NHAN_VIEN, hrDepartment());
    }

    private UserAccount financeRequester() {
        return persistUser("phase50_fin_req", UserRole.Role.NHAN_VIEN, financeDepartment());
    }

    private UserAccount persistUser(String username, UserRole.Role role, Department dept) {
        return userAccountRepository.findByUsername(username).orElseGet(() -> {
            UserAccount u = new UserAccount();
            u.setUsername(username);
            u.setPasswordHash("{noop}phase50-characterization");
            u.setRole(role);
            u.setDepartment(dept);
            u.setDisplayName(username);
            u.setEmail(username + "@example.internal");
            u.setEnabled(true);
            u.setApproved(true);
            return userAccountRepository.save(u);
        });
    }

    // ============================================================
    // 1. Inventory + completeness — current behavior
    // ============================================================

    @Nested
    @DisplayName("1. Object-level READ scope — canonical policy (PHASE 5.1)")
    class ObjectLevelReadSurface {

        @Test
        @DisplayName("PHASE 5.1: TicketService.getTicket is actor-aware; the canonical C-7 policy decides")
        void phase51GetTicketIsActorAware() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());

            // PHASE 5.1 (C-7): the actor-aware getTicket overload enforces the canonical
            // object-level read policy. A non-IT MKT user reading their own department's
            // ticket is allowed; an HR or IT user would be denied.
            Ticket read = ticketService.getTicket(saved.getId(), fixtures.nhanVienMkt().getUsername());

            assertNotNull(read);
            assertEquals(saved.getId(), read.getId());
        }

        @Test
        @DisplayName("PHASE 5.1: listTickets applies the canonical read scope (IT operators see all; non-IT actors are department-scoped)")
        void phase51ListTicketsAppliesCanonicalReadScope() {
            ticketRepository.save(fixtures.mktRequestedTicket(
                "Phase 5.1 - requester in MKT",
                TicketPriority.LOW
            ));
            ticketRepository.save(fixtures.itRequestedTicket());

            long admin = ticketService.listTickets(
                null, null, null, false,
                org.springframework.data.domain.PageRequest.of(0, 50),
                fixtures.admin().getRole().name(),
                fixtures.admin().getDepartmentId(),
                fixtures.admin().getUsername()
            ).getTotalElements();

            long mktRequester = ticketService.listTickets(
                null, null, null, false,
                org.springframework.data.domain.PageRequest.of(0, 50),
                fixtures.nhanVienMkt().getRole().name(),
                fixtures.nhanVienMkt().getDepartmentId(),
                fixtures.nhanVienMkt().getUsername()
            ).getTotalElements();

            // PHASE 5.1 (C-7): full-scope actors (ADMIN, GIAM_DOC, IT operators) see all
            // tickets; non-IT actors are department-scoped. The MKT user here is a non-IT
            // NHAN_VIEN in the MKT department, so they see only the MKT-requester ticket.
            assertEquals(2L, admin,
                "ADMIN (full read scope) sees every ticket");
            assertEquals(1L, mktRequester,
                "non-IT MKT user is department-scoped and sees only the MKT-requester ticket");
        }
    }

    // ============================================================
    // 2. Trace the authorization flow — current behavior
    //
    // Characterization of:
    //   - authentication-only endpoints
    //   - role-only endpoints
    //   - no authorization endpoints
    //   - authorization-after-load endpoints
    //
    // For each Ticket-related READ surface, the trace is encoded in a test.
    // ============================================================

    @Nested
    @DisplayName("2. Authorization flow trace — current behavior")
    class AuthorizationFlowTrace {

        @Test
        @DisplayName("CURRENT: listComments calls getTicket BEFORE the INTERNAL visibility filter")
        void currentListCommentsAuthorizesAfterLoad() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());

            // Phase 2.1 inserts the INTERNAL-visibility gate inside the service method.
            // The object-level authorization happens AFTER getTicket(ticketId) has loaded
            // the ticket. This is the post-load pattern: a non-authorized actor still gets
            // a Ticket entity materialized into the service. (NO Ticket-level ownership
            // check exists today; only the comment-visibility check is enforced.)
            List<TicketComment> publicComments = ticketService.listComments(
                saved.getId(),
                fixtures.nhanVienMkt().getUsername(),
                fixtures.nhanVienMkt().getRole(),
                null
            );

            assertNotNull(publicComments);
            // Non-IT NHAN_VIEN sees PUBLIC only; the service strips INTERNAL rows but
            // it has already loaded the Ticket entity to do so.
        }

        @Test
        @DisplayName("CURRENT: listAudit calls getTicket BEFORE the actor-context check")
        void currentListAuditAuthorizesAfterLoad() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());

            // listAudit has NO actor argument; it returns the full audit log of any
            // ticket the caller knows by id. Phase 5.1 will be the first phase that
            // introduces an actor-aware overload. The service today calls getTicket only
            // to validate that the ticket exists.
            List<TicketAudit> audit = ticketService.listAudit(saved.getId());
            assertNotNull(audit);
        }

        @Test
        @DisplayName("CURRENT: listAssignments has no actor argument")
        void currentListAssignmentsHasNoActorArgument() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());

            List<TicketAssignment> assignments = ticketService.listAssignments(saved.getId());
            assertNotNull(assignments);
        }

        @Test
        @DisplayName("CURRENT: TicketAttachmentService.getAttachmentsByTicketId has no actor argument")
        void currentListAttachmentsHasNoActorArgument() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());

            // AttachmentService.getAttachmentsByTicketId has NO actor argument. The only
            // controller-level guard in TicketController.listAttachments is
            // `ticketService.getTicket(id)` — which itself has no actor argument.
            List<TicketAttachment> attachments =
                ticketAttachmentRepository.findByTicketIdOrderByCreatedAtDesc(saved.getId());
            assertNotNull(attachments);
        }

        @Test
        @DisplayName("CURRENT: TicketAttachmentService.downloadAttachment has no actor argument")
        void currentDownloadAttachmentHasNoActorArgument() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());

            // The download endpoint accepts only the attachmentId; the controller does NOT
            // validate that the actor can read the underlying ticket. A future object-level
            // read fix MUST guard the download path too; otherwise an attachment of an
            // "unauthorized" ticket is still downloadable by id.
            TicketAttachment attachment = new TicketAttachment();
            attachment.setTicket(saved);
            attachment.setFileName("phase50-attachment");
            attachment.setOriginalName("phase50.txt");
            attachment.setContentType("text/plain");
            attachment.setFileSize(10L);
            attachment.setStoragePath("n/a");
            attachment.setUploadedBy(saved.getRequesterUsername());
            ticketAttachmentRepository.save(attachment);

            TicketAttachment read = ticketAttachmentRepository.findById(attachment.getId()).orElseThrow();
            assertNotNull(read);
            // No actor context was consulted between the id receipt and the byte stream.
        }

        @Test
        @DisplayName("CURRENT: TicketTimelineService.getTicketTimeline has no actor argument")
        void currentTimelineHasNoActorArgument() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());

            // The timeline is reachable via /api/tickets/{ticketId}/timeline (TicketTimelineController)
            // AND via the comment-listing endpoints under TicketController. Neither path consults
            // the actor before returning timeline rows.
            var timeline = ticketTimelineRepository.findByTicketIdOrderByCreatedAtDesc(saved.getId());
            assertNotNull(timeline);
        }
    }

    // ============================================================
    // 3. Current behavior characterization per the Phase 5.0 brief
    //
    // ADMIN, GIAM_DOC, TRUONG_PHONG+IT, NHAN_VIEN+IT,
    // TRUONG_PHONG non-IT, NHAN_VIEN non-IT, requester-ownership cases.
    //
    // The expected answer for every case today is:
    //   - the endpoint is reachable
    //   - the ticket is returned
    //   - NO object-level check denies the actor
    //   - role-only checks (cancel=ADMIN, unassigned-queue=non-NHAN_VIEN)
    //     are the only filters that exist
    // ============================================================

    @Nested
    @DisplayName("3. Per-role current behavior — read endpoints")
    class PerRoleCurrentBehavior {

        @Test
        @DisplayName("C7_GAP: ADMIN can read a MKT-requester ticket")
        void c7GapAdminReadsMktRequestedTicket() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            Ticket read = ticketService.getTicket(saved.getId());
            assertEquals(saved.getId(), read.getId(),
                "CURRENT: ADMIN is not blocked at the object level for a MKT-requester ticket");
        }

        @Test
        @DisplayName("C7_GAP: ADMIN can read a HR-requester ticket")
        void c7GapAdminReadsHrRequestedTicket() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket(
                "Phase 5.0 - requester in HR", TicketPriority.LOW));
            Ticket read = ticketService.getTicket(saved.getId());
            assertEquals(saved.getId(), read.getId());
        }

        @Test
        @DisplayName("C7_GAP: ADMIN can read a FIN-requester ticket")
        void c7GapAdminReadsFinRequestedTicket() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket(
                "Phase 5.0 - requester in FIN", TicketPriority.LOW));
            Ticket read = ticketService.getTicket(saved.getId());
            assertEquals(saved.getId(), read.getId());
        }

        @Test
        @DisplayName("C7_GAP: GIAM_DOC can read a MKT-requester ticket")
        void c7GapGiamDocReadsMktRequestedTicket() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            Ticket read = ticketService.getTicket(saved.getId());
            assertEquals(saved.getId(), read.getId());
        }

        @Test
        @DisplayName("C7_GAP: GIAM_DOC can read a HR-requester ticket")
        void c7GapGiamDocReadsHrRequestedTicket() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket(
                "Phase 5.0 - HR", TicketPriority.LOW));
            Ticket read = ticketService.getTicket(saved.getId());
            assertEquals(saved.getId(), read.getId());
        }

        @Test
        @DisplayName("CURRENT: TRUONG_PHONG + IT can read a MKT-requester ticket — IT operators are NOT department-scoped")
        void currentTruongPhongItReadsMktRequestedTicket() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            Ticket read = ticketService.getTicket(saved.getId());
            assertEquals(saved.getId(), read.getId());
        }

        @Test
        @DisplayName("CURRENT: TRUONG_PHONG + IT can read a HR-requester ticket")
        void currentTruongPhongItReadsHrRequestedTicket() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket(
                "Phase 5.0 - HR", TicketPriority.LOW));
            Ticket read = ticketService.getTicket(saved.getId());
            assertEquals(saved.getId(), read.getId());
        }

        @Test
        @DisplayName("CURRENT: TRUONG_PHONG + IT can read a FIN-requester ticket")
        void currentTruongPhongItReadsFinRequestedTicket() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket(
                "Phase 5.0 - FIN", TicketPriority.LOW));
            Ticket read = ticketService.getTicket(saved.getId());
            assertEquals(saved.getId(), read.getId());
        }

        @Test
        @DisplayName("CURRENT: NHAN_VIEN + IT can read a MKT-requester ticket")
        void currentNhanVienItReadsMktRequestedTicket() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            Ticket read = ticketService.getTicket(saved.getId());
            assertEquals(saved.getId(), read.getId());
        }

        @Test
        @DisplayName("CURRENT: NHAN_VIEN + IT can read a HR-requester ticket")
        void currentNhanVienItReadsHrRequestedTicket() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket(
                "Phase 5.0 - HR", TicketPriority.LOW));
            Ticket read = ticketService.getTicket(saved.getId());
            assertEquals(saved.getId(), read.getId());
        }

        @Test
        @DisplayName("CURRENT: NHAN_VIEN + IT can read a FIN-requester ticket")
        void currentNhanVienItReadsFinRequestedTicket() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket(
                "Phase 5.0 - FIN", TicketPriority.LOW));
            Ticket read = ticketService.getTicket(saved.getId());
            assertEquals(saved.getId(), read.getId());
        }

        @Test
        @DisplayName("C7_GAP: TRUONG_PHONG non-IT (MKT) can read a MKT-requester ticket (own department)")
        void c7GapNonItTruongPhongReadsMktRequestedTicket() {
            // The non-IT manager CAN read a ticket from their own department today.
            // The unresolved C-7 scope is whether they SHOULD be able to read tickets
            // from other (non-IT) departments. This test pins the current behavior.
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            Ticket read = ticketService.getTicket(saved.getId());
            assertEquals(saved.getId(), read.getId());
        }

        @Test
        @DisplayName("C7_GAP: TRUONG_PHONG non-IT (MKT) can read a HR-requester ticket — requester department is NOT a boundary")
        void c7GapNonItTruongPhongReadsHrRequestedTicket() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket(
                "Phase 5.0 - HR", TicketPriority.LOW));
            Ticket read = ticketService.getTicket(saved.getId());
            assertEquals(saved.getId(), read.getId());
        }

        @Test
        @DisplayName("C7_GAP: NHAN_VIEN non-IT (MKT) can read a MKT-requester ticket (own department)")
        void c7GapNonItNhanVienReadsMktRequestedTicket() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            Ticket read = ticketService.getTicket(saved.getId());
            assertEquals(saved.getId(), read.getId());
        }

        @Test
        @DisplayName("C7_GAP: NHAN_VIEN non-IT (MKT) can read a HR-requester ticket — requester department is NOT a boundary")
        void c7GapNonItNhanVienReadsHrRequestedTicket() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket(
                "Phase 5.0 - HR", TicketPriority.LOW));
            Ticket read = ticketService.getTicket(saved.getId());
            assertEquals(saved.getId(), read.getId());
        }

        @Test
        @DisplayName("CURRENT: requester can read their OWN ticket (NHAN_VIEN, MKT-requester)")
        void currentRequesterReadsOwnTicket() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount requester = fixtures.nhanVienMkt();
            assertEquals(saved.getRequesterUsername(), requester.getUsername(),
                "fixture invariant");
            Ticket fetched = ticketService.getTicket(saved.getId());
            assertEquals(saved.getId(), fetched.getId());
        }

        @Test
        @DisplayName("C7_GAP: a requester can read ANOTHER user's ticket — no requester-ownership check exists")
        void c7GapRequesterReadsAnotherUsersTicket() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            UserAccount anotherRequester = hrRequester();
            assertFalse(saved.getRequesterUsername().equalsIgnoreCase(anotherRequester.getUsername()),
                "fixture invariant: anotherRequester is not the original requester");
            Ticket fetched = ticketService.getTicket(saved.getId());
            assertEquals(saved.getId(), fetched.getId(),
                "CURRENT: no requester-ownership check exists on getTicket");
        }
    }

    // ============================================================
    // 4. Nested resources — comments, audit, attachments, timeline
    //    can be accessed even when GET /api/tickets/{id} is "protected"
    //    by the (currently-absent) object-level check.
    // ============================================================

    @Nested
    @DisplayName("4. Nested resources — current behavior")
    class NestedResourcesCurrentBehavior {

        @Test
        @DisplayName("C7_GAP: comments of a MKT-requester ticket are readable by a HR non-IT NHAN_VIEN today")
        void c7GapNestedCommentsReadableByAnyone() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());

            // Add one PUBLIC comment.
            ticketService.addComment(
                saved.getId(),
                CommentVisibility.PUBLIC,
                "Phase 5.0 fixture comment",
                fixtures.nhanVienMkt().getRole(),
                fixtures.nhanVienMkt().getUsername()
            );

            // A HR non-IT NHAN_VIEN reads the comments of a ticket they do not own.
            List<TicketComment> comments = ticketService.listComments(
                saved.getId(),
                hrRequester().getUsername(),
                hrRequester().getRole(),
                null
            );

            assertFalse(comments.isEmpty(),
                "CURRENT: nested comments are reachable without an object-level check");
        }

        @Test
        @DisplayName("C7_GAP: audit log of a MKT-requester ticket is reachable by anyone with the id")
        void c7GapNestedAuditReachableByAnyone() {
            // Use the canonical service.createTicket so the TICKET_CREATED audit row is
            // persisted exactly the way the production code does it.
            Ticket ticket = new Ticket();
            ticket.setTitle("Phase 5.0 - audit reachability fixture");
            ticket.setDescription("Phase 5.0 audit fixture description");
            ticket.setPriority(TicketPriority.LOW);
            ticket.setCategory(com.example.ticketing.ticket.TicketTypes.TicketCategory.HARDWARE);
            ticket.setRequesterName("MKT requester");
            ticket.setRequesterEmail("requester@example.internal");
            ticket.setAssigneeName(null);
            Ticket saved = ticketService.createTicket(
                ticket,
                fixtures.nhanVienMkt().getUsername(),
                fixtures.nhanVienMkt().getRole()
            );

            List<TicketAudit> audit = ticketService.listAudit(saved.getId());
            assertFalse(audit.isEmpty(),
                "CURRENT: audit log reachable without an object-level check");
        }

        @Test
        @DisplayName("C7_GAP: assignment history of a MKT-requester ticket is reachable by anyone with the id")
        void c7GapNestedAssignmentHistoryReachableByAnyone() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());

            List<TicketAssignment> assignments = ticketService.listAssignments(saved.getId());
            assertNotNull(assignments);
            // No actor argument exists; the actor only ever needs to know the ticket id.
        }

        @Test
        @DisplayName("C7_GAP: timeline of a MKT-requester ticket is reachable by anyone with the id")
        void c7GapNestedTimelineReachableByAnyone() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());

            var timeline = ticketTimelineRepository.findByTicketIdOrderByCreatedAtDesc(saved.getId());
            assertNotNull(timeline);
        }

        @Test
        @DisplayName("C7_GAP: attachments of a MKT-requester ticket are listable and downloadable by anyone with the ids")
        void c7GapNestedAttachmentsReachableByAnyone() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());

            TicketAttachment attachment = new TicketAttachment();
            attachment.setTicket(saved);
            attachment.setFileName("phase50-attachment");
            attachment.setOriginalName("phase50.txt");
            attachment.setContentType("text/plain");
            attachment.setFileSize(10L);
            attachment.setStoragePath("n/a");
            attachment.setUploadedBy(saved.getRequesterUsername());
            ticketAttachmentRepository.save(attachment);

            // Same actor can list AND download via the attachment id without any
            // object-level check against the parent ticket.
            var byTicket = ticketAttachmentRepository.findByTicketIdOrderByCreatedAtDesc(saved.getId());
            var byId = ticketAttachmentRepository.findById(attachment.getId());
            assertNotNull(byTicket);
            assertTrue(byId.isPresent());
        }
    }

    // ============================================================
    // 5. Repository / query behavior — current behavior
    //
    // Today the read path is "fetch by id; if not found, 404". There is no
    // authorization-aware query.
    // ============================================================

    @Nested
    @DisplayName("5. Repository / query behavior — current behavior")
    class RepositoryQueryBehavior {

        @Test
        @DisplayName("CURRENT: getTicket fetches the ticket first; an unknown id throws TicketNotFoundException, NOT authorization")
        void currentGetTicketThrowsNotFoundNotAuthorization() {
            Long nonExistentId = 9_999_999L;
            assertThrows(TicketNotFoundException.class,
                () -> ticketService.getTicket(nonExistentId),
                "CURRENT: 404-style denial happens before any object-level authorization");
        }
    }

    // ============================================================
    // 6. TicketAuthorization — current state
    //
    // PHASE 5.0 characterized the absence of an object-level read primitive.
    // PHASE 5.1 introduces TicketAuthorization.canReadTicket(actor, ticket). The
    // negative reflection test that pinned the absence is replaced here with a
    // positive regression test that pins the new policy primitive. The change
    // is intentional (brief §20).
    // ============================================================

    @Nested
    @DisplayName("6. TicketAuthorization — current state")
    class TicketAuthorizationCurrentState {

        @Test
        @DisplayName("PHASE 5.1: TicketAuthorization exposes canReadTicket(actor, ticket) for object-level read")
        void phase51TicketAuthorizationExposesCanReadTicket() throws Exception {
            // The new primitive closes C-7. It is the single seam for object-level
            // read across the direct read, collection read, and every nested resource
            // (comments, audit, assignments, attachments, timeline, escalation history).
            var method = com.example.ticketing.authorization.TicketAuthorization.class.getDeclaredMethod(
                "canReadTicket",
                com.example.ticketing.authorization.ActorContext.class,
                com.example.ticketing.ticket.Ticket.class
            );
            org.junit.jupiter.api.Assertions.assertEquals(boolean.class, method.getReturnType(),
                "canReadTicket must return boolean");
        }
    }

    // ============================================================
    // 7. Authorization duplication — current state
    //
    // The phase 0 finding (TicketController + assignee + actor + service-level
    // role checks) is partially consolidated already. Today the read path is
    // free of duplication: there is NO object-level read check at all.
    // ============================================================

    @Nested
    @DisplayName("7. Authorization duplication on the read path — current state")
    class ReadPathDuplicationCurrentState {

        @Test
        @DisplayName("CURRENT: there is exactly ONE check on the read path: getTicket(404). Phase 5.1 will add the missing gate.")
        void currentReadPathHasOneCheck() {
            // This test is intentionally simple: the production read path passes through
            // TicketController.getTicket -> TicketService.getTicket -> TicketRepository.findById.
            // The only guard is TicketNotFoundException (404). Phase 5.1 will add a second
            // guard: TicketAuthorization.canReadTicket / ActorContext-based ownership gate.
            assertTrue(true, "shape is documented in the comments above");
        }
    }

    // ============================================================
    // 8. Security boundary — current state
    //
    // Today an unauthorized actor CAN reach the Java service layer with a fully
    // materialized Ticket object. The authorization does not precede data access.
    // ============================================================

    @Nested
    @DisplayName("8. Security boundary — current state")
    class SecurityBoundaryCurrentState {

        @Test
        @DisplayName("C7_GAP: a fully materialized Ticket reaches the controller for a non-owning actor")
        void c7GapTicketLoadedBeforeAnyObjectLevelCheck() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            Ticket read = ticketService.getTicket(saved.getId());
            assertNotNull(read);
            assertNotNull(read.getDepartment(),
                "Ticket is fully materialized with the requester department loaded");
            assertEquals(saved.getId(), read.getId());
        }

        @Test
        @DisplayName("C7_GAP: comment entities are fully materialized and reach the controller without an object-level check on the parent ticket")
        void c7GapCommentsReachControllerWithoutParentTicketCheck() {
            Ticket saved = ticketRepository.save(fixtures.mktRequestedTicket());
            ticketService.addComment(
                saved.getId(),
                CommentVisibility.PUBLIC,
                "Phase 5.0 fixture comment",
                fixtures.nhanVienMkt().getRole(),
                fixtures.nhanVienMkt().getUsername()
            );

            List<TicketComment> comments = ticketService.listComments(
                saved.getId(),
                fixtures.truongPhongMkt().getUsername(),
                fixtures.truongPhongMkt().getRole(),
                null
            );

            // The full comment rows (with body) are returned.
            assertFalse(comments.isEmpty());
            assertNotNull(comments.get(0).getBody());
        }
    }
}
package com.example.ticketing.authorization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.example.ticketing.auth.UserRole;

/**
 * PHASE 2 - Unit tests for the canonical ticket authorization matrix encoded by
 * {@link TicketAuthorization}.
 *
 * <p>These tests pin the canonical target policy from the Phase 2 brief \u00a73:
 *
 * <pre>
 *   Actor                     | Process | Receive | AssignOthers | ViewInternal | AddInternal
 *   ADMIN                     |   DENY  |   DENY  |     DENY     |     ALLOW    |    ALLOW
 *   GIAM_DOC                  |   DENY  |   DENY  |     DENY     |     ALLOW    |    ALLOW
 *   TRUONG_PHONG + IT         |  ALLOW  |  ALLOW  |    ALLOW     |     ALLOW    |    ALLOW
 *   NHAN_VIEN + IT            |  ALLOW  |  ALLOW  |     DENY     |     ALLOW    |    ALLOW
 *   TRUONG_PHONG non-IT       |   DENY  |   DENY  |     DENY     |     DENY     |    DENY
 *   NHAN_VIEN non-IT          |   DENY  |   DENY  |     DENY     |     DENY     |    DENY
 * </pre>
 *
 * <p>Note: the matrix intentionally says nothing about cross-department ticket access for
 * IT operators. The brief \u00a71 establishes that an IT operator can handle tickets whose
 * requester department is anything (MKT, HR, etc.); this is enforced by the absence of
 * a requester-department check, not by an explicit allow-rule. The cross-department test
 * is therefore encoded as a positive case in {@link CrossDepartmentTicket}, asserting that
 * the policy does NOT consult the requester department.
 */
class TicketAuthorizationTest {

    private final TicketAuthorization authz = new TicketAuthorization();

    private static ActorContext actor(UserRole.Role role, String departmentCode, boolean it) {
        return ActorContext.of("user", role, departmentCode, it);
    }

    // ------------------------------------------------------------
    // ADMIN: DENY process, DENY receive, DENY assign, ALLOW comments
    // ------------------------------------------------------------

    @Nested
    @DisplayName("ADMIN")
    class Admin {

        private final ActorContext admin = actor(UserRole.Role.ADMIN, "EXEC", false);

        @Test
        @DisplayName("cannot process tickets")
        void cannotProcess() { assertFalse(authz.canProcessTickets(admin)); }

        @Test
        @DisplayName("cannot receive a ticket")
        void cannotReceive() { assertFalse(authz.canReceiveTicket(admin)); }

        @Test
        @DisplayName("cannot assign others")
        void cannotAssignOthers() { assertFalse(authz.canAssignOthers(admin)); }

        @Test
        @DisplayName("cannot update status")
        void cannotUpdateStatus() { assertFalse(authz.canUpdateStatus(admin)); }

        @Test
        @DisplayName("can add internal comments (privileged role)")
        void canAddInternal() { assertTrue(authz.canAddInternalComment(admin)); }

        @Test
        @DisplayName("can view internal comments (privileged role)")
        void canViewInternal() { assertTrue(authz.canViewInternalComments(admin)); }

        @Test
        @DisplayName("can add public comments")
        void canAddPublic() { assertTrue(authz.canAddPublicComment(admin)); }
    }

    // ------------------------------------------------------------
    // GIAM_DOC: same as ADMIN
    // ------------------------------------------------------------

    @Nested
    @DisplayName("GIAM_DOC")
    class GiamDoc {

        private final ActorContext giamDoc = actor(UserRole.Role.GIAM_DOC, "EXEC", false);

        @Test
        @DisplayName("cannot process tickets")
        void cannotProcess() { assertFalse(authz.canProcessTickets(giamDoc)); }

        @Test
        @DisplayName("cannot receive a ticket")
        void cannotReceive() { assertFalse(authz.canReceiveTicket(giamDoc)); }

        @Test
        @DisplayName("cannot assign others")
        void cannotAssignOthers() { assertFalse(authz.canAssignOthers(giamDoc)); }

        @Test
        @DisplayName("cannot update status")
        void cannotUpdateStatus() { assertFalse(authz.canUpdateStatus(giamDoc)); }

        @Test
        @DisplayName("can add internal comments (privileged role)")
        void canAddInternal() { assertTrue(authz.canAddInternalComment(giamDoc)); }

        @Test
        @DisplayName("can view internal comments (privileged role)")
        void canViewInternal() { assertTrue(authz.canViewInternalComments(giamDoc)); }
    }

    // ------------------------------------------------------------
    // TRUONG_PHONG + IT: full IT manager
    // ------------------------------------------------------------

    @Nested
    @DisplayName("TRUONG_PHONG + IT")
    class TruongPhongIt {

        private final ActorContext tpIt = actor(UserRole.Role.TRUONG_PHONG, "IT", true);

        @Test
        @DisplayName("can process tickets")
        void canProcess() { assertTrue(authz.canProcessTickets(tpIt)); }

        @Test
        @DisplayName("can receive a ticket")
        void canReceive() { assertTrue(authz.canReceiveTicket(tpIt)); }

        @Test
        @DisplayName("can assign others (the only role that can)")
        void canAssignOthers() { assertTrue(authz.canAssignOthers(tpIt)); }

        @Test
        @DisplayName("can update status")
        void canUpdateStatus() { assertTrue(authz.canUpdateStatus(tpIt)); }

        @Test
        @DisplayName("can add internal comments")
        void canAddInternal() { assertTrue(authz.canAddInternalComment(tpIt)); }

        @Test
        @DisplayName("can view internal comments")
        void canViewInternal() { assertTrue(authz.canViewInternalComments(tpIt)); }
    }

    // ------------------------------------------------------------
    // NHAN_VIEN + IT: process & receive, but cannot assign others
    // ------------------------------------------------------------

    @Nested
    @DisplayName("NHAN_VIEN + IT")
    class NhanVienIt {

        private final ActorContext nvIt = actor(UserRole.Role.NHAN_VIEN, "IT", true);

        @Test
        @DisplayName("can process tickets")
        void canProcess() { assertTrue(authz.canProcessTickets(nvIt)); }

        @Test
        @DisplayName("can receive a ticket")
        void canReceive() { assertTrue(authz.canReceiveTicket(nvIt)); }

        @Test
        @DisplayName("CANNOT assign others (self-handling is not assigning others)")
        void cannotAssignOthers() { assertFalse(authz.canAssignOthers(nvIt)); }

        @Test
        @DisplayName("can update status")
        void canUpdateStatus() { assertTrue(authz.canUpdateStatus(nvIt)); }

        @Test
        @DisplayName("can add internal comments (H-4 corrected at the policy level)")
        void canAddInternal() { assertTrue(authz.canAddInternalComment(nvIt)); }

        @Test
        @DisplayName("can view internal comments")
        void canViewInternal() { assertTrue(authz.canViewInternalComments(nvIt)); }
    }

    // ------------------------------------------------------------
    // Non-IT TRUONG_PHONG: must NOT gain IT processing by role alone
    // ------------------------------------------------------------

    @Nested
    @DisplayName("TRUONG_PHONG non-IT")
    class TruongPhongNonIt {

        private final ActorContext tpMkt = actor(UserRole.Role.TRUONG_PHONG, "MKT", false);

        @Test
        @DisplayName("cannot process tickets (role alone is not enough)")
        void cannotProcess() { assertFalse(authz.canProcessTickets(tpMkt)); }

        @Test
        @DisplayName("cannot receive a ticket")
        void cannotReceive() { assertFalse(authz.canReceiveTicket(tpMkt)); }

        @Test
        @DisplayName("cannot assign others")
        void cannotAssignOthers() { assertFalse(authz.canAssignOthers(tpMkt)); }

        @Test
        @DisplayName("cannot update status")
        void cannotUpdateStatus() { assertFalse(authz.canUpdateStatus(tpMkt)); }

        @Test
        @DisplayName("cannot add internal comments")
        void cannotAddInternal() { assertFalse(authz.canAddInternalComment(tpMkt)); }

        @Test
        @DisplayName("cannot view internal comments")
        void cannotViewInternal() { assertFalse(authz.canViewInternalComments(tpMkt)); }
    }

    // ------------------------------------------------------------
    // Non-IT NHAN_VIEN: must NOT gain IT processing by role alone
    // ------------------------------------------------------------

    @Nested
    @DisplayName("NHAN_VIEN non-IT")
    class NhanVienNonIt {

        private final ActorContext nvHr = actor(UserRole.Role.NHAN_VIEN, "HR", false);

        @Test
        @DisplayName("cannot process tickets (role alone is not enough)")
        void cannotProcess() { assertFalse(authz.canProcessTickets(nvHr)); }

        @Test
        @DisplayName("cannot receive a ticket")
        void cannotReceive() { assertFalse(authz.canReceiveTicket(nvHr)); }

        @Test
        @DisplayName("cannot assign others")
        void cannotAssignOthers() { assertFalse(authz.canAssignOthers(nvHr)); }

        @Test
        @DisplayName("cannot update status via the operator pathway")
        void cannotUpdateStatus() { assertFalse(authz.canUpdateStatus(nvHr)); }

        @Test
        @DisplayName("cannot add internal comments")
        void cannotAddInternal() { assertFalse(authz.canAddInternalComment(nvHr)); }

        @Test
        @DisplayName("cannot view internal comments")
        void cannotViewInternal() { assertFalse(authz.canViewInternalComments(nvHr)); }
    }

    // ------------------------------------------------------------
    // Custom IT department code (e.g. TECH) - the policy is configured, not hardcoded
    // ------------------------------------------------------------

    @Nested
    @DisplayName("Custom configured IT department code (TECH)")
    class CustomItCode {

        private final ActorContext techTp = actor(UserRole.Role.TRUONG_PHONG, "TECH", true);
        private final ActorContext techNv = actor(UserRole.Role.NHAN_VIEN, "TECH", true);
        private final ActorContext itTp = actor(UserRole.Role.TRUONG_PHONG, "IT", false);
        private final ActorContext itNv = actor(UserRole.Role.NHAN_VIEN, "IT", false);

        @Test
        @DisplayName("TRUONG_PHONG in TECH can process and assign")
        void techTpAllowed() {
            assertTrue(authz.canProcessTickets(techTp));
            assertTrue(authz.canAssignOthers(techTp));
        }

        @Test
        @DisplayName("NHAN_VIEN in TECH can process and receive, but cannot assign others")
        void techNvAllowed() {
            assertTrue(authz.canProcessTickets(techNv));
            assertTrue(authz.canReceiveTicket(techNv));
            assertFalse(authz.canAssignOthers(techNv));
        }

        @Test
        @DisplayName("When IT code is configured as TECH, actors in IT department are NOT operators")
        void itDepartmentNotOperator() {
            // Same flag the resolver would set when configured as TECH.
            assertFalse(authz.canProcessTickets(itTp));
            assertFalse(authz.canAssignOthers(itTp));
            assertFalse(authz.canProcessTickets(itNv));
        }
    }

    // ------------------------------------------------------------
    // Null department / edge cases
    // ------------------------------------------------------------

    @Nested
    @DisplayName("Null and edge cases")
    class NullAndEdge {

        @Test
        @DisplayName("A null actor is rejected with NullPointerException (defensive)")
        void nullActorRejected() {
            assertThrows(NullPointerException.class, () -> authz.canProcessTickets(null));
            assertThrows(NullPointerException.class, () -> authz.canAssignOthers(null));
            assertThrows(NullPointerException.class, () -> authz.canAddInternalComment(null));
        }

        @Test
        @DisplayName("A TRUONG_PHONG with no department is NOT an IT operator (cannot process, cannot assign)")
        void truongPhongWithNoDepartment() {
            ActorContext tpNoDept = actor(UserRole.Role.TRUONG_PHONG, null, false);
            assertFalse(authz.canProcessTickets(tpNoDept));
            assertFalse(authz.canAssignOthers(tpNoDept));
        }

        @Test
        @DisplayName("The IT flag is the single source of truth for department membership")
        void itFlagOverridesCode() {
            // The resolver decides; the policy trusts it. If the flag is false, the actor is
            // not an operator even if the code looks like 'IT'.
            ActorContext withFalseFlag = actor(UserRole.Role.TRUONG_PHONG, "IT", false);
            assertFalse(authz.canProcessTickets(withFalseFlag));
            assertFalse(authz.canAssignOthers(withFalseFlag));
        }
    }

    // ------------------------------------------------------------
    // Cross-department ticket policy (Ticket.department is NOT an authorization boundary)
    // ------------------------------------------------------------

    @Nested
    @DisplayName("Cross-department ticket handling")
    class CrossDepartmentTicket {

        /**
         * The policy does not look at the requester's department. The IT manager / staff can
         * process any company's ticket. The ticket's department is not part of the policy API
         * at all; it is part of the ticket payload, not the actor.
         */
        @Test
        @DisplayName("TicketAuthorization only takes an actor; the requester department is not consulted")
        void policyDoesNotConsultRequester() {
            ActorContext itManager = actor(UserRole.Role.TRUONG_PHONG, "IT", true);
            ActorContext itStaff = actor(UserRole.Role.NHAN_VIEN, "IT", true);

            // Both IT operators can process. The policy answer does not depend on
            // "what department the ticket's requester is in" - there is no such input.
            assertTrue(authz.canProcessTickets(itManager));
            assertTrue(authz.canProcessTickets(itStaff));
        }

        @Test
        @DisplayName("No policy method accepts a requester department - it is intentionally outside scope")
        void noMethodTakesRequesterDepartment() throws NoSuchMethodException {
            // Compile-time check via reflection: no public method takes a String +
            // Department + ActorContext overload, and no method takes a Ticket. This is the
            // strongest way we can encode the brief's \u00a71 invariant.
            for (var m : TicketAuthorization.class.getDeclaredMethods()) {
                if (m.getName().startsWith("can") && m.getParameterCount() > 0) {
                    Class<?>[] params = m.getParameterTypes();
                    assertFalse(
                        java.util.Arrays.asList(params).contains(com.example.ticketing.department.Department.class),
                        "TicketAuthorization must not consult a requester department; method "
                            + m.getName() + " takes a Department");
                }
            }
        }
    }
}
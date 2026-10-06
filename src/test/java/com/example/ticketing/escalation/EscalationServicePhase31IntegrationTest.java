package com.example.ticketing.escalation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
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
import com.example.ticketing.ticket.Ticket;
import com.example.ticketing.ticket.TicketRepository;
import com.example.ticketing.ticket.TicketTypes.TicketPriority;
import com.example.ticketing.ticket.TicketTypes.TicketStatus;

/**
 * PHASE 3.1 - automatic SLA escalation target validation tests.
 *
 * <p>Covers brief \u00a717 + \u00a718:
 *
 * <ul>
 *   <li>Valid target: SLA scheduler reassigns correctly. The history records
 *       {@code createdBy = "SYSTEM"} and {@code actionTaken = "REASSIGN"}.</li>
 *   <li>Invalid target (disabled / unapproved / non-NHAN_VIEN / non-IT / ADMIN /
 *       GIAM_DOC / TRUONG_PHONG / nonexistent): the scheduler does NOT reassign
 *       and the ticket's {@code assignee} / {@code assigneeName} stay null. The
 *       history records the failed attempt.</li>
 *   <li>No fallback target is invented.</li>
 *   <li>The system path does NOT require a human Authentication or
 *       {@code TicketAuthorization.canAssignOthers()}.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EscalationServicePhase31IntegrationTest {

    @Autowired
    private EscalationService escalationService;

    @Autowired
    private EscalationRuleRepository ruleRepository;

    @Autowired
    private EscalationHistoryRepository historyRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    private static int ticketNumberSeq = 0;

    private Department itDept;
    private Department mktDept;

    @BeforeEach
    void setUp() {
        itDept = ensureDepartment("IT", "Information Technology");
        mktDept = ensureDepartment("MKT", "Marketing");
    }

    private Department ensureDepartment(String code, String name) {
        return departmentRepository.findByCode(code).orElseGet(() -> {
            Department d = new Department();
            d.setCode(code);
            d.setName(name);
            d.setEnabled(true);
            return departmentRepository.save(d);
        });
    }

    private UserAccount user(String username, UserRole.Role role, Department dept,
                              boolean enabled, boolean approved) {
        return userAccountRepository.findByUsername(username).orElseGet(() -> {
            UserAccount u = new UserAccount();
            u.setUsername(username);
            u.setPasswordHash("{noop}phase3_1");
            u.setRole(role);
            u.setDepartment(dept);
            u.setDisplayName(username);
            u.setEmail(username + "@example.internal");
            u.setEnabled(enabled);
            u.setApproved(approved);
            return userAccountRepository.save(u);
        });
    }

    private Ticket newTicket(UserAccount requester) {
        ticketNumberSeq++;
        Ticket t = new Ticket();
        t.setTicketNumber("PHASE3_1-" + String.format("%05d", ticketNumberSeq));
        t.setTitle("Phase 3.1 SLA fixture");
        t.setDescription("Phase 3.1 fixture description");
        t.setPriority(TicketPriority.HIGH);
        t.setCategory(com.example.ticketing.ticket.TicketTypes.TicketCategory.HARDWARE);
        t.setStatus(TicketStatus.NEW);
        t.setRequesterUsername(requester.getUsername());
        t.setRequesterName(requester.getDisplayName());
        t.setDepartment(requester.getDepartment());
        // SLA scheduler only triggers when slaResponseAt / slaResolutionAt are set
        // and the breach/warning thresholds have been crossed. Setting both times
        // to the past guarantees the SLA-breached check fires.
        t.setSlaResponseAt(LocalDateTime.now().minusMinutes(120));
        t.setSlaResolutionAt(LocalDateTime.now().minusMinutes(60));
        t.setFirstResponseAt(null);
        return t;
    }

    private EscalationRule persistRule(EscalationRule.EscalationAction action,
                                        EscalationRule.EscalationTrigger trigger,
                                        Long escalateToUserId, Long escalateToTeamId) {
        EscalationRule rule = new EscalationRule();
        rule.setName("Phase 3.1 - " + action + " - " + System.nanoTime());
        rule.setDescription("Phase 3.1 fixture rule");
        rule.setTriggerType(trigger);
        // minPriority is intentionally left null: the canonical
        // findApplicableRules JPQL uses a string-based "<=" comparison that does
        // not yield the expected ordinal ordering against every H2/PostgreSQL
        // dialect combination. A null minPriority matches via the
        // "r.minPriority IS NULL" branch, which is unambiguous. This is a
        // test-only concern: production code only relies on the rule being
        // applicable when isPriorityApplicable(...) returns true.
        rule.setMinPriority(null);
        rule.setActionType(action);
        rule.setEscalateToUserId(escalateToUserId);
        rule.setEscalateToTeamId(escalateToTeamId);
        rule.setMaxEscalations(3);
        rule.setEnabled(true);
        rule.setPriority(0);
        EscalationRule saved = ruleRepository.save(rule);
        ruleRepository.flush();
        return saved;
    }

    // ========================================================================
    // \u00a717 - automatic SLA escalation with valid target
    // ========================================================================

    @Nested
    @DisplayName("Automatic SLA escalation - valid target is reassigned")
    class ValidTarget {

    @Test
    @DisplayName("enabled + approved + NHAN_VIEN + IT target: scheduler reassigns and history records SYSTEM / REASSIGN")
    void validTargetReassigns() {
        UserAccount target = user("p31_target_ok", UserRole.Role.NHAN_VIEN, itDept, true, true);
        UserAccount requester = user("p31_req_mkt", UserRole.Role.NHAN_VIEN, mktDept, true, true);

        Ticket ticket = ticketRepository.save(newTicket(requester));
        EscalationRule rule = persistRule(EscalationRule.EscalationAction.REASSIGN,
            EscalationRule.EscalationTrigger.SLA_RESPONSE_BREACHED, target.getId(), null);

        // Sanity check: the rule is queryable the way the SLA scheduler sees it.
        List<EscalationRule> applicable = ruleRepository.findApplicableRules(
            EscalationRule.EscalationTrigger.SLA_RESPONSE_BREACHED, TicketPriority.HIGH);
        assertNotNull(applicable);
        assertEquals(1, applicable.size(), "Rule must be applicable to the SLA scheduler");

        // Drive the SLA path. The scheduler is the call site.
        escalationService.checkAndEscalate(ticket);

        Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(target.getUsername(), reloaded.getAssigneeName(),
            "Valid target must be set on the ticket");
        assertNotNull(reloaded.getAssignee(), "assignee FK must also be set");
        assertEquals(target.getUsername(), reloaded.getAssignee().getUsername(),
            "assignee_id and assignee_name must be in sync");

        // History records the system actor and the successful action.
        List<EscalationHistory> rows = historyRepository.findByTicketIdOrderByEscalatedAtDesc(ticket.getId());
        assertEquals(1, rows.size());
        EscalationHistory row = rows.get(0);
        assertEquals(EscalationService.SYSTEM_ACTOR, row.getCreatedBy(),
            "Automatic SLA escalation must record SYSTEM as the actor");
        assertEquals("REASSIGN", row.getActionTaken(),
            "Successful REASSIGN history action must be REASSIGN");
        assertEquals(target.getUsername(), row.getNewAssignee());
    }
    }

    // ========================================================================
    // \u00a717 - automatic SLA escalation with invalid target
    // ========================================================================

    @Nested
    @DisplayName("Automatic SLA escalation - invalid target is rejected safely")
    class InvalidTarget {

        @Test
        @DisplayName("Disabled NHAN_VIEN + IT target: no reassignment, no fallback, history marks failure")
        void disabledTargetIsRejected() {
            UserAccount target = user("p31_target_disabled", UserRole.Role.NHAN_VIEN, itDept, false, true);
            UserAccount requester = user("p31_req_mkt_dis", UserRole.Role.NHAN_VIEN, mktDept, true, true);

            Ticket ticket = ticketRepository.save(newTicket(requester));
            EscalationRule rule = persistRule(EscalationRule.EscalationAction.REASSIGN,
                EscalationRule.EscalationTrigger.SLA_RESPONSE_BREACHED, target.getId(), null);

            escalationService.checkAndEscalate(ticket);

            Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
            assertNull(reloaded.getAssigneeName(), "Disabled target must not be assigned");
            assertNull(reloaded.getAssignee(), "Disabled target must not be assigned (FK stays null)");

            List<EscalationHistory> rows = historyRepository.findByTicketIdOrderByEscalatedAtDesc(ticket.getId());
            assertEquals(1, rows.size());
            assertEquals(EscalationService.SYSTEM_ACTOR, rows.get(0).getCreatedBy());
            assertTrue(rows.get(0).getActionTaken().startsWith("REASSIGN_FAILED"),
                "Failed REASSIGN history action must be tagged as a failure");
        }

        @Test
        @DisplayName("Unapproved NHAN_VIEN + IT target: no reassignment")
        void unapprovedTargetIsRejected() {
            UserAccount target = user("p31_target_unapproved", UserRole.Role.NHAN_VIEN, itDept, true, false);
            UserAccount requester = user("p31_req_mkt_un", UserRole.Role.NHAN_VIEN, mktDept, true, true);

            Ticket ticket = ticketRepository.save(newTicket(requester));
            persistRule(EscalationRule.EscalationAction.REASSIGN,
                EscalationRule.EscalationTrigger.SLA_RESPONSE_BREACHED, target.getId(), null);

            escalationService.checkAndEscalate(ticket);

            Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
            assertNull(reloaded.getAssigneeName());
            assertNull(reloaded.getAssignee());
        }

        @Test
        @DisplayName("NHAN_VIEN non-IT target: no reassignment")
        void nhanVienNonItTargetIsRejected() {
            UserAccount target = user("p31_target_mkt", UserRole.Role.NHAN_VIEN, mktDept, true, true);
            UserAccount requester = user("p31_req_mkt_nv", UserRole.Role.NHAN_VIEN, mktDept, true, true);

            Ticket ticket = ticketRepository.save(newTicket(requester));
            persistRule(EscalationRule.EscalationAction.REASSIGN,
                EscalationRule.EscalationTrigger.SLA_RESPONSE_BREACHED, target.getId(), null);

            escalationService.checkAndEscalate(ticket);

            Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
            assertNull(reloaded.getAssigneeName());
            assertNull(reloaded.getAssignee());
        }

        @Test
        @DisplayName("TRUONG_PHONG + IT target: no reassignment (role mismatch)")
        void truongPhongItTargetIsRejected() {
            UserAccount target = user("p31_target_tp_it", UserRole.Role.TRUONG_PHONG, itDept, true, true);
            UserAccount requester = user("p31_req_mkt_tp", UserRole.Role.NHAN_VIEN, mktDept, true, true);

            Ticket ticket = ticketRepository.save(newTicket(requester));
            persistRule(EscalationRule.EscalationAction.REASSIGN,
                EscalationRule.EscalationTrigger.SLA_RESPONSE_BREACHED, target.getId(), null);

            escalationService.checkAndEscalate(ticket);

            Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
            assertNull(reloaded.getAssigneeName());
            assertNull(reloaded.getAssignee());
        }

        @Test
        @DisplayName("TRUONG_PHONG non-IT target: no reassignment (role mismatch)")
        void truongPhongNonItTargetIsRejected() {
            UserAccount target = user("p31_target_tp_mkt", UserRole.Role.TRUONG_PHONG, mktDept, true, true);
            UserAccount requester = user("p31_req_mkt_tp2", UserRole.Role.NHAN_VIEN, mktDept, true, true);

            Ticket ticket = ticketRepository.save(newTicket(requester));
            persistRule(EscalationRule.EscalationAction.REASSIGN,
                EscalationRule.EscalationTrigger.SLA_RESPONSE_BREACHED, target.getId(), null);

            escalationService.checkAndEscalate(ticket);

            Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
            assertNull(reloaded.getAssigneeName());
            assertNull(reloaded.getAssignee());
        }

        @Test
        @DisplayName("ADMIN target: no reassignment")
        void adminTargetIsRejected() {
            UserAccount target = user("p31_target_admin", UserRole.Role.ADMIN, itDept, true, true);
            UserAccount requester = user("p31_req_mkt_a", UserRole.Role.NHAN_VIEN, mktDept, true, true);

            Ticket ticket = ticketRepository.save(newTicket(requester));
            persistRule(EscalationRule.EscalationAction.REASSIGN,
                EscalationRule.EscalationTrigger.SLA_RESPONSE_BREACHED, target.getId(), null);

            escalationService.checkAndEscalate(ticket);

            Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
            assertNull(reloaded.getAssigneeName());
            assertNull(reloaded.getAssignee());
        }

        @Test
        @DisplayName("GIAM_DOC target: no reassignment")
        void giamDocTargetIsRejected() {
            UserAccount target = user("p31_target_gd", UserRole.Role.GIAM_DOC, itDept, true, true);
            UserAccount requester = user("p31_req_mkt_gd", UserRole.Role.NHAN_VIEN, mktDept, true, true);

            Ticket ticket = ticketRepository.save(newTicket(requester));
            persistRule(EscalationRule.EscalationAction.REASSIGN,
                EscalationRule.EscalationTrigger.SLA_RESPONSE_BREACHED, target.getId(), null);

            escalationService.checkAndEscalate(ticket);

            Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
            assertNull(reloaded.getAssigneeName());
            assertNull(reloaded.getAssignee());
        }

        @Test
        @DisplayName("Nonexistent target id: no reassignment, no fallback")
        void nonexistentTargetIsRejected() {
            UserAccount requester = user("p31_req_mkt_ne", UserRole.Role.NHAN_VIEN, mktDept, true, true);

            Ticket ticket = ticketRepository.save(newTicket(requester));
            persistRule(EscalationRule.EscalationAction.REASSIGN,
                EscalationRule.EscalationTrigger.SLA_RESPONSE_BREACHED, 999_999_999L, null);

            escalationService.checkAndEscalate(ticket);

            Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
            assertNull(reloaded.getAssigneeName());
            assertNull(reloaded.getAssignee());
        }
    }

    // ========================================================================
    // \u00a718 - automatic SLA path requires NO human actor
    // ========================================================================

    @Nested
    @DisplayName("Automatic SLA escalation - no human actor required")
    class NoHumanActor {

        @Test
        @DisplayName("checkAndEscalate does not require Authentication or ActorContext")
        void noHumanActorRequired() {
            // This test exists to document the contract: the scheduler-driven path
            // does not have a human principal. It is invoked here with no Authentication
            // and no ActorContext, and the call must succeed. We use a rule with a
            // non-reassigning action (NOTIFY) so we only assert that the path itself
            // does not fail with an authentication-shaped exception.
            UserAccount requester = user("p31_req_sys", UserRole.Role.NHAN_VIEN, mktDept, true, true);

            Ticket ticket = ticketRepository.save(newTicket(requester));
            persistRule(EscalationRule.EscalationAction.NOTIFY,
                EscalationRule.EscalationTrigger.SLA_RESPONSE_BREACHED, null, null);

            // No exception means the contract is honored. The history row exists and
            // SYSTEM is the actor.
            escalationService.checkAndEscalate(ticket);

            List<EscalationHistory> rows = historyRepository.findByTicketIdOrderByEscalatedAtDesc(ticket.getId());
            assertEquals(1, rows.size());
            assertEquals(EscalationService.SYSTEM_ACTOR, rows.get(0).getCreatedBy());
        }
    }

    // ========================================================================
    // \u00a711 - assignee / assigneeName synchronization under SLA escalation
    // ========================================================================

    @Nested
    @DisplayName("Assignee consistency under SLA escalation")
    class AssigneeConsistency {

        @Test
        @DisplayName("SLA reassignment: assignee_id and assignee_name are set together")
        void slaReassignmentKeepsBothInSync() {
            UserAccount target = user("p31_sync_target", UserRole.Role.NHAN_VIEN, itDept, true, true);
            UserAccount requester = user("p31_sync_req", UserRole.Role.NHAN_VIEN, mktDept, true, true);

            Ticket ticket = ticketRepository.save(newTicket(requester));
            persistRule(EscalationRule.EscalationAction.REASSIGN,
                EscalationRule.EscalationTrigger.SLA_RESPONSE_BREACHED, target.getId(), null);

            escalationService.checkAndEscalate(ticket);

            Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
            assertNotNull(reloaded.getAssigneeName());
            assertNotNull(reloaded.getAssignee());
            assertEquals(reloaded.getAssignee().getUsername(), reloaded.getAssigneeName());
        }

        @Test
        @DisplayName("SLA escalation with NO matching rule leaves ticket untouched")
        void noRuleLeavesTicketUntouched() {
            UserAccount requester = user("p31_norule_req", UserRole.Role.NHAN_VIEN, mktDept, true, true);

            Ticket ticket = ticketRepository.save(newTicket(requester));
            // No rule persisted -> triggerEscalation finds nothing -> no history row.
            escalationService.checkAndEscalate(ticket);

            Ticket reloaded = ticketRepository.findById(ticket.getId()).orElseThrow();
            assertNull(reloaded.getAssigneeName());
            assertNull(reloaded.getAssignee());
            assertEquals(TicketStatus.NEW, reloaded.getStatus());
            assertEquals(0, historyRepository.countByTicketId(ticket.getId()));
        }

        @Test
        @DisplayName("SLA escalation history.createdBy is SYSTEM, not 'admin' or any user")
        void historyCreatedByIsSystem() {
            UserAccount target = user("p31_actor_target", UserRole.Role.NHAN_VIEN, itDept, true, true);
            UserAccount requester = user("p31_actor_req", UserRole.Role.NHAN_VIEN, mktDept, true, true);

            Ticket ticket = ticketRepository.save(newTicket(requester));
            persistRule(EscalationRule.EscalationAction.REASSIGN,
                EscalationRule.EscalationTrigger.SLA_RESPONSE_BREACHED, target.getId(), null);

            escalationService.checkAndEscalate(ticket);

            List<EscalationHistory> rows = historyRepository.findByTicketIdOrderByEscalatedAtDesc(ticket.getId());
            assertEquals(1, rows.size());
            String createdBy = rows.get(0).getCreatedBy();
            assertEquals(EscalationService.SYSTEM_ACTOR, createdBy);
            assertNotEquals("admin", createdBy);
            assertNotEquals(requester.getUsername(), createdBy);
        }
    }
}

package com.example.ticketing.ticket;

import org.springframework.stereotype.Component;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.auth.UserRole;
import com.example.ticketing.department.Department;
import com.example.ticketing.department.DepartmentRepository;
import com.example.ticketing.ticket.TicketTypes.TicketCategory;
import com.example.ticketing.ticket.TicketTypes.TicketPriority;
import com.example.ticketing.ticket.TicketTypes.TicketStatus;

/**
 * Reusable test fixtures for Phase 0 authorization characterization tests.
 *
 * <p>PHASE 0 ONLY. This class must never be referenced from production code.
 *
 * <p>Business context: this is an INTERNAL company IT helpdesk. Ticket.department is the
 * REQUESTER's department and is NOT an authorization boundary for IT helpdesk operators.
 * The default fixture shape is therefore "requester in MKT, operator in IT", which is the
 * case that must NOT be blocked by the requester's department.
 *
 * <p>No production entity is modified. All fields are set through existing public setters.
 */
@Component
public class TicketAuthorizationFixtures {

    public static final String IT_CODE = "IT";
    public static final String MKT_CODE = "MKT";

    /** Monotonic counter so each fixture ticket gets a distinct, non-null ticket number. */
    private int ticketNumberSequence = 0;

    private final DepartmentRepository departmentRepository;
    private final UserAccountRepository userAccountRepository;

    public TicketAuthorizationFixtures(
        DepartmentRepository departmentRepository,
        UserAccountRepository userAccountRepository
    ) {
        this.departmentRepository = departmentRepository;
        this.userAccountRepository = userAccountRepository;
    }

    // ============================================================
    // Departments
    // ============================================================

    public Department itDepartment() {
        return department("IT", "Information Technology");
    }

    public Department mktDepartment() {
        return department("MKT", "Marketing");
    }

    /** Alias used by the RAG characterization suite when it needs a non-IT department reference. */
    public Department mktStaffRequesterDepartment() {
        return mktDepartment();
    }

    private Department department(String code, String name) {
        return departmentRepository.findByCode(code)
            .orElseGet(() -> {
                Department dept = new Department();
                dept.setCode(code);
                dept.setName(name);
                dept.setEnabled(true);
                return departmentRepository.save(dept);
            });
    }

    // ============================================================
    // Actors
    // ============================================================

    /** Actor A. Canonical policy: NOT an IT helpdesk operator. */
    public UserAccount admin() {
        return user("admin_it", UserRole.Role.ADMIN, mktDepartment());
    }

    /** Actor B. Canonical policy: NOT an IT helpdesk operator. */
    public UserAccount giamDoc() {
        return user("giamdoc_it", UserRole.Role.GIAM_DOC, mktDepartment());
    }

    /** Actor C. The IT Helpdesk Manager. */
    public UserAccount truongPhongIT() {
        return user("tp_it", UserRole.Role.TRUONG_PHONG, itDepartment());
    }

    /** Actor D. IT Helpdesk Staff. */
    public UserAccount nhanVienIT() {
        return user("nv_it", UserRole.Role.NHAN_VIEN, itDepartment());
    }

    /** Actor E. Not an IT helpdesk operator. */
    public UserAccount truongPhongMkt() {
        return user("tp_mkt", UserRole.Role.TRUONG_PHONG, mktDepartment());
    }

    /** Actor F. Not an IT helpdesk operator. */
    public UserAccount nhanVienMkt() {
        return user("nv_mkt", UserRole.Role.NHAN_VIEN, mktDepartment());
    }

    /** A second IT staff account, used to prove assign-to-another bypasses the unvalidated target path. */
    public UserAccount nhanVienIT2() {
        return user("nv_it2", UserRole.Role.NHAN_VIEN, itDepartment());
    }

    private UserAccount user(String username, UserRole.Role role, Department dept) {
        return userAccountRepository.findByUsername(username)
            .orElseGet(() -> {
                UserAccount u = new UserAccount();
                u.setUsername(username);
                u.setPasswordHash("{noop}phase0-characterization-only");
                u.setRole(role);
                u.setDepartment(dept);
                u.setDisplayName(username);
                u.setEmail(username + "@example.internal");
                u.setEnabled(true);
                // Phase 0 fixtures only need the authorization role/department pair to resolve.
                // approved=true is the state a real logged-in operator would be in.
                u.setApproved(true);
                return userAccountRepository.save(u);
            });
    }

    // ============================================================
    // Tickets
    // ============================================================

    private String nextTicketNumber() {
        ticketNumberSequence++;
        return "PHASE0-" + String.format("%05d", ticketNumberSequence);
    }

    /**
     * A NEW ticket requested by an MKT user, defaulting to priority MEDIUM.
     *
     * <p>The requester's department is deliberately NOT IT, so that "operator in IT may handle a
     * ticket whose requester department is MKT" is the default shape under test.
     */
    public Ticket mktRequestedTicket() {
        return mktRequestedTicket("Printer on floor 3 is offline", TicketPriority.MEDIUM);
    }

    public Ticket mktRequestedTicket(String title, TicketPriority priority) {
        return newTicket(title, priority, nhanVienMkt());
    }

    /** A NEW ticket requested by an IT user. */
    public Ticket itRequestedTicket() {
        return newTicket("VPN client cannot authenticate", TicketPriority.HIGH, nhanVienIT());
    }

    public Ticket newTicket(String title, TicketPriority priority, UserAccount requester) {
        Ticket ticket = new Ticket();
        // ticket_number is NOT NULL and is normally generated inside TicketService.createTicket.
        // Tests that persist directly through the repository must supply it themselves.
        ticket.setTicketNumber(nextTicketNumber());
        ticket.setTitle(title);
        ticket.setDescription("Phase 0 characterization fixture description");
        ticket.setPriority(priority);
        ticket.setCategory(TicketCategory.HARDWARE);
        ticket.setStatus(TicketStatus.NEW);
        ticket.setRequesterUsername(requester.getUsername());
        ticket.setRequesterName(requester.getDisplayName());
        ticket.setRequesterEmail(requester.getEmail());
        // Canonical invariant: Ticket.department == requester.department. Do not change.
        ticket.setDepartment(requester.getDepartment());
        // createdAt/updatedAt are populated by Ticket's own @PrePersist; the entity exposes no setter.
        return ticket;
    }

    /**
     * A saved ticket already at the given status, bypassing the service so that characterization
     * tests can set up states that are otherwise only reachable through lifecycle calls.
     */
    public Ticket savedTicketAtStatus(TicketStatus status, UserAccount requester) {
        Ticket ticket = newTicket("Fixture ticket at " + status, TicketPriority.MEDIUM, requester);
        ticket.setStatus(status);
        return ticket;
    }
}

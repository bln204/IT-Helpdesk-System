package com.example.ticketing.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.auth.UserRole;
import com.example.ticketing.department.Department;
import com.example.ticketing.department.DepartmentRepository;

/**
 * PHASE 2 - Integration test for {@link ActorContextService}.
 *
 * <p>Verifies that the canonical seam between {@link Authentication} and {@link ActorContext}
 * correctly:
 * <ul>
 *   <li>reads the username from {@code Authentication.getName()},</li>
 *   <li>loads the {@link UserAccount},</li>
 *   <li>resolves IT membership via {@code ItDepartmentResolver} (default {@code IT}),</li>
 *   <li>rejects missing or disabled accounts.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ActorContextServiceTest {

    @Autowired
    private ActorContextService service;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    private Department it;
    private Department mkt;
    private UserAccount admin;
    private UserAccount itStaff;
    private UserAccount nonItStaff;

    @BeforeEach
    void setUp() {
        it = persistDepartment("IT", "Information Technology");
        mkt = persistDepartment("MKT", "Marketing");
        admin = persistUser("phase2_admin", UserRole.Role.ADMIN, null);
        itStaff = persistUser("phase2_itstaff", UserRole.Role.NHAN_VIEN, it);
        nonItStaff = persistUser("phase2_nonit", UserRole.Role.NHAN_VIEN, mkt);
    }

    @AfterEach
    void cleanUp() {
        // Test-managed; @Transactional rolls back at end of test.
    }

    @Test
    @DisplayName("An ADMIN actor with no department resolves to a non-IT, ADMIN role context")
    void adminResolvesAsAdminNonIt() {
        Authentication authn = authenticationFor("phase2_admin");
        ActorContext ctx = service.fromAuthentication(authn);
        assertEquals("phase2_admin", ctx.username());
        assertEquals(UserRole.Role.ADMIN, ctx.role());
        assertFalse(ctx.isItDepartmentMember());
    }

    @Test
    @DisplayName("An NHAN_VIEN in IT is recognized as an IT department member")
    void nhanVienInItIsItMember() {
        Authentication authn = authenticationFor("phase2_itstaff");
        ActorContext ctx = service.fromAuthentication(authn);
        assertEquals("phase2_itstaff", ctx.username());
        assertEquals(UserRole.Role.NHAN_VIEN, ctx.role());
        assertEquals("IT", ctx.departmentCode());
        assertTrue(ctx.isItDepartmentMember());
    }

    @Test
    @DisplayName("An NHAN_VIEN in a non-IT department is NOT an IT member")
    void nhanVienInMktIsNotItMember() {
        Authentication authn = authenticationFor("phase2_nonit");
        ActorContext ctx = service.fromAuthentication(authn);
        assertEquals("phase2_nonit", ctx.username());
        assertEquals(UserRole.Role.NHAN_VIEN, ctx.role());
        assertEquals("MKT", ctx.departmentCode());
        assertFalse(ctx.isItDepartmentMember());
    }

    @Test
    @DisplayName("A null Authentication yields 401 Unauthorized")
    void nullAuthenticationRejected() {
        assertThrows(ResponseStatusException.class, () -> service.fromAuthentication(null));
    }

    @Test
    @DisplayName("An unknown username yields 401 Unauthorized")
    void unknownUsernameRejected() {
        Authentication authn = authenticationFor("nobody_here");
        assertThrows(ResponseStatusException.class, () -> service.fromAuthentication(authn));
    }

    @Test
    @DisplayName("A blank principal yields 401 Unauthorized")
    void blankPrincipalRejected() {
        Authentication authn = authenticationFor(" ");
        assertThrows(ResponseStatusException.class, () -> service.fromAuthentication(authn));
    }

    @Test
    @DisplayName("fromUsername(username) returns the same context as fromAuthentication(...)")
    void fromUsernameMatchesFromAuthentication() {
        Authentication authn = authenticationFor("phase2_itstaff");
        ActorContext fromAuthn = service.fromAuthentication(authn);
        ActorContext fromUsername = service.fromUsername("phase2_itstaff");
        assertEquals(fromAuthn, fromUsername);
    }

    @Test
    @DisplayName("fromUsername with a blank value is rejected")
    void fromUsernameBlankRejected() {
        assertThrows(ResponseStatusException.class, () -> service.fromUsername(""));
        assertThrows(ResponseStatusException.class, () -> service.fromUsername(null));
    }

    // ---- helpers --------------------------------------------------------------------

    private static Authentication authenticationFor(String username) {
        return new UsernamePasswordAuthenticationToken(
            username,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

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
            u.setPasswordHash("{noop}phase2");
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
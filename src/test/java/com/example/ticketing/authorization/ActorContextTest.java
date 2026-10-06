package com.example.ticketing.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.example.ticketing.auth.UserRole;

/**
 * PHASE 2 - Unit tests for {@link ActorContext}.
 *
 * <p>These tests pin the facts: identity, role, department code, and IT membership, exactly
 * as resolved by {@code ActorContext.of(...)}. They make no policy assertions -
 * those live in {@link TicketAuthorizationTest}.
 */
class ActorContextTest {

    @Test
    @DisplayName("of(username, role, code, it) builds the expected facts")
    void factoryBuildsFacts() {
        ActorContext a = ActorContext.of("alice", UserRole.Role.NHAN_VIEN, "IT", true);
        assertEquals("alice", a.username());
        assertEquals(UserRole.Role.NHAN_VIEN, a.role());
        assertEquals("IT", a.departmentCode());
        assertTrue(a.isItDepartmentMember());
    }

    @Test
    @DisplayName("A null department code is preserved exactly (no normalization to 'IT' or '')")
    void nullDepartmentCodeIsPreserved() {
        ActorContext a = ActorContext.of("bob", UserRole.Role.NHAN_VIEN, null, false);
        assertNull(a.departmentCode());
        assertFalse(a.isItDepartmentMember());
    }

    @Test
    @DisplayName("A custom configured IT code (TECH) is preserved as the department code")
    void customCodeIsPreserved() {
        ActorContext a = ActorContext.of("carol", UserRole.Role.TRUONG_PHONG, "TECH", true);
        assertEquals("TECH", a.departmentCode());
        assertTrue(a.isItDepartmentMember());
    }

    @Test
    @DisplayName("Non-IT department code is preserved verbatim")
    void nonItCodeIsPreserved() {
        ActorContext a = ActorContext.of("dave", UserRole.Role.NHAN_VIEN, "MKT", false);
        assertEquals("MKT", a.departmentCode());
        assertFalse(a.isItDepartmentMember());
    }

    @Test
    @DisplayName("Case-sensitive: 'it' and 'IT' are NOT equal in department code identity")
    void caseSensitive() {
        ActorContext it = ActorContext.of("e", UserRole.Role.NHAN_VIEN, "IT", true);
        ActorContext lc = ActorContext.of("e", UserRole.Role.NHAN_VIEN, "it", false);
        assertNotEquals(it, lc);
    }

    @Test
    @DisplayName("Equality: same username/role/code/it flag is equal")
    void equalityOnAllFourFields() {
        ActorContext a = ActorContext.of("u", UserRole.Role.ADMIN, "EXEC", false);
        ActorContext b = ActorContext.of("u", UserRole.Role.ADMIN, "EXEC", false);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("Equality: a different role yields inequality")
    void differentRoleYieldsInequality() {
        ActorContext a = ActorContext.of("u", UserRole.Role.ADMIN, null, false);
        ActorContext b = ActorContext.of("u", UserRole.Role.GIAM_DOC, null, false);
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("Factory requires username and role")
    void factoryRejectsNulls() {
        assertThrows(NullPointerException.class,
            () -> ActorContext.of(null, UserRole.Role.ADMIN, null, false));
        assertThrows(NullPointerException.class,
            () -> ActorContext.of("u", null, null, false));
    }

    @Test
    @DisplayName("toString includes the four facts and is non-null")
    void toStringIncludesFacts() {
        ActorContext a = ActorContext.of("u", UserRole.Role.NHAN_VIEN, "IT", true);
        String s = a.toString();
        assertNotNull(s);
        assertTrue(s.contains("u"));
        assertTrue(s.contains("NHAN_VIEN"));
        assertTrue(s.contains("IT"));
    }

    @Nested
    @DisplayName("ActorContext built from a UserAccount entity")
    class FromUserAccount {

        private com.example.ticketing.auth.UserAccount userWith(
            String username,
            UserRole.Role role,
            String departmentCode
        ) {
            com.example.ticketing.auth.UserAccount u = new com.example.ticketing.auth.UserAccount();
            u.setUsername(username);
            u.setRole(role);
            if (departmentCode != null) {
                com.example.ticketing.department.Department d = new com.example.ticketing.department.Department();
                d.setCode(departmentCode);
                d.setName(departmentCode);
                d.setEnabled(true);
                u.setDepartment(d);
            } else {
                u.setDepartment(null);
            }
            return u;
        }

        @Test
        @DisplayName("Reads username, role, and department code from the entity")
        void fromEntityReadsFacts() {
            var u = userWith("alice", UserRole.Role.TRUONG_PHONG, "IT");
            ActorContext a = ActorContext.of(u, true);
            assertEquals("alice", a.username());
            assertEquals(UserRole.Role.TRUONG_PHONG, a.role());
            assertEquals("IT", a.departmentCode());
            assertTrue(a.isItDepartmentMember());
        }

        @Test
        @DisplayName("An entity with no department yields a null department code")
        void entityWithNullDepartmentYieldsNullCode() {
            var u = userWith("bob", UserRole.Role.NHAN_VIEN, null);
            ActorContext a = ActorContext.of(u, false);
            assertNull(a.departmentCode());
            assertFalse(a.isItDepartmentMember());
        }

        @Test
        @DisplayName("The IT flag is supplied by the caller, not derived from the entity")
        void itFlagIsCallerSupplied() {
            // Same entity, two different IT flags => two non-equal contexts.
            var u = userWith("carol", UserRole.Role.NHAN_VIEN, "IT");
            ActorContext aIt = ActorContext.of(u, true);
            ActorContext aNonIt = ActorContext.of(u, false);
            assertNotEquals(aIt, aNonIt);
            assertTrue(aIt.isItDepartmentMember());
            assertFalse(aNonIt.isItDepartmentMember());
        }
    }
}
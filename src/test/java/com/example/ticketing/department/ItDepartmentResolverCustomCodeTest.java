package com.example.ticketing.department;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * PHASE 1 TESTS - ItDepartmentResolver with a CUSTOM configured IT department code.
 *
 * <p>This is the test that proves the value is genuinely configuration-driven: when the code is
 * configured as {@code TECH}, a user in the old hardcoded {@code IT} department must stop being
 * recognized as IT.
 *
 * <p>ISOLATION: runs under its own profile ("test", "customit") and therefore its own Spring
 * application context, so the overridden property cannot contaminate any other test.
 */
@SpringBootTest
@ActiveProfiles({"test", "customit"})
class ItDepartmentResolverCustomCodeTest {

    @Autowired
    private ItDepartmentResolver resolver;

    private static Department dept(String code) {
        Department d = new Department();
        d.setName("dept");
        d.setEnabled(true);
        d.setCode(code);
        return d;
    }

    @Test
    @DisplayName("The custom configured IT department code is TECH, not the compiled-in IT")
    void customConfigurationIsTech() {
        assertEquals("TECH", resolver.getItDepartmentCode());
    }

    @Test
    @DisplayName("Test 3: with the code configured as TECH, a user in TECH resolves to the IT department")
    void configuredDepartmentResolvesAsIt() {
        assertTrue(resolver.isITDepartment(dept("TECH")));
    }

    @Test
    @DisplayName("Test 4: with the code configured as TECH, the old IT department is NO LONGER IT")
    void previousHardcodedCodeIsNoLongerIt() {
        // This is the assertion that proves configuration actually drives resolution. Under the
        // old hardcoded implementation, IT would still have matched.
        assertFalse(resolver.isITDepartment(dept("IT")));
    }

    @Test
    @DisplayName("Unrelated departments remain false under the custom configuration")
    void unrelatedDepartmentsRemainFalse() {
        assertFalse(resolver.isITDepartment(dept("MKT")));
        assertFalse(resolver.isITDepartment(dept("HR")));
        assertFalse(resolver.isITDepartment(null));
    }

    @Test
    @DisplayName("Case sensitivity is preserved under the custom configuration")
    void caseSensitivityIsPreserved() {
        assertFalse(resolver.isITDepartment(dept("tech")));
        assertTrue(resolver.isITDepartment(dept("TECH")));
    }
}
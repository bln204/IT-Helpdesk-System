package com.example.ticketing.department;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * PHASE 1 TESTS - ItDepartmentResolver with the DEFAULT configuration.
 *
 * <p>Proves the default value remains exactly {@code IT} and that the resolver's null, case, and
 * whitespace semantics match the hardcoded {@code "IT".equals(dept.getCode())} checks it replaces.
 *
 * <p>Test 3 and Test 4 (custom configured department, and a custom config rejecting the old code)
 * live in {@link ItDepartmentResolverCustomCodeTest}, which requires a separate application
 * context. Keeping them apart avoids Spring test-context cache contamination.
 */
@SpringBootTest
@ActiveProfiles("test")
class ItDepartmentResolverTest {

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
    @DisplayName("DEFAULT: the configured IT department code is exactly IT")
    void defaultConfigurationIsIt() {
        assertEquals("IT", resolver.getItDepartmentCode(),
            "the default must remain IT so behavior is unchanged for existing deployments");
    }

    @Test
    @DisplayName("Test 1: a user in department IT resolves to the IT department")
    void itDepartmentResolvesAsIt() {
        assertTrue(resolver.isITDepartment(dept("IT")));
    }

    @Test
    @DisplayName("Test 2: a user in department MKT is not the IT department")
    void nonItDepartmentResolvesAsNotIt() {
        assertFalse(resolver.isITDepartment(dept("MKT")));
    }

    @Test
    @DisplayName("Test 5: a user with no department resolves to false, not an error")
    void nullDepartmentIsSafelyFalse() {
        assertFalse(resolver.isITDepartment(null));
    }

    @Nested
    @DisplayName("Test 6: null, blank and near-miss department codes")
    class NullAndNearMissCodes {

        @Test
        @DisplayName("A null department code is false rather than a NullPointerException")
        void nullCodeIsFalse() {
            assertFalse(resolver.isITDepartment(dept(null)));
        }

        @Test
        @DisplayName("An empty or whitespace-only code is false and is NOT trimmed to match")
        void blankCodeIsFalseAndNotTrimmed() {
            assertFalse(resolver.isITDepartment(dept("")));
            assertFalse(resolver.isITDepartment(dept("   ")));
            // Guards against a future "helpful" normalization changing behavior.
            assertFalse(resolver.isITDepartment(dept(" IT")));
            assertFalse(resolver.isITDepartment(dept("IT ")));
        }

        @Test
        @DisplayName("Comparison stays case-sensitive, matching the previous equals check")
        void comparisonIsCaseSensitive() {
            assertFalse(resolver.isITDepartment(dept("it")));
            assertFalse(resolver.isITDepartment(dept("It")));
            assertTrue(resolver.isITDepartment(dept("IT")));
        }

        @Test
        @DisplayName("Codes that merely contain IT are not treated as the IT department")
        void codesContainingItAreNotMatched() {
            assertFalse(resolver.isITDepartment(dept("ITT")));
            assertFalse(resolver.isITDepartment(dept("IT_SUPPORT")));
            assertFalse(resolver.isITDepartment(dept("MARKETING")));
        }
    }

    @Nested
    @DisplayName("Equivalence with the hardcoded checks this class replaces")
    class EquivalenceWithPreviousBehavior {

        /**
         * The previous implementations were all of the form
         * {@code dept != null && "IT".equals(dept.getCode())}. This pins that the resolver produces
         * the identical result for the full matrix of interesting inputs.
         */
        @Test
        @DisplayName("Resolver matches the old 'IT'.equals(code) predicate for every input shape")
        void resolverMatchesPreviousPredicate() {
            String[] codes = {"IT", "it", "It", "MKT", "", "  ", " IT", "IT ", "ITT", null};
            Department[] departments = new Department[codes.length + 1];
            for (int i = 0; i < codes.length; i++) {
                departments[i] = dept(codes[i]);
            }
            departments[codes.length] = null; // the null-department case

            for (Department d : departments) {
                boolean previous = d != null && "IT".equals(d.getCode());
                assertEquals(previous, resolver.isITDepartment(d),
                    "resolver disagreed with the previous predicate for department: "
                        + (d == null ? "null" : "'" + d.getCode() + "'"));
            }
        }
    }

    /**
     * Confirms the resolver bean receives its value from configuration rather than a constant.
     */
    @Nested
    @DisplayName("Configuration wiring")
    class ConfigurationWiring {

        @Value("${ticketing.authorization.it-department-code:IT}")
        private String injectedValue;

        @Test
        @DisplayName("The property is bound from application.properties with default IT")
        void propertyIsBoundFromConfiguration() {
            assertEquals("IT", injectedValue);
            assertEquals(injectedValue, resolver.getItDepartmentCode(),
                "the resolver must be driven by the configured property");
        }
    }
}
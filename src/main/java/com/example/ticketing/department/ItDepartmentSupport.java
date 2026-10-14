package com.example.ticketing.department;

/**
 * Non-Spring accessor for the configured IT department code.
 *
 * <p>UserAccount is a JPA entity that is also serialized to JSON. Its {@code isInITDepartment()}
 * and {@code isTruongPhongIT()} methods may therefore be invoked OUTSIDE any Spring-managed
 * context (Jackson serialization, Hikari triggers, non-managed test fixtures), where injecting
 * {@link ItDepartmentResolver} is not possible.
 *
 * <p>Phase 1 strategy: this class provides a single non-Spring accessor for the configured IT
 * department code, used by those compatibility methods. The Spring-managed
 * {@link ItDepartmentResolver} is the only authority that application code reads; this support
 * class only exists so that entity accessors can stay correct in non-DI contexts.
 *
 * <p>The default value is {@code IT}, matching the previously hardcoded behavior. The semantics
 * of {@code configuredCode.equals(code)} (null-safe, case-sensitive, non-trimming) are
 * deliberately identical to the old {@code "IT".equals(dept.getCode())} checks.
 */
public final class ItDepartmentSupport {

    private ItDepartmentSupport() {}

    /**
     * Returns the configured IT department code, defaulting to {@code IT}.
     *
     * <p>If a {@link ItDepartmentResolver} bean is present in the application context, its
     * value is used. Otherwise, the default {@code IT} is returned. This is the exact same
     * behavior as the hardcoded checks it replaces.
     */
    public static String itDepartmentCode() {
        ItDepartmentResolver resolver = ItDepartmentSupportHolder.resolver;
        return resolver != null ? resolver.getItDepartmentCode() : "IT";
    }

    /**
     * Returns true when the supplied department is the configured IT department.
     */
    public static boolean isITDepartment(Department department) {
        return department != null && itDepartmentCode().equals(department.getCode());
    }

    /**
     * Holder used to make a Spring-managed {@link ItDepartmentResolver} available without
     * introducing a hard dependency on a running application context. The bean is supplied by
     * {@link ItDepartmentSupportRegistrar}, which the application context wires automatically.
     */
    static final class ItDepartmentSupportHolder {
        static volatile ItDepartmentResolver resolver;
    }
}
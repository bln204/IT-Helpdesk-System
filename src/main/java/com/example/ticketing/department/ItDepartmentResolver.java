package com.example.ticketing.department;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for answering: "Is this the configured IT department?"
 *
 * <p>SCOPE (Phase 1). This class centralizes IT DEPARTMENT RESOLUTION only. It deliberately knows
 * nothing about roles, tickets, or operations. It does NOT answer questions such as "may this
 * actor assign tickets?" or "may this actor process tickets?" Those are authorization policy
 * concerns and remain with the existing call sites.
 *
 * <p>The department code is read from {@code ticketing.authorization.it-department-code} and
 * defaults to {@code IT}, which is the value that was previously hardcoded at every call site.
 *
 * <p>COMPARISON SEMANTICS ARE DELIBERATELY IDENTICAL to the code this class replaces. All previous
 * implementations used {@code "IT".equals(dept.getCode())}, which is an exact, case-sensitive,
 * non-null-safe comparison performed in that order. That exact ordering is preserved here so that
 * null departments, null codes, and codes differing only in case or whitespace keep behaving
 * exactly as before. This class intentionally does NOT trim, uppercase, or normalize codes.
 */
@Component
@ConfigurationProperties(prefix = "ticketing.authorization")
public class ItDepartmentResolver {

    /**
     * Department code identifying the IT department. Defaults to {@code IT}, preserving the
     * previously hardcoded value exactly.
     */
    private String itDepartmentCode = "IT";

    public String getItDepartmentCode() {
        return itDepartmentCode;
    }

    public void setItDepartmentCode(String itDepartmentCode) {
        this.itDepartmentCode = itDepartmentCode;
    }

    /**
     * Returns true when the supplied department is the configured IT department.
     *
     * <p>A null department yields false, matching the {@code dept != null && ...} guard that every
     * previous call site performed.
     */
    public boolean isITDepartment(Department department) {
        return department != null && isITDepartmentCode(department.getCode());
    }

    /**
     * Returns true when the supplied department code equals the configured IT department code.
     *
     * <p>Uses {@code configuredCode.equals(code)} so a null department code yields false rather
     * than throwing, which mirrors the previous {@code "IT".equals(dept.getCode())} behavior.
     */
    public boolean isITDepartmentCode(String departmentCode) {
        return itDepartmentCode != null && itDepartmentCode.equals(departmentCode);
    }
}
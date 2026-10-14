package com.example.ticketing.ticket;

import org.springframework.stereotype.Component;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.auth.UserRole;
import com.example.ticketing.department.Department;
import com.example.ticketing.department.ItDepartmentResolver;

/**
 * PHASE 3.1 - shared target validator for all assignment mutations.
 *
 * <p>This is the single component that answers the question
 * "Is this user a valid assignment TARGET?" It is intentionally separate from
 * {@link com.example.ticketing.authorization.TicketAuthorization}, which answers
 * the orthogonal question "Is this ACTOR allowed to perform this action?".
 *
 * <p>A target is valid IFF all of the following are true:
 * <ol>
 *   <li>The user exists (otherwise {@link AssignmentTargetVerdict#TARGET_NOT_FOUND}).</li>
 *   <li>The user is {@code enabled} AND {@code approved} (active). Otherwise
 *       {@link AssignmentTargetVerdict#TARGET_DISABLED}.</li>
 *   <li>The user role is exactly {@link UserRole.Role#NHAN_VIEN}. Otherwise
 *       {@link AssignmentTargetVerdict#INVALID_ROLE}.</li>
 *   <li>The user's department is the configured IT department (resolved through
 *       {@link ItDepartmentResolver}). Otherwise
 *       {@link AssignmentTargetVerdict#INVALID_DEPARTMENT}.</li>
 * </ol>
 *
 * <p>The component is a pure-function database-aware validator. It does NOT know about
 * authenticated actors, does NOT call {@code TicketAuthorization}, and does NOT throw
 * exceptions. Callers convert the verdict into the appropriate project exception or
 * choose a safe escalation fallback.
 *
 * <p>The IT department code is supplied by {@link ItDepartmentResolver} - this
 * component never hardcodes {@code "IT"}.
 */
@Component
public class AssignmentTargetValidator {

    private final UserAccountRepository userAccountRepository;
    private final ItDepartmentResolver itDepartmentResolver;

    public AssignmentTargetValidator(
        UserAccountRepository userAccountRepository,
        ItDepartmentResolver itDepartmentResolver
    ) {
        this.userAccountRepository = userAccountRepository;
        this.itDepartmentResolver = itDepartmentResolver;
    }

    /**
     * Classify the given username as a target. The username is treated as null/blank
     * = "not found" so that callers can pass a possibly-missing string without
     * pre-checking.
     */
    public AssignmentTargetVerdict validate(String targetUsername) {
        if (targetUsername == null || targetUsername.isBlank()) {
            return AssignmentTargetVerdict.TARGET_NOT_FOUND;
        }
        UserAccount target = userAccountRepository.findByUsername(targetUsername).orElse(null);
        if (target == null) {
            return AssignmentTargetVerdict.TARGET_NOT_FOUND;
        }
        if (!target.isEnabled() || !target.isApproved()) {
            return AssignmentTargetVerdict.TARGET_DISABLED;
        }
        if (target.getRole() != UserRole.Role.NHAN_VIEN) {
            return AssignmentTargetVerdict.INVALID_ROLE;
        }
        Department dept = target.getDepartment();
        if (dept == null || !itDepartmentResolver.isITDepartment(dept)) {
            return AssignmentTargetVerdict.INVALID_DEPARTMENT;
        }
        return AssignmentTargetVerdict.VALID;
    }

    /**
     * Convenience for the SLA escalation / system paths: classify a target by its
     * database id. A null id is treated as "not found" so callers can pass an
     * unset configuration value directly. The {@code Long}-based path exists
     * because SLA rules are configured with a numeric FK; a username is not
     * always available at the time of validation.
     */
    public AssignmentTargetVerdict validateById(Long targetUserId) {
        if (targetUserId == null) {
            return AssignmentTargetVerdict.TARGET_NOT_FOUND;
        }
        UserAccount target = userAccountRepository.findById(targetUserId).orElse(null);
        if (target == null) {
            return AssignmentTargetVerdict.TARGET_NOT_FOUND;
        }
        return validate(target.getUsername());
    }

    /**
     * Resolve the target {@link UserAccount} after {@link #validate(String)} returns
     * {@link AssignmentTargetVerdict#VALID}. Returns {@code null} if the verdict is not
     * VALID - callers MUST check the verdict first.
     */
    public UserAccount resolve(String targetUsername) {
        AssignmentTargetVerdict verdict = validate(targetUsername);
        if (verdict != AssignmentTargetVerdict.VALID) {
            return null;
        }
        return userAccountRepository.findByUsername(targetUsername).orElse(null);
    }
}

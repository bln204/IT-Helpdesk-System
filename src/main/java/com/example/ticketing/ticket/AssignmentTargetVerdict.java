package com.example.ticketing.ticket;

/**
 * PHASE 3.1 - the outcome of a target-validation check.
 *
 * <p>Mirrors {@link TicketService.AssignmentTargetValidation} but is the dedicated
 * public enum returned by {@link AssignmentTargetValidator} for any caller
 * (including the SLA escalation path, where an invalid verdict must NOT throw).
 */
public enum AssignmentTargetVerdict {
    /** Target satisfies existence + enabled + approved + role + department. */
    VALID,
    /** Username / id does not resolve to any UserAccount. */
    TARGET_NOT_FOUND,
    /** Target exists but is not enabled/approved. */
    TARGET_DISABLED,
    /** Target role is not NHAN_VIEN. */
    INVALID_ROLE,
    /** Target department is not the configured IT department. */
    INVALID_DEPARTMENT
}

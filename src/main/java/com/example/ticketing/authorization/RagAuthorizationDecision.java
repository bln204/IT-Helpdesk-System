package com.example.ticketing.authorization;

import java.util.Objects;

/**
 * RAG authorization verdict.
 *
 * <p>PHASE 4.1. This is the single, unambiguous answer the canonical RAG policy returns for an
 * actor (or for an unresolved / unknown actor). It deliberately distinguishes the three states
 * the policy can be in, so callers do not have to overload the meaning of an empty
 * {@code List<Long>} (which would collide with the previous "no filter" convention and was the
 * root cause of the invalid-actor unsafe default that Phase 4.0 characterized as a sharp
 * edge).
 *
 * <p>The three states are:
 * <ul>
 *   <li>{@link Kind#ALLOW_ALL} - the actor may retrieve/process tickets owned by any
 *       department. Used for IT Helpdesk operators (TRUONG_PHONG + IT and NHAN_VIEN + IT) and
 *       for the privileged read-only roles (ADMIN, GIAM_DOC).</li>
 *   <li>{@link Kind#DEPARTMENT_SCOPED} - the actor may retrieve/process tickets whose
 *       requester department matches exactly one company department (the actor's own
 *       department). Used for non-IT TRUONG_PHONG and NHAN_VIEN. The
 *       {@link #departmentCode()} carries the configured department code; the service
 *       resolves the code to an internal id when it materializes the SQL filter.</li>
 *   <li>{@link Kind#DENY} - the actor is not authorized. Used for null / blank / unknown /
 *       inactive principals. The service MUST short-circuit and return an empty response
 *       without invoking the vector search.</li>
 * </ul>
 *
 * <p>The verdict is immutable. Equality is value-based on the three fields.
 */
public final class RagAuthorizationDecision {

    /**
     * The shape of the verdict.
     */
    public enum Kind {
        ALLOW_ALL,
        DEPARTMENT_SCOPED,
        DENY
    }

    private final Kind kind;
    private final String departmentCode;
    private final String reason;

    private RagAuthorizationDecision(Kind kind, String departmentCode, String reason) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.departmentCode = departmentCode;
        this.reason = reason;
    }

    /**
     * Build an ALLOW_ALL verdict.
     */
    public static RagAuthorizationDecision allowAll() {
        return new RagAuthorizationDecision(Kind.ALLOW_ALL, null, null);
    }

    /**
     * Build a DEPARTMENT_SCOPED verdict for the given department code.
     *
     * @param departmentCode the configured department code the actor is scoped to; must be
     *        non-null
     */
    public static RagAuthorizationDecision departmentScoped(String departmentCode) {
        if (departmentCode == null) {
            throw new IllegalArgumentException("departmentCode must not be null");
        }
        return new RagAuthorizationDecision(Kind.DEPARTMENT_SCOPED, departmentCode, null);
    }

    /**
     * Build a DENY verdict.
     */
    public static RagAuthorizationDecision deny() {
        return new RagAuthorizationDecision(Kind.DENY, null, null);
    }

    /**
     * Build a DENY verdict with a human-readable reason (logged but never exposed to the
     * caller).
     */
    public static RagAuthorizationDecision deny(String reason) {
        return new RagAuthorizationDecision(Kind.DENY, null, reason);
    }

    public Kind kind() {
        return kind;
    }

    /**
     * The configured department code when {@link #kind()} is {@link Kind#DEPARTMENT_SCOPED};
     * otherwise {@code null}.
     */
    public String departmentCode() {
        return departmentCode;
    }

    /**
     * A diagnostic reason for the decision, mainly for DENY. Never exposed to API callers.
     */
    public String reason() {
        return reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RagAuthorizationDecision that)) return false;
        return kind == that.kind
            && Objects.equals(departmentCode, that.departmentCode)
            && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, departmentCode, reason);
    }

    @Override
    public String toString() {
        return "RagAuthorizationDecision[kind=" + kind
            + ", departmentCode=" + departmentCode
            + ", reason=" + reason
            + "]";
    }
}
package com.example.ticketing.authorization;

import java.util.Objects;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserRole;

/**
 * Normalized, immutable view of the authenticated actor for ticket authorization.
 *
 * <p>PHASE 2 FOUNDATION. The {@link com.example.ticketing.auth.UserAccount} entity carries a
 * great deal of state (password hash, approval, audit fields, etc.) that is irrelevant to
 * authorization policy. {@code ActorContext} exposes only the minimum facts the canonical
 * ticket policy needs:
 *
 * <ul>
 *   <li>{@link #username} - the authenticated principal (always the source of truth from
 *       {@code Authentication.getName()} via {@link ActorContextService});</li>
 *   <li>{@link #role} - the actor's role;</li>
 *   <li>{@link #departmentCode} - the actor's department code, or {@code null} when the actor
 *       has no department. The original department code is preserved exactly (no
 *       normalization);</li>
 *   <li>{@link #itDepartmentMember} - whether the actor's department is the currently
 *       configured IT department, resolved via
 *       {@link com.example.ticketing.department.ItDepartmentResolver}.</li>
 * </ul>
 *
 * <p>SCOPE. {@code ActorContext} provides facts, not policy. It deliberately answers
 * "what is this actor?" rather than "may this actor do X?". Policy decisions live in
 * {@link TicketAuthorization}.
 *
 * <p>IMMUTABILITY. All fields are {@code final}. Factories that build an {@code ActorContext}
 * are the only way to construct one. Two actor contexts are equal when their username,
 * role, department code, and IT membership are all equal; the equality contract is precise
 * enough for the policy tests but does not leak entity identity.
 */
public final class ActorContext {

    private final String username;
    private final UserRole.Role role;
    private final String departmentCode;
    private final boolean itDepartmentMember;

    private ActorContext(
        String username,
        UserRole.Role role,
        String departmentCode,
        boolean itDepartmentMember
    ) {
        this.username = Objects.requireNonNull(username, "username");
        this.role = Objects.requireNonNull(role, "role");
        this.departmentCode = departmentCode;
        this.itDepartmentMember = itDepartmentMember;
    }

    /**
     * Build an {@code ActorContext} for the given authenticated user.
     *
     * <p>The IT membership flag is supplied by the caller (typically
     * {@link ActorContextService}, after consulting
     * {@link com.example.ticketing.department.ItDepartmentResolver}). This factory does NOT
     * consult any IT source itself, so it stays a pure data carrier and is trivially testable.
     *
     * @param username the authenticated principal
     * @param role the actor's role
     * @param departmentCode the actor's department code, or {@code null}
     * @param itDepartmentMember whether the actor's department is the configured IT
     *        department
     */
    public static ActorContext of(
        String username,
        UserRole.Role role,
        String departmentCode,
        boolean itDepartmentMember
    ) {
        return new ActorContext(username, role, departmentCode, itDepartmentMember);
    }

    /**
     * Build an {@code ActorContext} from a {@link UserAccount} entity plus an already-resolved
     * IT membership flag.
     *
     * <p>The {@code departmentCode} is read from {@code user.getDepartment().getCode()} when
     * the user has a department, and is {@code null} otherwise. The {@code role} is read from
     * {@code user.getRole()}.
     */
    public static ActorContext of(UserAccount user, boolean itDepartmentMember) {
        Objects.requireNonNull(user, "user");
        String code = user.getDepartment() != null ? user.getDepartment().getCode() : null;
        return new ActorContext(user.getUsername(), user.getRole(), code, itDepartmentMember);
    }

    public String username() {
        return username;
    }

    public UserRole.Role role() {
        return role;
    }

    /**
     * The actor's department code, or {@code null} when the actor has no department.
     */
    public String departmentCode() {
        return departmentCode;
    }

    /**
     * Whether the actor's department is the currently configured IT department.
     */
    public boolean isItDepartmentMember() {
        return itDepartmentMember;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActorContext that)) return false;
        return itDepartmentMember == that.itDepartmentMember
            && username.equals(that.username)
            && role == that.role
            && Objects.equals(departmentCode, that.departmentCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, role, departmentCode, itDepartmentMember);
    }

    @Override
    public String toString() {
        return "ActorContext[username=" + username
            + ", role=" + role
            + ", departmentCode=" + departmentCode
            + ", itDepartmentMember=" + itDepartmentMember
            + "]";
    }
}
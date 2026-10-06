package com.example.ticketing.authorization;

import java.util.Objects;

import com.example.ticketing.auth.UserRole;
import org.springframework.stereotype.Component;

/**
 * Canonical ticket authorization policy.
 *
 * <p>PHASE 2 FOUNDATION. The existing code has authorization decisions scattered across
 * {@code TicketService} ({@code canAssignTickets}, {@code canModifyTicketStatus},
 * {@code addComment} visibility check), {@code TicketController} ({@code requireAssignPermission}),
 * and the JSON-serialized {@code UserAccount} entity accessors. This class is the
 * single, focused policy component that ticket authorization must converge on.
 *
 * <p>It answers policy questions; it does NOT perform ticket mutations, it does NOT load
 * tickets, it does NOT interact with the database or the security context. Callers (services
 * and controllers) obtain an {@link ActorContext} and then ask the policy.
 *
 * <p>PHASE 2 SCOPE. The current code has documented authorization gaps (Phase 0: C-1..C-7,
 * H-3, H-4). Per the Phase 2 brief, the canonical target policy is documented here, but the
 * call-site migration of gap sites is explicitly deferred to later phases so that existing
 * Phase 0 characterization tests remain green. This component can be unit-tested in
 * isolation today; call sites can adopt it incrementally once the corresponding gap is
 * scheduled.
 *
 * <p>CANONICAL TARGET POLICY (the matrix this class encodes):
 *
 * <pre>
 *   Actor                     | Process | Receive | AssignOthers | ViewInternal | AddInternal
 *   --------------------------|---------|---------|--------------|--------------|------------
 *   ADMIN                     |   DENY  |   DENY  |     DENY     |     ALLOW    |    ALLOW
 *   GIAM_DOC                  |   DENY  |   DENY  |     DENY     |     ALLOW    |    ALLOW
 *   TRUONG_PHONG + IT         |  ALLOW  |  ALLOW  |    ALLOW     |     ALLOW    |    ALLOW
 *   NHAN_VIEN + IT            |  ALLOW  |  ALLOW  |     DENY     |     ALLOW    |    ALLOW
 *   TRUONG_PHONG non-IT       |   DENY  |   DENY  |     DENY     |     DENY     |    DENY
 *   NHAN_VIEN non-IT          |   DENY  |   DENY  |     DENY     |     DENY     |    DENY
 * </pre>
 *
 * <p>"IT" above means the department whose code is returned by the configured
 * {@link com.example.ticketing.department.ItDepartmentResolver}. The matrix makes no use of
 * the requester's department; an IT Helpdesk operator can process any company's ticket.
 *
 * <p>Object-level read authorization for non-IT actors is intentionally NOT decided here -
 * see Phase 2 brief \u00a713 / unresolved policy dependency C-7.
 *
 * <p>Assignment TARGET validation is intentionally NOT in this class - see Phase 2 brief
 * \u00a711 / unresolved policy dependency C-3.
 */
@Component
public final class TicketAuthorization {

    /**
     * Default constructor. The class is a pure-function policy component: it has no state
     * and reads nothing from the surrounding application, so it is trivially safe as a
     * singleton bean.
     */
    public TicketAuthorization() {
    }

    // ------------------------------------------------------------
    // IT Helpdesk processing / receiving
    // ------------------------------------------------------------

    /**
     * May the actor process a ticket (i.e. perform ticket lifecycle operations that move a
     * ticket through its statuses, add public comments, etc.)?
     *
     * <p>Canonical answer: {@code TRUONG_PHONG + IT} or {@code NHAN_VIEN + IT}.
     */
    public boolean canProcessTickets(ActorContext actor) {
        requireActor(actor);
        return actor.role() == UserRole.Role.TRUONG_PHONG && actor.isItDepartmentMember()
            || actor.role() == UserRole.Role.NHAN_VIEN && actor.isItDepartmentMember();
    }

    /**
     * May the actor receive a ticket (take it into their own queue, e.g. self-assign)?
     *
     * <p>Distinct from {@link #canAssignOthers(ActorContext)}: an actor who can
     * <em>receive</em> may set themselves as the assignee; an actor who can
     * <em>assign others</em> may set any other user as the assignee. The current
     * codebase conflates the two; the canonical policy separates them.
     */
    public boolean canReceiveTicket(ActorContext actor) {
        requireActor(actor);
        return canProcessTickets(actor);
    }

    // ------------------------------------------------------------
    // Assignment
    // ------------------------------------------------------------

    /**
     * May the actor assign <em>other</em> users to a ticket?
     *
     * <p>Canonical answer: {@code TRUONG_PHONG + IT} only. {@code NHAN_VIEN + IT} cannot
     * assign others; they can only receive (self-assign). ADMIN and GIAM_DOC are not
     * IT Helpdesk operators and cannot assign.
     *
     * <p>This answers only the ACTOR side. TARGET validation (whether a given target user
     * is a valid assignment target) is a Phase 3 concern and is deliberately not
     * implemented here.
     */
    public boolean canAssignOthers(ActorContext actor) {
        requireActor(actor);
        return actor.role() == UserRole.Role.TRUONG_PHONG && actor.isItDepartmentMember();
    }

    // ------------------------------------------------------------
    // Status updates
    // ------------------------------------------------------------

    /**
     * May the actor update a ticket's status?
     *
     * <p>Canonical answer: same as {@link #canProcessTickets(ActorContext)}. Structural
     * validity of the transition itself is checked by
     * {@code com.example.ticketing.ticket.StatusTransitionValidator}; this method answers
     * only the actor-side policy question.
     */
    public boolean canUpdateStatus(ActorContext actor) {
        requireActor(actor);
        return canProcessTickets(actor);
    }

    // ------------------------------------------------------------
    // Comments
    // ------------------------------------------------------------

    /**
     * May the actor add a comment with {@code INTERNAL} visibility?
     *
     * <p>Canonical answer: IT Helpdesk operators ({@code TRUONG_PHONG + IT} or
     * {@code NHAN_VIEN + IT}), and the privileged roles ADMIN and GIAM_DOC (they already
     * have this ability today). Non-IT actors cannot add internal comments.
     */
    public boolean canAddInternalComment(ActorContext actor) {
        requireActor(actor);
        return switch (actor.role()) {
            case ADMIN, GIAM_DOC -> true;
            case TRUONG_PHONG, NHAN_VIEN -> actor.isItDepartmentMember();
        };
    }

    /**
     * May the actor see (list) comments with {@code INTERNAL} visibility?
     *
     * <p>Canonical answer: same as {@link #canAddInternalComment(ActorContext)}; visibility
     * of internal comments must be symmetric with the ability to author them, so the two
     * methods answer the same question.
     */
    public boolean canViewInternalComments(ActorContext actor) {
        requireActor(actor);
        return canAddInternalComment(actor);
    }

    /**
     * May the actor add a {@code PUBLIC} comment?
     *
     * <p>Canonical answer: any authenticated, active user. Public comments are the default
     * channel for requester-actor communication and must not be restricted to IT Helpdesk
     * operators. (This method is provided for clarity and for future tightening; the
     * existing code does not gate public comments at the actor level.)
     */
    public boolean canAddPublicComment(ActorContext actor) {
        requireActor(actor);
        return true;
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private static void requireActor(ActorContext actor) {
        Objects.requireNonNull(actor, "actor");
    }
}
package com.example.ticketing.authorization;

import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.ticketing.auth.UserRole;

/**
 * Canonical RAG authorization policy.
 *
 * <p>PHASE 4.1 (C-6 fix). This is the single source of truth that answers
 * "what set of department ids may this actor retrieve via RAG?". It replaces the duplicated
 * private {@code getAuthorizedDepartmentIds} method that previously lived in both
 * {@link com.example.ticketing.ai.RagSearchService} and
 * {@link com.example.ticketing.ai.RagRetrievalService}.
 *
 * <p>CANONICAL POLICY (the matrix this class encodes):
 * <pre>
 *   Actor                     | RAG verdict
 *   --------------------------+-------------------------------------------------
 *   ADMIN                     | ALLOW_ALL
 *   GIAM_DOC                  | ALLOW_ALL
 *   TRUONG_PHONG + IT         | ALLOW_ALL  (canonical IT Helpdesk manager)
 *   NHAN_VIEN + IT            | ALLOW_ALL  (canonical IT Helpdesk staff)
 *   TRUONG_PHONG non-IT       | DEPARTMENT_SCOPED (own department)
 *   NHAN_VIEN non-IT          | DEPARTMENT_SCOPED (own department)
 *   null / unresolved actor   | DENY       (NEVER falls back to ALLOW_ALL)
 * </pre>
 *
 * <p>"IT" above means the department whose code is returned by the configured
 * {@link com.example.ticketing.department.ItDepartmentResolver}. The matrix makes no use of
 * the requester's department; an IT Helpdesk operator can retrieve/process tickets from any
 * company department. This is the C-6 fix.
 *
 * <p>IT MEMBERSHIP DETECTION. This class never hardcodes the string {@code "IT"}. IT
 * membership is supplied by the caller through {@link ActorContext#isItDepartmentMember()},
 * which is itself populated by {@link ActorContextService} via
 * {@link com.example.ticketing.department.ItDepartmentResolver}. The single seam is preserved.
 *
 * <p>SCOPE. This class answers policy questions. It does NOT load tickets, it does NOT interact
 * with the database or the security context, and it does NOT perform vector search. Callers
 * obtain an {@link ActorContext} (typically via {@link ActorContextService#fromUsername(String)})
 * and ask the policy.
 *
 * <p>PURE FUNCTION. The {@link #decide(ActorContext)} method has no state and reads nothing
 * from the surrounding application beyond the actor passed in. It is trivially safe as a
 * singleton bean. The {@link #decideByUsername(String, ActorContextService)} convenience
 * overload is the only place that depends on the resolver; it is small and isolated so the rest
 * of the policy remains pure.
 */
@Component
public final class RagAuthorizationPolicy {

    /**
     * Default constructor. The class is a pure-function policy component; it has no state and
     * reads nothing from the surrounding application, so it is trivially safe as a singleton.
     */
    public RagAuthorizationPolicy() {
    }

    /**
     * Decide the RAG authorization verdict for the given actor.
     *
     * <p>A {@code null} actor yields {@link RagAuthorizationDecision#deny()}. This is the
     * critical difference from the previous {@code null}-means-no-filter convention; a
     * {@code null} principal MUST NOT execute an unfiltered vector search.
     *
     * <p>IT operators (TRUONG_PHONG + IT or NHAN_VIEN + IT) receive
     * {@link RagAuthorizationDecision#allowAll()}. This is the C-6 fix: the canonical IT
     * Helpdesk model says an IT operator may see ALL company IT tickets, including ones
     * requested by MKT / HR / Finance / etc.
     *
     * @param actor the resolved actor; may be {@code null}
     * @return the verdict, never {@code null}
     */
    public RagAuthorizationDecision decide(ActorContext actor) {
        if (actor == null) {
            return RagAuthorizationDecision.deny("unresolved actor");
        }

        // ADMIN, GIAM_DOC -> ALLOW_ALL (read-only across the company).
        if (actor.role() == UserRole.Role.ADMIN) {
            return RagAuthorizationDecision.allowAll();
        }
        if (actor.role() == UserRole.Role.GIAM_DOC) {
            return RagAuthorizationDecision.allowAll();
        }

        // TRUONG_PHONG, NHAN_VIEN -> DEPARTMENT_SCOPED OR ALLOW_ALL depending on IT membership.
        if (actor.role() == UserRole.Role.TRUONG_PHONG
            || actor.role() == UserRole.Role.NHAN_VIEN) {
            if (actor.isItDepartmentMember()) {
                // C-6 fix: IT operators see ALL company IT tickets, not just IT-requester ones.
                return RagAuthorizationDecision.allowAll();
            }
            // Non-IT TRUONG_PHONG / NHAN_VIEN: own department only.
            String departmentCode = actor.departmentCode();
            if (departmentCode == null) {
                // A non-IT operator without a department has no scope to retrieve within.
                return RagAuthorizationDecision.deny("actor has no department");
            }
            return RagAuthorizationDecision.departmentScoped(departmentCode);
        }

        // Any other role or actor shape: deny.
        return RagAuthorizationDecision.deny("actor is not authorized for RAG retrieval");
    }

    /**
     * Resolve the actor from the username, ask the policy, and return the verdict.
     *
     * <p>This overload is for service call sites that already have an
     * {@link ActorContextService} on hand. The convenience wrapper keeps the username ->
     * ActorContext -> decision conversion in one place, so the r ag services do not have to
     * repeat it.
     *
     * @param username the principal name; may be null / blank / unknown
     * @param actorContextService the canonical actor resolver; must be non-null
     * @return the verdict, never {@code null}
     */
    public RagAuthorizationDecision decideByUsername(String username, ActorContextService actorContextService) {
        Objects.requireNonNull(actorContextService, "actorContextService");
        if (username == null || username.isBlank()) {
            return RagAuthorizationDecision.deny("blank or null username");
        }
        ActorContext actor;
        try {
            actor = actorContextService.fromUsername(username);
        } catch (ResponseStatusException ex) {
            // The canonical resolver throws 401 for null / blank / unknown / inactive principals.
            return RagAuthorizationDecision.deny("unresolved principal: " + ex.getReason());
        } catch (RuntimeException ex) {
            return RagAuthorizationDecision.deny("resolver failed: " + ex);
        }
        return decide(actor);
    }
}
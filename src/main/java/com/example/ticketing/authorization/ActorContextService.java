package com.example.ticketing.authorization;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.department.ItDepartmentResolver;

/**
 * Canonical seam for converting an authenticated request into an {@link ActorContext}.
 *
 * <p>PHASE 2 FOUNDATION. The current codebase resolves the current user in many places by
 * calling {@code userAccountRepository.findByUsername(authentication.getName())} or
 * {@code UserAccount.isInITDepartment()}. This service is the single place that
 *
 * <ol>
 *   <li>reads the principal from {@link Authentication#getName()},</li>
 *   <li>loads the corresponding {@link UserAccount},</li>
 *   <li>and resolves IT membership via the existing
 *       {@link com.example.ticketing.department.ItDepartmentResolver}.</li>
 * </ol>
 *
 * <p>It does not invent a new authentication mechanism. It does not hardcode {@code "IT"}. It
 * does not change the principal type. It does not silently substitute a default user when
 * the principal cannot be resolved - it propagates a {@code 401 Unauthorized} response,
 * matching the application's existing convention for {@code userAccountRepository.findByUsername}
 * returning empty.
 *
 * <p>When the application has not loaded a Spring context (e.g. the entity is being
 * constructed for an embedded test fixture), the
 * {@link com.example.ticketing.department.ItDepartmentSupport} shadow is used so that the
 * same default code is honored.
 */
@Service
public class ActorContextService {

    private final UserAccountRepository userAccountRepository;
    private final ItDepartmentResolver itDepartmentResolver;

    public ActorContextService(
        UserAccountRepository userAccountRepository,
        ItDepartmentResolver itDepartmentResolver
    ) {
        this.userAccountRepository = userAccountRepository;
        this.itDepartmentResolver = itDepartmentResolver;
    }

    /**
     * Build an {@link ActorContext} for the given authenticated principal.
     *
     * @param authentication the Spring Security authentication object; its {@link
     *        Authentication#getName()} is the canonical username
     * @return the resolved actor context
     * @throws ResponseStatusException {@code 401 Unauthorized} when the principal is missing
     *         or does not map to an existing user account. This matches the convention used
     *         by the existing controllers (e.g. {@code TicketController.getCurrentUser}).
     */
    public ActorContext fromAuthentication(Authentication authentication) {
        if (authentication == null || authentication.getName() == null
            || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        String username = authentication.getName();
        UserAccount user = userAccountRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "User not found: " + username));
        if (!user.isEnabled() || !user.isApproved()) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "User is not active: " + username);
        }
        return ActorContext.of(user, itDepartmentResolver.isITDepartment(user.getDepartment()));
    }

    /**
     * Build an {@link ActorContext} for a username that has already been authenticated. Used
     * by service-layer methods that have already established the actor from
     * {@code authentication.getName()}.
     *
     * <p>Throws {@code 401 Unauthorized} when the username does not resolve to an active
     * user, matching the {@link #fromAuthentication(Authentication)} contract.
     */
    public ActorContext fromUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        UserAccount user = userAccountRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "User not found: " + username));
        if (!user.isEnabled() || !user.isApproved()) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "User is not active: " + username);
        }
        return ActorContext.of(user, itDepartmentResolver.isITDepartment(user.getDepartment()));
    }
}
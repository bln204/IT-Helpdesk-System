package com.example.ticketing.ticket;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.auth.UserRole;
import com.example.ticketing.ai.EmbeddingService;
import com.example.ticketing.authorization.ActorContext;
import com.example.ticketing.authorization.ActorContextService;
import com.example.ticketing.authorization.TicketAuthorization;
import com.example.ticketing.category.Category;
import com.example.ticketing.category.CategoryRepository;
import com.example.ticketing.category.CategoryTeamMapping;
import com.example.ticketing.category.CategoryTeamMappingRepository;
import com.example.ticketing.department.Department;
import com.example.ticketing.department.DepartmentRepository;
import com.example.ticketing.department.ItDepartmentResolver;
import com.example.ticketing.sla.SlaPolicyService;
import com.example.ticketing.team.Team;
import com.example.ticketing.team.TeamRepository;

import org.springframework.http.HttpStatus;

@Service
@Transactional
public class TicketService {
    private final TicketRepository ticketRepository;
    private final TicketAssignmentRepository ticketAssignmentRepository;
    private final TicketAuditRepository ticketAuditRepository;
    private final TicketCommentRepository ticketCommentRepository;
    private final TicketTimelineRepository ticketTimelineRepository;
    private final UserAccountRepository userAccountRepository;
    private final DepartmentRepository departmentRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryTeamMappingRepository categoryTeamMappingRepository;
    private final TeamRepository teamRepository;
    private final TicketNotificationService ticketNotificationService;
    private final SlaPolicyService slaPolicyService;
    private final EmbeddingService embeddingService;
    private final ItDepartmentResolver itDepartmentResolver;
    private final ActorContextService actorContextService;
    private final TicketAuthorization ticketAuthorization;
    private final AssignmentTargetValidator assignmentTargetValidator;

    public TicketService(
        TicketRepository ticketRepository,
        TicketAssignmentRepository ticketAssignmentRepository,
        TicketAuditRepository ticketAuditRepository,
        TicketCommentRepository ticketCommentRepository,
        TicketTimelineRepository ticketTimelineRepository,
        UserAccountRepository userAccountRepository,
        DepartmentRepository departmentRepository,
        CategoryRepository categoryRepository,
        CategoryTeamMappingRepository categoryTeamMappingRepository,
        TeamRepository teamRepository,
        TicketNotificationService ticketNotificationService,
        SlaPolicyService slaPolicyService,
        EmbeddingService embeddingService,
        ItDepartmentResolver itDepartmentResolver,
        ActorContextService actorContextService,
        TicketAuthorization ticketAuthorization,
        AssignmentTargetValidator assignmentTargetValidator
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketAssignmentRepository = ticketAssignmentRepository;
        this.ticketAuditRepository = ticketAuditRepository;
        this.ticketCommentRepository = ticketCommentRepository;
        this.ticketTimelineRepository = ticketTimelineRepository;
        this.userAccountRepository = userAccountRepository;
        this.departmentRepository = departmentRepository;
        this.categoryRepository = categoryRepository;
        this.categoryTeamMappingRepository = categoryTeamMappingRepository;
        this.teamRepository = teamRepository;
        this.ticketNotificationService = ticketNotificationService;
        this.slaPolicyService = slaPolicyService;
        this.embeddingService = embeddingService;
        this.itDepartmentResolver = itDepartmentResolver;
        this.actorContextService = actorContextService;
        this.ticketAuthorization = ticketAuthorization;
        this.assignmentTargetValidator = assignmentTargetValidator;
    }

    // ============================================================
    // PHASE 2.1 - canonical actor authorization helpers
    // ============================================================

    /**
     * Build the canonical {@link ActorContext} from role + department id. Used by service
     * methods that already receive both inputs from the controller. The department code is
     * resolved via a single repository lookup, then the IT membership flag is decided by the
     * configured {@link ItDepartmentResolver}.
     */
    private ActorContext actorContextFor(String username, UserRole.Role role, Long departmentId) {
        if (role == null) {
            // Defensive: an unauthenticated call should never reach the service. Mirror the
            // existing convention of throwing a 401 for the missing principal.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        String departmentCode = null;
        boolean itMember = false;
        if (departmentId != null) {
            Department dept = departmentRepository.findById(departmentId).orElse(null);
            if (dept != null) {
                departmentCode = dept.getCode();
                itMember = itDepartmentResolver.isITDepartment(dept);
            }
        }
        return ActorContext.of(username, role, departmentCode, itMember);
    }

    /**
     * Build the canonical {@link ActorContext} from a username only. Used by service methods
     * (close, reopen, addComment, listComments) that historically did not receive the actor's
     * department id. Delegates to {@link ActorContextService} so the principal source remains
     * exactly the same one the controller used.
     */
    private ActorContext actorContextFor(String username) {
        return actorContextService.fromUsername(username);
    }

    // ============================================================
    // PHASE 3 - canonical assignment TARGET validation (C-3 fix).
    // The actor policy is encoded in TicketAuthorization. The TARGET policy
    // (existence / enabled / role / department) is a database-aware concern
    // and therefore lives in the orchestration layer (TicketService).
    // A target is valid iff all of the following are true:
    //   1. User exists.
    //   2. User is enabled AND approved (active).
    //   3. User role is exactly NHAN_VIEN.
    //   4. User department is the configured IT department.
    // The IT department code is delegated to ItDepartmentResolver; "IT"
    // is never hardcoded here.
    // ============================================================

    /**
     * The outcome of the canonical target-validation step.
     *
     * <p>PHASE 3. The actor policy says "may I act"; this struct says
     * "is this specific target a valid assignment target?". The two answers
     * are intentionally separate.
     */
    public enum AssignmentTargetValidation {
        /** Target is a valid enabled NHAN_VIEN in the configured IT department. */
        VALID,
        /** Username does not resolve to any UserAccount. */
        TARGET_NOT_FOUND,
        /** Target exists but is not enabled/approved. */
        TARGET_DISABLED,
        /** Target role is not NHAN_VIEN. */
        INVALID_ROLE,
        /** Target department is not the configured IT department. */
        INVALID_DEPARTMENT
    }

    /**
     * Validate that the given target username is a valid NHAN_VIEN in the configured IT
     * department. The caller is responsible for performing the actor authorization check
     * (TicketAuthorization.canAssignOthers / canReceiveTicket) BEFORE this call.
     *
     * <p>PHASE 3.1: the underlying rule is now shared with the SLA escalation path through
     * {@link AssignmentTargetValidator}. The verdict enum and semantics are preserved so the
     * existing Phase 3 API and tests are not affected.
     */
    public AssignmentTargetValidation validateAssignmentTarget(String targetUsername) {
        return toLegacyVerdict(assignmentTargetValidator.validate(targetUsername));
    }

    private static AssignmentTargetValidation toLegacyVerdict(AssignmentTargetVerdict v) {
        return switch (v) {
            case VALID -> AssignmentTargetValidation.VALID;
            case TARGET_NOT_FOUND -> AssignmentTargetValidation.TARGET_NOT_FOUND;
            case TARGET_DISABLED -> AssignmentTargetValidation.TARGET_DISABLED;
            case INVALID_ROLE -> AssignmentTargetValidation.INVALID_ROLE;
            case INVALID_DEPARTMENT -> AssignmentTargetValidation.INVALID_DEPARTMENT;
        };
    }

    /**
     * Resolve the target UserAccount for an assignment. This method:
     * <ol>
     *   <li>Performs the canonical target validation (C-3).</li>
     *   <li>Returns the resolved UserAccount so the caller can set the FK and
     *       keep assignee_id and assignee_name synchronized.</li>
     *   <li>Throws an existing project exception (TicketRuleViolationException for
     *       actor/policy issues; TicketNotFoundException for missing targets).</li>
     * </ol>
     */
    private UserAccount resolveAssignmentTarget(String targetUsername) {
        AssignmentTargetValidation verdict = validateAssignmentTarget(targetUsername);
        switch (verdict) {
            case VALID:
                // Safe: validateAssignmentTarget already ensured the user exists and
                // is enabled.
                return userAccountRepository.findByUsername(targetUsername).orElseThrow(
                    () -> new TicketNotFoundException("Assignment target not found: " + targetUsername));
            case TARGET_NOT_FOUND:
                throw new TicketNotFoundException("Assignment target not found: " + targetUsername);
            case TARGET_DISABLED:
                throw new TicketRuleViolationException(
                    "Assignment target is not an active user: " + targetUsername);
            case INVALID_ROLE:
                throw new TicketRuleViolationException(
                    "Assignment target role is not NHAN_VIEN: " + targetUsername);
            case INVALID_DEPARTMENT:
                throw new TicketRuleViolationException(
                    "Assignment target department is not the IT Helpdesk: " + targetUsername);
            default:
                // Defensive: unreachable in this state.
                throw new TicketRuleViolationException(
                    "Assignment target is not a valid IT Helpdesk staff member: " + targetUsername);
        }
    }

    /**
     * Apply a validated assignment to the ticket and return the resolved target.
     * Keeps assignee_id and assignee_name synchronized: the caller passes both via
     * the resolved UserAccount.
     */
    private void applyValidatedTarget(Ticket t, UserAccount target) {
        t.setAssignee(target);
        t.setAssigneeName(target.getUsername());
    }

    /**
     * Apply an unassignment. Both representations are cleared consistently:
     * the legacy {@code assigneeName} and the FK {@code assignee} are both nulled.
     */
    private void clearAssignment(Ticket t) {
        t.setAssignee(null);
        t.setAssigneeName(null);
    }

    // ============================================================
    // TICKET CREATION
    // ============================================================

    /**
     * Tạo ticket mới.
     *
     * <p>PHASE 3 (H-3 fix). The actor is the authenticated principal; the requester's
     * department is the ticket's department. A client-supplied {@code assigneeName}
     * is honored ONLY when the canonical policy permits it:
     *
     * <ul>
     *   <li>ADMIN / GIAM_DOC / non-IT TRUONG_PHONG / non-IT NHAN_VIEN /
     *       NHAN_VIEN + IT: client-supplied assignee is REJECTED. The ticket
     *       remains unassigned. (NHAN_VIEN + IT uses {@code /assign/me} instead.)</li>
     *   <li>TRUONG_PHONG + IT: client-supplied assignee is accepted ONLY when the
     *       target is an enabled NHAN_VIEN in the configured IT department.
     *       Invalid targets are rejected per C-3.</li>
     * </ul>
     */
    public Ticket createTicket(
        Ticket ticket,
        String actorUsername,
        UserRole.Role actorRole
    ) {
        ticket.setTicketNumber(generateTicketNumber());
        ticket.setStatus(TicketTypes.TicketStatus.NEW);
        ticket.setRequesterUsername(actorUsername);

        // Lấy department từ requester
        UserAccount requester = userAccountRepository.findByUsername(actorUsername)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        ticket.setDepartment(requester.getDepartment());

        // Set SLA times dựa trên priority
        setSLATimes(ticket);

        // Auto-assign team dựa trên category (nếu có category)
        autoAssignTeam(ticket);

        // PHASE 3 (H-3): build the canonical ActorContext once.
        ActorContext requesterActor = actorContextFor(actorUsername, actorRole, requester.getDepartmentId());

        // PHASE 3 (H-3): decide what to do with the client-supplied assignee.
        //   * TRUONG_PHONG + IT may specify an assignee at create time, but only
        //     when the target is a valid enabled NHAN_VIEN + IT (C-3 target validation).
        //   * Every other role must not specify an assignee during creation.
        //     The client-supplied field is REJECTED (TicketRuleViolationException) rather
        //     than silently discarded; silent discard would allow a malicious actor to
        //     probe for stale data without triggering an alarm.
        // No auto-self-assign on create: the self-receive flow is /assign/me (POST
        // \u00a76), which is a separate operation that happens AFTER creation.
        String clientAssignee = normalizeAssignee(ticket.getAssigneeName());
        if (clientAssignee != null) {
            boolean canCreateWithAssignee =
                requesterActor.role() == UserRole.Role.TRUONG_PHONG
                    && requesterActor.isItDepartmentMember();
            if (!canCreateWithAssignee) {
                throw new TicketRuleViolationException(
                    "Only the IT Helpdesk manager may specify an assignee during ticket creation.");
            }
            // Validate target (C-3). resolveAssignmentTarget throws on any failure
            // (not-found, disabled, wrong department, wrong role).
            UserAccount target = resolveAssignmentTarget(clientAssignee);
            applyValidatedTarget(ticket, target);
        } else {
            // No client-supplied assignee. The ticket starts unassigned regardless of the
            // creator's role - including for NHAN_VIEN + IT, which takes ownership via
            // /assign/me (a separate post-creation operation).
            clearAssignment(ticket);
        }

        Ticket created = ticketRepository.save(ticket);
        
        // Log timeline - ticket created
        logTicketTimeline(
            created,
            TicketTimeline.EventType.TICKET_CREATED,
            TicketTimeline.EventCategory.STATUS,
            "Ticket được tạo",
            actorRole,
            actorUsername
        );
        
        // Log audit
        logAudit(
            created.getId(),
            TicketTypes.AuditAction.CREATED,
            "ticket",
            null,
            created.getTicketNumber(),
            actorRole,
            actorUsername
        );
        
        // Gửi notification
        ticketNotificationService.notifyTicketCreated(created);
        
        // Create embedding record for async processing
        try {
            embeddingService.createEmbedding(created.getId());
        } catch (Exception e) {
            // Log error but don't fail ticket creation
            // Embedding can be retried later
        }
        
        return created;
    }
    
    /**
     * Log ticket timeline event.
     */
    private void logTicketTimeline(Ticket ticket, TicketTimeline.EventType eventType,
                                  TicketTimeline.EventCategory category, String title,
                                  UserRole.Role actorRole, String actorUsername) {
        TicketTimeline event = TicketTimeline.builder()
            .ticket(ticket)
            .eventType(eventType)
            .eventCategory(category)
            .title(title)
            .userActor(actorUsername, actorUsername, actorRole != null ? actorRole.name() : null)
            .build();
        ticketTimelineRepository.save(event);
    }
    
    /**
     * Auto-assign team dựa trên category.
     * Ưu tiên subcategory > parent category.
     */
    private void autoAssignTeam(Ticket ticket) {
        Long categoryId = null;
        
        // Ưu tiên subcategory
        if (ticket.getSubcategoryEntity() != null) {
            categoryId = ticket.getSubcategoryEntity().getId();
        } else if (ticket.getCategoryEntity() != null) {
            categoryId = ticket.getCategoryEntity().getId();
        }
        
        // Nếu có category, tìm team được recommend
        if (categoryId != null) {
            categoryTeamMappingRepository.findBestTeamForCategory(categoryId)
                .ifPresent(mapping -> {
                    ticket.setTeam(mapping.getTeam());
                });
        }
    }
    
    /**
     * Set SLA times dựa trên priority và SLA Policy từ database.
     * Ưu tiên đọc từ SlaPolicy, fallback sang hardcoded values nếu không có.
     */
    private void setSLATimes(Ticket ticket) {
        TicketTypes.TicketPriority priority = ticket.getPriority();
        LocalDateTime now = LocalDateTime.now();
        
        // Sử dụng SlaPolicyService để lấy SLA deadlines
        SlaPolicyService.SlaDeadline deadline = slaPolicyService.calculateSlaDeadlines(priority, now);
        
        ticket.setSlaResponseAt(deadline.getResponseDeadline());
        ticket.setSlaResolutionAt(deadline.getResolutionDeadline());
    }

    // ============================================================
    // TICKET LISTING
    // ============================================================
    
    /**
     * Lấy tickets với department filter.
     *
     * <p>PHASE 5.1 (C-7): the authorization scope is computed from the actor context and
     * dispatched to a single authorization-aware query. We DO NOT load-then-filter; the
     * SQL boundary applies the read scope.
     *
     * <p>Scopes:
     * <ul>
     *   <li>{@code ALL} (ADMIN, GIAM_DOC, IT operators): every ticket, filtered by
     *       assignee / status / search / excludeClosed.</li>
     *   <li>{@code DEPARTMENT} (non-IT TP/NV): only tickets whose requester department
     *       equals the actor's department, with the requester-ownership carve-out
     *       (so a non-IT user can see tickets they personally created even outside
     *       their department — defensive; in normal operation requester.department
     *       equals ticket.department, so the OR-clause is a no-op).</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public Page<Ticket> listTickets(
        String assigneeName,
        TicketTypes.TicketStatus status,
        String search,
        boolean excludeClosed,
        Pageable pageable,
        String userRole,
        Long userDepartmentId,
        String username
    ) {
        ActorContext actor = actorContextFor(username, parseRoleOrNull(userRole), userDepartmentId);
        boolean hasSearch = search != null && !search.isBlank();
        boolean applyExcludeClosed = excludeClosed && status == null;
        TicketTypes.TicketStatus excludedStatus = TicketTypes.TicketStatus.CLOSED;
        String normalizedAssignee = normalizeAssignee(assigneeName);
        boolean assigneeIsUnassigned = normalizedAssignee != null
            && normalizedAssignee.equalsIgnoreCase("UNASSIGNED");
        String assigneeFilter = assigneeIsUnassigned ? "" : normalizedAssignee;
        String searchParam = hasSearch ? search.trim() : null;

        boolean isFullScope = actor.role() == UserRole.Role.ADMIN
            || actor.role() == UserRole.Role.GIAM_DOC
            || (actor.role() == UserRole.Role.TRUONG_PHONG && actor.isItDepartmentMember())
            || (actor.role() == UserRole.Role.NHAN_VIEN && actor.isItDepartmentMember());

        if (isFullScope) {
            return ticketRepository.findAuthorizedAll(
                status, assigneeFilter, searchParam, excludedStatus, applyExcludeClosed, pageable);
        }
        if (actor.role() == UserRole.Role.TRUONG_PHONG
            || actor.role() == UserRole.Role.NHAN_VIEN) {
            // Non-IT TP/NV: department OR requester ownership (canonical policy branch 4).
            Long deptId = userDepartmentId;
            if (deptId == null) {
                // A non-IT user with no department can only see their own requests.
                return ticketRepository.findByRequesterUsername(actor.username(), pageable);
            }
            return ticketRepository.findAuthorizedByDepartmentOrOwner(
                deptId, actor.username(),
                status, assigneeFilter, searchParam, excludedStatus, applyExcludeClosed, pageable);
        }
        // Unknown / null role: deny all (canonical "invalid actor" path).
        return Page.empty(pageable);
    }

    private static UserRole.Role parseRoleOrNull(String userRole) {
        if (userRole == null) {
            return null;
        }
        try {
            return UserRole.Role.valueOf(userRole);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * PHASE 5.1 (C-7): object-level read of a single ticket. The actor is loaded through
     * {@link ActorContextService} (the canonical String-principal seam) and the read
     * decision is delegated to {@link TicketAuthorization#canReadTicket(ActorContext, Ticket)}.
     * On denial we throw {@code ResponseStatusException(403)} so the existing exception
     * contract is preserved.
     */
    @Transactional(readOnly = true)
    public Ticket getTicket(Long id, Authentication authentication) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new TicketNotFoundException(id));
        ActorContext actor = actorContextService.fromAuthentication(authentication);
        if (!ticketAuthorization.canReadTicket(actor, ticket)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You don't have permission to read this ticket.");
        }
        return ticket;
    }

    /**
     * Backwards-compatible overload used by call sites that already have the ticket
     * materialized. The actor is loaded from the String principal via
     * {@link ActorContextService#fromUsername(String)}. Prefer the {@code Authentication}
     * overload when authentication is available.
     */
    @Transactional(readOnly = true)
    public Ticket getTicket(Long id, String actorUsername) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new TicketNotFoundException(id));
        ActorContext actor = actorContextService.fromUsername(actorUsername);
        if (!ticketAuthorization.canReadTicket(actor, ticket)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You don't have permission to read this ticket.");
        }
        return ticket;
    }

    /**
     * Backwards-compatible legacy overload: returns the ticket by id without any
     * object-level check. Call sites that use this are explicitly accepting the
     * pre-C-7 read behavior (e.g. internal services that already do their own check).
     * The new write/audit paths use the actor-aware overloads.
     */
    @Transactional(readOnly = true)
    public Ticket getTicket(Long id) {
        return ticketRepository.findById(id)
            .orElseThrow(() -> new TicketNotFoundException(id));
    }

    // ============================================================
    // TICKET ASSIGNMENT
    // ============================================================
    
    /**
     * Assign ticket cho user/team.
     *
     * <p>PHASE 3 (C-3 fix). After the actor authorization gate (canAssignOthers or
     * canReceiveTicket for self-receive) succeeds, the requested target is validated
     * against the canonical target policy: must be enabled NHAN_VIEN in the configured
     * IT department. Invalid targets (nonexistent / non-IT / non-NHAN_VIEN / disabled)
     * are rejected with a project exception; no partial mutation reaches the database.
     * The FK (assignee) and legacy (assigneeName) representations are kept
     * synchronized.
     */
    public Ticket assignTicket(
        Long id,
        String newAssignee,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName
    ) {
        // PHASE 2.1: canonical policy separates "receive" (self-assign) from "assign others".
        // The actor's username is passed in actorName (see TicketController), so the self-receive
        // case is exactly: newAssignee.equalsIgnoreCase(actorName).
        boolean isSelfReceive = newAssignee != null
            && actorName != null
            && newAssignee.equalsIgnoreCase(actorName);
        ActorContext actor = actorContextFor(actorName, actorRole, actorDepartmentId);
        boolean allowed = isSelfReceive
            ? ticketAuthorization.canReceiveTicket(actor)
            : ticketAuthorization.canAssignOthers(actor);
        if (!allowed) {
            throw new TicketRuleViolationException(
                isSelfReceive
                    ? "You don't have permission to receive this ticket."
                    : "You don't have permission to assign other users.");
        }

        Ticket ticket = getTicket(id);
        String previousAssignee = ticket.getAssigneeName();

        // PHASE 3 (C-3): validate the target user. This throws TicketNotFoundException for a
        // missing username and TicketRuleViolationException for role / department / disabled
        // mismatches. The validation runs BEFORE any state mutation so a partial assignment
        // can never reach the database.
        String normalizedNewAssignee = normalizeAssignee(newAssignee);
        UserAccount resolvedTarget = resolvedTargetForAssignment(newAssignee, isSelfReceive);

        // Validate transition: NEW -> ASSIGNED hoặc ASSIGNED -> ASSIGNED
        if (ticket.getStatus() == TicketTypes.TicketStatus.NEW) {
            if (!StatusTransitionValidator.isValidTransition(TicketTypes.TicketStatus.NEW, TicketTypes.TicketStatus.ASSIGNED)) {
                throw new TicketRuleViolationException("Cannot assign ticket from status: " + ticket.getStatus());
            }
            ticket.setStatus(TicketTypes.TicketStatus.ASSIGNED);
        }

        if (resolvedTarget != null) {
            applyValidatedTarget(ticket, resolvedTarget);
        } else {
            // Defensive: resolvedTargetForAssignment must always return non-null when the
            // actor passed the authorization check. If we got here, treat as unassign.
            clearAssignment(ticket);
            normalizedNewAssignee = null;
        }

        TicketAssignment assignment = new TicketAssignment();
        assignment.setTicketId(ticket.getId());
        assignment.setPreviousAssignee(previousAssignee);
        assignment.setNewAssignee(normalizedNewAssignee);
        assignment.setActorRole(actorRole.name());
        assignment.setActorName(actorName);
        ticketAssignmentRepository.save(assignment);

        logAudit(
            ticket.getId(),
            TicketTypes.AuditAction.ASSIGNEE_CHANGED,
            "assigneeName",
            previousAssignee,
            normalizedNewAssignee,
            actorRole,
            actorName
        );
        
        // Gửi notification
        ticketNotificationService.notifyTicketAssigned(ticket, actorName);

        return ticket;
    }

    /**
     * Resolve the target UserAccount for an assignment, applying the C-3 canonical
     * target policy. Self-receive (target = actor) does NOT trigger C-3 because the
     * actor's identity has already been authenticated and verified, and the
     * {@code canReceiveTicket} actor gate already restricts self-receive to IT
     * operators (TRUONG_PHONG + IT and NHAN_VIEN + IT per the canonical policy).
     * C-3 applies to assign-others: the resolved target must be a valid
     * enabled NHAN_VIEN + IT.
     */
    private UserAccount resolvedTargetForAssignment(String newAssignee, boolean isSelfReceive) {
        if (isSelfReceive) {
            // Self-receive is the self-receive policy (\u00a76), not a target-validation case.
            // The actor's eligibility to take a ticket into their own queue is decided by
            // the canReceiveTicket actor gate above. We do not re-validate their own role
            // or department here.
            return userAccountRepository.findByUsername(newAssignee).orElseThrow(
                () -> new TicketNotFoundException("Self-receive target not found: " + newAssignee));
        }
        return resolveAssignmentTarget(newAssignee);
    }
    
    /**
     * Unassign ticket (bỏ gán).
     */
    public Ticket unassignTicket(
        Long id,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName
    ) {
        // PHASE 2.1: unassign is "remove another's assignment" - canonical policy is
        // canAssignOthers (only TRUONG_PHONG + IT). ADMIN/GIAM_DOC and NHAN_VIEN + IT no longer
        // reach this code path. The previous private canAssignTickets is removed in this phase.
        ActorContext actor = actorContextFor(actorName, actorRole, actorDepartmentId);
        if (!ticketAuthorization.canAssignOthers(actor)) {
            throw new TicketRuleViolationException("You don't have permission to unassign tickets.");
        }

        Ticket ticket = getTicket(id);
        String previousAssignee = ticket.getAssigneeName();
        
        clearAssignment(ticket);

        TicketAssignment assignment = new TicketAssignment();
        assignment.setTicketId(ticket.getId());
        assignment.setPreviousAssignee(previousAssignee);
        assignment.setNewAssignee(null);
        assignment.setActorRole(actorRole.name());
        assignment.setActorName(actorName);
        ticketAssignmentRepository.save(assignment);

        logAudit(
            ticket.getId(),
            TicketTypes.AuditAction.UNASSIGNED,
            "assigneeName",
            previousAssignee,
            null,
            actorRole,
            actorName
        );

        return ticket;
    }

    // PHASE 2.1: the private canAssignTickets helper has been removed. Assignment authorization
    // is decided through ActorContext + TicketAuthorization.canAssignOthers / canReceiveTicket
    // inside assignTicket / unassignTicket above. Keeping a second private switch would have
    // duplicated the canonical policy.

    // ============================================================
    // STATUS MANAGEMENT - Phase 2.2
    // ============================================================
    
    /**
     * Cập nhật status với validation.
     */
    public Ticket updateStatus(
        Long id,
        TicketTypes.TicketStatus newStatus,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName,
        String actorUsername,
        String comment
    ) {
        Ticket ticket = getTicket(id);
        TicketTypes.TicketStatus currentStatus = ticket.getStatus();

        // Kiểm tra xem có phải IT Staff không (NHAN_VIEN trong IT department)
        boolean isITStaffUser = actorRole == UserRole.Role.NHAN_VIEN && isITDepartment(actorDepartmentId);

        // PHASE 2.1: the requester's own-ticket close/reopen path for NHAN_VIEN is preserved
        // verbatim from the previous implementation. The brief \u00a74 keeps the existing
        // requester-oriented behavior, and the canonical policy for the IT Helpdesk manager
        // remains "may process any ticket", so the operator path is unchanged.
        if (actorRole == UserRole.Role.NHAN_VIEN && !isITStaffUser) {
            if (!actorUsername.equalsIgnoreCase(ticket.getRequesterUsername())) {
                throw new TicketRuleViolationException("You can only modify your own tickets.");
            }

            // NHAN_VIEN chỉ có thể:
            // - CLOSE ticket đã RESOLVED
            // - REOPEN ticket đã CLOSED
            if (newStatus == TicketTypes.TicketStatus.CLOSED) {
                if (currentStatus != TicketTypes.TicketStatus.RESOLVED) {
                    throw new TicketRuleViolationException("You can only close resolved tickets.");
                }
            } else if (newStatus == TicketTypes.TicketStatus.REOPENED) {
                if (currentStatus != TicketTypes.TicketStatus.CLOSED) {
                    throw new TicketRuleViolationException("You can only reopen closed tickets.");
                }
            } else {
                throw new TicketRuleViolationException("You can only close or reopen your own tickets.");
            }

            return performStatusChange(ticket, currentStatus, newStatus, actorRole, actorName, comment);
        }

        // PHASE 2.1: every non-NHAN_VIEN-non-IT actor must pass the canonical processing gate.
        // The previous code allowed ADMIN, GIAM_DOC, and non-IT TRUONG_PHONG to reach the
        // transition graph (C-4 / C-5 gaps). The canonical matrix says only IT operators may
        // process tickets, so we add the gate here, BEFORE the transition graph is consulted.
        ActorContext actor = actorContextFor(actorName, actorRole, actorDepartmentId);
        if (!ticketAuthorization.canProcessTickets(actor)) {
            throw new TicketRuleViolationException(
                "You don't have permission to modify ticket status.");
        }

        // IT Staff and IT Manager: validate transition
        if (!StatusTransitionValidator.isValidTransition(currentStatus, newStatus)) {
            Set<TicketTypes.TicketStatus> validNext = StatusTransitionValidator.getValidNextStatuses(currentStatus);
            throw new TicketRuleViolationException(
                "Invalid status transition: " + currentStatus + " -> " + newStatus +
                ". Valid transitions: " + validNext
            );
        }

        // CANCELLED chỉ có thể được set bởi Admin
        if (newStatus == TicketTypes.TicketStatus.CANCELLED && actorRole != UserRole.Role.ADMIN) {
            throw new TicketRuleViolationException("Only administrators can cancel tickets.");
        }

        return performStatusChange(ticket, currentStatus, newStatus, actorRole, actorName, comment);
    }
    
    /**
     * Thực hiện thay đổi status.
     */
    private Ticket performStatusChange(
        Ticket ticket,
        TicketTypes.TicketStatus oldStatus,
        TicketTypes.TicketStatus newStatus,
        UserRole.Role actorRole,
        String actorName,
        String comment
    ) {
        // Set timestamps dựa trên newStatus
        switch (newStatus) {
            case RESOLVED -> {
                ticket.setResolvedAt(LocalDateTime.now());
                // Gửi notification cho requester
                ticketNotificationService.notifyTicketResolved(ticket, actorName);
            }
            case CLOSED -> {
                ticket.setClosedAt(LocalDateTime.now());
                if (ticket.getResolvedAt() == null) {
                    ticket.setResolvedAt(ticket.getClosedAt());
                }
                ticketNotificationService.notifyTicketClosed(ticket, actorName);
            }
            case REOPENED -> {
                ticket.incrementReopen();
                ticket.setClosedAt(null);
                ticketNotificationService.notifyTicketReopened(ticket, actorName);
            }
            case IN_PROGRESS -> {
                // Set first response time nếu chưa có
                if (ticket.getFirstResponseAt() == null) {
                    ticket.setFirstResponseAt(LocalDateTime.now());
                }
            }
            case WAITING_FOR_USER -> {
                ticketNotificationService.notifyWaitingForUser(ticket, comment);
            }
            case ESCALATED -> {
                ticket.incrementEscalation();
                ticketNotificationService.notifyTicketEscalated(ticket, actorName, comment != null ? comment : "No reason provided");
            }
            default -> {
                // Các trường hợp khác không cần xử lý đặc biệt
            }
        }
        
        ticket.setStatus(newStatus);
        
        // Log audit
        logAudit(
            ticket.getId(),
            mapStatusToAuditAction(newStatus),
            "status",
            oldStatus.name(),
            newStatus.name(),
            actorRole,
            actorName
        );
        
        // Gửi notification
        ticketNotificationService.notifyStatusChanged(ticket, oldStatus, newStatus, actorName);
        
        return ticket;
    }
    
    /**
     * Map ticket status sang audit action.
     */
    private TicketTypes.AuditAction mapStatusToAuditAction(TicketTypes.TicketStatus status) {
        return switch (status) {
            case RESOLVED -> TicketTypes.AuditAction.RESOLVED;
            case CLOSED -> TicketTypes.AuditAction.CLOSED;
            case REOPENED -> TicketTypes.AuditAction.REOPENED;
            case CANCELLED -> TicketTypes.AuditAction.CANCELLED;
            case ESCALATED -> TicketTypes.AuditAction.ESCALATED;
            case WAITING_FOR_USER -> TicketTypes.AuditAction.WAITING_FOR_INFO;
            default -> TicketTypes.AuditAction.STATUS_CHANGED;
        };
    }
    
    /**
     * Bắt đầu xử lý ticket (chuyển sang IN_PROGRESS).
     */
    public Ticket startProgress(
        Long id,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName
    ) {
        if (!canModifyTicketStatus(actorRole, actorDepartmentId, actorName)) {
            throw new TicketRuleViolationException("You don't have permission to modify ticket status.");
        }
        
        Ticket ticket = getTicket(id);
        TicketTypes.TicketStatus currentStatus = ticket.getStatus();
        
        // Validate transition
        if (!StatusTransitionValidator.isValidTransition(currentStatus, TicketTypes.TicketStatus.IN_PROGRESS)) {
            throw new TicketRuleViolationException(
                "Cannot start progress from status: " + currentStatus + 
                ". Valid transitions: " + StatusTransitionValidator.getValidNextStatuses(currentStatus)
            );
        }
        
        return performStatusChange(ticket, currentStatus, TicketTypes.TicketStatus.IN_PROGRESS, actorRole, actorName, null);
    }
    
    /**
     * Đánh dấu cần thông tin từ user.
     */
    public Ticket requestUserInfo(
        Long id,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName,
        String message
    ) {
        if (!canModifyTicketStatus(actorRole, actorDepartmentId, actorName)) {
            throw new TicketRuleViolationException("You don't have permission to modify ticket status.");
        }
        
        Ticket ticket = getTicket(id);
        TicketTypes.TicketStatus currentStatus = ticket.getStatus();
        
        if (!StatusTransitionValidator.isValidTransition(currentStatus, TicketTypes.TicketStatus.WAITING_FOR_USER)) {
            throw new TicketRuleViolationException(
                "Cannot request info from status: " + currentStatus
            );
        }
        
        return performStatusChange(ticket, currentStatus, TicketTypes.TicketStatus.WAITING_FOR_USER, actorRole, actorName, message);
    }
    
    /**
     * User cung cấp thông tin (chuyển từ WAITING_FOR_USER về IN_PROGRESS).
     */
    public Ticket provideUserInfo(
        Long id,
        UserRole.Role actorRole,
        String actorName
    ) {
        Ticket ticket = getTicket(id);
        TicketTypes.TicketStatus currentStatus = ticket.getStatus();
        
        if (currentStatus != TicketTypes.TicketStatus.WAITING_FOR_USER) {
            throw new TicketRuleViolationException("Ticket is not waiting for user info.");
        }
        
        // Validate transition
        if (!StatusTransitionValidator.isValidTransition(currentStatus, TicketTypes.TicketStatus.IN_PROGRESS)) {
            throw new TicketRuleViolationException(
                "Cannot provide info from status: " + currentStatus
            );
        }
        
        ticket = performStatusChange(ticket, currentStatus, TicketTypes.TicketStatus.IN_PROGRESS, actorRole, actorName, null);
        
        // Notify IT staff
        ticketNotificationService.notifyInfoProvided(ticket, actorName);
        
        return ticket;
    }
    
    /**
     * Resolve ticket.
     */
    public Ticket resolveTicket(
        Long id,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName,
        String resolution
    ) {
        if (!canModifyTicketStatus(actorRole, actorDepartmentId, actorName)) {
            throw new TicketRuleViolationException("You don't have permission to resolve tickets.");
        }
        
        Ticket ticket = getTicket(id);
        TicketTypes.TicketStatus currentStatus = ticket.getStatus();
        
        if (!StatusTransitionValidator.isValidTransition(currentStatus, TicketTypes.TicketStatus.RESOLVED)) {
            throw new TicketRuleViolationException(
                "Cannot resolve from status: " + currentStatus
            );
        }
        
        // Add resolution as internal comment if provided
        if (resolution != null && !resolution.isBlank()) {
            TicketComment comment = new TicketComment();
            comment.setTicketId(ticket.getId());
            comment.setVisibility(TicketTypes.CommentVisibility.INTERNAL);
            comment.setBody("Resolution: " + resolution);
            comment.setActorRole(actorRole.name());
            comment.setActorName(actorName);
            ticketCommentRepository.save(comment);
        }
        
        return performStatusChange(ticket, currentStatus, TicketTypes.TicketStatus.RESOLVED, actorRole, actorName, null);
    }
    
    /**
     * Close ticket (user confirm resolution).
     */
    public Ticket closeTicket(
        Long id,
        String actorUsername,
        UserRole.Role actorRole
    ) {
        Ticket ticket = getTicket(id);

        // PHASE 2.1: the requester's own-ticket close path for NHAN_VIEN is preserved
        // verbatim from the previous implementation (brief \u00a74). The canonical matrix says
        // ADMIN/GIAM_DOC and non-IT TRUONG_PHONG cannot process tickets, so we add the canonical
        // processing gate for everyone who is NOT the requester closing their own ticket.
        if (actorRole == UserRole.Role.NHAN_VIEN
            && actorUsername.equalsIgnoreCase(ticket.getRequesterUsername())) {
            // Non-IT staff chỉ có thể close RESOLVED tickets
            if (ticket.getStatus() != TicketTypes.TicketStatus.RESOLVED) {
                throw new TicketRuleViolationException("You can only close resolved tickets.");
            }
        } else {
            // IT operators (TRUONG_PHONG + IT, NHAN_VIEN + IT) close any ticket;
            // ADMIN/GIAM_DOC and non-IT TRUONG_PHONG are denied.
            ActorContext actor = actorContextFor(actorUsername);
            if (!ticketAuthorization.canProcessTickets(actor)) {
                throw new TicketRuleViolationException(
                    "You don't have permission to close this ticket.");
            }
        }

        TicketTypes.TicketStatus currentStatus = ticket.getStatus();

        if (!StatusTransitionValidator.isValidTransition(currentStatus, TicketTypes.TicketStatus.CLOSED)) {
            throw new TicketRuleViolationException(
                "Cannot close from status: " + currentStatus
            );
        }

        return performStatusChange(ticket, currentStatus, TicketTypes.TicketStatus.CLOSED, actorRole, actorUsername, null);
    }
    
    /**
     * Reopen ticket.
     */
    public Ticket reopenTicket(
        Long id,
        String actorUsername,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName,
        String reason
    ) {
        Ticket ticket = getTicket(id);

        // PHASE 2.1: the requester's own-ticket reopen path for NHAN_VIEN is preserved
        // verbatim (brief \u00a74). For everyone else, the canonical processing gate applies:
        // only IT operators may reopen; ADMIN/GIAM_DOC and non-IT TRUONG_PHONG are denied.
        if (!(actorRole == UserRole.Role.NHAN_VIEN
            && actorUsername.equalsIgnoreCase(ticket.getRequesterUsername()))) {
            ActorContext actor = actorContextFor(actorUsername);
            if (!ticketAuthorization.canProcessTickets(actor)) {
                throw new TicketRuleViolationException(
                    "You don't have permission to reopen this ticket.");
            }
        }

        TicketTypes.TicketStatus currentStatus = ticket.getStatus();

        if (!StatusTransitionValidator.isValidTransition(currentStatus, TicketTypes.TicketStatus.REOPENED)) {
            throw new TicketRuleViolationException(
                "Cannot reopen from status: " + currentStatus +
                ". Only CLOSED tickets can be reopened."
            );
        }
        
        // Add comment if reason provided
        if (reason != null && !reason.isBlank()) {
            TicketComment comment = new TicketComment();
            comment.setTicketId(ticket.getId());
            comment.setVisibility(TicketTypes.CommentVisibility.PUBLIC);
            comment.setBody("Reopen reason: " + reason);
            comment.setActorRole(actorRole.name());
            comment.setActorName(actorName);
            ticketCommentRepository.save(comment);
        }
        
        return performStatusChange(ticket, currentStatus, TicketTypes.TicketStatus.REOPENED, actorRole, actorName, null);
    }
    
    /**
     * Escalate ticket.
     */
    public Ticket escalateTicket(
        Long id,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName,
        String reason
    ) {
        if (!canModifyTicketStatus(actorRole, actorDepartmentId, actorName)) {
            throw new TicketRuleViolationException("You don't have permission to escalate tickets.");
        }
        
        Ticket ticket = getTicket(id);
        TicketTypes.TicketStatus currentStatus = ticket.getStatus();
        
        if (!StatusTransitionValidator.isValidTransition(currentStatus, TicketTypes.TicketStatus.ESCALATED)) {
            throw new TicketRuleViolationException(
                "Cannot escalate from status: " + currentStatus
            );
        }
        
        return performStatusChange(ticket, currentStatus, TicketTypes.TicketStatus.ESCALATED, actorRole, actorName, reason);
    }
    
    /**
     * Cancel ticket (Admin only).
     */
    public Ticket cancelTicket(
        Long id,
        UserRole.Role actorRole,
        String actorName,
        String reason
    ) {
        if (actorRole != UserRole.Role.ADMIN) {
            throw new TicketRuleViolationException("Only administrators can cancel tickets.");
        }
        
        Ticket ticket = getTicket(id);
        TicketTypes.TicketStatus currentStatus = ticket.getStatus();
        
        if (!StatusTransitionValidator.isValidTransition(currentStatus, TicketTypes.TicketStatus.CANCELLED)) {
            throw new TicketRuleViolationException(
                "Cannot cancel from status: " + currentStatus
            );
        }
        
        // Add comment if reason provided
        if (reason != null && !reason.isBlank()) {
            TicketComment comment = new TicketComment();
            comment.setTicketId(ticket.getId());
            comment.setVisibility(TicketTypes.CommentVisibility.INTERNAL);
            comment.setBody("Cancellation reason: " + reason);
            comment.setActorRole(actorRole.name());
            comment.setActorName(actorName);
            ticketCommentRepository.save(comment);
        }
        
        return performStatusChange(ticket, currentStatus, TicketTypes.TicketStatus.CANCELLED, actorRole, actorName, null);
    }
    
    /**
     * Get valid next statuses cho một ticket.
     */
    @Transactional(readOnly = true)
    public Set<TicketTypes.TicketStatus> getValidNextStatuses(Long ticketId) {
        Ticket ticket = getTicket(ticketId);
        Set<TicketTypes.TicketStatus> valid = StatusTransitionValidator.getValidNextStatuses(ticket.getStatus());
        
        // Filter out CANCELLED nếu không phải Admin
        // Sẽ được filter ở controller level
        
        return valid;
    }

    /**
     * PHASE 2.1: canonical status-modification gate. Replaces the previous private
     * canModifyTicketStatus switch. Only IT operators may modify ticket status:
     * TRUONG_PHONG + IT or NHAN_VIEN + IT. ADMIN, GIAM_DOC, non-IT TRUONG_PHONG, and non-IT
     * NHAN_VIEN are all denied. Non-IT NHAN_VIEN requesters reach a separate
     * close/reopen-only path inside updateStatus, which is preserved per the brief.
     */
    private boolean canModifyTicketStatus(UserRole.Role actorRole, Long actorDepartmentId, String actorName) {
        if (actorRole == null) {
            return false;
        }
        return ticketAuthorization.canProcessTickets(
            actorContextFor(actorName, actorRole, actorDepartmentId));
    }

    // PHASE 2.1: the private isITDepartment(Long) helper is retained for read-only checks that
    // do not need a full ActorContext (e.g. compatibility accessors and the auto-assign on
    // create). It still delegates to the configured ItDepartmentResolver, so the Phase 1
    // centralization is preserved.
    
    /**
     * Kiểm tra user có thuộc IT department không.
     */
    private boolean isITDepartment(Long departmentId) {
        if (departmentId == null) {
            return false;
        }
        Department dept = departmentRepository.findById(departmentId).orElse(null);
        // Phase 1: IT department resolution is delegated to the single configured source of
        // truth. Semantics are unchanged - it still returns false for a null or unknown id.
        return itDepartmentResolver.isITDepartment(dept);
    }

    /**
     * Looks up the configured IT department entity.
     *
     * <p>Phase 1: replaces the hardcoded {@code findByCode("IT")} lookups with the single
     * configured source of truth. Returns null when the department does not exist, which is the
     * behavior the previous call sites already relied on.
     */
    private Department resolveItDepartment() {
        return departmentRepository.findByCode(itDepartmentResolver.getItDepartmentCode()).orElse(null);
    }

    // ============================================================
    // PRIORITY MANAGEMENT
    // ============================================================
    
    public Ticket updatePriority(
        Long id,
        TicketTypes.TicketPriority newPriority,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName
    ) {
        // NHAN_VIEN ngoài IT không được phép đổi priority
        if (actorRole == UserRole.Role.NHAN_VIEN && !isITDepartment(actorDepartmentId)) {
            throw new TicketRuleViolationException("You cannot change ticket priority.");
        }

        if (!canModifyTicketStatus(actorRole, actorDepartmentId, actorName)) {
            throw new TicketRuleViolationException("You don't have permission to change priority.");
        }

        Ticket ticket = getTicket(id);
        TicketTypes.TicketPriority currentPriority = ticket.getPriority();
        
        // Cập nhật SLA nếu priority thay đổi
        ticket.setPriority(newPriority);
        setSLATimes(ticket); // Recalculate SLA
        
        logAudit(
            ticket.getId(),
            TicketTypes.AuditAction.PRIORITY_CHANGED,
            "priority",
            currentPriority == null ? null : currentPriority.name(),
            newPriority.name(),
            actorRole,
            actorName
        );

        // Trigger re-embedding if priority changed
        triggerReembedding(ticket.getId());

        return ticket;
    }
    
    // ============================================================
    // CATEGORY MANAGEMENT
    // ============================================================
    
    /**
     * Cập nhật category và subcategory của ticket.
     * Tự động cập nhật team nếu có mapping.
     */
    public Ticket updateCategory(
        Long id,
        Long categoryId,
        Long subcategoryId,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName
    ) {
        if (!canModifyTicketStatus(actorRole, actorDepartmentId, actorName)) {
            throw new TicketRuleViolationException("You don't have permission to change ticket category.");
        }

        Ticket ticket = getTicket(id);
        
        // Validate subcategory belongs to category
        Category newCategory = null;
        Category newSubcategory = null;
        
        if (categoryId != null) {
            newCategory = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new TicketRuleViolationException("Category not found: " + categoryId));
            
            // Validate là top-level category
            if (newCategory.getParent() != null) {
                throw new TicketRuleViolationException("Category must be a top-level category, not a subcategory.");
            }
        }
        
        if (subcategoryId != null) {
            newSubcategory = categoryRepository.findById(subcategoryId)
                .orElseThrow(() -> new TicketRuleViolationException("Subcategory not found: " + subcategoryId));
            
            // Validate subcategory thuộc về category
            if (newCategory == null || !newSubcategory.getParent().getId().equals(newCategory.getId())) {
                throw new TicketRuleViolationException("Subcategory does not belong to the selected category.");
            }
        }
        
        // Log old values
        Long oldCategoryId = ticket.getCategoryId();
        String oldCategoryName = ticket.getCategoryName();
        Long oldSubcategoryId = ticket.getSubcategoryId();
        String oldSubcategoryName = ticket.getSubcategoryName();
        
        // Update category
        ticket.setCategoryEntity(newCategory);
        ticket.setSubcategoryEntity(newSubcategory);
        
        // Check if category or subcategory actually changed (for re-embedding decision)
        boolean categoryChanged = !Objects.equals(oldCategoryId, categoryId);
        boolean subcategoryChanged = !Objects.equals(oldSubcategoryId, subcategoryId);
        boolean semanticChanged = categoryChanged || subcategoryChanged;
        
        // Auto-update team nếu có category change và team chưa được set
        if (categoryChanged) {
            autoAssignTeam(ticket);
        }
        
        // Log audit
        String oldCatStr = oldCategoryName != null ? oldCategoryName : "None";
        String newCatStr = newCategory != null ? newCategory.getName() : "None";
        String oldSubcatStr = oldSubcategoryName != null ? oldSubcategoryName : "None";
        String newSubcatStr = newSubcategory != null ? newSubcategory.getName() : "None";
        
        logAudit(
            ticket.getId(),
            TicketTypes.AuditAction.CATEGORY_CHANGED,
            "category",
            oldCatStr + " / " + oldSubcatStr,
            newCatStr + " / " + newSubcatStr,
            actorRole,
            actorName
        );

        // Trigger re-embedding ONLY if category or subcategory actually changed
        // This matches the fields used in buildEmbeddingText()
        if (semanticChanged) {
            triggerReembedding(ticket.getId());
        }

        return ticket;
    }

    // ============================================================
    // EMBEDDING REFRESH
    // ============================================================

    /**
     * Update ticket title and/or description.
     * Triggers re-embedding if content changes.
     */
    public Ticket updateContent(
        Long id,
        String newTitle,
        String newDescription,
        UserRole.Role actorRole,
        Long actorDepartmentId,
        String actorName
    ) {
        if (!canModifyTicketStatus(actorRole, actorDepartmentId, actorName)) {
            throw new TicketRuleViolationException("You don't have permission to edit ticket content.");
        }

        Ticket ticket = getTicket(id);
        boolean contentChanged = false;

        // Update title if provided
        if (newTitle != null && !newTitle.equals(ticket.getTitle())) {
            String oldTitle = ticket.getTitle();
            ticket.setTitle(newTitle);
            logAudit(
                ticket.getId(),
                TicketTypes.AuditAction.TITLE_CHANGED,
                "title",
                oldTitle,
                newTitle,
                actorRole,
                actorName
            );
            contentChanged = true;
        }

        // Update description if provided
        if (newDescription != null && !newDescription.equals(ticket.getDescription())) {
            String oldDesc = ticket.getDescription();
            ticket.setDescription(newDescription);
            logAudit(
                ticket.getId(),
                TicketTypes.AuditAction.DESCRIPTION_CHANGED,
                "description",
                truncateForAudit(oldDesc),
                truncateForAudit(newDescription),
                actorRole,
                actorName
            );
            contentChanged = true;
        }

        // Trigger re-embedding if content changed
        if (contentChanged) {
            triggerReembedding(ticket.getId());
        }

        return ticket;
    }

    /**
     * Truncate string for audit log.
     */
    private String truncateForAudit(String value) {
        if (value == null) return null;
        if (value.length() <= 200) return value;
        return value.substring(0, 200) + "...";
    }

    /**
     * Trigger re-embedding for a ticket.
     * Schedules the embedding to be regenerated on next scheduler run.
     */
    private void triggerReembedding(Long ticketId) {
        try {
            embeddingService.reembedTicket(ticketId);
        } catch (Exception e) {
            // Log but don't fail the operation
            // Embedding can be retried later
        }
    }

    // ============================================================
    // COMMENTS
    // ============================================================
    
    public TicketComment addComment(
        Long ticketId,
        TicketTypes.CommentVisibility visibility,
        String body,
        UserRole.Role actorRole,
        String actorName
    ) {
        // PHASE 2.1: canonical comment authorization.
        //   * INTERNAL: only the canonical policy may add. ADMIN/GIAM_DOC and the IT Helpdesk
        //     operators (TRUONG_PHONG + IT, NHAN_VIEN + IT) are allowed. Non-IT operators and
        //     any other role are denied. The previous code denied all NHAN_VIEN regardless of
        //     department (H-4 gap); the canonical matrix lifts the deny for NHAN_VIEN + IT.
        //   * PUBLIC: every authenticated actor is allowed (no actor-level gate today).
        if (visibility == TicketTypes.CommentVisibility.INTERNAL) {
            ActorContext actor = actorContextFor(actorName);
            if (!ticketAuthorization.canAddInternalComment(actor)) {
                throw new TicketRuleViolationException("Only IT staff can add internal comments.");
            }
        }

        Ticket ticket = getTicket(ticketId);
        TicketComment comment = new TicketComment();
        comment.setTicketId(ticket.getId());
        comment.setVisibility(visibility);
        comment.setBody(body);
        comment.setActorRole(actorRole.name());
        comment.setActorName(actorName);
        TicketComment saved = ticketCommentRepository.save(comment);

        logAudit(
            ticket.getId(),
            TicketTypes.AuditAction.COMMENT_ADDED,
            "comment",
            null,
            buildCommentAuditValue(visibility, body),
            actorRole,
            actorName
        );
        
        // Gửi notification về comment mới
        ticketNotificationService.notifyCommentAdded(ticket, body, actorName, 
            visibility == TicketTypes.CommentVisibility.INTERNAL);
        
        return saved;
    }

    /**
     * PHASE 5.1 (C-7): list comments of a ticket through the canonical object-level read
     * policy. The actor is loaded from the authenticated principal; the parent ticket is
     * fetched, the read gate is consulted, and the comment query runs only on success.
     * On denial a {@code 403} is returned.
     */
    @Transactional(readOnly = true)
    public List<TicketComment> listComments(
        Long ticketId,
        String actorName,
        TicketTypes.CommentVisibility visibility
    ) {
        Ticket ticket = getTicket(ticketId);
        ActorContext actor = actorContextService.fromUsername(actorName);
        if (!ticketAuthorization.canReadTicket(actor, ticket)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You don't have permission to read this ticket.");
        }
        // Existing INTERNAL-visibility gate (Phase 2.1) is preserved as an additional filter.
        if (visibility == TicketTypes.CommentVisibility.INTERNAL) {
            if (!ticketAuthorization.canViewInternalComments(actor)) {
                throw new TicketRuleViolationException(
                    "You don't have permission to view internal comments.");
            }
        }
        if (actor.role() == UserRole.Role.NHAN_VIEN && !actor.isItDepartmentMember()) {
            return ticketCommentRepository.findByTicketIdAndVisibilityOrderByCreatedAtDesc(
                ticketId, TicketTypes.CommentVisibility.PUBLIC);
        }
        if (visibility != null) {
            return ticketCommentRepository.findByTicketIdAndVisibilityOrderByCreatedAtDesc(
                ticketId, visibility);
        }
        return ticketCommentRepository.findByTicketIdOrderByCreatedAtDesc(ticketId);
    }

    /**
     * Backwards-compatible listComments overload. The new actor-aware overload is the
     * canonical C-7 path; this overload exists for the few call sites that have not yet
     * been migrated and which perform their own authorization.
     */
    @Transactional(readOnly = true)
    public List<TicketComment> listComments(
        Long ticketId,
        String actorName,
        UserRole.Role actorRole,
        TicketTypes.CommentVisibility visibility
    ) {
        // The Phase 2.1 visibility gate is preserved. The C-7 object-level read policy
        // is enforced through {@link #listComments(Long, String, TicketTypes.CommentVisibility)}
        // which is the new canonical overload.
        getTicket(ticketId);
        if (visibility == TicketTypes.CommentVisibility.INTERNAL) {
            ActorContext actor = actorContextFor(actorName);
            if (!ticketAuthorization.canViewInternalComments(actor)) {
                throw new TicketRuleViolationException(
                    "You don't have permission to view internal comments.");
            }
        }
        if (actorRole == UserRole.Role.NHAN_VIEN) {
            ActorContext actor = actorContextFor(actorName);
            if (!actor.isItDepartmentMember()) {
                return ticketCommentRepository.findByTicketIdAndVisibilityOrderByCreatedAtDesc(
                    ticketId, TicketTypes.CommentVisibility.PUBLIC);
            }
        }
        if (visibility != null) {
            return ticketCommentRepository.findByTicketIdAndVisibilityOrderByCreatedAtDesc(
                ticketId, visibility);
        }
        return ticketCommentRepository.findByTicketIdOrderByCreatedAtDesc(ticketId);
    }

    // ============================================================
    // AUDIT & ASSIGNMENTS
    // ============================================================

    /**
     * PHASE 5.1 (C-7): list audit entries of a ticket through the canonical object-level
     * read policy. Same pattern as {@link #listComments(Long, String, TicketTypes.CommentVisibility)}.
     */
    @Transactional(readOnly = true)
    public List<TicketAudit> listAudit(Long ticketId, String actorName) {
        Ticket ticket = getTicket(ticketId);
        ActorContext actor = actorContextService.fromUsername(actorName);
        if (!ticketAuthorization.canReadTicket(actor, ticket)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You don't have permission to read this ticket.");
        }
        return ticketAuditRepository.findByTicketIdOrderByCreatedAtDesc(ticketId);
    }

    /**
     * Backwards-compatible listAudit overload (no actor). The actor-aware overload is the
     * canonical C-7 path; the legacy overload is kept for internal services that already
     * do their own authorization (e.g. SLA scheduler).
     */
    @Transactional(readOnly = true)
    public List<TicketAudit> listAudit(Long ticketId) {
        getTicket(ticketId);
        return ticketAuditRepository.findByTicketIdOrderByCreatedAtDesc(ticketId);
    }

    /**
     * PHASE 5.1 (C-7): list assignment history through the canonical object-level read policy.
     */
    @Transactional(readOnly = true)
    public List<TicketAssignment> listAssignments(Long ticketId, String actorName) {
        Ticket ticket = getTicket(ticketId);
        ActorContext actor = actorContextService.fromUsername(actorName);
        if (!ticketAuthorization.canReadTicket(actor, ticket)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "You don't have permission to read this ticket.");
        }
        return ticketAssignmentRepository.findByTicketIdOrderByCreatedAtDesc(ticketId);
    }

    /**
     * Backwards-compatible listAssignments overload (no actor). The actor-aware overload is
     * the canonical C-7 path.
     */
    @Transactional(readOnly = true)
    public List<TicketAssignment> listAssignments(Long ticketId) {
        getTicket(ticketId);
        return ticketAssignmentRepository.findByTicketIdOrderByCreatedAtDesc(ticketId);
    }
    
    @Transactional(readOnly = true)
    public List<Ticket> listUnassignedQueue() {
        return ticketRepository.findByAssigneeNameIsNullOrAssigneeName("", Pageable.unpaged()).stream()
            .filter(ticket -> ticket.getStatus() != TicketTypes.TicketStatus.CLOSED)
            .toList();
    }

    // ============================================================
    // REPORTS (Giữ nguyên từ code cũ)
    // ============================================================
    
    @Transactional(readOnly = true)
    public TicketDtos.TicketCountReport getTicketCounts(String requesterUsername) {
        long total = requesterUsername == null
            ? ticketRepository.count()
            : ticketRepository.countByRequesterUsername(requesterUsername);
        List<TicketDtos.TicketStatusCountResponse> byStatus = java.util.Arrays.stream(TicketTypes.TicketStatus.values())
            .map(status -> new TicketDtos.TicketStatusCountResponse(
                status,
                requesterUsername == null
                    ? ticketRepository.countByStatus(status)
                    : ticketRepository.countByRequesterUsernameAndStatus(requesterUsername, status)
            ))
            .toList();
        return new TicketDtos.TicketCountReport(total, byStatus);
    }

    @Transactional(readOnly = true)
    public List<TicketDtos.EngineerReportRow> buildEngineerReport(LocalDateTime from, LocalDateTime to) {
        Department itDept = resolveItDepartment();
        List<UserAccount> itStaff;
        if (itDept != null) {
            itStaff = userAccountRepository.findByDepartmentIdAndEnabledTrueOrderByUsernameAsc(itDept.getId());
        } else {
            itStaff = List.of();
        }

        List<TicketAssignment> assignments = ticketAssignmentRepository.findByCreatedAtBetween(from, to);
        List<Ticket> closedTickets = ticketRepository.findByClosedAtBetween(from, to);
        double days = Math.max(1.0, Duration.between(from, to).toHours() / 24.0);

        Map<String, Set<Long>> assignedTicketIds = assignments.stream()
            .filter(assignment -> assignment.getNewAssignee() != null && !assignment.getNewAssignee().isBlank())
            .collect(Collectors.groupingBy(
                TicketAssignment::getNewAssignee,
                Collectors.mapping(TicketAssignment::getTicketId, Collectors.toSet())
            ));

        Map<String, List<Ticket>> closedByEngineer = closedTickets.stream()
            .filter(ticket -> ticket.getAssigneeName() != null && !ticket.getAssigneeName().isBlank())
            .collect(Collectors.groupingBy(Ticket::getAssigneeName));

        return itStaff.stream()
            .map(engineer -> {
                String name = engineer.getUsername();
                Set<Long> assignedIds = assignedTicketIds.getOrDefault(name, Set.of());
                List<Ticket> closed = closedByEngineer.getOrDefault(name, List.of());
                long assignedCount = assignedIds.size();
                long completedCount = closed.size();
                double avgAssignedPerDay = assignedCount / days;
                double avgCompletedPerDay = completedCount / days;
                double avgCompletionHours = closed.stream()
                    .filter(ticket -> ticket.getCreatedAt() != null && ticket.getClosedAt() != null)
                    .mapToDouble(ticket -> Duration.between(ticket.getCreatedAt(), ticket.getClosedAt()).toMinutes() / 60.0)
                    .average()
                    .orElse(0);
                return new TicketDtos.EngineerReportRow(
                    name,
                    assignedCount,
                    completedCount,
                    avgAssignedPerDay,
                    avgCompletedPerDay,
                    avgCompletionHours
                );
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public List<TicketDtos.RequesterReportRow> buildRequesterReport(LocalDateTime from, LocalDateTime to) {
        List<UserAccount> requesters = userAccountRepository.findByRoleAndEnabledTrueOrderByUsernameAsc(UserRole.Role.NHAN_VIEN);
        List<Ticket> createdTickets = ticketRepository.findByCreatedAtBetween(from, to);
        List<Ticket> closedTickets = ticketRepository.findByClosedAtBetween(from, to);

        Map<String, Long> submittedCounts = createdTickets.stream()
            .filter(ticket -> ticket.getRequesterUsername() != null && !ticket.getRequesterUsername().isBlank())
            .collect(Collectors.groupingBy(Ticket::getRequesterUsername, Collectors.counting()));

        Map<String, List<Ticket>> closedByRequester = closedTickets.stream()
            .filter(ticket -> ticket.getRequesterUsername() != null && !ticket.getRequesterUsername().isBlank())
            .collect(Collectors.groupingBy(Ticket::getRequesterUsername));

        return requesters.stream()
            .map(requester -> {
                String name = requester.getUsername();
                long submitted = submittedCounts.getOrDefault(name, 0L);
                double avgCompletionHours = closedByRequester.getOrDefault(name, List.of()).stream()
                    .filter(ticket -> ticket.getCreatedAt() != null && ticket.getClosedAt() != null)
                    .mapToDouble(ticket -> Duration.between(ticket.getCreatedAt(), ticket.getClosedAt()).toMinutes() / 60.0)
                    .average()
                    .orElse(0);
                return new TicketDtos.RequesterReportRow(name, submitted, avgCompletionHours);
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public TicketDtos.DashboardSummary buildDashboardSummary(
        String requesterUsername,
        LocalDateTime from,
        LocalDateTime to
    ) {
        List<Ticket> tickets = ticketRepository.findAll();
        if (requesterUsername != null) {
            tickets = tickets.stream()
                .filter(ticket -> requesterUsername.equalsIgnoreCase(ticket.getRequesterUsername()))
                .toList();
        }
        LocalDateTime overdueThreshold = LocalDateTime.now().minusDays(7);
        long openCount = tickets.stream()
            .filter(ticket -> ticket.getStatus() != TicketTypes.TicketStatus.CLOSED)
            .count();
        long overdueCount = tickets.stream()
            .filter(ticket -> ticket.getStatus() != TicketTypes.TicketStatus.CLOSED)
            .filter(ticket -> ticket.getCreatedAt() != null && ticket.getCreatedAt().isBefore(overdueThreshold))
            .count();
        List<Ticket> closedInRange = tickets.stream()
            .filter(ticket -> ticket.getClosedAt() != null)
            .filter(ticket -> !ticket.getClosedAt().isBefore(from) && !ticket.getClosedAt().isAfter(to))
            .toList();
        long closedCount = closedInRange.size();
        double avgCompletionHours = closedInRange.stream()
            .filter(ticket -> ticket.getCreatedAt() != null)
            .mapToDouble(ticket -> Duration.between(ticket.getCreatedAt(), ticket.getClosedAt()).toMinutes() / 60.0)
            .average()
            .orElse(0);
        List<TicketDtos.TicketStatusCountResponse> openByStatus = tickets.stream()
            .filter(ticket -> ticket.getStatus() != TicketTypes.TicketStatus.CLOSED)
            .collect(Collectors.groupingBy(Ticket::getStatus, Collectors.counting()))
            .entrySet()
            .stream()
            .map(entry -> new TicketDtos.TicketStatusCountResponse(entry.getKey(), entry.getValue()))
            .toList();
        
        // Extended dashboard stats
        TicketDtos.DashboardSummary summary = new TicketDtos.DashboardSummary(
            openCount,
            closedCount,
            overdueCount,
            avgCompletionHours,
            openByStatus
        );
        
        // Count by specific statuses
        summary.setNewCount(tickets.stream().filter(t -> t.getStatus() == TicketTypes.TicketStatus.NEW).count());
        summary.setAssignedCount(tickets.stream().filter(t -> t.getStatus() == TicketTypes.TicketStatus.ASSIGNED).count());
        summary.setInProgressCount(tickets.stream().filter(t -> t.getStatus() == TicketTypes.TicketStatus.IN_PROGRESS).count());
        summary.setWaitingCount(tickets.stream().filter(t -> t.getStatus() == TicketTypes.TicketStatus.WAITING_FOR_USER).count());
        summary.setSlaBreachedCount(tickets.stream().filter(Ticket::isResolutionSLABreached).count());
        
        return summary;
    }

    @Transactional(readOnly = true)
    public List<TicketDtos.BacklogAgingRow> buildBacklogAging(LocalDateTime from, LocalDateTime to) {
        LocalDateTime now = LocalDateTime.now();
        List<Ticket> openTickets = ticketRepository.findAll().stream()
            .filter(ticket -> ticket.getStatus() != TicketTypes.TicketStatus.CLOSED)
            .filter(ticket -> ticket.getCreatedAt() != null)
            .filter(ticket -> !ticket.getCreatedAt().isBefore(from) && !ticket.getCreatedAt().isAfter(to))
            .toList();
        Map<TicketTypes.TicketStatus, List<Ticket>> grouped = openTickets.stream()
            .collect(Collectors.groupingBy(Ticket::getStatus));
        return grouped.entrySet().stream()
            .map(entry -> {
                List<Ticket> tickets = entry.getValue();
                double avgAgeHours = tickets.stream()
                    .mapToDouble(ticket -> Duration.between(ticket.getCreatedAt(), now).toMinutes() / 60.0)
                    .average()
                    .orElse(0);
                return new TicketDtos.BacklogAgingRow(entry.getKey(), tickets.size(), avgAgeHours);
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public List<TicketDtos.SlaBucketRow> buildSlaBuckets(LocalDateTime from, LocalDateTime to) {
        LocalDateTime now = LocalDateTime.now();
        List<Ticket> openTickets = ticketRepository.findAll().stream()
            .filter(ticket -> ticket.getStatus() != TicketTypes.TicketStatus.CLOSED)
            .filter(ticket -> ticket.getCreatedAt() != null)
            .filter(ticket -> !ticket.getCreatedAt().isBefore(from) && !ticket.getCreatedAt().isAfter(to))
            .toList();
        long bucket0to2 = openTickets.stream()
            .filter(ticket -> Duration.between(ticket.getCreatedAt(), now).toHours() <= 48)
            .count();
        long bucket3to7 = openTickets.stream()
            .filter(ticket -> {
                long hours = Duration.between(ticket.getCreatedAt(), now).toHours();
                return hours > 48 && hours <= 168;
            })
            .count();
        long bucket8plus = openTickets.stream()
            .filter(ticket -> Duration.between(ticket.getCreatedAt(), now).toHours() > 168)
            .count();
        return List.of(
            new TicketDtos.SlaBucketRow("0-2 days", bucket0to2),
            new TicketDtos.SlaBucketRow("3-7 days", bucket3to7),
            new TicketDtos.SlaBucketRow("8+ days", bucket8plus)
        );
    }

    // ============================================================
    // PRIVATE HELPERS
    // ============================================================
    
    private void logAudit(
        Long ticketId,
        TicketTypes.AuditAction action,
        String fieldName,
        String oldValue,
        String newValue,
        UserRole.Role actorRole,
        String actorName
    ) {
        TicketAudit audit = new TicketAudit();
        audit.setTicketId(ticketId);
        audit.setAction(action);
        audit.setFieldName(fieldName);
        audit.setOldValue(oldValue);
        audit.setNewValue(newValue);
        audit.setActorRole(actorRole.name());
        audit.setActorName(actorName);
        ticketAuditRepository.save(audit);
    }

    private String buildCommentAuditValue(TicketTypes.CommentVisibility visibility, String body) {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.length() > 400) {
            trimmed = trimmed.substring(0, 400) + "...";
        }
        return visibility.name() + ":" + trimmed;
    }

    private String normalizeAssignee(String assigneeName) {
        if (assigneeName == null) {
            return null;
        }
        String trimmed = assigneeName.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String generateTicketNumber() {
        return "TCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
            .toUpperCase();
    }
}

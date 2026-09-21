package com.example.ticketing.auth;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserAuditService {
    private final UserAuditRepository userAuditRepository;

    public UserAuditService(UserAuditRepository userAuditRepository) {
        this.userAuditRepository = userAuditRepository;
    }

    /**
     * Log an audit action with basic information.
     */
    public void log(
        UserAuditAction action,
        String actorUsername,
        String actorRole,
        String targetUsername
    ) {
        log(action, actorUsername, actorRole, targetUsername, null, null, null);
    }
    
    /**
     * Log an audit action with details (additional context).
     */
    public void log(
        UserAuditAction action,
        String actorUsername,
        String actorRole,
        String targetUsername,
        String details
    ) {
        log(action, actorUsername, actorRole, targetUsername, details, null, null);
    }

    /**
     * Log an audit action with old and new values (for tracking changes).
     */
    public void log(
        UserAuditAction action,
        String actorUsername,
        String actorRole,
        String targetUsername,
        String oldValue,
        String newValue
    ) {
        log(action, actorUsername, actorRole, targetUsername, null, oldValue, newValue);
    }

    /**
     * Log an audit action with full details including context, old value, and new value.
     * 
     * @param action The action being performed
     * @param actorUsername Username of the user performing the action
     * @param actorRole Role of the actor
     * @param targetUsername Username of the user being affected
     * @param details Additional context about the action
     * @param oldValue Previous value before the change
     * @param newValue New value after the change
     */
    public void log(
        UserAuditAction action,
        String actorUsername,
        String actorRole,
        String targetUsername,
        String details,
        String oldValue,
        String newValue
    ) {
        UserAudit audit = new UserAudit();
        audit.setAction(action);
        audit.setActorUsername(actorUsername);
        audit.setActorRole(actorRole);
        audit.setTargetUsername(targetUsername);
        audit.setDetails(details);
        audit.setOldValue(oldValue);
        audit.setNewValue(newValue);
        userAuditRepository.save(audit);
    }

    @Transactional(readOnly = true)
    public List<UserAudit> listAll() {
        return userAuditRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<UserAudit> listForTarget(String targetUsername) {
        return userAuditRepository.findByTargetUsernameOrderByCreatedAtDesc(targetUsername);
    }
    
    /**
     * List audit records by action type.
     */
    @Transactional(readOnly = true)
    public List<UserAudit> listByAction(UserAuditAction action) {
        return userAuditRepository.findByActionOrderByCreatedAtDesc(action);
    }
    
    /**
     * List audit records by actor.
     */
    @Transactional(readOnly = true)
    public List<UserAudit> listByActor(String actorUsername) {
        return userAuditRepository.findByActorUsernameOrderByCreatedAtDesc(actorUsername);
    }
}

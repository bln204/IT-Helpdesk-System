package com.example.ticketing.auth;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.ticket.TicketTypes;

@Service
@Transactional
public class UserAuditService {
    private final UserAuditRepository userAuditRepository;

    public UserAuditService(UserAuditRepository userAuditRepository) {
        this.userAuditRepository = userAuditRepository;
    }

    public void log(
        UserAuditAction action,
        String actorUsername,
        TicketTypes.TicketRole actorRole,
        String targetUsername
    ) {
        UserAudit audit = new UserAudit();
        audit.setAction(action);
        audit.setActorUsername(actorUsername);
        audit.setActorRole(actorRole);
        audit.setTargetUsername(targetUsername);
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
}

package com.example.ticketing.authorization;

import org.springframework.stereotype.Component;

/**
 * Quyền Problem / Known Error — spec phase1-rbac-hardening §4.3.
 * Đọc Problem và Known Error mở cho mọi role (self-service); chỉ ghi chú nội bộ bị giới hạn.
 */
@Component
public final class ProblemAuthorization {

    public boolean canWriteProblem(ActorContext actor) {
        return ActorTiers.isItOperator(actor);
    }

    public boolean canWriteKnownError(ActorContext actor) {
        return ActorTiers.isItLead(actor);
    }

    public boolean canReadNotes(ActorContext actor) {
        return ActorTiers.seesAll(actor);
    }

    public boolean canWriteNotes(ActorContext actor) {
        return ActorTiers.isItOperator(actor);
    }

    public boolean canDeleteProblem(ActorContext actor) {
        return ActorTiers.isAdmin(actor);
    }
}

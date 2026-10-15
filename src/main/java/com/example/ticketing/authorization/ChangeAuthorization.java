package com.example.ticketing.authorization;

import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import com.example.ticketing.change.ChangeRequest;

/** Quyền Change — spec phase1-rbac-hardening §4.2. */
@Component
public final class ChangeAuthorization {

    public boolean canRead(ActorContext actor, ChangeRequest change) {
        return ActorTiers.seesAll(actor) || ActorTiers.sameUser(actor, change.getRequesterUsername());
    }

    public Specification<ChangeRequest> readScope(ActorContext actor) {
        if (ActorTiers.seesAll(actor)) {
            return Specification.unrestricted();
        }
        String me = actor.username().toLowerCase(Locale.ROOT);
        return (root, q, cb) -> cb.equal(cb.lower(root.get("requesterUsername")), me);
    }

    public boolean canWrite(ActorContext actor) {
        return ActorTiers.isItOperator(actor);
    }

    public boolean canApprove(ActorContext actor) {
        return ActorTiers.isItLead(actor);
    }

    public boolean canViewCab(ActorContext actor) {
        return ActorTiers.seesAll(actor);
    }
}

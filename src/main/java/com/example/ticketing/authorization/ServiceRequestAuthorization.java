package com.example.ticketing.authorization;

import java.util.Locale;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import com.example.ticketing.servicerequest.ServiceRequest;

import jakarta.persistence.criteria.Predicate;

/** Quyền Service Request — spec phase1-rbac-hardening §4.1. */
@Component
public final class ServiceRequestAuthorization {

    private final DepartmentKeys departmentKeys;

    public ServiceRequestAuthorization(DepartmentKeys departmentKeys) {
        this.departmentKeys = departmentKeys;
    }

    public boolean canRead(ActorContext actor, ServiceRequest sr) {
        if (ActorTiers.seesAll(actor) || isRequester(actor, sr)) {
            return true;
        }
        return ActorTiers.isBusinessLead(actor) && departmentKeys.matches(actor, sr.getRequesterDepartment());
    }

    public Specification<ServiceRequest> readScope(ActorContext actor) {
        if (ActorTiers.seesAll(actor)) {
            return Specification.unrestricted();
        }
        String me = actor.username().toLowerCase(Locale.ROOT);
        Set<String> deptKeys = ActorTiers.isBusinessLead(actor) ? departmentKeys.of(actor) : Set.of();
        return (root, q, cb) -> {
            Predicate own = cb.equal(cb.lower(root.get("requesterUsername")), me);
            return deptKeys.isEmpty()
                    ? own
                    : cb.or(own, cb.lower(root.get("requesterDepartment")).in(deptKeys));
        };
    }

    public boolean isRequester(ActorContext actor, ServiceRequest sr) {
        return ActorTiers.sameUser(actor, sr.getRequesterUsername());
    }

    public boolean canProcess(ActorContext actor) {
        return ActorTiers.isItOperator(actor);
    }

    public boolean canApprove(ActorContext actor) {
        return ActorTiers.isItLead(actor);
    }

    public boolean canDelete(ActorContext actor) {
        return ActorTiers.isAdmin(actor);
    }

    public boolean canSeeInternal(ActorContext actor) {
        return ActorTiers.seesAll(actor);
    }
}

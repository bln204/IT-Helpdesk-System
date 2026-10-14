package com.example.ticketing.authorization;

import java.util.Locale;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import com.example.ticketing.asset.Asset;

import jakarta.persistence.criteria.Predicate;

/** Quyền Asset — spec phase1-rbac-hardening §4.4. */
@Component
public final class AssetAuthorization {

    private final DepartmentKeys departmentKeys;

    public AssetAuthorization(DepartmentKeys departmentKeys) {
        this.departmentKeys = departmentKeys;
    }

    public boolean canRead(ActorContext actor, Asset asset) {
        if (ActorTiers.seesAll(actor) || ActorTiers.sameUser(actor, asset.getAssignedTo())) {
            return true;
        }
        return ActorTiers.isBusinessLead(actor) && departmentKeys.matches(actor, asset.getAssignedDepartment());
    }

    public Specification<Asset> readScope(ActorContext actor) {
        if (ActorTiers.seesAll(actor)) {
            return Specification.unrestricted();
        }
        String me = actor.username().toLowerCase(Locale.ROOT);
        Set<String> deptKeys = ActorTiers.isBusinessLead(actor) ? departmentKeys.of(actor) : Set.of();
        return (root, q, cb) -> {
            Predicate own = cb.equal(cb.lower(root.get("assignedTo")), me);
            return deptKeys.isEmpty()
                    ? own
                    : cb.or(own, cb.lower(root.get("assignedDepartment")).in(deptKeys));
        };
    }

    public boolean canWrite(ActorContext actor) {
        return ActorTiers.isItOperator(actor);
    }

    /** Thống kê, bảo hành, lịch bảo trì toàn công ty. */
    public boolean canViewFleet(ActorContext actor) {
        return ActorTiers.seesAll(actor);
    }
}

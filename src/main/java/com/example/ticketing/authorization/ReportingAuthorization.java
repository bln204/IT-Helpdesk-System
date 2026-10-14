package com.example.ticketing.authorization;

import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import com.example.ticketing.ticket.Ticket;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

/** Quyền Dashboard / Analytics / Report — spec phase1-rbac-hardening §4.6. */
@Component
public final class ReportingAuthorization {

    public boolean canSeeCompanyStats(ActorContext actor) {
        return ActorTiers.seesAll(actor);
    }

    public boolean canSeeDepartmentStats(ActorContext actor) {
        return ActorTiers.seesAll(actor) || ActorTiers.isBusinessLead(actor);
    }

    /** GS/IT: tất cả · TP nghiệp vụ: phòng mình hoặc mình tạo · NV nghiệp vụ: chỉ mình tạo. */
    public Specification<Ticket> ticketStatsScope(ActorContext actor) {
        if (ActorTiers.seesAll(actor)) {
            return Specification.unrestricted();
        }
        String me = actor.username().toLowerCase(Locale.ROOT);
        String dept = ActorTiers.isBusinessLead(actor) ? actor.departmentCode() : null;
        return (root, q, cb) -> {
            Predicate own = cb.equal(cb.lower(root.get("requesterUsername")), me);
            return dept == null
                    ? own
                    : cb.or(own, cb.equal(root.join("department", JoinType.LEFT).get("code"), dept));
        };
    }
}

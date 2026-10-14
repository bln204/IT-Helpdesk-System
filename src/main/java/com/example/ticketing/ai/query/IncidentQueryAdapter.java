package com.example.ticketing.ai.query;

import com.example.ticketing.incident.Incident;
import com.example.ticketing.incident.IncidentRepository;
import com.example.ticketing.ticket.TicketTypes;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
public class IncidentQueryAdapter extends JpaQueryAdapter<Incident> {

    /** Mirrors IncidentController#getAllIncidents. */
    static final AccessRule ACCESS = AccessRule.allRoles();

    private static final List<String> OPEN = List.of("INVESTIGATING", "IN_PROGRESS", "IDENTIFIED");

    static final DomainDef DEF = new DomainDef("incident", "sự cố lớn",
            "sự cố lớn (incident) ảnh hưởng nhiều người dùng, có SLA phản hồi/xử lý",
            List.of(
                    FieldDef.text("incidentNumber", "incidentNumber", "mã incident, vd INC-E47D19D9"),
                    FieldDef.text("title", "title", "tiêu đề"),
                    FieldDef.enumeration("status", "status", Incident.IncidentStatus.class, "trạng thái")
                            .withGroups(Map.of("OPEN", OPEN, "DANG_MO", OPEN, "DONE", List.of("RESOLVED", "CLOSED"))),
                    FieldDef.enumeration("priority", "priority", TicketTypes.TicketPriority.class, "độ ưu tiên"),
                    FieldDef.enumeration("impactLevel", "impactLevel", Incident.ImpactLevel.class, "mức ảnh hưởng"),
                    FieldDef.custom("assignee", "người phụ trách (username hoặc tên)",
                            EnumSet.of(FilterOp.EQ, FilterOp.IN, FilterOp.CONTAINS), IncidentQueryAdapter::assignee),
                    FieldDef.boolCustom("slaBreached", "đã vi phạm SLA", IncidentQueryAdapter::slaBreached),
                    FieldDef.date("createdAt", "createdAt", "ngày tạo"),
                    FieldDef.date("resolvedAt", "resolvedAt", "ngày giải quyết")),
            "createdAt", true);

    private final IncidentRepository repository;

    public IncidentQueryAdapter(IncidentRepository repository) {
        this.repository = repository;
    }

    @Override public DomainDef definition() { return DEF; }
    @Override public AccessRule accessRule() { return ACCESS; }
    @Override protected JpaSpecificationExecutor<Incident> repository() { return repository; }
    @Override protected String idPath() { return "incidentNumber"; }

    // Breached = response OR resolution SLA breached; a null flag counts as not breached.
    private static Predicate assignee(Root<?> root, CriteriaQuery<?> q, CriteriaBuilder cb, ResolvedFilter f) {
        Join<?, ?> user = root.join("assignedTo", JoinType.LEFT);
        return cb.or(SpecificationFactory.anyContains(cb, cb.coalesce(user.<String>get("username"), ""), f.values()),
                SpecificationFactory.anyContains(cb, cb.coalesce(user.<String>get("displayName"), ""), f.values()));
    }

    private static Predicate slaBreached(Root<?> root, CriteriaQuery<?> q, CriteriaBuilder cb, ResolvedFilter f) {
        Predicate breached = cb.or(
                cb.isTrue(cb.coalesce(root.<Boolean>get("responseSlaBreached"), Boolean.FALSE)),
                cb.isTrue(cb.coalesce(root.<Boolean>get("resolutionSlaBreached"), Boolean.FALSE)));
        return Boolean.TRUE.equals(f.values().get(0)) ? breached : cb.not(breached);
    }

    @Override
    protected Row toRow(Incident i) {
        boolean breached = Boolean.TRUE.equals(i.getResponseSlaBreached()) || Boolean.TRUE.equals(i.getResolutionSlaBreached());
        return new Row(i.getIncidentNumber(), i.getTitle(), fields(
                "trạng thái", i.getStatus(),
                "ưu tiên", i.getPriority(),
                "ảnh hưởng", i.getImpactLevel(),
                "người phụ trách", i.getAssignedTo() == null ? null : i.getAssignedTo().getDisplayName(),
                "vi phạm SLA", breached ? "có" : "không",
                "ngày tạo", i.getCreatedAt()),
                "Mô tả: " + fmt(i.getDescription()) + " | Nguyên nhân gốc: " + fmt(i.getRootCause())
                        + " | Cách khắc phục tạm: " + fmt(i.getWorkaround()));
    }
}

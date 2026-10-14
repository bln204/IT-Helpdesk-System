package com.example.ticketing.ai.query;

import com.example.ticketing.authorization.ActorContext;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Role gate + dispatch + audit log. No business logic. */
@Service
public class QueryExecutor {

    private static final Logger log = LoggerFactory.getLogger(QueryExecutor.class);

    private final DomainCatalog catalog;

    private final com.example.ticketing.auth.UserAccountRepository users;

    public QueryExecutor(DomainCatalog catalog, com.example.ticketing.auth.UserAccountRepository users) {
        this.catalog = catalog;
        this.users = users;
    }

    public QueryResult execute(ValidatedPlan plan, ActorContext actor) {
        DomainQueryAdapter adapter = catalog.adapter(plan.domain().name())
                .orElseThrow(() -> new IllegalStateException("Không có adapter cho " + plan.domain().name()));
        QueryResult result;
        ValidatedPlan effective = resolveMe(plan, actor);
        if (!adapter.accessRule().allows(actor)) {
            result = QueryResult.denied(adapter.accessRule().deniedMessage(plan.domain().label()));
        } else {
            try {
                result = adapter.execute(effective, actor);
            } catch (RuntimeException e) {
                log.error("AI query lỗi domain={} user={}", plan.domain().name(), actor == null ? null : actor.username(), e);
                result = QueryResult.error(e.getMessage());
            }
        }
        // Audit: who asked what, and the outcome. Never the row contents.
        log.info("AI query user={} domain={} action={} filters={} target={} status={} rows={} total={}",
                actor == null ? null : actor.username(), plan.domain().name(), plan.action(),
                plan.filters().stream().map(f -> f.field().name() + ":" + f.op().wire() + "=" + f.values())
                        .collect(Collectors.joining(",")),
                plan.targetId(), result.status(), result.rows().size(), result.totalCount());
        return result;
    }

    /** Text columns hold a username or a display name depending on who wrote them: "@me" matches either. */
    private ValidatedPlan resolveMe(ValidatedPlan plan, ActorContext actor) {
        if (actor == null || actor.username() == null
                || plan.filters().stream().noneMatch(f -> f.values().contains(JpaQueryAdapter.ME))) {
            return plan;
        }
        String display = users.findByUsername(actor.username())
                .map(com.example.ticketing.auth.UserAccount::getDisplayName)
                .filter(s -> !s.isBlank()).orElse(null);
        List<ResolvedFilter> filters = plan.filters().stream().map(f -> {
            if (!f.values().contains(JpaQueryAdapter.ME)) return f;
            List<Object> values = new ArrayList<>();
            for (Object v : f.values()) {
                if (!JpaQueryAdapter.ME.equals(v)) values.add(v);
                else {
                    values.add(actor.username());
                    if (display != null) values.add(display);
                }
            }
            return new ResolvedFilter(f.field(), f.op(), values);
        }).toList();
        return new ValidatedPlan(plan.action(), plan.domain(), filters, plan.sortPath(), plan.sortDesc(),
                plan.limit(), plan.offset(), plan.targetId(), plan.clarifyQuestion(), plan.source());
    }
}

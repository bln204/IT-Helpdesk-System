package com.example.ticketing.ai.query;

import com.example.ticketing.authorization.ActorContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.example.ticketing.ai.RagContextDto;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

public abstract class JpaQueryAdapter<T> implements DomainQueryAdapter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    static final String ME = "@me";

    public record Scope<T>(Specification<T> spec, String note, String deniedMessage) {
        public static <T> Scope<T> all() {
            return new Scope<>(Specification.unrestricted(), null, null);
        }

        public static <T> Scope<T> of(Specification<T> spec, String note) {
            return new Scope<>(spec, note, null);
        }

        public static <T> Scope<T> deny(String message) {
            return new Scope<>(Specification.unrestricted(), null, message);
        }
    }

    protected abstract JpaSpecificationExecutor<T> repository();

    /** Entity attribute holding the human id (ticketNumber, assetNumber, ...). */
    protected abstract String idPath();

    protected abstract Row toRow(T entity);

    /** Row-level scope mirroring what the UI shows this actor. Default: no row restriction. */
    protected Scope<T> scope(ActorContext actor) {
        return Scope.all();
    }

    /** Ids semantically similar to the text, already RBAC-scoped. Only Ticket and KnownError support it. */
    protected List<Long> semanticIds(String text, ActorContext actor) {
        throw new UnsupportedOperationException(definition().name() + " không hỗ trợ semantic_match");
    }

    /** A reference value stored by name but also known by its code ("Phòng Nhân sự" / "HR"). */
    public record Term(String name, String code) { }

    /**
     * Reference-name fields with few real values (department, team). Users name them by code ("HR") or
     * without diacritics ("ke toan"); both must resolve to the stored name before the SQL LIKE.
     */
    protected Map<String, Supplier<Collection<Term>>> vocabularies() {
        return Map.of();
    }

    private static final Set<String> NO_HIT_STATUSES = Set.of("success", "no_results", "empty_query", "denied");

    /** A failed embedding search must surface as ERROR, never as "no matching rows". */
    protected static List<Long> semanticHits(RagContextDto.RetrievalResponse r) {
        if (r == null) return List.of();
        if (r.getStatus() != null && !NO_HIT_STATUSES.contains(r.getStatus())) {
            throw new IllegalStateException("Tìm kiếm ngữ nghĩa lỗi: " + r.getStatus());
        }
        if (r.getSources() == null) return List.of();
        return r.getSources().stream().map(RagContextDto.RagSource::getId).filter(Objects::nonNull).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public QueryResult execute(ValidatedPlan plan, ActorContext actor) {
        Scope<T> scope = scope(actor);
        if (scope.deniedMessage() != null) return QueryResult.denied(scope.deniedMessage());

        List<Specification<T>> parts = new ArrayList<>();
        parts.add(scope.spec());
        for (ResolvedFilter raw : plan.filters()) {
            ResolvedFilter f = canonical(withMe(raw, actor));
            if (f.op() == FilterOp.SEMANTIC_MATCH) {
                List<Long> ids = semanticIds((String) f.values().get(0), actor);
                if (ids.isEmpty()) return QueryResult.of(List.of(), 0, plan.offset(), scope.note());
                parts.add((root, q, cb) -> root.get("id").in(ids));
            } else {
                parts.add(SpecificationFactory.toSpecification(f));
            }
        }

        if (plan.targetId() != null) {
            parts.add(idEquals(plan.targetId()));
            List<T> found = repository().findAll(Specification.allOf(parts));
            if (found.isEmpty()
                    && !repository().exists(Specification.allOf(List.of(scope.spec(), idEquals(plan.targetId()))))
                    && repository().exists(idEquals(plan.targetId()))) {
                // The record exists but is outside this actor's scope: same 403 the UI returns.
                return QueryResult.denied("Bạn không có quyền xem " + plan.targetId() + ".");
            }
            return QueryResult.of(found.stream().map(this::toRow).toList(), found.size(), 0, scope.note());
        }

        Specification<T> spec = Specification.allOf(parts);
        if (plan.action() == QueryAction.COUNT) {
            return QueryResult.count(repository().count(spec), scope.note());
        }
        Sort sort = Sort.by(plan.sortDesc() ? Sort.Direction.DESC : Sort.Direction.ASC, plan.sortPath());
        Page<T> page = repository().findAll(spec, PageRequest.of(plan.offset() / plan.limit(), plan.limit(), sort));
        return QueryResult.of(page.getContent().stream().map(this::toRow).toList(),
                page.getTotalElements(), plan.offset(), scope.note());
    }

    /** "@me" is how the planner says "the person asking"; only Java knows who that is. */
    private static ResolvedFilter withMe(ResolvedFilter f, ActorContext actor) {
        if (actor == null || f.values().stream().noneMatch(ME::equals)) return f;
        List<Object> values = f.values().stream().map(v -> ME.equals(v) ? (Object) actor.username() : v).toList();
        return new ResolvedFilter(f.field(), f.op(), values);
    }

    private ResolvedFilter canonical(ResolvedFilter f) {
        Supplier<Collection<Term>> vocabulary = vocabularies().get(f.field().name());
        if (vocabulary == null || f.values().isEmpty()) return f;
        Collection<Term> terms = vocabulary.get();
        List<Object> out = new ArrayList<>();
        for (Object v : f.values()) {
            String want = Text.fold(v.toString()).trim().toLowerCase(Locale.ROOT);
            List<String> hits = terms.stream()
                    .filter(t -> t.name() != null)
                    .filter(t -> Text.fold(t.name()).toLowerCase(Locale.ROOT).contains(want)
                            || (t.code() != null && t.code().trim().equalsIgnoreCase(want)))
                    .map(Term::name).toList();
            if (hits.isEmpty()) out.add(v);
            else out.addAll(hits);
        }
        return new ResolvedFilter(f.field(), f.op(), out);
    }

    private Specification<T> idEquals(String id) {
        String wanted = id.trim().toUpperCase(Locale.ROOT);
        return (root, q, cb) -> cb.equal(cb.upper(root.<String>get(idPath())), wanted);
    }

    /** fields("trạng thái", status, "vị trí", location) -> ordered map; null shows as "(không có)". */
    protected static Map<String, String> fields(Object... labelValuePairs) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < labelValuePairs.length; i += 2) {
            out.put((String) labelValuePairs[i], fmt(labelValuePairs[i + 1]));
        }
        return out;
    }

    protected static String yesNo(Boolean value) {
        return Boolean.TRUE.equals(value) ? "có" : "không";
    }

    protected static String fmt(Object value) {
        if (value == null) return "(không có)";
        if (value instanceof LocalDateTime t) return t.format(DATE_TIME);
        if (value instanceof LocalDate d) return d.format(DATE);
        if (value instanceof Enum<?> e) return e.name();
        String s = value.toString();
        return s.isBlank() ? "(không có)" : s;
    }
}

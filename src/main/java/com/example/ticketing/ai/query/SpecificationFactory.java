package com.example.ticketing.ai.query;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Generic ResolvedFilter -> JPA predicate. Every filter runs in SQL, before paging. */
final class SpecificationFactory {

    private SpecificationFactory() { }

    static <T> Specification<T> toSpecification(ResolvedFilter filter) {
        return (root, query, cb) -> toPredicate(root, query, cb, filter);
    }

    static Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder cb, ResolvedFilter f) {
        FieldDef field = f.field();
        if (field.custom() != null) return field.custom().toPredicate(root, query, cb, f);
        Path<?> path = resolvePath(root, field.path());
        return switch (field.type()) {
            case TEXT -> text(cb, cast(path), f);
            case ENUM -> enumeration(cb, path, f);
            case DATE -> date(cb, path, f);
            case BOOLEAN -> bool(cb, cast(path), f);
            case SEMANTIC, CUSTOM -> throw new IllegalStateException("Field " + field.name() + " cần xử lý riêng");
        };
    }

    /** Left joins so an OR over a nullable relation never drops rows. */
    static Path<?> resolvePath(Root<?> root, String dotted) {
        String[] parts = dotted.split("\\.");
        From<?, ?> from = root;
        for (int i = 0; i < parts.length - 1; i++) {
            from = from.join(parts[i], JoinType.LEFT);
        }
        return from.get(parts[parts.length - 1]);
    }

    static Predicate anyContains(CriteriaBuilder cb, Expression<String> expr, List<Object> values) {
        return cb.or(values.stream()
                .map(v -> cb.like(cb.lower(expr), "%" + escapeLike(v.toString().toLowerCase(Locale.ROOT)) + "%", '\\'))
                .toArray(Predicate[]::new));
    }

    private static Predicate text(CriteriaBuilder cb, Path<String> p, ResolvedFilter f) {
        return switch (f.op()) {
            case IS_NULL -> cb.or(cb.isNull(p), cb.equal(p, ""));
            case NOT_NULL -> cb.and(cb.isNotNull(p), cb.notEqual(p, ""));
            case NOT_IN -> cb.or(cb.isNull(p), cb.not(anyContains(cb, p, f.values())));
            // eq is "contains" on purpose: users and the LLM rarely type the exact stored value.
            default -> anyContains(cb, p, f.values());
        };
    }

    // null counts as false: an unset flag is "not breached / not enabled".
    private static Predicate bool(CriteriaBuilder cb, Path<Boolean> p, ResolvedFilter f) {
        return Boolean.TRUE.equals(f.values().get(0)) ? cb.isTrue(p) : cb.or(cb.isNull(p), cb.isFalse(p));
    }

    private static Predicate enumeration(CriteriaBuilder cb, Path<?> p, ResolvedFilter f) {
        return switch (f.op()) {
            case EQ -> cb.equal(p, f.values().get(0));
            case IN -> p.in(f.values());
            case NOT_IN -> cb.or(cb.isNull(p), cb.not(p.in(f.values())));
            default -> throw new IllegalStateException("Op " + f.op() + " không hợp lệ cho enum");
        };
    }

    private static Predicate date(CriteriaBuilder cb, Path<?> path, ResolvedFilter f) {
        if (f.op() == FilterOp.IS_NULL) return cb.isNull(path);
        if (f.op() == FilterOp.NOT_NULL) return cb.isNotNull(path);
        DateRange range = (DateRange) f.values().get(0);
        if (LocalDateTime.class.equals(path.getJavaType())) {
            Path<LocalDateTime> p = cast(path);
            return compare(cb, p, f.op(), range.from().atStartOfDay(), range.toExclusive().atStartOfDay());
        }
        Path<LocalDate> p = cast(path);
        return compare(cb, p, f.op(), range.from(), range.toExclusive());
    }

    private static <Y extends Comparable<? super Y>> Predicate compare(CriteriaBuilder cb, Path<Y> p, FilterOp op,
                                                                      Y from, Y toExclusive) {
        return switch (op) {
            case BEFORE -> cb.lessThan(p, from);
            case AFTER -> cb.greaterThanOrEqualTo(p, from);
            case IN_RANGE -> cb.and(cb.greaterThanOrEqualTo(p, from), cb.lessThan(p, toExclusive));
            default -> throw new IllegalStateException("Op " + op + " không hợp lệ cho ngày");
        };
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @SuppressWarnings("unchecked")
    private static <Y> Path<Y> cast(Path<?> p) {
        return (Path<Y>) p;
    }
}

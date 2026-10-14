package com.example.ticketing.ai.query;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * One filterable field of a domain. {@code path} is the JPA attribute path ("department.name");
 * {@code groups} are named value sets the LLM may use instead of listing constants ("OPEN").
 */
public record FieldDef(
        String name,
        FieldType type,
        String path,
        Class<? extends Enum<?>> enumType,
        String description,
        Set<FilterOp> ops,
        Map<String, List<String>> groups,
        CustomPredicate custom) {

    @FunctionalInterface
    public interface CustomPredicate {
        Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder cb, ResolvedFilter filter);
    }

    public static FieldDef text(String name, String path, String description) {
        return new FieldDef(name, FieldType.TEXT, path, null, description,
                EnumSet.of(FilterOp.EQ, FilterOp.IN, FilterOp.NOT_IN, FilterOp.CONTAINS,
                        FilterOp.IS_NULL, FilterOp.NOT_NULL),
                Map.of(), null);
    }

    public static FieldDef enumeration(String name, String path, Class<? extends Enum<?>> enumType,
                                       String description) {
        return new FieldDef(name, FieldType.ENUM, path, enumType, description,
                EnumSet.of(FilterOp.EQ, FilterOp.IN, FilterOp.NOT_IN), Map.of(), null);
    }

    public static FieldDef date(String name, String path, String description) {
        return new FieldDef(name, FieldType.DATE, path, null, description,
                EnumSet.of(FilterOp.BEFORE, FilterOp.AFTER, FilterOp.IN_RANGE,
                        FilterOp.IS_NULL, FilterOp.NOT_NULL),
                Map.of(), null);
    }

    public static FieldDef bool(String name, String path, String description) {
        return new FieldDef(name, FieldType.BOOLEAN, path, null, description, EnumSet.of(FilterOp.EQ), Map.of(), null);
    }

    /** Boolean computed by a predicate (e.g. "SLA breached" = response OR resolution breached). */
    public static FieldDef boolCustom(String name, String description, CustomPredicate predicate) {
        return new FieldDef(name, FieldType.BOOLEAN, null, null, description, EnumSet.of(FilterOp.EQ), Map.of(), predicate);
    }

    public static FieldDef semantic(String description) {
        return new FieldDef("text", FieldType.SEMANTIC, null, null, description,
                EnumSet.of(FilterOp.SEMANTIC_MATCH), Map.of(), null);
    }

    public static FieldDef custom(String name, String description, Set<FilterOp> ops, CustomPredicate predicate) {
        return new FieldDef(name, FieldType.CUSTOM, null, null, description, EnumSet.copyOf(ops), Map.of(), predicate);
    }

    public FieldDef withGroups(Map<String, List<String>> newGroups) {
        return new FieldDef(name, type, path, enumType, description, ops, new TreeMap<>(newGroups), custom);
    }

    public List<String> enumNames() {
        if (enumType == null) return List.of();
        return Arrays.stream(enumType.getEnumConstants()).map(Enum::name).toList();
    }
}

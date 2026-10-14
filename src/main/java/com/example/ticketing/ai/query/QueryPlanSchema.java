package com.example.ticketing.ai.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON Schema passed as Ollama `format`: the 4B model cannot emit anything outside it. */
final class QueryPlanSchema {

    private QueryPlanSchema() { }

    /** fieldNames: every field of every domain; constraining them stops the model inventing or rambling. */
    static Map<String, Object> build(List<String> domains, List<String> fieldNames) {
        List<String> domainEnum = new ArrayList<>(domains);
        domainEnum.add("none");

        Map<String, Object> filterProps = new LinkedHashMap<>();
        filterProps.put("field", Map.of("type", "string", "enum", fieldNames));
        filterProps.put("op", Map.of("type", "string",
                "enum", Arrays.stream(FilterOp.values()).map(FilterOp::wire).toList()));
        filterProps.put("values", Map.of("type", "array", "items", Map.of("type", "string")));
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("type", "object");
        filter.put("properties", filterProps);
        filter.put("required", List.of("field", "op", "values"));

        Map<String, Object> sortProps = new LinkedHashMap<>();
        sortProps.put("field", Map.of("type", "string", "enum", fieldNames));
        sortProps.put("dir", Map.of("type", "string", "enum", List.of("asc", "desc")));

        // action first: small models decide better when the verb comes before the details.
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("action", Map.of("type", "string",
                "enum", Arrays.stream(QueryAction.values()).map(QueryAction::wire).toList()));
        props.put("domain", Map.of("type", "string", "enum", domainEnum));
        props.put("filters", Map.of("type", "array", "items", filter));
        // dir and limit are required: left optional, the small model silently drops them ("mới nhất" -> oldest page).
        props.put("sort", Map.of("type", "object", "properties", sortProps, "required", List.of("field", "dir")));
        props.put("limit", Map.of("type", "integer"));
        props.put("offset", Map.of("type", "integer"));
        props.put("targetId", Map.of("type", "string"));
        props.put("clarifyQuestion", Map.of("type", "string"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("action", "domain", "filters", "limit"));
        return schema;
    }
}

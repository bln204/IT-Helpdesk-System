package com.example.ticketing.ai.query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DomainCatalog {

    private final Map<String, DomainQueryAdapter> adapters = new LinkedHashMap<>();

    public DomainCatalog(List<DomainQueryAdapter> adapters) {
        for (DomainQueryAdapter a : adapters) {
            this.adapters.put(a.definition().name(), a);
        }
    }

    public Optional<DomainQueryAdapter> adapter(String domain) {
        if (domain == null) return Optional.empty();
        return Optional.ofNullable(adapters.get(domain.trim().toLowerCase(Locale.ROOT)));
    }

    public Optional<DomainDef> domain(String domain) {
        return adapter(domain).map(DomainQueryAdapter::definition);
    }

    public List<String> domainNames() {
        return List.copyOf(adapters.keySet());
    }

    /** Vietnamese labels for user-facing text ("thiết bị", "known error"). */
    public List<String> labels() {
        return adapters.values().stream().map(a -> a.definition().label()).toList();
    }

    /** Distinct field names across all domains, in catalog order. */
    public List<String> fieldNames() {
        return adapters.values().stream().flatMap(a -> a.definition().fields().stream())
                .map(FieldDef::name).distinct().toList();
    }

    /** Compact catalog for the Pass-1 prompt: one header line per domain, one line per field. */
    public String promptText() {
        StringBuilder sb = new StringBuilder();
        for (DomainQueryAdapter a : adapters.values()) {
            DomainDef d = a.definition();
            sb.append(d.name()).append(" — ").append(d.description()).append('\n');
            for (FieldDef f : d.fields()) {
                sb.append("  - ").append(f.name()).append(' ').append(typeText(f))
                        .append(' ').append(f.description()).append('\n');
            }
        }
        return sb.toString();
    }

    private static String typeText(FieldDef f) {
        return switch (f.type()) {
            case ENUM -> "[" + String.join("|", f.enumNames())
                    + (f.groups().isEmpty() ? "" : " | nhóm: " + String.join(", ", f.groups().keySet())) + "]";
            case DATE -> "[date]";
            case SEMANTIC -> "[semantic]";
            case TEXT, CUSTOM -> "[text]";
            case BOOLEAN -> "[true|false]";
        };
    }
}

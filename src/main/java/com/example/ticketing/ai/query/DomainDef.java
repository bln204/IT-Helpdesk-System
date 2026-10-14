package com.example.ticketing.ai.query;

import java.util.List;
import java.util.Optional;

/** {@code label} is the Vietnamese noun used in answers ("thiết bị"). */
public record DomainDef(
        String name,
        String label,
        String description,
        List<FieldDef> fields,
        String defaultSortPath,
        boolean defaultSortDesc) {

    public Optional<FieldDef> field(String fieldName) {
        if (fieldName == null) return Optional.empty();
        String wanted = fieldName.trim();
        return fields.stream().filter(f -> f.name().equalsIgnoreCase(wanted)).findFirst();
    }
}

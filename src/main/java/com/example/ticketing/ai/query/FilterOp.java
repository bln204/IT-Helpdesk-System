package com.example.ticketing.ai.query;

import java.util.Locale;
import java.util.Optional;

public enum FilterOp {
    EQ, IN, NOT_IN, IS_NULL, NOT_NULL, CONTAINS, SEMANTIC_MATCH, BEFORE, AFTER, IN_RANGE;

    public static Optional<FilterOp> parse(String raw) {
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}

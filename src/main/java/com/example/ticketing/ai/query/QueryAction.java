package com.example.ticketing.ai.query;

import java.util.Locale;
import java.util.Optional;

public enum QueryAction {
    LIST, COUNT, DETAIL, EXPLAIN, CREATE_TICKET, TROUBLESHOOT, RESOLVED, ESCALATE, UNSUPPORTED, CLARIFY, CHITCHAT;

    public static Optional<QueryAction> parse(String raw) {
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

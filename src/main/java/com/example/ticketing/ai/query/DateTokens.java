package com.example.ticketing.ai.query;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** The LLM only emits tokens or ISO dates; Java owns calendar arithmetic. */
final class DateTokens {

    private DateTokens() { }

    static DateRange resolve(String raw, LocalDate today) {
        if (raw == null) return null;
        String t = raw.trim().toLowerCase(Locale.ROOT);
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        return switch (t) {
            case "today" -> new DateRange(today, today.plusDays(1));
            case "yesterday" -> new DateRange(today.minusDays(1), today);
            case "this_week" -> new DateRange(monday, monday.plusWeeks(1));
            case "last_week" -> new DateRange(monday.minusWeeks(1), monday);
            case "last_7_days" -> new DateRange(today.minusDays(6), today.plusDays(1));
            case "this_month" -> new DateRange(firstOfMonth, firstOfMonth.plusMonths(1));
            case "last_month" -> new DateRange(firstOfMonth.minusMonths(1), firstOfMonth);
            case "last_30_days" -> new DateRange(today.minusDays(29), today.plusDays(1));
            default -> literal(t);
        };
    }

    private static DateRange literal(String t) {
        try {
            LocalDate d = LocalDate.parse(t);
            return new DateRange(d, d.plusDays(1));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}

package com.example.ticketing.ai.query;

import java.util.List;

/** values: Enum constants for ENUM, DateRange for DATE, String otherwise. */
public record ResolvedFilter(FieldDef field, FilterOp op, List<Object> values) { }

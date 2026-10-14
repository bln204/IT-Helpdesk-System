package com.example.ticketing.ai.query;

import java.util.Map;

/** fields are shown in lists; details (long text) only go to the Pass-2 DATA block. */
public record Row(String id, String title, Map<String, String> fields, String details) { }

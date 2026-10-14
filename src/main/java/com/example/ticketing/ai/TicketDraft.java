package com.example.ticketing.ai;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Structured draft of a ticket the AI wants to create from a
 * conversation. Produced by {@link com.example.ticketing.ai.query.TicketDraftService} (LLM call
 * with a JSON Schema) and confirmed by the user before the
 * actual POST /api/tickets is issued.
 *
 * <p>All fields are nullable: a missing field means the LLM was
 * not able to infer it from the conversation and the user needs
 * to be asked for it via the ASK_MISSING_FIELD action.
 */
public class TicketDraft {

    private String title;
    private String description;
    private String priority;       // LOW, MEDIUM, HIGH, URGENT, CRITICAL
    private String category;       // SOFTWARE, ACCESS, NETWORK, HARDWARE, SECURITY, EMAIL, ACCOUNT, OTHER

    public TicketDraft() {}

    public TicketDraft(String title, String description, String priority, String category) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.category = category;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public static final List<String> REQUIRED_FIELDS = Arrays.asList(
            "title", "description", "priority", "category");

    public static final List<String> VALID_PRIORITIES = Arrays.asList(
            "LOW", "MEDIUM", "HIGH", "URGENT", "CRITICAL");

    public static final List<String> VALID_CATEGORIES = Arrays.asList(
            "SOFTWARE", "ACCESS", "NETWORK", "HARDWARE", "SECURITY",
            "EMAIL", "ACCOUNT", "OTHER");

    /**
     * Return the list of required fields that are null/blank on
     * this draft. The caller is expected to ask the user for the
     * first missing field (one-at-a-time UX).
     */
    public List<String> missingFields() {
        return REQUIRED_FIELDS.stream()
                .filter(name -> {
                    switch (name) {
                        case "title": return isBlank(title);
                        case "description": return isBlank(description);
                        case "priority": return !isValidPriority(priority);
                        case "category": return !isValidCategory(category);
                        default: return true;
                    }
                })
                .collect(java.util.stream.Collectors.toList());
    }

    public boolean isValidPriority(String s) {
        if (s == null) return false;
        return VALID_PRIORITIES.contains(s.toUpperCase(Locale.ROOT));
    }

    public boolean isValidCategory(String s) {
        if (s == null) return false;
        return VALID_CATEGORIES.contains(s.toUpperCase(Locale.ROOT));
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

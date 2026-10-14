package com.example.ticketing.problem;

import org.springframework.stereotype.Component;

/**
 * Phase 6.6.4 - Canonical embedding text builder for {@link KnownError}.
 *
 * <p>Sections 6-7 of the task brief mandate a specific semantic-only text
 * shape. This component is the single source of truth for that shape so
 * <ul>
 *   <li>the embedding service and the reindex path produce identical
 *       text,</li>
 *   <li>tests can pin the produced text without mocking the LLM,</li>
 *   <li>excluded fields (id, errorCode, timestamps, status, managedBy,
 *       teamId, relatedProblemsCount) never leak into the semantic
 *       representation,</li>
 *   <li>null / blank fields are skipped silently - no "null" / "N/A"
 *       / "unknown" placeholders, matching the user's explicit rule.</li>
 * </ul>
 *
 * <p>Ordering of sections matches the task brief. Each section is emitted
 * only when the corresponding field is non-null and non-blank.
 */
@Component
public class KnownErrorEmbeddingTextBuilder {

    private static final String NL = "\n";

    /**
     * Build the canonical semantic text for a KnownError.
     *
     * <p>Fields excluded on purpose (per task brief section 6): id, errorCode,
     * status, relatedProblemsCount, managedBy, teamId, knowledgeArticleId,
     * occurrenceCount, lastOccurrenceAt, validFrom, validUntil, createdAt,
     * updatedAt, createdBy, updatedBy.
     *
     * @param ke the KnownError to build text for; must not be null
     * @return the canonical text. Empty string when no semantic fields are
     *         present, so callers can decide whether to short-circuit.
     */
    public String build(KnownError ke) {
        if (ke == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();

        appendSection(sb, "Known Error", ke.getTitle());
        appendSection(sb, "Description", ke.getDescription());
        appendSection(sb, "Symptoms", ke.getSymptoms());
        appendSection(sb, "Root Cause", ke.getRootCause());
        appendSection(sb, "Root Cause Category", ke.getRootCauseCategory());
        appendSection(sb, "Workaround", ke.getWorkaround());
        appendSection(sb, "Resolution", ke.getResolution());
        appendSection(sb, "Fix Steps", ke.getFixSteps());
        appendSection(sb, "Impact", ke.getImpactDescription());
        appendSection(sb, "Affected Systems", ke.getAffectedSystems());

        // Trim the trailing newline so two consecutive separators never appear.
        return stripTrailingNewline(sb);
    }

    private static void appendSection(StringBuilder sb, String header, String value) {
        if (value == null) return;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return;
        sb.append(header).append(":").append(NL);
        sb.append(trimmed).append(NL).append(NL);
    }

    private static String stripTrailingNewline(StringBuilder sb) {
        int end = sb.length();
        while (end > 0 && (sb.charAt(end - 1) == '\n' || sb.charAt(end - 1) == ' ')) {
            end--;
        }
        return sb.substring(0, end);
    }
}

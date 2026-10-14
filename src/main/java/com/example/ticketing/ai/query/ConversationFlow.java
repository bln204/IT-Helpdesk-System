package com.example.ticketing.ai.query;

import com.example.ticketing.ai.TicketDraft;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Locale;

/** Where the conversation is, stored in the assistant message metadata and sent to the planner as a label. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConversationFlow(Kind kind, String problem, TicketDraft draft, String missingField) {

    public enum Kind { NONE, TROUBLESHOOT, OFFER_TICKET, DRAFTING, CONFIRM }

    public static final ConversationFlow NONE = new ConversationFlow(Kind.NONE, null, null, null);

    public static ConversationFlow troubleshoot(String problem) {
        return new ConversationFlow(Kind.TROUBLESHOOT, problem, null, null);
    }

    public static ConversationFlow offerTicket(String problem) {
        return new ConversationFlow(Kind.OFFER_TICKET, problem, null, null);
    }

    public static ConversationFlow drafting(String problem, TicketDraft draft, String missingField) {
        return new ConversationFlow(Kind.DRAFTING, problem, draft, missingField);
    }

    public static ConversationFlow confirm(String problem, TicketDraft draft) {
        return new ConversationFlow(Kind.CONFIRM, problem, draft, null);
    }

    public String label() {
        return kind == null ? "none" : kind.name().toLowerCase(Locale.ROOT);
    }
}

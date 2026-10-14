package com.example.ticketing.ai.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ticketing.ai.TicketDraft;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ConversationFlowTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void labelsMatchPlannerVocabulary() {
        assertThat(ConversationFlow.NONE.label()).isEqualTo("none");
        assertThat(ConversationFlow.offerTicket("x").label()).isEqualTo("offer_ticket");
        assertThat(ConversationFlow.troubleshoot("x").label()).isEqualTo("troubleshoot");
    }

    @Test
    void roundTripsThroughJsonWithDraft() throws Exception {
        TicketDraft draft = new TicketDraft("Mất mạng", "macOS", "MEDIUM", null);
        ConversationFlow flow = ConversationFlow.drafting("máy không vào mạng", draft, "category");
        String json = mapper.writeValueAsString(flow);
        ConversationFlow back = mapper.readValue(json, ConversationFlow.class);
        assertThat(back.kind()).isEqualTo(ConversationFlow.Kind.DRAFTING);
        assertThat(back.problem()).isEqualTo("máy không vào mạng");
        assertThat(back.missingField()).isEqualTo("category");
        assertThat(back.draft().getTitle()).isEqualTo("Mất mạng");
        assertThat(back.draft().missingFields()).containsExactly("category");
    }
}

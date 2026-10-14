package com.example.ticketing.ai.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ticketing.ai.LlmGenerationException;
import com.example.ticketing.ai.OllamaChatService;
import com.example.ticketing.ai.OllamaChatService.ChatMessage;
import com.example.ticketing.ai.OllamaChatService.ChatOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class QueryPlannerTest {

    private final OllamaChatService ollama = mock(OllamaChatService.class);
    private final DomainCatalog catalog = new DomainCatalog(List.of(new StubAdapter(StubAdapter.ticketLike())));
    private final QueryPlanner planner = new QueryPlanner(ollama, catalog, 30);

    @Test
    void parsesPlanAndSendsSchemaWithDomainsAndZeroTemperature() {
        when(ollama.generate(anyList(), any(ChatOptions.class))).thenReturn(
                "{\"action\":\"count\",\"domain\":\"ticket\",\"filters\":[{\"field\":\"assignee\",\"op\":\"is_null\",\"values\":[]}]}");

        QueryPlan p = planner.plan("có bao nhiêu ticket chưa được phân công", null);

        assertThat(p.action()).isEqualTo("count");
        assertThat(p.filters()).extracting(QueryPlan.PlanFilter::field).containsExactly("assignee");
        ArgumentCaptor<ChatOptions> opts = ArgumentCaptor.forClass(ChatOptions.class);
        verify(ollama).generate(anyList(), opts.capture());
        assertThat(opts.getValue().temperature()).isEqualTo(0.0);
        assertThat(opts.getValue().timeoutSeconds()).isEqualTo(30);
        assertThat(opts.getValue().maxTokens()).isEqualTo(QueryPlanner.MAX_PLAN_TOKENS);
        assertThat(new ObjectMapper().valueToTree(opts.getValue().format()).toString()).contains("\"ticket\"", "\"none\"");
    }

    @Test
    void sendsCatalogExamplesAndPreviousPlan() throws Exception {
        when(ollama.generate(anyList(), any(ChatOptions.class))).thenReturn("{\"action\":\"list\",\"domain\":\"ticket\",\"filters\":[]}");
        QueryPlan previous = new QueryPlan("list", "ticket", List.of(), null, 20, 0, null, null);

        planner.plan("xem tiếp", previous);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> msgs = ArgumentCaptor.forClass(List.class);
        verify(ollama).generate(msgs.capture(), any(ChatOptions.class));
        List<ChatMessage> sent = msgs.getValue();
        assertThat(sent.get(0).role()).isEqualTo("system");
        assertThat(sent.get(0).content()).contains("status [NEW|");
        assertThat(sent).hasSize(1 + 2 * PlannerPrompt.EXAMPLES.size() + 1);
        assertThat(sent.get(sent.size() - 1).content()).contains("\"offset\":0").contains("xem tiếp");
    }

    @Test
    void retriesOnceOnInvalidJsonThenClarifies() {
        when(ollama.generate(anyList(), any(ChatOptions.class))).thenReturn("not json", "still not json");
        QueryPlan p = planner.plan("???", null);
        assertThat(p.action()).isEqualTo("clarify");
        verify(ollama, times(2)).generate(anyList(), any(ChatOptions.class));
    }

    @Test
    void ollamaFailurePropagates() {
        when(ollama.generate(anyList(), any(ChatOptions.class)))
                .thenThrow(LlmGenerationException.ollamaUnavailable(new java.net.ConnectException("down")));
        assertThatThrownBy(() -> planner.plan("x", null)).isInstanceOf(LlmGenerationException.class);
    }

    @Test
    void everyExampleOutputIsAValidPlanForTheRealCatalog() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        DomainCatalog real = new DomainCatalog(List.of(
                new StubAdapter(TicketQueryAdapter.DEF), new StubAdapter(AssetQueryAdapter.DEF),
                new StubAdapter(ChangeQueryAdapter.DEF), new StubAdapter(ProblemQueryAdapter.DEF),
                new StubAdapter(KnownErrorQueryAdapter.DEF), new StubAdapter(DepartmentQueryAdapter.DEF),
                new StubAdapter(TeamQueryAdapter.DEF), new StubAdapter(UserQueryAdapter.DEF),
                new StubAdapter(IncidentQueryAdapter.DEF), new StubAdapter(ServiceRequestQueryAdapter.DEF),
                new StubAdapter(ServiceCatalogQueryAdapter.DEF), new StubAdapter(SlaPolicyQueryAdapter.DEF),
                new StubAdapter(EscalationRuleQueryAdapter.DEF), new StubAdapter(EscalationHistoryQueryAdapter.DEF),
                new StubAdapter(CategoryQueryAdapter.DEF)));
        PlanValidator validator = new PlanValidator(real, java.time.Clock.systemDefaultZone(), 20, 50);
        for (PlannerPrompt.Example ex : PlannerPrompt.EXAMPLES) {
            QueryPlan p = mapper.readValue(ex.output(), QueryPlan.class);
            ValidatedPlan v = validator.validate(p);
            boolean expectClarify = "clarify".equals(p.action());
            assertThat(v.action() == QueryAction.CLARIFY).as(ex.input()).isEqualTo(expectClarify);
        }
    }

    @Test
    void sendsCurrentFlowLabel() {
        when(ollama.generate(anyList(), any(ChatOptions.class))).thenReturn("{\"action\":\"resolved\",\"domain\":\"none\",\"filters\":[]}");

        QueryPlan p = planner.plan("ổn rồi", null, "troubleshoot");

        assertThat(p.action()).isEqualTo("resolved");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> msgs = ArgumentCaptor.forClass(List.class);
        verify(ollama).generate(msgs.capture(), any(ChatOptions.class));
        List<ChatMessage> sent = msgs.getValue();
        assertThat(sent.get(sent.size() - 1).content()).startsWith("Luồng hiện tại: troubleshoot\n");
        assertThat(sent.get(0).content()).contains("resolved:").contains("escalate:").contains("offer_ticket");
    }

    @Test
    void schemaRequiresActionDomainFilters() {
        Map<String, Object> schema = QueryPlanSchema.build(List.of("ticket"), List.of("status", "title"));
        assertThat(schema.get("required")).isEqualTo(List.of("action", "domain", "filters", "limit"));
        @SuppressWarnings("unchecked")
        Map<String, Object> sort = (Map<String, Object>) ((Map<String, Object>) schema.get("properties")).get("sort");
        assertThat(sort.get("required")).isEqualTo(List.of("field", "dir"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void schemaConstrainsFieldNamesToCatalog() {
        Map<String, Object> props = (Map<String, Object>) QueryPlanSchema.build(List.of("ticket"), List.of("status", "title")).get("properties");
        Map<String, Object> filter = (Map<String, Object>) ((Map<String, Object>) props.get("filters")).get("items");
        Map<String, Object> filterField = (Map<String, Object>) ((Map<String, Object>) filter.get("properties")).get("field");
        Map<String, Object> sortField = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) props.get("sort")).get("properties")).get("field");
        assertThat(filterField.get("enum")).isEqualTo(List.of("status", "title"));
        assertThat(sortField.get("enum")).isEqualTo(List.of("status", "title"));
        assertThat(QueryPlanner.MAX_PLAN_TOKENS).isLessThanOrEqualTo(256);
    }
}

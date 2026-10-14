package com.example.ticketing.ai.query;

import static com.example.ticketing.ai.query.QueryTestPlans.filter;
import static com.example.ticketing.ai.query.QueryTestPlans.plan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ticketing.ai.LlmGenerationException;
import com.example.ticketing.ai.OllamaChatService;
import com.example.ticketing.ai.OllamaChatService.ChatOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnswerComposerTest {

    private final OllamaChatService ollama = mock(OllamaChatService.class);
    private final AnswerComposer composer = new AnswerComposer(ollama, new GroundingChecker(), 60);
    private final DomainDef d = StubAdapter.ticketLike();

    private static Row row(String id, String title) {
        return new Row(id, title, Map.of("trạng thái", "NEW"), "mô tả " + title);
    }

    @Test
    void countIsTemplatedWithoutLlm() {
        ValidatedPlan p = plan(d, QueryAction.COUNT, 20, 0, null, filter(d, "assignee", FilterOp.IS_NULL));
        AnswerComposer.Composed c = composer.compose("q", p, QueryResult.count(8, null));
        assertThat(c.text()).isEqualTo("Có 8 ticket (người xử lý trống).");
        assertThat(c.usedLlm()).isFalse();
        verify(ollama, never()).generate(anyList(), any(ChatOptions.class));
    }

    @Test
    void listRendersEveryRowAndMoreHint() {
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i <= 20; i++) rows.add(row("TCK-" + i, "T" + i));
        AnswerComposer.Composed c = composer.compose("q", plan(d, QueryAction.LIST, 20, 0, null),
                QueryResult.of(rows, 23, 0, "Kết quả chỉ gồm ticket của phòng Marketing và ticket do bạn tạo."));
        assertThat(c.text()).startsWith("Có 23 ticket. Đang hiển thị 1–20:")
                .contains("- TCK-1: T1 (trạng thái: NEW)").contains("- TCK-20: T20")
                .contains("Còn 3 ticket nữa. Hỏi \"xem tiếp\" để xem thêm.")
                .endsWith("Kết quả chỉ gồm ticket của phòng Marketing và ticket do bạn tạo.");
    }

    @Test
    void listWithoutMoreHasNoPagingHint() {
        AnswerComposer.Composed c = composer.compose("q", plan(d, QueryAction.LIST, 20, 0, null),
                QueryResult.of(List.of(row("TCK-1", "A")), 1, 0, null));
        assertThat(c.text()).startsWith("Có 1 ticket:").doesNotContain("xem tiếp");
    }

    @Test
    void deniedAndErrorAndEmptyAreDistinct() {
        ValidatedPlan p = plan(d, QueryAction.LIST, 20, 0, null);
        assertThat(composer.compose("q", p, QueryResult.denied("Bạn không có quyền xem team.")).text())
                .isEqualTo("Bạn không có quyền xem team.");
        assertThat(composer.compose("q", p, QueryResult.error("boom")).text())
                .isEqualTo("Không truy vấn được dữ liệu ticket, vui lòng thử lại sau.");
        assertThat(composer.compose("q", p, QueryResult.of(List.of(), 0, 0, null)).text())
                .isEqualTo("Không có ticket nào.");
    }

    @Test
    void pastLastPageSaysTheListEnded() {
        AnswerComposer.Composed c = composer.compose("xem tiếp", plan(d, QueryAction.LIST, 20, 40, null),
                QueryResult.of(List.of(), 25, 40, null));
        assertThat(c.text()).isEqualTo("Đã hết danh sách: có tổng cộng 25 ticket.");
    }

    @Test
    void meIsShownAsBan() {
        ValidatedPlan p = plan(d, QueryAction.COUNT, 20, 0, null, filter(d, "assignee", FilterOp.EQ, "@me"));
        assertThat(composer.compose("q", p, QueryResult.count(2, null)).text()).isEqualTo("Có 2 ticket (người xử lý là bạn).");
    }

    @Test
    void emptyDetailNamesTheId() {
        ValidatedPlan p = plan(d, QueryAction.DETAIL, 20, 0, "TCK-404");
        assertThat(composer.compose("q", p, QueryResult.of(List.of(), 0, 0, null)).text())
                .isEqualTo("Không tìm thấy ticket TCK-404.");
    }

    @Test
    void explainUsesLlmWhenGrounded() {
        when(ollama.generate(anyList(), any(ChatOptions.class))).thenReturn("Ticket [TCK-1] đang chờ xử lý.");
        AnswerComposer.Composed c = composer.compose("TCK-1 sao rồi", plan(d, QueryAction.DETAIL, 20, 0, "TCK-1"),
                QueryResult.of(List.of(row("TCK-1", "A")), 1, 0, null));
        assertThat(c.text()).isEqualTo("Ticket [TCK-1] đang chờ xử lý.");
        assertThat(c.usedLlm()).isTrue();
        assertThat(c.groundingFallback()).isFalse();
    }

    @Test
    void explainFallsBackWhenLlmInventsCode() {
        when(ollama.generate(anyList(), any(ChatOptions.class))).thenReturn("Giống TCK-999.");
        AnswerComposer.Composed c = composer.compose("q", plan(d, QueryAction.EXPLAIN, 20, 0, null),
                QueryResult.of(List.of(row("TCK-1", "A")), 1, 0, null));
        assertThat(c.groundingFallback()).isTrue();
        assertThat(c.text()).contains("- TCK-1: A").doesNotContain("TCK-999");
    }

    @Test
    void explainFallsBackWhenLlmFails() {
        when(ollama.generate(anyList(), any(ChatOptions.class)))
                .thenThrow(LlmGenerationException.timeout(new RuntimeException("slow")));
        AnswerComposer.Composed c = composer.compose("q", plan(d, QueryAction.EXPLAIN, 20, 0, null),
                QueryResult.of(List.of(row("TCK-1", "A")), 1, 0, null));
        assertThat(c.text()).contains("- TCK-1: A");
        assertThat(c.usedLlm()).isFalse();
    }

    @Test
    void dataBlockIsBoundedAndKeepsIds() {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < 50; i++) rows.add(new Row("TCK-" + i, "T", Map.of(), "x".repeat(2000)));
        String data = AnswerComposer.dataBlock(rows, 50);
        assertThat(data.length()).isLessThanOrEqualTo(AnswerComposer.MAX_DATA_CHARS + 200);
        assertThat(data).startsWith("Tổng số: 50").contains("[TCK-0]").contains("(còn ");
    }

    @Test
    void clarifyAndChitchatNeedNoResult() {
        assertThat(composer.compose("q", ValidatedPlan.clarify("Bạn hỏi gì?", null), null).text()).isEqualTo("Bạn hỏi gì?");
        assertThat(composer.compose("q", ValidatedPlan.noData(QueryAction.CHITCHAT, null), null).text())
                .isEqualTo(AnswerComposer.CHITCHAT);
    }

    @Test
    void singleRecordAnswerShowsOnlyThatRecord() {
        AnswerComposer.Composed c = composer.compose("ticket mới nhất", plan(d, QueryAction.LIST, 1, 0, null),
                QueryResult.of(List.of(row("TCK-9", "Wifi lỗi")), 18, 0, null));
        assertThat(c.text()).isEqualTo("- TCK-9: Wifi lỗi (trạng thái: NEW)");
    }

    @Test
    void unsupportedTopicIsStatedPlainly() {
        assertThat(composer.compose("SLA hiện tại là gì", ValidatedPlan.noData(QueryAction.UNSUPPORTED, null), null).text())
                .isEqualTo(AnswerComposer.UNSUPPORTED);
        assertThat(AnswerComposer.UNSUPPORTED).contains("chưa truy cập được").contains("ticket");
    }

    @Test
    void booleanFilterIsDescribedInVietnamese() {
        DomainDef sla = new DomainDef("sla_policy", "chính sách SLA", "x",
                List.of(FieldDef.bool("enabled", "enabled", "đang áp dụng")), "enabled", false);
        ValidatedPlan p = plan(sla, QueryAction.COUNT, 20, 0, null, filter(sla, "enabled", FilterOp.EQ, Boolean.TRUE));
        assertThat(composer.compose("q", p, QueryResult.count(5, null)).text())
                .isEqualTo("Có 5 chính sách SLA (đang áp dụng là có).");
    }

    @Test
    void fixedRepliesListEveryDomain() {
        for (DomainDef d : List.of(TicketQueryAdapter.DEF, AssetQueryAdapter.DEF, ChangeQueryAdapter.DEF,
                ProblemQueryAdapter.DEF, KnownErrorQueryAdapter.DEF, DepartmentQueryAdapter.DEF, TeamQueryAdapter.DEF,
                UserQueryAdapter.DEF, IncidentQueryAdapter.DEF, ServiceRequestQueryAdapter.DEF,
                ServiceCatalogQueryAdapter.DEF, SlaPolicyQueryAdapter.DEF, EscalationRuleQueryAdapter.DEF,
                EscalationHistoryQueryAdapter.DEF, CategoryQueryAdapter.DEF)) {
            assertThat(AnswerComposer.UNSUPPORTED).as(d.name()).contains(d.label());
            assertThat(AnswerComposer.CHITCHAT).as(d.name()).contains(d.label());
        }
    }
}

package com.example.ticketing.ai.query;

import com.example.ticketing.ai.LlmGenerationException;
import com.example.ticketing.ai.OllamaChatService;
import com.example.ticketing.ai.OllamaChatService.ChatMessage;
import com.example.ticketing.ai.OllamaChatService.ChatOptions;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Pass 2. Counts, lists and failures are Java templates; only detail/explain
 * reach the LLM.
 */
@Service
public class AnswerComposer {

    private static final Logger log = LoggerFactory.getLogger(AnswerComposer.class);
    static final int MAX_DATA_CHARS = 12_000; // ~4k tokens for qwen3:4b with num_ctx 12288
    private static final int MAX_DETAIL_CHARS = 600;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Answering from the nearest supported domain would be a confident wrong
    // answer; say what is not reachable.
    public static final String UNSUPPORTED = "Tôi chưa truy cập được loại dữ liệu này. Hiện tôi tra cứu được: ticket, "
            + "thiết bị, change, problem, known error, người dùng, phòng ban, team, sự cố lớn, yêu cầu dịch vụ, "
            + "dịch vụ, chính sách SLA, quy tắc escalation, lịch sử escalation và danh mục ticket.";

    public static final String CHITCHAT = "Xin chào! Tôi có thể giúp bạn tra cứu ticket, thiết bị, change, problem, "
            + "known error, người dùng, phòng ban, team, sự cố lớn, yêu cầu dịch vụ, dịch vụ, chính sách SLA, "
            + "quy tắc escalation, lịch sử escalation và danh mục ticket. Bạn muốn tìm gì?";

    private static final String SYSTEM = """
            Bạn là trợ lý IT Service Desk. Trả lời câu hỏi CHỈ dựa trên dữ liệu trong khối <DATA>.
            QUY TẮC:
            - Không dùng kiến thức bên ngoài. Không suy đoán nguyên nhân, ngày tháng, tên người nếu DATA không có.
            - Nếu DATA không đủ để trả lời, nói: "Hệ thống không ghi nhận thông tin này."
            - Khi nhắc tới một bản ghi, ghi mã của nó trong ngoặc vuông, vd [TCK-2026-0001].
            - Không tự đếm; nếu cần tổng số, dùng dòng "Tổng số" trong DATA.
            - Trả lời bằng tiếng Việt, ngắn gọn.
            """;

    public record Composed(String text, boolean usedLlm, boolean groundingFallback) {
    }

    private final OllamaChatService ollama;
    private final GroundingChecker grounding;
    private final int timeoutSeconds;

    public AnswerComposer(OllamaChatService ollama, GroundingChecker grounding,
            @Value("${ai.composer.timeout-seconds:60}") int timeoutSeconds) {
        this.ollama = ollama;
        this.grounding = grounding;
        this.timeoutSeconds = timeoutSeconds;
    }

    public Composed compose(String question, ValidatedPlan plan, QueryResult result) {
        if (plan.action() == QueryAction.CLARIFY)
            return template(plan.clarifyQuestion());
        if (plan.action() == QueryAction.CHITCHAT)
            return template(CHITCHAT);
        if (plan.action() == QueryAction.UNSUPPORTED)
            return template(UNSUPPORTED);

        String label = plan.domain().label();
        switch (result.status()) {
            case DENIED:
                return template(result.message());
            case ERROR:
                return template("Không truy vấn được dữ liệu " + label + ", vui lòng thử lại sau.");
            case EMPTY:
                String empty = plan.targetId() != null
                        ? "Không tìm thấy " + label + " " + plan.targetId() + describe(plan) + "."
                        : "Không có " + label + " nào" + describe(plan) + ".";
                return template(withScope(empty, result));
            default:
                break;
        }
        return switch (plan.action()) {
            case COUNT -> template(withScope("Có " + result.totalCount() + " " + label + describe(plan) + ".", result));
            case DETAIL, EXPLAIN -> explain(question, plan, result);
            default -> template(listText(plan, result));
        };
    }

    private Composed explain(String question, ValidatedPlan plan, QueryResult result) {
        String data = dataBlock(result.rows(), result.totalCount());
        String answer;
        try {
            answer = ollama.generate(List.of(
                    ChatMessage.system(SYSTEM),
                    ChatMessage.user("<DATA>\n" + data + "</DATA>\n\nCâu hỏi: " + question)),
                    new ChatOptions(null, 0.1, timeoutSeconds));
        } catch (LlmGenerationException e) {
            log.warn("Pass 2 lỗi, dùng câu trả lời tất định: {}", e.getMessage());
            return template(listText(plan, result));
        }
        if (!grounding.isGrounded(answer, data, plan.domain().label(), result.totalCount(), result.rows().size())) {
            return new Composed(listText(plan, result), false, true);
        }
        return new Composed(withScope(answer, result), true, false);
    }

    static String dataBlock(List<Row> rows, long total) {
        StringBuilder sb = new StringBuilder("Tổng số: ").append(total).append('\n');
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            StringBuilder one = new StringBuilder();
            one.append('[').append(r.id()).append("] ").append(r.title()).append('\n');
            r.fields().forEach((k, v) -> one.append("  ").append(k).append(": ").append(v).append('\n'));
            if (r.details() != null && !r.details().isBlank()) {
                String details = r.details().length() > MAX_DETAIL_CHARS
                        ? r.details().substring(0, MAX_DETAIL_CHARS) + "…"
                        : r.details();
                one.append("  chi tiết: ").append(details).append('\n');
            }
            if (sb.length() + one.length() > MAX_DATA_CHARS) {
                sb.append("(còn ").append(rows.size() - i).append(" bản ghi không đưa vào do giới hạn độ dài)\n");
                break;
            }
            sb.append(one);
        }
        return sb.toString();
    }

    private static String rowLine(Row r) {
        String line = "- " + r.id() + ": " + r.title();
        if (r.fields().isEmpty())
            return line;
        return line + " (" + r.fields().entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining(", ")) + ")";
    }

    private static String listText(ValidatedPlan plan, QueryResult result) {
        String label = plan.domain().label();
        List<Row> rows = result.rows();
        if (rows.isEmpty()) {
            return withScope("Đã hết danh sách: có tổng cộng " + result.totalCount() + " " + label + ".", result);
        }
        if (plan.limit() == 1 && plan.offset() == 0) {
            // "… mới nhất / cũ nhất": the user asked for one record, not a page with
            // totals.
            return withScope(rowLine(rows.get(0)), result);
        }
        int from = plan.offset() + 1;
        int to = plan.offset() + rows.size();
        StringBuilder sb = new StringBuilder("Có ").append(result.totalCount()).append(' ').append(label)
                .append(describe(plan));
        if (from == 1 && !result.hasMore())
            sb.append(':');
        else
            sb.append(". Đang hiển thị ").append(from).append('–').append(to).append(':');
        for (Row r : rows) {
            sb.append('\n').append(rowLine(r));
        }
        if (result.hasMore()) {
            sb.append("\n\nCòn ").append(result.totalCount() - to).append(' ').append(label)
                    .append(" nữa. Hỏi \"xem tiếp\" để xem thêm.");
        }
        return withScope(sb.toString(), result);
    }

    private static String describe(ValidatedPlan plan) {
        if (plan.filters().isEmpty())
            return "";
        return " (" + plan.filters().stream().map(AnswerComposer::describe).collect(Collectors.joining("; ")) + ")";
    }

    private static String describe(ResolvedFilter f) {
        String values = f.values().stream().map(AnswerComposer::valueText).collect(Collectors.joining(", "));
        String op = switch (f.op()) {
            case EQ -> "là";
            case IN -> "thuộc";
            case NOT_IN -> "không thuộc";
            case IS_NULL -> "trống";
            case NOT_NULL -> "đã có";
            case CONTAINS -> "chứa";
            case SEMANTIC_MATCH -> "liên quan đến";
            case BEFORE -> "trước";
            case AFTER -> "từ";
            case IN_RANGE -> "trong khoảng";
        };
        String label = f.field().description().split(" \\(")[0];
        return values.isEmpty() ? label + " " + op : label + " " + op + " " + values;
    }

    private static String valueText(Object v) {
        if (v instanceof DateRange r) {
            return r.from().format(DATE) + "–" + r.toExclusive().minusDays(1).format(DATE);
        }
        if (v instanceof Enum<?> e)
            return e.name();
        if (v instanceof Boolean b)
            return b ? "có" : "không";
        if (JpaQueryAdapter.ME.equals(v))
            return "bạn";
        return String.valueOf(v);
    }

    private static String withScope(String text, QueryResult result) {
        return result.scopeNote() == null ? text : text + "\n\n" + result.scopeNote();
    }

    private static Composed template(String text) {
        return new Composed(text, false, false);
    }
}

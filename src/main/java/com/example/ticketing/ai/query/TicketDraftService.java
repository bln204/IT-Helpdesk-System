package com.example.ticketing.ai.query;

import com.example.ticketing.ai.OllamaChatService;
import com.example.ticketing.ai.OllamaChatService.ChatMessage;
import com.example.ticketing.ai.OllamaChatService.ChatOptions;
import com.example.ticketing.ai.TicketDraft;
import com.example.ticketing.ai.session.AiMessage;
import com.example.ticketing.ai.session.AiMessageRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Builds a ticket draft from the conversation; asks for one missing field at a time. Never creates a ticket. */
@Service
public class TicketDraftService {

    public record Result(String text, String action, String payloadJson, ConversationFlow flow) { }

    static final String CONFIRM_TEXT = "Tôi đã soạn ticket từ cuộc trò chuyện. Vui lòng kiểm tra và bấm Tạo ticket để gửi:";

    static final Map<String, String> QUESTIONS = Map.of(
            "title", "Bạn có thể tóm tắt ngắn gọn vấn đề trong một câu không?",
            "description", "Bạn có thể mô tả chi tiết hơn về vấn đề và những gì đã thử không?",
            "priority", "Mức độ khẩn cấp của vấn đề này thế nào? (thấp, trung bình, cao, khẩn cấp, nghiêm trọng)",
            "category", "Vấn đề thuộc loại nào? (phần mềm, quyền truy cập, mạng, phần cứng, bảo mật, email, tài khoản, khác)");

    private static final List<String> PRIORITIES = List.of("LOW", "MEDIUM", "HIGH", "URGENT", "CRITICAL");
    private static final List<String> CATEGORIES =
            List.of("SOFTWARE", "ACCESS", "NETWORK", "HARDWARE", "SECURITY", "EMAIL", "ACCOUNT", "OTHER");

    // Checked in order: more specific phrases first ("rat gap" before "gap", "internet" before "truy cap").
    private static final Map<String, String> PRIORITY_ALIASES = new LinkedHashMap<>();
    private static final Map<String, String> CATEGORY_ALIASES = new LinkedHashMap<>();
    static {
        // Low levels first: "trung bình thôi, không gấp" must not hit "gap".
        PRIORITY_ALIASES.put("thap", "LOW");
        PRIORITY_ALIASES.put("trung binh", "MEDIUM");
        PRIORITY_ALIASES.put("binh thuong", "MEDIUM");
        PRIORITY_ALIASES.put("rat gap", "CRITICAL");
        PRIORITY_ALIASES.put("nghiem trong", "CRITICAL");
        PRIORITY_ALIASES.put("khan cap", "URGENT");
        PRIORITY_ALIASES.put("khan", "URGENT");
        PRIORITY_ALIASES.put("gap", "URGENT");
        PRIORITY_ALIASES.put("cao", "HIGH");
        CATEGORY_ALIASES.put("internet", "NETWORK");
        CATEGORY_ALIASES.put("wifi", "NETWORK");
        CATEGORY_ALIASES.put("mang", "NETWORK");
        CATEGORY_ALIASES.put("phan mem", "SOFTWARE");
        CATEGORY_ALIASES.put("ung dung", "SOFTWARE");
        CATEGORY_ALIASES.put("phan cung", "HARDWARE");
        CATEGORY_ALIASES.put("may in", "HARDWARE");
        CATEGORY_ALIASES.put("thiet bi", "HARDWARE");
        CATEGORY_ALIASES.put("bao mat", "SECURITY");
        CATEGORY_ALIASES.put("virus", "SECURITY");
        CATEGORY_ALIASES.put("outlook", "EMAIL");
        CATEGORY_ALIASES.put("email", "EMAIL");
        CATEGORY_ALIASES.put("mail", "EMAIL");
        CATEGORY_ALIASES.put("tai khoan", "ACCOUNT");
        CATEGORY_ALIASES.put("mat khau", "ACCOUNT");
        CATEGORY_ALIASES.put("quyen", "ACCESS");
        CATEGORY_ALIASES.put("truy cap", "ACCESS");
        CATEGORY_ALIASES.put("khac", "OTHER");
    }

    private static final String SYSTEM = """
            Bạn trích thông tin ticket hỗ trợ IT từ cuộc trò chuyện. Trả về JSON với:
            - title: tóm tắt sự cố trong một câu ngắn (tiếng Việt có dấu)
            - description: mô tả sự cố, môi trường (thiết bị, hệ điều hành), thời điểm xuất hiện và các bước người dùng đã thử
            - priority: LOW | MEDIUM | HIGH | URGENT | CRITICAL (MEDIUM nếu người dùng không nói)
            - category: SOFTWARE | ACCESS | NETWORK | HARDWARE | SECURITY | EMAIL | ACCOUNT | OTHER
            Chỉ dùng thông tin có trong cuộc trò chuyện; trường văn bản nào không có thông tin thì để chuỗi rỗng.
            """;
    private static final int HISTORY = 12;

    private final OllamaChatService ollama;
    private final int timeoutSeconds;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public TicketDraftService(OllamaChatService ollama,
                              @Value("${ai.composer.timeout-seconds:60}") int timeoutSeconds) {
        this.ollama = ollama;
        this.timeoutSeconds = timeoutSeconds;
    }

    /** previous: the draft already on screen (may be null); it fills only what the new extraction left empty. */
    public Result start(List<AiMessage> history, String problem, String question, TicketDraft previous) {
        String raw = ollama.generate(List.of(ChatMessage.system(SYSTEM), ChatMessage.user(transcript(history, problem, question))),
                new ChatOptions(schema(), 0.0, timeoutSeconds));
        TicketDraft draft;
        try {
            draft = mapper.readValue(raw, TicketDraft.class);
        } catch (JsonProcessingException e) {
            draft = new TicketDraft();
        }
        draft.setTitle(blankToNull(draft.getTitle()));
        draft.setDescription(blankToNull(draft.getDescription()));
        draft.setPriority(upper(draft.getPriority()));
        draft.setCategory(upper(draft.getCategory()));
        if (previous != null) {
            if (draft.getTitle() == null) draft.setTitle(previous.getTitle());
            if (draft.getDescription() == null) draft.setDescription(previous.getDescription());
            if (!draft.isValidPriority(draft.getPriority())) draft.setPriority(previous.getPriority());
            if (!draft.isValidCategory(draft.getCategory())) draft.setCategory(previous.getCategory());
        }
        return next(draft, problem);
    }

    public Result answerField(ConversationFlow drafting, String answer) {
        TicketDraft draft = drafting.draft() == null ? new TicketDraft() : drafting.draft();
        String value = answer == null ? "" : answer.trim();
        switch (drafting.missingField() == null ? "" : drafting.missingField()) {
            case "title" -> draft.setTitle(blankToNull(value));
            case "description" -> draft.setDescription(blankToNull(value));
            case "priority" -> draft.setPriority(alias(PRIORITIES, PRIORITY_ALIASES, value));
            case "category" -> draft.setCategory(alias(CATEGORIES, CATEGORY_ALIASES, value));
            default -> { }
        }
        return next(draft, drafting.problem());
    }

    private Result next(TicketDraft draft, String problem) {
        List<String> missing = draft.missingFields();
        if (missing.isEmpty()) {
            return new Result(CONFIRM_TEXT, "CONFIRM_TICKET_DRAFT", payload(draft), ConversationFlow.confirm(problem, draft));
        }
        String field = missing.get(0);
        return new Result(QUESTIONS.get(field), "ASK_MISSING_FIELD", "{\"field\":\"" + field + "\"}",
                ConversationFlow.drafting(problem, draft, field));
    }

    private static String transcript(List<AiMessage> history, String problem, String question) {
        StringBuilder sb = new StringBuilder("Sự cố: ").append(problem == null ? "" : problem).append("\n\n");
        List<AiMessage> dialog = history.stream()
                .filter(m -> m.getRole() == AiMessageRole.USER || m.getRole() == AiMessageRole.ASSISTANT).toList();
        for (AiMessage m : dialog.subList(Math.max(0, dialog.size() - HISTORY), dialog.size())) {
            sb.append(m.getRole() == AiMessageRole.USER ? "Người dùng: " : "Trợ lý: ").append(m.getContent()).append('\n');
        }
        return sb.append("Người dùng: ").append(question).toString();
    }

    private static Map<String, Object> schema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("title", Map.of("type", "string"));
        props.put("description", Map.of("type", "string"));
        props.put("priority", Map.of("type", "string", "enum", PRIORITIES));
        props.put("category", Map.of("type", "string", "enum", CATEGORIES));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("title", "description", "priority", "category"));
        return schema;
    }

    private String payload(TicketDraft d) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("title", d.getTitle());
        p.put("description", d.getDescription());
        p.put("priority", d.getPriority());
        p.put("category", d.getCategory());
        try {
            return mapper.writeValueAsString(p);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Exact enum name wins; otherwise the first Vietnamese alias contained in the answer; else the raw text (re-asked). */
    private static String alias(List<String> names, Map<String, String> aliases, String value) {
        String upper = value.toUpperCase(Locale.ROOT);
        if (names.contains(upper)) return upper;
        String folded = Text.fold(value).toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : aliases.entrySet()) {
            int i = folded.indexOf(e.getKey());
            if (i >= 0 && !negated(folded, i)) return e.getValue();
        }
        return value;
    }

    /** "không gấp", "chẳng cao": the word right before the alias negates it. */
    private static boolean negated(String folded, int index) {
        String before = folded.substring(0, index).trim();
        return before.endsWith("khong") || before.endsWith("chang") || before.endsWith("chua");
    }

    private static String upper(String s) {
        return s == null || s.isBlank() ? null : s.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

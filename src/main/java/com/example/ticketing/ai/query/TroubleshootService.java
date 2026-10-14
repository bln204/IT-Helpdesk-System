package com.example.ticketing.ai.query;

import com.example.ticketing.ai.KnownErrorRagRetrievalService;
import com.example.ticketing.ai.OllamaChatService;
import com.example.ticketing.ai.OllamaChatService.ChatMessage;
import com.example.ticketing.ai.OllamaChatService.ChatOptions;
import com.example.ticketing.ai.RagContextDto;
import com.example.ticketing.ai.session.AiMessage;
import com.example.ticketing.ai.session.AiMessageRole;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Self-help steps: recorded Known Error fixes first, then LLM guidance; never cites a code it was not given. */
@Service
public class TroubleshootService {

    private static final Logger log = LoggerFactory.getLogger(TroubleshootService.class);

    static final String FOOTER = "Nếu làm theo vẫn không được, hãy cho tôi biết.";
    static final String ASK_MORE = "Bạn có thể mô tả chi tiết hơn sự cố (thiết bị, hệ điều hành, thông báo lỗi, "
            + "xuất hiện từ khi nào) để tôi hướng dẫn cụ thể không?";
    private static final int MAX_KNOWN_ERRORS = 3;
    private static final int HISTORY = 6;

    private static final String SYSTEM = """
            Bạn là trợ lý IT Helpdesk giúp người dùng TỰ khắc phục sự cố máy tính.
            QUY TẮC:
            - Nếu có khối <KNOWN_ERRORS>, trình bày trước mục "Cách khắc phục đã ghi nhận" dựa đúng nội dung đó, \
            ghi mã trong ngoặc vuông, vd [KE-001].
            - Sau đó đưa tối đa 6 bước tự kiểm tra/khắc phục ngắn gọn, phù hợp mô tả và hệ điều hành của người dùng.
            - Nếu thiếu thông tin quan trọng (hệ điều hành, thông báo lỗi, xuất hiện từ khi nào), hỏi tối đa 2 câu ở cuối.
            - Không nhắc tới mã ticket, trạng thái, tên người hay dữ liệu hệ thống nào ngoài <KNOWN_ERRORS>.
            - Không đề nghị thao tác có thể mất dữ liệu hoặc giảm an toàn (xóa dữ liệu, tắt phần mềm diệt virus, \
            sửa registry).
            - Kết thúc bằng câu: "Nếu làm theo vẫn không được, hãy cho tôi biết."
            - Trả lời bằng tiếng Việt có dấu.
            """;

    private final OllamaChatService ollama;
    private final KnownErrorRagRetrievalService knownErrors;
    private final GroundingChecker grounding;
    private final int timeoutSeconds;
    private final double minScore;

    public TroubleshootService(OllamaChatService ollama, KnownErrorRagRetrievalService knownErrors,
                               GroundingChecker grounding,
                               @Value("${ai.composer.timeout-seconds:60}") int timeoutSeconds,
                               @Value("${ai.query.semantic-min-score:0.65}") double minScore) {
        this.ollama = ollama;
        this.knownErrors = knownErrors;
        this.grounding = grounding;
        this.timeoutSeconds = timeoutSeconds;
        this.minScore = minScore;
    }

    public String advise(String question, String problem, List<AiMessage> history, String username) {
        String known = knownErrorBlock(problem, username);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(SYSTEM));
        List<AiMessage> dialog = history.stream()
                .filter(m -> m.getRole() == AiMessageRole.USER || m.getRole() == AiMessageRole.ASSISTANT).toList();
        for (AiMessage m : dialog.subList(Math.max(0, dialog.size() - HISTORY), dialog.size())) {
            messages.add(m.getRole() == AiMessageRole.USER
                    ? ChatMessage.user(m.getContent()) : ChatMessage.assistant(m.getContent()));
        }
        messages.add(ChatMessage.user((known.isEmpty() ? "" : "<KNOWN_ERRORS>\n" + known + "</KNOWN_ERRORS>\n\n")
                + "Người dùng: " + question));

        ChatOptions options = new ChatOptions(null, 0.3, timeoutSeconds);
        // Codes already shown earlier in this conversation are not fabrications when repeated.
        String allowed = known + dialog.stream().filter(m -> m.getRole() == AiMessageRole.ASSISTANT)
                .map(AiMessage::getContent).reduce("", (a, b) -> a + "\n" + b);
        String answer = ollama.generate(messages, options);
        if (!grounding.codesGrounded(answer, allowed)) {
            messages.add(ChatMessage.assistant(answer));
            messages.add(ChatMessage.user("Câu trả lời vừa rồi nhắc tới mã không có trong <KNOWN_ERRORS>. "
                    + "Viết lại, không nhắc mã nào ngoài <KNOWN_ERRORS>."));
            answer = ollama.generate(messages, options);
            if (!grounding.codesGrounded(answer, allowed)) return fallback(known);
        }
        String trimmed = answer.strip();
        return trimmed.endsWith(FOOTER) ? trimmed : trimmed + "\n\n" + FOOTER;
    }

    private String knownErrorBlock(String problem, String username) {
        RagContextDto.RetrievalResponse r;
        try {
            r = knownErrors.retrieveContext(new RagContextDto.RetrievalRequest(problem, MAX_KNOWN_ERRORS, minScore), username);
        } catch (RuntimeException e) {
            log.warn("Troubleshoot: không tìm được Known Error, tiếp tục không có dữ liệu: {}", e.getMessage());
            return "";
        }
        if (r == null || r.getSources() == null || !"success".equals(r.getStatus())) return "";
        StringBuilder sb = new StringBuilder();
        for (RagContextDto.RagSource s : r.getSources()) {
            sb.append('[').append(s.getSourceRef()).append("] ").append(s.getTitle()).append('\n');
            if (s.getContent() != null) sb.append(s.getContent().strip()).append('\n');
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String fallback(String known) {
        if (known.isEmpty()) return ASK_MORE;
        return "Hệ thống đã ghi nhận cách khắc phục sau cho sự cố tương tự:\n\n" + known.strip() + "\n\n" + FOOTER;
    }
}

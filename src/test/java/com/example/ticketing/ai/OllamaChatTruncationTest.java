package com.example.ticketing.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ticketing.ai.OllamaChatService.ChatOptions;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OllamaChatTruncationTest {

    @Test
    void truncationHintIsStrippedFromStructuredOutput() {
        String cut = "{\"action\":\"list\"" + OllamaChatService.TRUNCATION_HINT;
        assertThat(OllamaChatService.forOptions(cut, new ChatOptions(Map.of(), 0.0, 30, 256)))
                .isEqualTo("{\"action\":\"list\"");
    }

    @Test
    void truncationHintStaysForProseAndIsAccented() {
        String cut = "Bước 1" + OllamaChatService.TRUNCATION_HINT;
        assertThat(OllamaChatService.forOptions(cut, new ChatOptions(null, 0.3, 60))).isEqualTo(cut);
        assertThat(OllamaChatService.forOptions(cut, null)).isEqualTo(cut);
        assertThat(OllamaChatService.TRUNCATION_HINT).contains("Phần còn lại");
    }
}

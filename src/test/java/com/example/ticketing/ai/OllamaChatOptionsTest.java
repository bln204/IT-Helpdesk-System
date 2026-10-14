package com.example.ticketing.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ticketing.ai.OllamaChatService.ChatMessage;
import com.example.ticketing.ai.OllamaChatService.ChatOptions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OllamaChatOptionsTest {

    private Map<String, Object> body(ChatOptions options) {
        OllamaProperties properties = new OllamaProperties();
        properties.setChatModel("qwen3:4b");
        properties.setChatTemperature(0.2);
        properties.setChatMaxTokens(2048);
        OllamaChatService service = new OllamaChatService(properties);
        return ReflectionTestUtils.invokeMethod(service, "buildRequestBody", List.of(ChatMessage.user("x")), options);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> options(Map<String, Object> body) {
        return (Map<String, Object>) body.get("options");
    }

    @Test
    void perCallMaxTokensOverridesProperty() {
        assertThat(options(body(new ChatOptions(Map.of(), 0.0, 30, 400))).get("num_predict")).isEqualTo(400);
    }

    @Test
    void threeArgOptionsKeepPropertyDefault() {
        assertThat(options(body(new ChatOptions(null, 0.1, 60))).get("num_predict")).isEqualTo(2048);
        assertThat(options(body(null)).get("num_predict")).isEqualTo(2048);
    }
}

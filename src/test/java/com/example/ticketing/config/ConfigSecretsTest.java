package com.example.ticketing.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/** Không còn bí mật hay code debug trong file cấu hình được track (spec auth-foundation §3.1). */
class ConfigSecretsTest {

    private static Properties load(String name) throws Exception {
        Properties p = new Properties();
        try (InputStream in = ConfigSecretsTest.class.getClassLoader().getResourceAsStream(name)) {
            p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }

    @Test
    void koyebPasswordComesOnlyFromEnvironment() throws Exception {
        assertThat(load("application-koyeb.properties").getProperty("spring.datasource.password"))
                .isEqualTo("${SPRING_DATASOURCE_PASSWORD}");
    }

    @Test
    void defaultProfileHasNoHardcodedSecrets() throws Exception {
        Properties p = load("application.properties");
        assertThat(p.getProperty("spring.datasource.password")).isEqualTo("${SPRING_DATASOURCE_PASSWORD:}");
        assertThat(p.getProperty("spring.mail.password")).isEqualTo("${EMAIL_PASSWORD:}");
        assertThat(p.stringPropertyNames()).noneMatch(k -> k.startsWith("debug.logging"));
        assertThat(p.getProperty("logging.level.com.example.ticketing.auth")).isEqualTo("${AUTH_LOG_LEVEL:INFO}");
    }

    @Test
    void notificationServiceHasNoDebugSink() throws Exception {
        String src = Files.readString(Path.of("src/main/java/com/example/ticketing/auth/NotificationService.java"));
        assertThat(src).doesNotContain("debugLog").doesNotContain("java.net.http");
    }
}

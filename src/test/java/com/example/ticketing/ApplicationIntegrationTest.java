package com.example.ticketing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Basic integration test to verify the application context loads correctly.
 */
@SpringBootTest
@ActiveProfiles("test")
public class ApplicationIntegrationTest {
    
    @Test
    @DisplayName("Application context should load successfully")
    void contextLoads() {
        // This test verifies the Spring context loads without errors
    }
}

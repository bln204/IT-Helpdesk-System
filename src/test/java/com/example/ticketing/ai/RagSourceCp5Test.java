package com.example.ticketing.ai;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.example.ticketing.ai.RagContextDto.RagSource;

/**
 * CP-5 Checkpoint 1: focused unit tests for the new {@code id}
 * field on {@link RagSource}.
 *
 * <p>Verifies the legacy 11-arg constructor still leaves id null
 * and that the id can be set via the setter.
 */
class RagSourceCp5Test {

    @Nested
    @DisplayName("RagSource id field")
    class RagSourceIdTests {

        @Test
        @DisplayName("legacy 11-arg constructor leaves id null")
        void legacyConstructorLeavesIdNull() {
            RagSource s = new RagSource("ASSET", "AST-001", "title", "desc",
                    "cat", "sub", "HIGH", "OPEN", null, 0.9, "content");
            assertNull(s.getId());
        }

        @Test
        @DisplayName("default constructor leaves id null")
        void defaultConstructorLeavesIdNull() {
            RagSource s = new RagSource();
            assertNull(s.getId());
        }

        @Test
        @DisplayName("id can be set via setter and retrieved via getter")
        void idCanBeSetAndRetrieved() {
            RagSource s = new RagSource();
            s.setId(123L);
            assertEquals(123L, s.getId());
        }

        @Test
        @DisplayName("builder populates id through the 12-arg constructor")
        void builderPopulatesId() {
            RagSource s = RagSource.builder()
                    .sourceType("ASSET")
                    .sourceRef("AST-001")
                    .id(456L)
                    .build();
            assertEquals(456L, s.getId());
        }
    }
}

package com.example.ticketing.ai.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GroundingCheckerTest {

    private final GroundingChecker checker = new GroundingChecker();
    private final String data = "Tổng số: 2\n[TCK-2026-0001] Máy in lỗi\n[AST-68578092-59] Laptop\n";

    @Test
    void acceptsCodesPresentInData() {
        assertThat(checker.isGrounded("Ticket [TCK-2026-0001] liên quan [AST-68578092-59].", data, "ticket", 2, 2)).isTrue();
    }

    @Test
    void rejectsInventedCode() {
        assertThat(checker.isGrounded("Xem thêm TCK-2026-0099.", data, "ticket", 2, 2)).isFalse();
    }

    @Test
    void rejectsWrongCount() {
        assertThat(checker.isGrounded("Có 9 ticket đang mở.", data, "ticket", 2, 2)).isFalse();
        assertThat(checker.isGrounded("Có 2 ticket đang mở.", data, "ticket", 2, 2)).isTrue();
    }

    @Test
    void ignoresNumbersNotFollowedByLabel() {
        assertThat(checker.isGrounded("Lỗi xảy ra 3 lần.", data, "ticket", 2, 2)).isTrue();
    }

    @Test
    void codesGroundedChecksOnlyCodes() {
        assertThat(checker.codesGrounded("Theo [KE-001], hãy tắt IPv6.", "[KE-001] Mất mạng sau restart")).isTrue();
        assertThat(checker.codesGrounded("Xem TCK-2026-0099.", "[KE-001] Mất mạng")).isFalse();
        assertThat(checker.codesGrounded("Khởi động lại 3 lần.", "")).isTrue();
    }

    @Test
    void ordinaryHyphenatedWordsAreNotCodes() {
        assertThat(checker.codesGrounded("Bật Wi-Fi, cắm cáp USB-C rồi thử lại.", "")).isTrue();
        assertThat(checker.codesGrounded("Xem AST-68578092-59.", "")).isFalse();
    }

    @Test
    void technicalTokensAreNotRecordCodes() {
        assertThat(checker.codesGrounded("Dùng cáp RJ-45, bật WPA2-PSK, cổng USB-3.0, băm SHA-256, RAM DDR4-3200.", "")).isTrue();
        assertThat(checker.codesGrounded("Xem INC-0042 và SR-17.", "")).isFalse();
    }
}

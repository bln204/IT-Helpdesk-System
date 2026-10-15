package com.example.ticketing.authorization;

import static com.example.ticketing.auth.UserRole.Role.ADMIN;
import static com.example.ticketing.auth.UserRole.Role.GIAM_DOC;
import static com.example.ticketing.auth.UserRole.Role.NHAN_VIEN;
import static com.example.ticketing.auth.UserRole.Role.TRUONG_PHONG;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProblemAuthorizationTest {

    private final ProblemAuthorization authz = new ProblemAuthorization();
    private final ActorContext admin = ActorContext.of("a", ADMIN, null, false);
    private final ActorContext gd = ActorContext.of("g", GIAM_DOC, "EXEC", false);
    private final ActorContext tpIt = ActorContext.of("t", TRUONG_PHONG, "IT", true);
    private final ActorContext nvIt = ActorContext.of("n", NHAN_VIEN, "IT", true);
    private final ActorContext tpBiz = ActorContext.of("b", TRUONG_PHONG, "MKT", false);

    @Test
    void problemWrites() {
        assertThat(authz.canWriteProblem(nvIt)).isTrue();
        assertThat(authz.canWriteProblem(tpBiz)).isFalse();
        assertThat(authz.canWriteProblem(admin)).isFalse();
        assertThat(authz.canDeleteProblem(admin)).isTrue();
        assertThat(authz.canDeleteProblem(tpIt)).isFalse();
    }

    @Test
    void knownErrorWritesOnlyItLead() {
        assertThat(authz.canWriteKnownError(tpIt)).isTrue();
        assertThat(authz.canWriteKnownError(nvIt)).isFalse();
        assertThat(authz.canWriteKnownError(gd)).isFalse();
        assertThat(authz.canWriteKnownError(admin)).isFalse();
        assertThat(authz.canWriteKnownError(tpBiz)).isFalse();
    }

    @Test
    void internalNotes() {
        assertThat(authz.canReadNotes(gd)).isTrue();
        assertThat(authz.canReadNotes(nvIt)).isTrue();
        assertThat(authz.canReadNotes(tpBiz)).isFalse();
        assertThat(authz.canWriteNotes(nvIt)).isTrue();
        assertThat(authz.canWriteNotes(gd)).isFalse();
    }
}

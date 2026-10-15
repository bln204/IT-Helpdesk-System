package com.example.ticketing.authorization;

import static com.example.ticketing.auth.UserRole.Role.ADMIN;
import static com.example.ticketing.auth.UserRole.Role.GIAM_DOC;
import static com.example.ticketing.auth.UserRole.Role.NHAN_VIEN;
import static com.example.ticketing.auth.UserRole.Role.TRUONG_PHONG;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.ticketing.change.ChangeRequest;

class ChangeAuthorizationTest {

    private final ChangeAuthorization authz = new ChangeAuthorization();

    private static ChangeRequest change(String requester) {
        ChangeRequest c = new ChangeRequest();
        c.setRequesterUsername(requester);
        return c;
    }

    @Test
    void readMatrix() {
        assertThat(authz.canRead(ActorContext.of("g", GIAM_DOC, "EXEC", false), change("x"))).isTrue();
        assertThat(authz.canRead(ActorContext.of("n", NHAN_VIEN, "IT", true), change("x"))).isTrue();
        assertThat(authz.canRead(ActorContext.of("alice", NHAN_VIEN, "MKT", false), change("ALICE"))).isTrue();
        assertThat(authz.canRead(ActorContext.of("alice", TRUONG_PHONG, "MKT", false), change("bob"))).isFalse();
    }

    @Test
    void actionMatrix() {
        ActorContext admin = ActorContext.of("a", ADMIN, null, false);
        ActorContext gd = ActorContext.of("g", GIAM_DOC, "EXEC", false);
        ActorContext tpIt = ActorContext.of("t", TRUONG_PHONG, "IT", true);
        ActorContext nvIt = ActorContext.of("n", NHAN_VIEN, "IT", true);
        ActorContext nv = ActorContext.of("u", NHAN_VIEN, "MKT", false);
        assertThat(authz.canWrite(nvIt)).isTrue();
        assertThat(authz.canWrite(nv)).isFalse();
        assertThat(authz.canWrite(admin)).isFalse();
        assertThat(authz.canApprove(tpIt)).isTrue();
        assertThat(authz.canApprove(nvIt)).isFalse();
        assertThat(authz.canApprove(gd)).isFalse();
        assertThat(authz.canViewCab(gd)).isTrue();
        assertThat(authz.canViewCab(nv)).isFalse();
    }
}

package com.example.ticketing.authorization;

import static com.example.ticketing.auth.UserRole.Role.ADMIN;
import static com.example.ticketing.auth.UserRole.Role.GIAM_DOC;
import static com.example.ticketing.auth.UserRole.Role.NHAN_VIEN;
import static com.example.ticketing.auth.UserRole.Role.TRUONG_PHONG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;

import com.example.ticketing.auth.UserRole;

class ActorTiersTest {

    @ParameterizedTest(name = "{0} it={1}")
    @CsvSource({
        // role,        it,    oversight, itOp,  itLead, seesAll, bizLead
        "ADMIN,         false, true,      false, false,  true,    false",
        "GIAM_DOC,      false, true,      false, false,  true,    false",
        "TRUONG_PHONG,  true,  false,     true,  true,   true,    false",
        "NHAN_VIEN,     true,  false,     true,  false,  true,    false",
        "TRUONG_PHONG,  false, false,     false, false,  false,   true",
        "NHAN_VIEN,     false, false,     false, false,  false,   false",
    })
    void tiers(UserRole.Role role, boolean it, boolean oversight, boolean itOp, boolean itLead,
               boolean seesAll, boolean bizLead) {
        ActorContext a = ActorContext.of("u", role, it ? "IT" : "MKT", it);
        assertThat(ActorTiers.isOversight(a)).isEqualTo(oversight);
        assertThat(ActorTiers.isItOperator(a)).isEqualTo(itOp);
        assertThat(ActorTiers.isItLead(a)).isEqualTo(itLead);
        assertThat(ActorTiers.seesAll(a)).isEqualTo(seesAll);
        assertThat(ActorTiers.isBusinessLead(a)).isEqualTo(bizLead);
    }

    @Test
    void sameUserIgnoresCaseAndNull() {
        ActorContext a = ActorContext.of("alice", NHAN_VIEN, "MKT", false);
        assertThat(ActorTiers.sameUser(a, "ALICE")).isTrue();
        assertThat(ActorTiers.sameUser(a, null)).isFalse();
    }

    @Test
    void requireThrows403() {
        assertThatThrownBy(() -> ActorTiers.require(false, "Không được"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        ActorTiers.require(true, "ok");
        assertThat(ActorTiers.isAdmin(ActorContext.of("r", ADMIN, null, false))).isTrue();
        assertThat(ActorTiers.isAdmin(ActorContext.of("g", GIAM_DOC, null, false))).isFalse();
        assertThat(ActorTiers.isItLead(ActorContext.of("t", TRUONG_PHONG, "IT", true))).isTrue();
    }
}

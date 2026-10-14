package com.example.ticketing.authorization;

import static com.example.ticketing.auth.UserRole.Role.ADMIN;
import static com.example.ticketing.auth.UserRole.Role.NHAN_VIEN;
import static com.example.ticketing.auth.UserRole.Role.TRUONG_PHONG;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReportingAuthorizationTest {

    private final ReportingAuthorization authz = new ReportingAuthorization();

    @Test
    void matrix() {
        ActorContext admin = ActorContext.of("a", ADMIN, null, false);
        ActorContext nvIt = ActorContext.of("n", NHAN_VIEN, "IT", true);
        ActorContext tpBiz = ActorContext.of("t", TRUONG_PHONG, "MKT", false);
        ActorContext nvBiz = ActorContext.of("u", NHAN_VIEN, "MKT", false);
        assertThat(authz.canSeeCompanyStats(admin)).isTrue();
        assertThat(authz.canSeeCompanyStats(nvIt)).isTrue();
        assertThat(authz.canSeeCompanyStats(tpBiz)).isFalse();
        assertThat(authz.canSeeDepartmentStats(tpBiz)).isTrue();
        assertThat(authz.canSeeDepartmentStats(nvBiz)).isFalse();
        assertThat(authz.ticketStatsScope(nvBiz)).isNotNull();
    }
}

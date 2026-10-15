package com.example.ticketing.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.auth.UserAccount;

/** GET /api/tickets/reports/dashboard theo phạm vi actor (spec phase1-rbac-hardening §4.6). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketDashboardSummaryScopeTest {

    @Autowired private TicketController controller;
    @Autowired private TicketAuthorizationFixtures fixtures;
    @Autowired private TicketRepository tickets;

    private static UsernamePasswordAuthenticationToken auth(UserAccount u) {
        return new UsernamePasswordAuthenticationToken(u.getUsername(), "n/a", u.getAuthorities());
    }

    private void seed() {
        tickets.save(fixtures.mktRequestedTicket());
        tickets.save(fixtures.newTicket("lead own", TicketTypes.TicketPriority.LOW, fixtures.truongPhongMkt()));
        tickets.save(fixtures.itRequestedTicket());
    }

    @Test
    void businessLeadSeesOnlyDepartmentTickets() {
        seed();
        assertThat(controller.dashboardSummary(auth(fixtures.truongPhongMkt()), null, null).getOpenCount())
                .isEqualTo(2);
    }

    @Test
    void itStaffSeesWholeCompany() {
        seed();
        assertThat(controller.dashboardSummary(auth(fixtures.nhanVienIT()), null, null).getOpenCount())
                .isEqualTo(3);
    }

    @Test
    void businessStaffSeesOnlyOwnTickets() {
        seed();
        assertThat(controller.dashboardSummary(auth(fixtures.nhanVienMkt()), null, null).getOpenCount())
                .isEqualTo(1);
    }
}

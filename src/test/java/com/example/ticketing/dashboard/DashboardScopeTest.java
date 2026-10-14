package com.example.ticketing.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.ticket.TicketAuthorizationFixtures;
import com.example.ticketing.ticket.TicketRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DashboardScopeTest {

    @Autowired private DashboardController controller;
    @Autowired private TicketAuthorizationFixtures fixtures;
    @Autowired private TicketRepository tickets;

    private static UsernamePasswordAuthenticationToken auth(UserAccount u) {
        return new UsernamePasswordAuthenticationToken(u.getUsername(), "n/a", u.getAuthorities());
    }

    @Test
    void businessStaffOverviewCountsOnlyOwnTickets() {
        UserAccount me = fixtures.nhanVienMkt();
        tickets.save(fixtures.mktRequestedTicket());
        tickets.save(fixtures.newTicket("other 1", com.example.ticketing.ticket.TicketTypes.TicketPriority.LOW, fixtures.truongPhongMkt()));
        tickets.save(fixtures.itRequestedTicket());
        ResponseEntity<DashboardController.DashboardOverview> res = controller.getOverview(auth(me));
        assertThat(res.getBody().getOpenTickets()).isEqualTo(1);
    }

    @Test
    void businessLeadOverviewCountsDepartmentTickets() {
        UserAccount lead = fixtures.truongPhongMkt();
        tickets.save(fixtures.mktRequestedTicket());
        tickets.save(fixtures.newTicket("lead own", com.example.ticketing.ticket.TicketTypes.TicketPriority.LOW, lead));
        tickets.save(fixtures.itRequestedTicket());
        ResponseEntity<DashboardController.DashboardOverview> res = controller.getOverview(auth(lead));
        assertThat(res.getBody().getOpenTickets()).isEqualTo(2);
    }

    @Test
    void businessStaffCannotSeeItWorkload() {
        UserAccount me = fixtures.nhanVienMkt();
        assertThatThrownBy(() -> controller.getITWorkload(auth(me)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }
}

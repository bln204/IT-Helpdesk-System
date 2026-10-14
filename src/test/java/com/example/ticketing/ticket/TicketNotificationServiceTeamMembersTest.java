package com.example.ticketing.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.ticketing.auth.Notification;
import com.example.ticketing.auth.NotificationRepository;
import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.department.ItDepartmentResolver;
import com.example.ticketing.team.Team;
import com.example.ticketing.team.TeamMember;
import com.example.ticketing.team.TeamMemberRepository;

class TicketNotificationServiceTeamMembersTest {

    @Test
    void assignmentToTeamNotifiesEnabledMembersExceptAssignee() {
        NotificationRepository notifications = mock(NotificationRepository.class);
        TeamMemberRepository members = mock(TeamMemberRepository.class);
        TicketNotificationService service = new TicketNotificationService(
            notifications, mock(UserAccountRepository.class), mock(ItDepartmentResolver.class), members);

        Team team = mock(Team.class);
        when(team.getId()).thenReturn(7L);
        UserAccount assignee = user("assignee", true);
        when(members.findByTeamIdAndEnabledTrue(7L)).thenReturn(List.of(
            member(assignee), member(user("active", true)), member(user("disabled", false))));

        Ticket ticket = new Ticket();
        ticket.setTicketNumber("T-1");
        ticket.setTitle("Printer down");
        ticket.setTeam(team);
        ticket.setAssignee(assignee);

        service.notifyTicketAssigned(ticket, "lead");

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, atLeastOnce()).save(saved.capture());
        // personal assignment notice to the assignee + one team notice to the active member
        assertThat(saved.getAllValues()).extracting(Notification::getRecipientUsername)
            .containsExactly("assignee", "active");
    }

    private static UserAccount user(String username, boolean enabled) {
        UserAccount u = new UserAccount();
        u.setUsername(username);
        u.setEnabled(enabled);
        return u;
    }

    private static TeamMember member(UserAccount user) {
        TeamMember m = new TeamMember();
        m.setUser(user);
        return m;
    }
}

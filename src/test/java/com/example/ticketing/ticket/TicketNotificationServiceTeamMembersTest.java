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

    @Test
    void assignmentNotifiesTheRequester() {
        NotificationRepository notifications = mock(NotificationRepository.class);
        TicketNotificationService service = new TicketNotificationService(notifications,
            mock(UserAccountRepository.class), mock(ItDepartmentResolver.class), mock(TeamMemberRepository.class));
        Ticket ticket = new Ticket();
        ticket.setTicketNumber("T-2");
        ticket.setTitle("VPN");
        ticket.setRequesterUsername("owner");
        ticket.setAssignee(user("tech", true));

        service.notifyTicketAssigned(ticket, "lead");

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Notification::getRecipientUsername)
            .containsExactly("tech", "owner");
        assertThat(saved.getAllValues().get(1).getMessage()).contains("T-2").contains("tech");
    }

    @Test
    void requesterIsNotNotifiedTwiceOrAboutOwnAction() {
        NotificationRepository notifications = mock(NotificationRepository.class);
        TicketNotificationService service = new TicketNotificationService(notifications,
            mock(UserAccountRepository.class), mock(ItDepartmentResolver.class), mock(TeamMemberRepository.class));
        Ticket selfReceived = new Ticket();
        selfReceived.setTicketNumber("T-3");
        selfReceived.setRequesterUsername("tech");
        selfReceived.setAssignee(user("tech", true));
        Ticket assignedByOwner = new Ticket();
        assignedByOwner.setTicketNumber("T-4");
        assignedByOwner.setRequesterUsername("lead");
        assignedByOwner.setAssignee(user("tech", true));

        service.notifyTicketAssigned(selfReceived, "tech");
        service.notifyTicketAssigned(assignedByOwner, "lead");

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Notification::getRecipientUsername)
            .containsExactly("tech", "tech");
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

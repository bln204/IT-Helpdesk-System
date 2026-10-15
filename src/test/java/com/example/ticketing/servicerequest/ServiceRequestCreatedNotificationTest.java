package com.example.ticketing.servicerequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.ticketing.auth.Notification;
import com.example.ticketing.auth.NotificationRepository;
import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.auth.UserRole;
import com.example.ticketing.department.Department;
import com.example.ticketing.department.ItDepartmentResolver;

class ServiceRequestCreatedNotificationTest {

    @Test
    void newRequestNotifiesAdminsAndItOnlyNotRequesterOrBusinessUsers() {
        ServiceRequestRepository srRepo = mock(ServiceRequestRepository.class);
        NotificationRepository notifRepo = mock(NotificationRepository.class);
        UserAccountRepository userRepo = mock(UserAccountRepository.class);
        ItDepartmentResolver resolver = mock(ItDepartmentResolver.class);

        Department it = new Department();
        it.setCode("IT");
        Department finance = new Department();
        finance.setCode("FIN");
        when(resolver.isITDepartment(it)).thenReturn(true);

        when(userRepo.findAll()).thenReturn(List.of(
                user("admin", UserRole.Role.ADMIN, null, true),
                user("tp.it", UserRole.Role.TRUONG_PHONG, it, true),
                user("tech.smith", UserRole.Role.NHAN_VIEN, it, true),
                user("tech.off", UserRole.Role.NHAN_VIEN, it, false),
                user("tp.fin", UserRole.Role.TRUONG_PHONG, finance, true),
                user("carlos", UserRole.Role.NHAN_VIEN, finance, true)));
        when(srRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ServiceRequestService service = new ServiceRequestService(srRepo,
                mock(ServiceRequestCommentRepository.class), mock(ServiceCatalogRepository.class),
                notifRepo, userRepo, resolver);
        ServiceRequest sr = new ServiceRequest();
        sr.setTitle("Tạo tài khoản mới");
        service.createRequest(sr, "carlos", "Carlos Nguyen", "c@x", "FIN");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifRepo, times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notification::getRecipientUsername)
                .containsExactlyInAnyOrder("admin", "tp.it", "tech.smith");
        assertThat(captor.getAllValues()).allMatch(
                n -> n.getType() == Notification.NotificationType.SERVICE_REQUEST_CREATED);
    }

    @Test
    void approvalOnlyFromPendingAndRequesterIsNotified() {
        ServiceRequestRepository srRepo = mock(ServiceRequestRepository.class);
        NotificationRepository notifRepo = mock(NotificationRepository.class);
        ServiceRequestService service = new ServiceRequestService(srRepo,
                mock(ServiceRequestCommentRepository.class), mock(ServiceCatalogRepository.class),
                notifRepo, mock(UserAccountRepository.class), mock(ItDepartmentResolver.class));
        ServiceRequest sr = new ServiceRequest();
        sr.setTitle("Tạo tài khoản mới");
        sr.setRequesterUsername("carlos");
        sr.setStatus(ServiceRequest.ServiceStatus.SUBMITTED);
        when(srRepo.findById(1L)).thenReturn(java.util.Optional.of(sr));
        when(srRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // SUBMITTED cannot be approved (and cannot skip to IN_PROGRESS via take-ownership from PENDING_APPROVAL).
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.approveRequest(1L, "tp.it", null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        sr.setStatus(ServiceRequest.ServiceStatus.PENDING_APPROVAL);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.takeOwnership(1L, "tech.smith"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        service.rejectRequest(1L, "tp.it", "Thiếu phê duyệt của trưởng phòng");
        assertThat(sr.getStatus()).isEqualTo(ServiceRequest.ServiceStatus.REJECTED);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifRepo).save(captor.capture());
        assertThat(captor.getValue().getRecipientUsername()).isEqualTo("carlos");
        assertThat(captor.getValue().getType()).isEqualTo(Notification.NotificationType.SERVICE_REQUEST_UPDATED);
    }

    private static UserAccount user(String username, UserRole.Role role, Department dept, boolean enabled) {
        UserAccount u = new UserAccount();
        u.setUsername(username);
        u.setRole(role);
        u.setDepartment(dept);
        u.setEnabled(enabled);
        return u;
    }
}

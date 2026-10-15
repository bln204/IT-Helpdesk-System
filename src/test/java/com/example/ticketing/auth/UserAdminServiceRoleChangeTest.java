package com.example.ticketing.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserAdminServiceRoleChangeTest {

    @Mock private UserAccountRepository userAccountRepository;
    @Mock private UserAuditService userAuditService;
    @InjectMocks private UserAdminService service;

    private UserAccount user(long id, String username, UserRole.Role role) {
        UserAccount u = new UserAccount();
        u.setUsername(username);
        u.setRole(role);
        u.setApproved(true);
        when(userAccountRepository.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    @BeforeEach
    void save() {
        when(userAccountRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void giamDocCannotChangeRoles() {
        user(2, "bob", UserRole.Role.NHAN_VIEN);
        assertThatThrownBy(() -> service.updateRole(2L, UserRole.Role.TRUONG_PHONG, "gd", "GIAM_DOC"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }

    @Test
    void adminCannotChangeOwnRole() {
        user(1, "root", UserRole.Role.GIAM_DOC);
        assertThatThrownBy(() -> service.updateRole(1L, UserRole.Role.NHAN_VIEN, "root", "ADMIN"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }

    @Test
    void nobodyCanGrantAdmin() {
        user(2, "bob", UserRole.Role.GIAM_DOC);
        assertThatThrownBy(() -> service.updateRole(2L, UserRole.Role.ADMIN, "root", "ADMIN"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }

    @Test
    void auditRecordsOldAndNewRole() {
        user(2, "bob", UserRole.Role.NHAN_VIEN);
        service.updateRole(2L, UserRole.Role.TRUONG_PHONG, "root", "ADMIN");
        verify(userAuditService).log(UserAuditAction.ROLE_CHANGED, "root", "ADMIN", "bob", "NHAN_VIEN", "TRUONG_PHONG");
    }
}

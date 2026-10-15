package com.example.ticketing.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
class UserAdminServiceMatrixTest {

    @Mock private UserAccountRepository userAccountRepository;
    @Mock private UserAuditService userAuditService;
    @InjectMocks private UserAdminService service;

    @BeforeEach
    void target() {
        UserAccount bob = new UserAccount();
        bob.setUsername("bob");
        bob.setRole(UserRole.Role.NHAN_VIEN);
        bob.setApproved(true);
        when(userAccountRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(userAccountRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void giamDocCannotEnableOrDisable() {
        assertThatThrownBy(() -> service.updateEnabled(2L, false, "gd", "GIAM_DOC", null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }

    @Test
    void giamDocCannotEditOthersProfile() {
        assertThatThrownBy(() -> service.updateProfile(2L, "Bob", null, null, "bob@x.vn", "gd", "GIAM_DOC", null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }

    @Test
    void giamDocCannotDelete() {
        assertThatThrownBy(() -> service.deleteUser(2L, "gd", "GIAM_DOC", null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }

    @Test
    void truongPhongCanOnlyCreateNhanVien() {
        assertThatThrownBy(() -> service.createUser("newgd", "Password1!", UserRole.Role.GIAM_DOC, true,
                "X", null, null, "x@x.vn", 5L, "tp", "TRUONG_PHONG"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }
}

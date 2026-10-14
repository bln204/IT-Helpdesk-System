package com.example.ticketing.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PendingApprovalRejectedTest {

    @Autowired private UserAccountRepository repo;
    @Autowired private UserAdminService service;

    private UserAccount pending(String username) {
        UserAccount u = new UserAccount();
        u.setUsername(username);
        u.setPasswordHash("x");
        u.setRole(UserRole.Role.NHAN_VIEN);
        u.setApproved(false);
        u.setEnabled(false);
        return repo.save(u);
    }

    @Test
    void rejectedAccountLeavesPendingQueries() {
        UserAccount waiting = pending("p4_waiting");
        UserAccount rejected = pending("p4_rejected");
        rejected.setRejectionReason("Sai thông tin");
        repo.save(rejected);

        assertThat(repo.findPendingApproval()).extracting(UserAccount::getUsername)
                .contains("p4_waiting").doesNotContain("p4_rejected");
        assertThat(repo.findByApprovedFalse(PageRequest.of(0, 100)).getContent())
                .extracting(UserAccount::getUsername).doesNotContain("p4_rejected");
        assertThat(waiting.getId()).isNotNull();
    }

    @Test
    void cannotRejectTwice() {
        UserAccount u = pending("p4_twice");
        service.rejectUser(u.getId(), "Lý do 1", "admin", "ADMIN");
        assertThatThrownBy(() -> service.rejectUser(u.getId(), "Lý do 2", "admin", "ADMIN"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
    }
}

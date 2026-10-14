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

import com.example.ticketing.ticket.TicketAuthorizationFixtures;

/** TRUONG_PHONG chỉ xem user/audit cùng phòng (spec phase1-rbac-hardening §4.8). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TruongPhongUserScopeTest {

    @Autowired private UserAdminService service;
    @Autowired private UserAuditService audits;
    @Autowired private TicketAuthorizationFixtures fixtures;

    @Test
    void truongPhongListsOnlyOwnDepartment() {
        UserAccount lead = fixtures.truongPhongMkt();
        fixtures.nhanVienMkt();
        fixtures.nhanVienIT();
        assertThat(service.listUsers(PageRequest.of(0, 100), null, "TRUONG_PHONG", lead.getDepartmentId()).getContent())
                .extracting(UserAccount::getUsername).contains("nv_mkt").doesNotContain("nv_it");
    }

    @Test
    void truongPhongAuditOnlyOwnDepartment() {
        UserAccount lead = fixtures.truongPhongMkt();
        fixtures.nhanVienMkt();
        fixtures.nhanVienIT();
        audits.log(UserAuditAction.PROFILE_UPDATED, "admin", "ADMIN", "nv_mkt", null, null);
        audits.log(UserAuditAction.PROFILE_UPDATED, "admin", "ADMIN", "nv_it", null, null);
        assertThat(service.listAudit("TRUONG_PHONG", lead.getDepartmentId()))
                .extracting(UserAudit::getTargetUsername).contains("nv_mkt").doesNotContain("nv_it");
        assertThatThrownBy(() -> service.listAudit("nv_it", "TRUONG_PHONG", lead.getDepartmentId()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }
}

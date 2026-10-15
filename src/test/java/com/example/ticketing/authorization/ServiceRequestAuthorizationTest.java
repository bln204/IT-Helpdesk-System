package com.example.ticketing.authorization;

import static com.example.ticketing.auth.UserRole.Role.ADMIN;
import static com.example.ticketing.auth.UserRole.Role.GIAM_DOC;
import static com.example.ticketing.auth.UserRole.Role.NHAN_VIEN;
import static com.example.ticketing.auth.UserRole.Role.TRUONG_PHONG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.ticketing.department.Department;
import com.example.ticketing.department.DepartmentRepository;
import com.example.ticketing.servicerequest.ServiceRequest;

class ServiceRequestAuthorizationTest {

    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final ServiceRequestAuthorization authz =
            new ServiceRequestAuthorization(new DepartmentKeys(departments));

    private static ServiceRequest sr(String requester, String dept) {
        ServiceRequest s = new ServiceRequest();
        s.setRequesterUsername(requester);
        s.setRequesterDepartment(dept);
        return s;
    }

    @Test
    void oversightAndItReadEverything() {
        ServiceRequest other = sr("zed", "HR");
        assertThat(authz.canRead(ActorContext.of("a", ADMIN, null, false), other)).isTrue();
        assertThat(authz.canRead(ActorContext.of("g", GIAM_DOC, "EXEC", false), other)).isTrue();
        assertThat(authz.canRead(ActorContext.of("n", NHAN_VIEN, "IT", true), other)).isTrue();
    }

    @Test
    void ownerMatchIsCaseInsensitive() {
        assertThat(authz.canRead(ActorContext.of("alice", NHAN_VIEN, "MKT", false), sr("Alice", "MKT"))).isTrue();
    }

    @Test
    void businessStaffCannotReadColleagueRequest() {
        assertThat(authz.canRead(ActorContext.of("alice", NHAN_VIEN, "MKT", false), sr("bob", "MKT"))).isFalse();
    }

    @Test
    void businessLeadReadsDepartmentByName() {
        Department d = new Department();
        d.setCode("MKT");
        d.setName("Marketing");
        when(departments.findByCode("MKT")).thenReturn(Optional.of(d));
        ActorContext lead = ActorContext.of("lead", TRUONG_PHONG, "MKT", false);
        assertThat(authz.canRead(lead, sr("bob", "Marketing"))).isTrue();
        assertThat(authz.canRead(lead, sr("bob", "MKT"))).isTrue();
        assertThat(authz.canRead(lead, sr("bob", "HR"))).isFalse();
    }

    @Test
    void businessLeadWithoutDepartmentSeesOwnOnly() {
        ActorContext lead = ActorContext.of("lead", TRUONG_PHONG, null, false);
        assertThat(authz.canRead(lead, sr("lead", null))).isTrue();
        assertThat(authz.canRead(lead, sr("bob", null))).isFalse();
    }

    @Test
    void actionsMatrix() {
        ActorContext admin = ActorContext.of("a", ADMIN, null, false);
        ActorContext tpIt = ActorContext.of("t", TRUONG_PHONG, "IT", true);
        ActorContext nvIt = ActorContext.of("n", NHAN_VIEN, "IT", true);
        ActorContext nv = ActorContext.of("u", NHAN_VIEN, "MKT", false);
        assertThat(authz.canProcess(nvIt)).isTrue();
        assertThat(authz.canProcess(admin)).isFalse();
        assertThat(authz.canProcess(nv)).isFalse();
        assertThat(authz.canApprove(tpIt)).isTrue();
        assertThat(authz.canApprove(nvIt)).isFalse();
        assertThat(authz.canApprove(admin)).isFalse();
        assertThat(authz.canDelete(admin)).isTrue();
        assertThat(authz.canDelete(tpIt)).isFalse();
        assertThat(authz.canSeeInternal(nv)).isFalse();
        assertThat(authz.canSeeInternal(nvIt)).isTrue();
    }
}

package com.example.ticketing.authorization;

import static com.example.ticketing.auth.UserRole.Role.GIAM_DOC;
import static com.example.ticketing.auth.UserRole.Role.NHAN_VIEN;
import static com.example.ticketing.auth.UserRole.Role.TRUONG_PHONG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.example.ticketing.asset.Asset;
import com.example.ticketing.department.DepartmentRepository;

class AssetAuthorizationTest {

    private final AssetAuthorization authz =
            new AssetAuthorization(new DepartmentKeys(mock(DepartmentRepository.class)));

    private static Asset asset(String assignedTo, String dept) {
        Asset a = new Asset();
        a.setAssignedTo(assignedTo);
        a.setAssignedDepartment(dept);
        return a;
    }

    @Test
    void assigneeMatchIsCaseInsensitive() {
        assertThat(authz.canRead(ActorContext.of("alice", NHAN_VIEN, "MKT", false), asset("ALICE", "MKT"))).isTrue();
    }

    @Test
    void readMatrix() {
        assertThat(authz.canRead(ActorContext.of("g", GIAM_DOC, "EXEC", false), asset("x", "HR"))).isTrue();
        assertThat(authz.canRead(ActorContext.of("n", NHAN_VIEN, "IT", true), asset("x", "HR"))).isTrue();
        assertThat(authz.canRead(ActorContext.of("alice", NHAN_VIEN, "MKT", false), asset("bob", "MKT"))).isFalse();
        assertThat(authz.canRead(ActorContext.of("lead", TRUONG_PHONG, "MKT", false), asset("bob", "mkt"))).isTrue();
        assertThat(authz.canRead(ActorContext.of("lead", TRUONG_PHONG, "MKT", false), asset("bob", "HR"))).isFalse();
    }

    @Test
    void actions() {
        assertThat(authz.canWrite(ActorContext.of("n", NHAN_VIEN, "IT", true))).isTrue();
        assertThat(authz.canWrite(ActorContext.of("g", GIAM_DOC, "EXEC", false))).isFalse();
        assertThat(authz.canViewFleet(ActorContext.of("g", GIAM_DOC, "EXEC", false))).isTrue();
        assertThat(authz.canViewFleet(ActorContext.of("lead", TRUONG_PHONG, "MKT", false))).isFalse();
    }
}

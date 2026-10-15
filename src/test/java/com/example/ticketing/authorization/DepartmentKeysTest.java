package com.example.ticketing.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.ticketing.auth.UserRole;
import com.example.ticketing.department.Department;
import com.example.ticketing.department.DepartmentRepository;

class DepartmentKeysTest {

    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final DepartmentKeys keys = new DepartmentKeys(departments);

    private ActorContext tp(String code) {
        return ActorContext.of("tp", UserRole.Role.TRUONG_PHONG, code, false);
    }

    @Test
    void matchesDepartmentName() {
        Department d = new Department();
        d.setCode("ACC");
        d.setName("Kế toán");
        when(departments.findByCode("ACC")).thenReturn(Optional.of(d));
        assertThat(keys.matches(tp("ACC"), "acc")).isTrue();
        assertThat(keys.matches(tp("ACC"), " Kế toán ")).isTrue();
        assertThat(keys.matches(tp("ACC"), "HR")).isFalse();
    }

    @Test
    void noDepartmentMatchesNothing() {
        assertThat(keys.of(tp(null))).isEmpty();
        assertThat(keys.matches(tp(null), "ACC")).isFalse();
        assertThat(keys.matches(tp("ACC"), null)).isFalse();
    }
}

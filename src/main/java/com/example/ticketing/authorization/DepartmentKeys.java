package com.example.ticketing.authorization;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.ticketing.department.DepartmentRepository;

/**
 * Khoá so khớp phòng ban của actor: mã và tên (lowercase). Các cột phòng ban của SR/Asset
 * là chuỗi tự do, dữ liệu cũ có thể lưu tên thay vì mã.
 */
@Component
public class DepartmentKeys {

    private final DepartmentRepository departments;

    public DepartmentKeys(DepartmentRepository departments) {
        this.departments = departments;
    }

    public Set<String> of(ActorContext actor) {
        String code = actor.departmentCode();
        if (code == null || code.isBlank()) {
            return Set.of();
        }
        Set<String> keys = new HashSet<>();
        keys.add(code.trim().toLowerCase(Locale.ROOT));
        departments.findByCode(code).ifPresent(d -> {
            if (d.getName() != null) {
                keys.add(d.getName().trim().toLowerCase(Locale.ROOT));
            }
        });
        return keys;
    }

    public boolean matches(ActorContext actor, String value) {
        return value != null && of(actor).contains(value.trim().toLowerCase(Locale.ROOT));
    }
}

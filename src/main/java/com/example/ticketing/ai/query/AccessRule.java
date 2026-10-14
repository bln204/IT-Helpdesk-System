package com.example.ticketing.ai.query;

import com.example.ticketing.auth.UserRole.Role;
import com.example.ticketing.authorization.ActorContext;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Role gate mirroring the @PreAuthorize of the UI endpoint the adapter replaces. */
public record AccessRule(Set<Role> roles) {

    public static AccessRule of(Role... roles) {
        return new AccessRule(EnumSet.copyOf(Arrays.asList(roles)));
    }

    public static AccessRule allRoles() {
        return new AccessRule(EnumSet.allOf(Role.class));
    }

    public boolean allows(ActorContext actor) {
        return actor != null && actor.role() != null && roles.contains(actor.role());
    }

    public String deniedMessage(String what) {
        String who = EnumSet.copyOf(roles).stream().map(AccessRule::roleLabel).collect(Collectors.joining(", "));
        return "Bạn không có quyền xem " + what + ". Chức năng này dành cho " + who + ".";
    }

    static String roleLabel(Role role) {
        return switch (role) {
            case ADMIN -> "Admin";
            case GIAM_DOC -> "Giám đốc";
            case TRUONG_PHONG -> "Trưởng phòng";
            case NHAN_VIEN -> "Nhân viên";
        };
    }
}

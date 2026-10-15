package com.example.ticketing.authorization;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.ticketing.auth.UserRole;

/**
 * Nhóm actor dùng chung cho các policy module (spec phase1-rbac-hardening §2).
 * GS = ADMIN/GIAM_DOC; IT = TRUONG_PHONG/NHAN_VIEN thuộc phòng IT; NV = còn lại.
 */
public final class ActorTiers {

    private ActorTiers() {}

    public static boolean isAdmin(ActorContext a) {
        return a.role() == UserRole.Role.ADMIN;
    }

    public static boolean isOversight(ActorContext a) {
        return a.role() == UserRole.Role.ADMIN || a.role() == UserRole.Role.GIAM_DOC;
    }

    public static boolean isItOperator(ActorContext a) {
        return (a.role() == UserRole.Role.TRUONG_PHONG || a.role() == UserRole.Role.NHAN_VIEN)
                && a.isItDepartmentMember();
    }

    public static boolean isItLead(ActorContext a) {
        return a.role() == UserRole.Role.TRUONG_PHONG && a.isItDepartmentMember();
    }

    /** GS hoặc IT: đọc toàn công ty. */
    public static boolean seesAll(ActorContext a) {
        return isOversight(a) || isItOperator(a);
    }

    /** Trưởng phòng nghiệp vụ: thấy thêm dữ liệu phòng mình. */
    public static boolean isBusinessLead(ActorContext a) {
        return a.role() == UserRole.Role.TRUONG_PHONG && !a.isItDepartmentMember();
    }

    public static boolean sameUser(ActorContext a, String username) {
        return username != null && username.equalsIgnoreCase(a.username());
    }

    public static void require(boolean allowed, String message) {
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }
}

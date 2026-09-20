package com.example.ticketing.auth;

public final class UserRole {
    private UserRole() {}

    /**
     * User Role Hierarchy:
     * ADMIN > GIAM_DOC > TRUONG_PHONG > NHAN_VIEN
     */
    public enum Role {
        /** ADMIN: Tách biệt - không thuộc phòng ban nào, có toàn quyền */
        ADMIN,
        
        /** GIAM_DOC: Giám đốc - thuộc EXEC, quản lý cấp cao */
        GIAM_DOC,
        
        /** TRUONG_PHONG: Trưởng phòng - thuộc phòng ban, quản lý nhân viên phòng mình */
        TRUONG_PHONG,
        
        /** NHAN_VIEN: Nhân viên - thuộc phòng ban, quyền hạn cơ bản */
        NHAN_VIEN
    }
}

-- ============================================================
-- V17: Seed role mapping - Chuyển từ role cũ sang role mới
-- ============================================================

-- Role mapping:
-- REQUESTER -> NHAN_VIEN (Nhân viên thường)
-- ENGINEER -> NHAN_VIEN (Nhân viên phòng IT - sẽ thuộc department IT)
-- ADMIN -> GIAM_DOC (Admin hiện tại trở thành Giám đốc)

-- NOTE: Trưởng phòng (TRUONG_PHONG) sẽ được tạo mới trong V18
-- NOTE: ADMIN mới (tách biệt) cũng sẽ được tạo trong V18

-- Giám đốc thuộc EXEC department
UPDATE users SET role = 'GIAM_DOC', 
                 department_id = (SELECT id FROM departments WHERE code = 'EXEC')
WHERE role = 'ADMIN';

-- ENGINEER và REQUESTER đều thành NHAN_VIEN
UPDATE users SET role = 'NHAN_VIEN' WHERE role = 'ENGINEER';
UPDATE users SET role = 'NHAN_VIEN' WHERE role = 'REQUESTER';

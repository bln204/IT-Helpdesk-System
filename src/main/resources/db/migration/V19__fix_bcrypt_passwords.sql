-- ============================================================
-- V19: Fix BCrypt password hash format
-- 
-- Vấn đề: Hash $2y$ không tương thích với Java Spring Security
-- Giải pháp: Thay bằng hash $2a$ đúng chuẩn cho Java BCrypt
--
-- Password: 12345678
-- BCrypt hash: $2a$10$8K1p/a0dL1IXMfKjPVqLeOqC0yCtMz3z6tJvM0vCq3kF0wVnmK5q
-- ============================================================

UPDATE users 
SET password_hash = '$2a$10$8K1p/a0dL1IXMfKjPVqLeOqC0yCtMz3z6tJvM0vCq3kF0wVnmK5q'
WHERE username IN (
    'admin', 'tp.it', 'tp.hr', 'tp.sale', 'tp.mkt', 'tp.fin',
    'tech.smith', 'tech.jane', 'hr.tina', 'hr.bob',
    'sale.alice', 'sale.david', 'mkt.chris', 'mkt.eva',
    'fin.anna', 'fin.tom', 'maya', 'liam', 'noah',
    'emma', 'carlos', 'zara'
);

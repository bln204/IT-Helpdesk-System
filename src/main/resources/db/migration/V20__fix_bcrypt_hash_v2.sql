-- ============================================================
-- V20: Fix BCrypt password hash (corrected)
-- 
-- Password: 12345678
-- BCrypt hash (60 chars): $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3.rsS7/kXwQiP8x8qWKi
-- ============================================================

UPDATE users 
SET password_hash = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3.rsS7/kXwQiP8x8qWKi'
WHERE enabled = TRUE;

-- ============================================================
-- V21: Set correct BCrypt hash for all users
-- Password: 12345678
-- ============================================================

UPDATE users 
SET password_hash = '$2a$10$N181o7K17JwZScnThUyNBeSvj22beOcqp1xJDznicO68aTVC8nlNW'
WHERE enabled = TRUE;

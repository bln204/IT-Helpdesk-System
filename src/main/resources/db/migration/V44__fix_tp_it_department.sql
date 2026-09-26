-- ============================================================
-- V44: Fix tp.it department assignment
-- ============================================================
-- Ensures tp.it has TRUONG_PHONG role and IT department
-- so they can assign tickets to IT staff

-- Update department for tp.it
UPDATE users 
SET department_id = (SELECT id FROM departments WHERE code = 'IT')
WHERE username = 'tp.it' AND department_id IS NULL;

-- Verify tp.it settings
SELECT u.username, u.role, d.code as dept_code, d.name as dept_name
FROM users u 
LEFT JOIN departments d ON u.department_id = d.id 
WHERE u.username = 'tp.it';

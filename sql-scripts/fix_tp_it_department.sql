-- ============================================================
-- FIX: Assign IT department to tp.it user
-- Run this in pgAdmin to fix tp.it department
-- ============================================================

-- Step 1: Check current state
SELECT u.username, u.role, d.code as dept_code, d.name as dept_name
FROM users u 
LEFT JOIN departments d ON u.department_id = d.id 
WHERE u.username = 'tp.it';

-- Step 2: Fix department (only if currently NULL or wrong)
UPDATE users 
SET department_id = (SELECT id FROM departments WHERE code = 'IT')
WHERE username = 'tp.it';

-- Step 3: Verify fix
SELECT u.username, u.role, d.code as dept_code, d.name as dept_name
FROM users u 
LEFT JOIN departments d ON u.department_id = d.id 
WHERE u.username = 'tp.it';

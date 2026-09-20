-- ============================================================
-- V23: Enable all IT staff members
-- ============================================================

-- Ensure all users in IT department are enabled
UPDATE users 
SET enabled = TRUE 
WHERE department_id = (SELECT id FROM departments WHERE code = 'IT');

-- Also ensure newly added IT staff are enabled (in case they were added with enabled=FALSE)
UPDATE users 
SET enabled = TRUE 
WHERE username IN ('tech.smith', 'tech.jane', 'maya', 'liam', 'noah');

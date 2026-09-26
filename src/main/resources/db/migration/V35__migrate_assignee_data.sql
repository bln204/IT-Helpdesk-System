-- ============================================================
-- V35: Migrate dữ liệu từ assignee_name (String) sang assignee_id (FK)
-- ============================================================
-- Và seed SLA rules theo priority

-- 1. Migrate assignee_name -> assignee_id
-- Cập nhật assignee_id dựa trên assignee_name (display_name hoặc username)
UPDATE tickets t
SET assignee_id = (
    SELECT u.id 
    FROM users u 
    WHERE u.display_name = t.assignee_name 
       OR u.username = t.assignee_name
    LIMIT 1
)
WHERE t.assignee_name IS NOT NULL 
  AND t.assignee_id IS NULL;

-- 2. Migrate category enum -> category_id
-- Map từ TicketTypes.TicketCategory enum sang categories table
UPDATE tickets t
SET category_id = (
    SELECT id FROM categories 
    WHERE code = CASE t.category::TEXT
        WHEN 'SOFTWARE' THEN 'SOFTWARE'
        WHEN 'ACCESS' THEN 'ACCOUNT'
        WHEN 'NETWORK' THEN 'NETWORK'
        WHEN 'HARDWARE' THEN 'HARDWARE'
        WHEN 'OTHER' THEN 'OTHER'
        ELSE NULL
    END
    AND parent_id IS NULL  -- Chỉ lấy top-level categories
)
WHERE t.category IS NOT NULL;

-- 3. Seed SLA rules - Update SLA times based on priority
-- CRITICAL: 1 giờ response, 4 giờ resolution
-- HIGH: 2 giờ response, 8 giờ resolution  
-- MEDIUM: 4 giờ response, 24 giờ resolution
-- LOW: 8 giờ response, 72 giờ resolution

UPDATE tickets SET
    sla_response_at = created_at + INTERVAL '1 hour',
    sla_resolution_at = created_at + INTERVAL '4 hours'
WHERE priority = 'URGENT'
  AND status NOT IN ('CLOSED', 'RESOLVED')
  AND sla_resolution_at IS NULL;

UPDATE tickets SET
    sla_response_at = created_at + INTERVAL '2 hours',
    sla_resolution_at = created_at + INTERVAL '8 hours'
WHERE priority = 'HIGH'
  AND status NOT IN ('CLOSED', 'RESOLVED')
  AND sla_resolution_at IS NULL;

UPDATE tickets SET
    sla_response_at = created_at + INTERVAL '4 hours',
    sla_resolution_at = created_at + INTERVAL '24 hours'
WHERE priority = 'MEDIUM'
  AND status NOT IN ('CLOSED', 'RESOLVED')
  AND sla_resolution_at IS NULL;

UPDATE tickets SET
    sla_response_at = created_at + INTERVAL '8 hours',
    sla_resolution_at = created_at + INTERVAL '72 hours'
WHERE priority = 'LOW'
  AND status NOT IN ('CLOSED', 'RESOLVED')
  AND sla_resolution_at IS NULL;

-- 4. Auto-assign team dựa trên category
UPDATE tickets t
SET team_id = (
    SELECT id FROM teams 
    WHERE code = CASE 
        WHEN t.category_id = (SELECT id FROM categories WHERE code = 'NETWORK') THEN 'NET'
        WHEN t.category_id = (SELECT id FROM categories WHERE code = 'HARDWARE') THEN 'HW'
        WHEN t.category_id = (SELECT id FROM categories WHERE code = 'SOFTWARE') THEN 'SW'
        WHEN t.category_id = (SELECT id FROM categories WHERE code = 'SECURITY') THEN 'SEC'
        ELSE 'GEN'
    END
    LIMIT 1
)
WHERE t.team_id IS NULL 
  AND t.category_id IS NOT NULL;

-- 5. Set first_response_at cho tickets đã có assignee (đánh dấu là đã phản hồi)
UPDATE tickets t
SET first_response_at = updated_at
WHERE t.assignee_id IS NOT NULL
  AND t.first_response_at IS NULL
  AND t.status IN ('IN_PROGRESS', 'RESOLVED', 'CLOSED');

-- 6. Log migration results
DO $$
DECLARE
    assignee_migrated INT;
    category_migrated INT;
    team_assigned INT;
BEGIN
    SELECT COUNT(*) INTO assignee_migrated FROM tickets WHERE assignee_id IS NOT NULL AND assignee_name IS NOT NULL;
    SELECT COUNT(*) INTO category_migrated FROM tickets WHERE category_id IS NOT NULL;
    SELECT COUNT(*) INTO team_assigned FROM tickets WHERE team_id IS NOT NULL;
    
    RAISE NOTICE 'Migration V35 completed:';
    RAISE NOTICE '  - Assignees migrated: %', assignee_migrated;
    RAISE NOTICE '  - Categories migrated: %', category_migrated;
    RAISE NOTICE '  - Teams auto-assigned: %', team_assigned;
END $$;

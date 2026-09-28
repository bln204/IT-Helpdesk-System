-- ============================================================
-- V56: Ensure admin and all demo users are approved
-- ============================================================

-- Update all existing users to be approved (in case V26 didn't run correctly)
UPDATE users SET approved = TRUE, approved_at = NOW() WHERE approved = FALSE AND enabled = TRUE;

-- Specifically ensure admin is approved
UPDATE users SET approved = TRUE, approved_at = NOW() WHERE username = 'admin';

-- Ensure all demo users from V18 and V24 are approved
UPDATE users SET approved = TRUE, approved_at = NOW() 
WHERE username IN (
    'admin', 'tp.it', 'tp.hr', 'tp.sale', 'tp.mkt', 'tp.fin',
    'tech.smith', 'tech.jane', 'hr.tina', 'hr.bob',
    'sale.alice', 'sale.david', 'mkt.chris', 'mkt.eva',
    'fin.anna', 'fin.tom', 'requester', 'engineer'
) AND enabled = TRUE;

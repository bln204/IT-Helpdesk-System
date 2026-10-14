-- ============================================================
-- V71: AI Assistant Multi-Turn Conversation Memory
-- Purpose: Let the AI assistant keep a per-user chat history
--          so it can reference prior context (e.g. problem
--          description, attempted fix steps) when the user
--          asks it to "create a ticket". Also lets the AI
--          ask follow-up questions one at a time when a
--          required ticket field is missing.
--
-- Tables:
--   ai_session  - 1 per conversation thread (UUID). Owned by
--                 a single user_account. Soft delete via
--                 is_archived.
--   ai_message  - 1 row per user/assistant turn in that thread.
--                 metadata is JSONB (carries the TicketDraft
--                 action payload, source refs, etc.).
-- ============================================================

-- 1. ai_session
CREATE TABLE ai_session (
    id UUID PRIMARY KEY,
    user_username VARCHAR(80) NOT NULL,
    title VARCHAR(200),
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_session_user
        FOREIGN KEY (user_username)
        REFERENCES users(username)
        ON DELETE CASCADE
);

CREATE INDEX idx_ai_session_user_active
    ON ai_session(user_username, last_active_at DESC)
    WHERE is_archived = FALSE;

COMMENT ON TABLE ai_session IS
    'AI assistant conversation thread. One per chat, owned by a users row.';

-- 2. ai_message
CREATE TABLE ai_message (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_message_session
        FOREIGN KEY (session_id)
        REFERENCES ai_session(id)
        ON DELETE CASCADE,
    CONSTRAINT chk_ai_message_role
        CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM'))
);

CREATE INDEX idx_ai_message_session_time
    ON ai_message(session_id, created_at);

COMMENT ON TABLE ai_message IS
    'One row per turn in an ai_session. metadata is JSONB and carries action payload (TicketDraft, sources, etc.).';

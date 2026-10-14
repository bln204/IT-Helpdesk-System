package com.example.ticketing.ai.session;

/**
 * Role of a single message in an AI assistant conversation.
 * Mirrors the DB CHECK constraint on ai_message.role.
 */
public enum AiMessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

package com.example.ticketing.ai;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTOs for AI session / message REST endpoints.
 * Kept separate from AiAssistantDto to avoid bloating the ask
 * endpoint contract.
 */
public class AiSessionDtos {

    private AiSessionDtos() {
    }

    /** Summary of one conversation thread (for the sidebar list). */
    public static class SessionSummary {
        private UUID id;
        private String title;
        private LocalDateTime lastActiveAt;
        private LocalDateTime createdAt;

        public SessionSummary() {
        }

        public SessionSummary(UUID id, String title, LocalDateTime lastActiveAt,
                LocalDateTime createdAt) {
            this.id = id;
            this.title = title;
            this.lastActiveAt = lastActiveAt;
            this.createdAt = createdAt;
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public LocalDateTime getLastActiveAt() {
            return lastActiveAt;
        }

        public void setLastActiveAt(LocalDateTime lastActiveAt) {
            this.lastActiveAt = lastActiveAt;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }

    /** One turn in a conversation. */
    public static class MessageView {
        private UUID id;
        private String role;
        private String content;
        private String metadata;
        private LocalDateTime createdAt;

        public MessageView() {
        }

        public MessageView(UUID id, String role, String content, String metadata,
                LocalDateTime createdAt) {
            this.id = id;
            this.role = role;
            this.content = content;
            this.metadata = metadata;
            this.createdAt = createdAt;
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getMetadata() {
            return metadata;
        }

        public void setMetadata(String metadata) {
            this.metadata = metadata;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }

    /** Body for POST /api/ai/sessions. */
    public static class CreateSessionRequest {
        private String title;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    /** Body for POST /api/ai/sessions/{id}/confirm-ticket. */
    public static class ConfirmTicketRequest {
        /** The full draft the user just confirmed (server validates again). */
        private String title;
        private String description;
        private String priority;
        private String category;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }
    }
}

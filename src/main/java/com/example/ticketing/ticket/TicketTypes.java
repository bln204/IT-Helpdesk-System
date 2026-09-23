package com.example.ticketing.ticket;

public final class TicketTypes {
    private TicketTypes() {
    }

    public enum TicketCategory {
        SOFTWARE,
        ACCESS,
        NETWORK,
        HARDWARE,
        OTHER
    }

    public enum TicketPriority {
        LOW,
        MEDIUM,
        HIGH,
        URGENT
    }

    public enum TicketStatus {
        NEW,
        IN_PROGRESS,
        BLOCKED,
        CLOSED
    }

    public enum TicketRole {
        REQUESTER,
        ENGINEER,
        ADMIN
    }

    public enum AuditAction {
        CREATED,
        STATUS_CHANGED,
        ASSIGNEE_CHANGED,
        COMMENT_ADDED,
        PRIORITY_CHANGED
    }

    public enum CommentVisibility {
        PUBLIC,
        INTERNAL
    }
}

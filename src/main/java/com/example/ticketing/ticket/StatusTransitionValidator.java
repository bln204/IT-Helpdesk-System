package com.example.ticketing.ticket;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Validator cho Ticket Status Transitions.
 * Định nghĩa các transitions hợp lệ theo Phase 2 lifecycle.
 * 
 * Ticket Lifecycle:
 * 
 *     NEW ──► ASSIGNED ──► IN_PROGRESS
 *                          │       ▲
 *                          │       │
 *                          ▼       │
 *                  WAITING_FOR_USER┘
 *                          │
 *                          ▼
 *                       RESOLVED
 *                          │
 *                          ▼
 *                       CLOSED
 * 
 * Các trạng thái đặc biệt:
 * - ESCALATED: Từ IN_PROGRESS hoặc WAITING_FOR_USER
 * - REOPENED: Từ CLOSED
 * - CANCELLED: Từ bất kỳ trạng thái nào (admin only)
 */
public class StatusTransitionValidator {
    
    /**
     * Map lưu trữ các transitions hợp lệ.
     * Key = Current Status
     * Value = Set các statuses có thể chuyển đến
     */
    private static final Map<TicketTypes.TicketStatus, Set<TicketTypes.TicketStatus>> VALID_TRANSITIONS;
    
    /**
     * Các trạng thái terminal (không thể chuyển đi đâu nữa trừ REOPENED hoặc CANCELLED).
     */
    private static final Set<TicketTypes.TicketStatus> TERMINAL_STATUSES = EnumSet.of(
        TicketTypes.TicketStatus.CLOSED
    );
    
    /**
     * Các trạng thái có thể bị cancel.
     */
    private static final Set<TicketTypes.TicketStatus> CANCELLABLE_STATUSES = EnumSet.of(
        TicketTypes.TicketStatus.NEW,
        TicketTypes.TicketStatus.ASSIGNED,
        TicketTypes.TicketStatus.IN_PROGRESS,
        TicketTypes.TicketStatus.WAITING_FOR_USER,
        TicketTypes.TicketStatus.BLOCKED
    );
    
    static {
        VALID_TRANSITIONS = new EnumMap<>(TicketTypes.TicketStatus.class);
        
        // NEW -> ASSIGNED, IN_PROGRESS (khi IT bắt đầu xử lý - có thể assign trước hoặc bắt đầu luôn)
        VALID_TRANSITIONS.put(TicketTypes.TicketStatus.NEW, EnumSet.of(
            TicketTypes.TicketStatus.ASSIGNED,
            TicketTypes.TicketStatus.IN_PROGRESS,
            TicketTypes.TicketStatus.CANCELLED
        ));
        
        // ASSIGNED -> IN_PROGRESS (khi IT bắt đầu xử lý)
        VALID_TRANSITIONS.put(TicketTypes.TicketStatus.ASSIGNED, EnumSet.of(
            TicketTypes.TicketStatus.IN_PROGRESS,
            TicketTypes.TicketStatus.CANCELLED
        ));
        
        // IN_PROGRESS -> (nhiều transitions)
        VALID_TRANSITIONS.put(TicketTypes.TicketStatus.IN_PROGRESS, EnumSet.of(
            TicketTypes.TicketStatus.WAITING_FOR_USER,
            TicketTypes.TicketStatus.RESOLVED,
            TicketTypes.TicketStatus.ESCALATED,
            TicketTypes.TicketStatus.CANCELLED
        ));
        
        // WAITING_FOR_USER -> IN_PROGRESS (khi user cung cấp thông tin)
        VALID_TRANSITIONS.put(TicketTypes.TicketStatus.WAITING_FOR_USER, EnumSet.of(
            TicketTypes.TicketStatus.IN_PROGRESS,
            TicketTypes.TicketStatus.ESCALATED,
            TicketTypes.TicketStatus.CANCELLED
        ));
        
        // RESOLVED -> CLOSED (khi user xác nhận)
        VALID_TRANSITIONS.put(TicketTypes.TicketStatus.RESOLVED, EnumSet.of(
            TicketTypes.TicketStatus.CLOSED,
            TicketTypes.TicketStatus.REOPENED
        ));
        
        // CLOSED -> REOPENED (khi ticket được mở lại)
        VALID_TRANSITIONS.put(TicketTypes.TicketStatus.CLOSED, EnumSet.of(
            TicketTypes.TicketStatus.REOPENED
        ));
        
        // ESCALATED -> IN_PROGRESS (sau khi escalate)
        VALID_TRANSITIONS.put(TicketTypes.TicketStatus.ESCALATED, EnumSet.of(
            TicketTypes.TicketStatus.IN_PROGRESS,
            TicketTypes.TicketStatus.WAITING_FOR_USER,
            TicketTypes.TicketStatus.RESOLVED
        ));
        
        // REOPENED -> IN_PROGRESS (khi ticket được reopen)
        VALID_TRANSITIONS.put(TicketTypes.TicketStatus.REOPENED, EnumSet.of(
            TicketTypes.TicketStatus.IN_PROGRESS,
            TicketTypes.TicketStatus.WAITING_FOR_USER,
            TicketTypes.TicketStatus.CANCELLED
        ));
        
        // BLOCKED -> IN_PROGRESS (khi block được giải quyết)
        VALID_TRANSITIONS.put(TicketTypes.TicketStatus.BLOCKED, EnumSet.of(
            TicketTypes.TicketStatus.IN_PROGRESS,
            TicketTypes.TicketStatus.CANCELLED
        ));
        
        // CANCELLED -> (không có transitions, terminal state)
        VALID_TRANSITIONS.put(TicketTypes.TicketStatus.CANCELLED, EnumSet.noneOf(TicketTypes.TicketStatus.class));
        
        // IN_PROGRESS -> BLOCKED (khi bị block bởi bên thứ 3)
        VALID_TRANSITIONS.get(TicketTypes.TicketStatus.IN_PROGRESS).add(TicketTypes.TicketStatus.BLOCKED);
        
        // WAITING_FOR_USER -> BLOCKED
        VALID_TRANSITIONS.get(TicketTypes.TicketStatus.WAITING_FOR_USER).add(TicketTypes.TicketStatus.BLOCKED);
    }
    
    /**
     * Kiểm tra xem một transition có hợp lệ không.
     * 
     * @param from Trạng thái hiện tại
     * @param to Trạng thái muốn chuyển đến
     * @return true nếu transition hợp lệ
     */
    public static boolean isValidTransition(TicketTypes.TicketStatus from, TicketTypes.TicketStatus to) {
        if (from == null || to == null) {
            return false;
        }
        
        // CANCELLED là terminal state
        if (from == TicketTypes.TicketStatus.CANCELLED) {
            return false;
        }
        
        Set<TicketTypes.TicketStatus> validTargets = VALID_TRANSITIONS.get(from);
        return validTargets != null && validTargets.contains(to);
    }
    
    /**
     * Lấy danh sách các trạng thái có thể chuyển đến từ trạng thái hiện tại.
     * 
     * @param from Trạng thái hiện tại
     * @return Set các trạng thái có thể chuyển đến
     */
    public static Set<TicketTypes.TicketStatus> getValidNextStatuses(TicketTypes.TicketStatus from) {
        if (from == null) {
            return EnumSet.noneOf(TicketTypes.TicketStatus.class);
        }
        Set<TicketTypes.TicketStatus> valid = VALID_TRANSITIONS.get(from);
        return valid != null ? EnumSet.copyOf(valid) : EnumSet.noneOf(TicketTypes.TicketStatus.class);
    }
    
    /**
     * Kiểm tra xem trạng thái hiện tại có phải là terminal state không.
     * 
     * @param status Trạng thái cần kiểm tra
     * @return true nếu là terminal state
     */
    public static boolean isTerminalStatus(TicketTypes.TicketStatus status) {
        return TERMINAL_STATUSES.contains(status);
    }
    
    /**
     * Kiểm tra xem trạng thái có thể bị cancel không.
     * 
     * @param status Trạng thái cần kiểm tra
     * @return true nếu có thể cancel
     */
    public static boolean isCancellable(TicketTypes.TicketStatus status) {
        return CANCELLABLE_STATUSES.contains(status);
    }
    
    /**
     * Kiểm tra xem transition có phải là "progressing forward" không.
     * Forward progress = NEW -> ASSIGNED -> IN_PROGRESS -> RESOLVED -> CLOSED
     * 
     * @param from Trạng thái hiện tại
     * @param to Trạng thái muốn chuyển đến
     * @return true nếu là forward progress
     */
    public static boolean isForwardProgress(TicketTypes.TicketStatus from, TicketTypes.TicketStatus to) {
        if (from == null || to == null) {
            return false;
        }
        
        // Define forward progression
        int[] forwardOrder = {
            TicketTypes.TicketStatus.NEW.ordinal(),
            TicketTypes.TicketStatus.ASSIGNED.ordinal(),
            TicketTypes.TicketStatus.IN_PROGRESS.ordinal(),
            TicketTypes.TicketStatus.WAITING_FOR_USER.ordinal(),
            TicketTypes.TicketStatus.RESOLVED.ordinal(),
            TicketTypes.TicketStatus.CLOSED.ordinal()
        };
        
        int fromIndex = -1;
        int toIndex = -1;
        
        for (int i = 0; i < forwardOrder.length; i++) {
            if (TicketTypes.TicketStatus.values()[forwardOrder[i]] == from) {
                fromIndex = i;
            }
            if (TicketTypes.TicketStatus.values()[forwardOrder[i]] == to) {
                toIndex = i;
            }
        }
        
        // WAITING_FOR_USER can go back to IN_PROGRESS, which is forward progress
        if (from == TicketTypes.TicketStatus.WAITING_FOR_USER && to == TicketTypes.TicketStatus.IN_PROGRESS) {
            return true;
        }
        
        return fromIndex >= 0 && toIndex >= 0 && toIndex > fromIndex;
    }
    
    /**
     * Lấy mô tả human-readable cho một transition.
     * 
     * @param from Trạng thái hiện tại
     * @param to Trạng thái muốn chuyển đến
     * @return Mô tả transition
     */
    public static String getTransitionDescription(TicketTypes.TicketStatus from, TicketTypes.TicketStatus to) {
        return from.name() + " -> " + to.name();
    }
    
    /**
     * Exception thrown khi một transition không hợp lệ.
     */
    public static class InvalidTransitionException extends RuntimeException {
        private final TicketTypes.TicketStatus from;
        private final TicketTypes.TicketStatus to;
        
        public InvalidTransitionException(TicketTypes.TicketStatus from, TicketTypes.TicketStatus to) {
            super("Invalid status transition: " + getTransitionDescription(from, to));
            this.from = from;
            this.to = to;
        }
        
        public TicketTypes.TicketStatus getFrom() {
            return from;
        }
        
        public TicketTypes.TicketStatus getTo() {
            return to;
        }
    }
}

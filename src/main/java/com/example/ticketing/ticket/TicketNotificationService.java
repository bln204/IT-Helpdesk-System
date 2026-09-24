package com.example.ticketing.ticket;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.auth.Notification;
import com.example.ticketing.auth.Notification.NotificationType;
import com.example.ticketing.auth.NotificationRepository;
import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.ticket.TicketTypes.TicketStatus;

/**
 * Service xử lý notifications cho ticket lifecycle.
 * Gửi notifications khi có thay đổi trạng thái, assignment, comment...
 */
@Service
@Transactional
public class TicketNotificationService {
    
    private static final Logger log = LoggerFactory.getLogger(TicketNotificationService.class);
    
    private final NotificationRepository notificationRepository;
    private final UserAccountRepository userAccountRepository;
    
    public TicketNotificationService(
            NotificationRepository notificationRepository,
            UserAccountRepository userAccountRepository) {
        this.notificationRepository = notificationRepository;
        this.userAccountRepository = userAccountRepository;
    }
    
    /**
     * Gửi notification khi ticket được tạo mới.
     */
    public void notifyTicketCreated(Ticket ticket) {
        // Notify IT team (all IT staff) về ticket mới
        notifyITStaff(
            ticket,
            NotificationType.TICKET_CREATED,
            "Ticket mới #" + ticket.getTicketNumber(),
            "Ticket mới: " + ticket.getTitle() + " (Priority: " + ticket.getPriority() + ")"
        );
    }
    
    /**
     * Gửi notification khi ticket được gán cho user.
     */
    public void notifyTicketAssigned(Ticket ticket, String assignedBy) {
        if (ticket.getAssignee() != null) {
            String assigneeUsername = ticket.getAssignee().getUsername();
            Notification notification = Notification.forTicketAssigned(
                assigneeUsername,
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getTitle(),
                assignedBy
            );
            notificationRepository.save(notification);
            log.info("Sent assignment notification to {}", assigneeUsername);
        }
        
        // Nếu được gán cho team, notify team members
        if (ticket.getTeam() != null) {
            notifyTeamMembers(ticket, NotificationType.TICKET_ASSIGNED_TO_TEAM,
                "Ticket mới được giao cho team",
                "Ticket #" + ticket.getTicketNumber() + " - " + ticket.getTitle());
        }
    }
    
    /**
     * Gửi notification khi status thay đổi.
     */
    public void notifyStatusChanged(Ticket ticket, TicketStatus oldStatus, TicketStatus newStatus, String changedBy) {
        // Notify requester
        if (ticket.getRequesterUsername() != null) {
            Notification notification = Notification.forStatusChanged(
                ticket.getRequesterUsername(),
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getTitle(),
                oldStatus.name(),
                newStatus.name(),
                changedBy
            );
            notificationRepository.save(notification);
        }
        
        // Notify assignee (nếu khác requester)
        if (ticket.getAssignee() != null && 
            !ticket.getAssignee().getUsername().equals(ticket.getRequesterUsername())) {
            Notification notification = Notification.forStatusChanged(
                ticket.getAssignee().getUsername(),
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getTitle(),
                oldStatus.name(),
                newStatus.name(),
                changedBy
            );
            notificationRepository.save(notification);
        }
        
        log.info("Status change notification sent for ticket {}: {} -> {}", 
            ticket.getTicketNumber(), oldStatus, newStatus);
    }
    
    /**
     * Gửi notification khi ticket được resolved.
     */
    public void notifyTicketResolved(Ticket ticket, String resolvedBy) {
        if (ticket.getRequesterUsername() != null) {
            Notification notification = Notification.forTicketResolved(
                ticket.getRequesterUsername(),
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getTitle(),
                resolvedBy
            );
            notificationRepository.save(notification);
            log.info("Sent resolved notification to requester: {}", ticket.getRequesterUsername());
        }
    }
    
    /**
     * Gửi notification khi ticket được closed.
     */
    public void notifyTicketClosed(Ticket ticket, String closedBy) {
        if (ticket.getRequesterUsername() != null) {
            Notification notification = new Notification(
                ticket.getRequesterUsername(),
                NotificationType.TICKET_CLOSED,
                "Ticket #" + ticket.getTicketNumber() + " đã được đóng",
                "Ticket đã được đóng bởi " + closedBy
            );
            notification.setTicketId(ticket.getId());
            notification.setTicketNumber(ticket.getTicketNumber());
            notification.setActorUsername(closedBy);
            notificationRepository.save(notification);
        }
    }
    
    /**
     * Gửi notification khi ticket được reopen.
     */
    public void notifyTicketReopened(Ticket ticket, String reopenedBy) {
        // Notify assignee
        if (ticket.getAssignee() != null) {
            Notification notification = new Notification(
                ticket.getAssignee().getUsername(),
                NotificationType.TICKET_REOPENED,
                "Ticket #" + ticket.getTicketNumber() + " được mở lại",
                "Ticket đã được mở lại bởi " + reopenedBy
            );
            notification.setTicketId(ticket.getId());
            notification.setTicketNumber(ticket.getTicketNumber());
            notification.setActorUsername(reopenedBy);
            notificationRepository.save(notification);
        }
        
        // Notify IT staff
        notifyITStaff(ticket, NotificationType.TICKET_REOPENED,
            "Ticket #" + ticket.getTicketNumber() + " được mở lại",
            "Ticket đã được mở lại bởi " + reopenedBy);
    }
    
    /**
     * Gửi notification khi ticket được escalate.
     */
    public void notifyTicketEscalated(Ticket ticket, String escalatedBy, String reason) {
        // Notify IT Manager / Admins
        notifyITManagers(ticket, NotificationType.TICKET_ESCALATED,
            "Ticket #" + ticket.getTicketNumber() + " được escalate",
            "Ticket đã được escalate bởi " + escalatedBy + ". Lý do: " + reason);
    }
    
    /**
     * Gửi notification khi IT cần thông tin từ user.
     */
    public void notifyWaitingForUser(Ticket ticket, String message) {
        if (ticket.getRequesterUsername() != null) {
            Notification notification = new Notification(
                ticket.getRequesterUsername(),
                NotificationType.TICKET_WAITING_FOR_INFO,
                "Ticket #" + ticket.getTicketNumber() + " - Cần thông tin từ bạn",
                message != null ? message : "IT cần bạn cung cấp thêm thông tin để xử lý ticket."
            );
            notification.setTicketId(ticket.getId());
            notification.setTicketNumber(ticket.getTicketNumber());
            notificationRepository.save(notification);
            log.info("Sent waiting for info notification to: {}", ticket.getRequesterUsername());
        }
    }
    
    /**
     * Gửi notification khi user cung cấp thông tin.
     */
    public void notifyInfoProvided(Ticket ticket, String providedBy) {
        // Notify assignee
        if (ticket.getAssignee() != null) {
            Notification notification = new Notification(
                ticket.getAssignee().getUsername(),
                NotificationType.TICKET_INFO_PROVIDED,
                "Ticket #" + ticket.getTicketNumber() + " - User đã cung cấp thông tin",
                providedBy + " đã cung cấp thông tin cho ticket."
            );
            notification.setTicketId(ticket.getId());
            notification.setTicketNumber(ticket.getTicketNumber());
            notification.setActorUsername(providedBy);
            notificationRepository.save(notification);
        }
    }
    
    /**
     * Gửi notification khi có comment mới.
     */
    public void notifyCommentAdded(Ticket ticket, String commentBody, String commentedBy, boolean isInternal) {
        // Nếu là internal comment, chỉ notify IT staff
        if (isInternal) {
            notifyITStaff(ticket, NotificationType.TICKET_COMMENT_ADDED,
                "Comment mới trên ticket #" + ticket.getTicketNumber(),
                commentedBy + " đã thêm comment (internal): " + truncate(commentBody, 100));
        } else {
            // Notify requester
            if (ticket.getRequesterUsername() != null && !ticket.getRequesterUsername().equals(commentedBy)) {
                Notification notification = new Notification(
                    ticket.getRequesterUsername(),
                    NotificationType.TICKET_COMMENT_ADDED,
                    "Comment mới trên ticket #" + ticket.getTicketNumber(),
                    commentedBy + ": " + truncate(commentBody, 100)
                );
                notification.setTicketId(ticket.getId());
                notification.setTicketNumber(ticket.getTicketNumber());
                notification.setActorUsername(commentedBy);
                notificationRepository.save(notification);
            }
            
            // Notify assignee (nếu khác requester và commenter)
            if (ticket.getAssignee() != null && 
                !ticket.getAssignee().getUsername().equals(ticket.getRequesterUsername()) &&
                !ticket.getAssignee().getUsername().equals(commentedBy)) {
                Notification notification = new Notification(
                    ticket.getAssignee().getUsername(),
                    NotificationType.TICKET_COMMENT_ADDED,
                    "Comment mới trên ticket #" + ticket.getTicketNumber(),
                    commentedBy + ": " + truncate(commentBody, 100)
                );
                notification.setTicketId(ticket.getId());
                notification.setTicketNumber(ticket.getTicketNumber());
                notification.setActorUsername(commentedBy);
                notificationRepository.save(notification);
            }
        }
    }
    
    /**
     * Gửi notification khi SLA sắp hết hạn.
     */
    public void notifySlaWarning(Ticket ticket, String slaType) {
        // Notify assignee
        if (ticket.getAssignee() != null) {
            Notification notification = Notification.forSlaWarning(
                ticket.getAssignee().getUsername(),
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getTitle(),
                slaType
            );
            notificationRepository.save(notification);
        }
        
        // Notify IT team
        notifyITStaff(ticket, NotificationType.TICKET_SLA_WARNING,
            "Cảnh báo SLA - Ticket #" + ticket.getTicketNumber(),
            "SLA " + slaType + " sắp hết hạn cho ticket này.");
    }
    
    /**
     * Gửi notification khi SLA bị breached.
     */
    public void notifySlaBreached(Ticket ticket, String slaType) {
        // Notify assignee
        if (ticket.getAssignee() != null) {
            Notification notification = Notification.forSlaBreached(
                ticket.getAssignee().getUsername(),
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getTitle(),
                slaType
            );
            notificationRepository.save(notification);
        }
        
        // Notify IT Managers về SLA breach
        notifyITManagers(ticket, NotificationType.TICKET_SLA_BREACHED,
            "SLA VI PHẠM - Ticket #" + ticket.getTicketNumber(),
            "SLA " + slaType + " đã bị vi phạm cho ticket này. Priority: " + ticket.getPriority());
    }
    
    // ============ Private Helper Methods ============
    
    /**
     * Notify tất cả IT staff.
     */
    private void notifyITStaff(Ticket ticket, NotificationType type, String title, String message) {
        List<UserAccount> itStaff = getITStaff();
        for (UserAccount staff : itStaff) {
            // Không notify người tạo ticket (nếu là IT staff)
            if (staff.getUsername().equals(ticket.getRequesterUsername())) {
                continue;
            }
            Notification notification = new Notification(staff.getUsername(), type, title, message);
            notification.setTicketId(ticket.getId());
            notification.setTicketNumber(ticket.getTicketNumber());
            notification.setActorUsername(ticket.getRequesterUsername());
            notificationRepository.save(notification);
        }
    }
    
    /**
     * Notify IT Managers (TRUONG_PHONG IT và Admins).
     */
    private void notifyITManagers(Ticket ticket, NotificationType type, String title, String message) {
        List<UserAccount> itManagers = getITManagers();
        for (UserAccount manager : itManagers) {
            Notification notification = new Notification(manager.getUsername(), type, title, message);
            notification.setTicketId(ticket.getId());
            notification.setTicketNumber(ticket.getTicketNumber());
            notificationRepository.save(notification);
        }
    }
    
    /**
     * Notify các thành viên trong team.
     */
    private void notifyTeamMembers(Ticket ticket, NotificationType type, String title, String message) {
        // TODO: Implement khi có thông tin về team members
        // Hiện tại chỉ notify team lead
        log.info("Team member notification not implemented yet for team: {}", ticket.getTeamCode());
    }
    
    /**
     * Lấy danh sách IT staff.
     */
    private List<UserAccount> getITStaff() {
        return userAccountRepository.findAll().stream()
            .filter(u -> u.getDepartment() != null && "IT".equals(u.getDepartment().getCode()))
            .filter(UserAccount::isEnabled)
            .collect(Collectors.toList());
    }
    
    /**
     * Lấy danh sách IT Managers (TRUONG_PHONG IT và Admins).
     */
    private List<UserAccount> getITManagers() {
        return userAccountRepository.findAll().stream()
            .filter(u -> u.isAdmin() || 
                (u.getRole() == com.example.ticketing.auth.UserRole.Role.TRUONG_PHONG && 
                 u.getDepartment() != null && "IT".equals(u.getDepartment().getCode())))
            .filter(UserAccount::isEnabled)
            .collect(Collectors.toList());
    }
    
    /**
     * Cắt text nếu quá dài.
     */
    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen - 3) + "..." : text;
    }
}

package com.example.ticketing.incident;

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
import com.example.ticketing.auth.UserRole;

/**
 * Service xử lý notifications cho incident lifecycle.
 * Gửi notifications khi có thay đổi trạng thái, assignment, SLA...
 */
@Service
@Transactional
public class IncidentNotificationService {
    
    private static final Logger log = LoggerFactory.getLogger(IncidentNotificationService.class);
    
    private final NotificationRepository notificationRepository;
    private final UserAccountRepository userAccountRepository;
    
    public IncidentNotificationService(
            NotificationRepository notificationRepository,
            UserAccountRepository userAccountRepository) {
        this.notificationRepository = notificationRepository;
        this.userAccountRepository = userAccountRepository;
    }
    
    /**
     * Gửi notification khi incident mới được tạo.
     * Notify IT staff về incident mới.
     */
    public void notifyIncidentCreated(Incident incident) {
        log.info("Sending incident created notifications for: {}", incident.getIncidentNumber());
        
        // Notify IT team members
        notifyITStaff(
            incident,
            NotificationType.TICKET_CREATED,
            "Incident mới #" + incident.getIncidentNumber(),
            "Incident mới: " + incident.getTitle() + " (Priority: " + incident.getPriority() + ")"
        );
        
        // Notify IT Managers (TRUONG_PHONG IT và Admins)
        notifyITManagers(
            incident,
            NotificationType.TICKET_CREATED,
            "Incident mới cần xử lý",
            "Incident #" + incident.getIncidentNumber() + " - " + incident.getTitle()
        );
    }
    
    /**
     * Gửi notification khi incident được gán cho user.
     */
    public void notifyIncidentAssigned(Incident incident, String assignedBy) {
        if (incident.getAssignedTo() != null) {
            String assigneeUsername = incident.getAssignedTo().getUsername();
            Notification notification = Notification.forTicketAssigned(
                assigneeUsername,
                incident.getId(),
                incident.getIncidentNumber(),
                incident.getTitle(),
                assignedBy
            );
            notificationRepository.save(notification);
            log.info("Sent assignment notification to {} for incident {}", assigneeUsername, incident.getIncidentNumber());
        }
        
        // Nếu được gán cho team, notify team members
        if (incident.getTeam() != null) {
            notifyTeamMembers(incident, NotificationType.TICKET_ASSIGNED_TO_TEAM,
                "Incident mới được giao cho team",
                "Incident #" + incident.getIncidentNumber() + " - " + incident.getTitle());
        }
    }
    
    /**
     * Gửi notification khi incident được gán lại.
     */
    public void notifyIncidentReassigned(Incident incident, String oldAssignee, String newAssignee, String reassignedBy) {
        // Notify người cũ (nếu có)
        if (oldAssignee != null && !oldAssignee.equals(newAssignee)) {
            Notification notification = new Notification(
                oldAssignee,
                NotificationType.TICKET_UNASSIGNED,
                "Incident #" + incident.getIncidentNumber() + " - Bỏ gán",
                "Incident không còn được gán cho bạn nữa."
            );
            notification.setTicketId(incident.getId());
            notification.setTicketNumber(incident.getIncidentNumber());
            notification.setActorUsername(reassignedBy);
            notificationRepository.save(notification);
        }
        
        // Notify người mới
        if (newAssignee != null) {
            Notification notification = Notification.forTicketAssigned(
                newAssignee,
                incident.getId(),
                incident.getIncidentNumber(),
                incident.getTitle(),
                reassignedBy
            );
            notificationRepository.save(notification);
            log.info("Sent reassignment notification to {} for incident {}", newAssignee, incident.getIncidentNumber());
        }
    }
    
    /**
     * Gửi notification khi status incident thay đổi.
     */
    public void notifyStatusChanged(Incident incident, Incident.IncidentStatus oldStatus, 
                                   Incident.IncidentStatus newStatus, String changedBy) {
        // Notify người tạo incident (nếu khác người thay đổi)
        if (incident.getCreatedBy() != null && !incident.getCreatedBy().equals(changedBy)) {
            Notification notification = Notification.forStatusChanged(
                incident.getCreatedBy(),
                incident.getId(),
                incident.getIncidentNumber(),
                incident.getTitle(),
                oldStatus.name(),
                newStatus.name(),
                changedBy
            );
            notificationRepository.save(notification);
        }
        
        // Notify người phụ trách (nếu khác người tạo và người thay đổi)
        if (incident.getAssignedTo() != null) {
            String assigneeUsername = incident.getAssignedTo().getUsername();
            if (!assigneeUsername.equals(incident.getCreatedBy()) && !assigneeUsername.equals(changedBy)) {
                Notification notification = Notification.forStatusChanged(
                    assigneeUsername,
                    incident.getId(),
                    incident.getIncidentNumber(),
                    incident.getTitle(),
                    oldStatus.name(),
                    newStatus.name(),
                    changedBy
                );
                notificationRepository.save(notification);
            }
        }
        
        // Notify IT Managers
        notifyITManagers(incident, NotificationType.TICKET_STATUS_CHANGED,
            "Incident #" + incident.getIncidentNumber() + " - Status thay đổi",
            "Chuyển từ " + oldStatus + " sang " + newStatus + " bởi " + changedBy);
        
        log.info("Status change notification sent for incident {}: {} -> {}", 
            incident.getIncidentNumber(), oldStatus, newStatus);
    }
    
    /**
     * Gửi notification khi incident được giải quyết.
     */
    public void notifyIncidentResolved(Incident incident, String resolvedBy) {
        // Notify người tạo incident
        if (incident.getCreatedBy() != null) {
            Notification notification = Notification.forTicketResolved(
                incident.getCreatedBy(),
                incident.getId(),
                incident.getIncidentNumber(),
                incident.getTitle(),
                resolvedBy
            );
            notificationRepository.save(notification);
            log.info("Sent resolved notification to creator: {}", incident.getCreatedBy());
        }
        
        // Notify IT Managers
        notifyITManagers(incident, NotificationType.TICKET_RESOLVED,
            "Incident #" + incident.getIncidentNumber() + " đã được giải quyết",
            "Đã được giải quyết bởi " + resolvedBy);
    }
    
    /**
     * Gửi notification khi incident được đóng.
     */
    public void notifyIncidentClosed(Incident incident, String closedBy) {
        // Notify người tạo incident
        if (incident.getCreatedBy() != null) {
            Notification notification = new Notification(
                incident.getCreatedBy(),
                NotificationType.TICKET_CLOSED,
                "Incident #" + incident.getIncidentNumber() + " đã được đóng",
                "Incident đã được đóng bởi " + closedBy
            );
            notification.setTicketId(incident.getId());
            notification.setTicketNumber(incident.getIncidentNumber());
            notification.setActorUsername(closedBy);
            notificationRepository.save(notification);
        }
        
        // Notify IT Managers
        notifyITManagers(incident, NotificationType.TICKET_CLOSED,
            "Incident #" + incident.getIncidentNumber() + " đã được đóng",
            "Đã được đóng bởi " + closedBy);
    }
    
    /**
     * Gửi notification khi incident được mở lại.
     */
    public void notifyIncidentReopened(Incident incident, String reopenedBy) {
        // Notify người phụ trách
        if (incident.getAssignedTo() != null) {
            Notification notification = new Notification(
                incident.getAssignedTo().getUsername(),
                NotificationType.TICKET_REOPENED,
                "Incident #" + incident.getIncidentNumber() + " được mở lại",
                "Incident đã được mở lại bởi " + reopenedBy
            );
            notification.setTicketId(incident.getId());
            notification.setTicketNumber(incident.getIncidentNumber());
            notification.setActorUsername(reopenedBy);
            notificationRepository.save(notification);
        }
        
        // Notify người tạo (nếu khác)
        if (incident.getCreatedBy() != null && !incident.getCreatedBy().equals(reopenedBy)) {
            Notification notification = new Notification(
                incident.getCreatedBy(),
                NotificationType.TICKET_REOPENED,
                "Incident #" + incident.getIncidentNumber() + " được mở lại",
                "Incident đã được mở lại bởi " + reopenedBy
            );
            notification.setTicketId(incident.getId());
            notification.setTicketNumber(incident.getIncidentNumber());
            notification.setActorUsername(reopenedBy);
            notificationRepository.save(notification);
        }
        
        // Notify IT staff
        notifyITStaff(incident, NotificationType.TICKET_REOPENED,
            "Incident #" + incident.getIncidentNumber() + " được mở lại",
            "Incident đã được mở lại bởi " + reopenedBy);
    }
    
    /**
     * Gửi notification khi SLA sắp hết hạn.
     */
    public void notifySlaWarning(Incident incident, String slaType) {
        // Notify người phụ trách
        if (incident.getAssignedTo() != null) {
            Notification notification = Notification.forSlaWarning(
                incident.getAssignedTo().getUsername(),
                incident.getId(),
                incident.getIncidentNumber(),
                incident.getTitle(),
                slaType
            );
            notificationRepository.save(notification);
        }
        
        // Notify IT team
        notifyITStaff(incident, NotificationType.TICKET_SLA_WARNING,
            "Cảnh báo SLA - Incident #" + incident.getIncidentNumber(),
            "SLA " + slaType + " sắp hết hạn cho incident này.");
        
        // Notify IT Managers
        notifyITManagers(incident, NotificationType.TICKET_SLA_WARNING,
            "Cảnh báo SLA - Incident #" + incident.getIncidentNumber(),
            "SLA " + slaType + " sắp hết hạn. Priority: " + incident.getPriority());
    }
    
    /**
     * Gửi notification khi SLA bị breached.
     */
    public void notifySlaBreached(Incident incident, String slaType) {
        // Notify người phụ trách
        if (incident.getAssignedTo() != null) {
            Notification notification = Notification.forSlaBreached(
                incident.getAssignedTo().getUsername(),
                incident.getId(),
                incident.getIncidentNumber(),
                incident.getTitle(),
                slaType
            );
            notificationRepository.save(notification);
        }
        
        // Notify IT Managers về SLA breach
        notifyITManagers(incident, NotificationType.TICKET_SLA_BREACHED,
            "SLA VI PHẠM - Incident #" + incident.getIncidentNumber(),
            "SLA " + slaType + " đã bị vi phạm! Priority: " + incident.getPriority());
        
        log.error("SLA BREACH notification sent for incident: {}", incident.getIncidentNumber());
    }
    
    // ============ Private Helper Methods ============
    
    /**
     * Notify tất cả IT staff.
     */
    private void notifyITStaff(Incident incident, NotificationType type, String title, String message) {
        List<UserAccount> itStaff = getITStaff();
        for (UserAccount staff : itStaff) {
            // Không notify người tạo incident (nếu là IT staff)
            if (staff.getUsername().equals(incident.getCreatedBy())) {
                continue;
            }
            // Không notify người phụ trách (nếu có, vì họ đã được notify riêng)
            if (incident.getAssignedTo() != null && 
                staff.getUsername().equals(incident.getAssignedTo().getUsername())) {
                continue;
            }
            Notification notification = new Notification(staff.getUsername(), type, title, message);
            notification.setTicketId(incident.getId());
            notification.setTicketNumber(incident.getIncidentNumber());
            notification.setActorUsername(incident.getCreatedBy());
            notificationRepository.save(notification);
        }
    }
    
    /**
     * Notify IT Managers (TRUONG_PHONG IT và Admins).
     */
    private void notifyITManagers(Incident incident, NotificationType type, String title, String message) {
        List<UserAccount> itManagers = getITManagers();
        for (UserAccount manager : itManagers) {
            // Không notify người tạo (nếu là manager)
            if (manager.getUsername().equals(incident.getCreatedBy())) {
                continue;
            }
            // Không notify người phụ trách (nếu có)
            if (incident.getAssignedTo() != null && 
                manager.getUsername().equals(incident.getAssignedTo().getUsername())) {
                continue;
            }
            Notification notification = new Notification(manager.getUsername(), type, title, message);
            notification.setTicketId(incident.getId());
            notification.setTicketNumber(incident.getIncidentNumber());
            notificationRepository.save(notification);
        }
    }
    
    /**
     * Notify các thành viên trong team.
     */
    private void notifyTeamMembers(Incident incident, NotificationType type, String title, String message) {
        if (incident.getTeam() == null) {
            return;
        }
        // TODO: Implement khi có thông tin về team members
        log.info("Team member notification for incident: {} - Team: {}", 
            incident.getIncidentNumber(), incident.getTeam().getName());
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
                (u.getRole() == UserRole.Role.TRUONG_PHONG && 
                 u.getDepartment() != null && "IT".equals(u.getDepartment().getCode())))
            .filter(UserAccount::isEnabled)
            .collect(Collectors.toList());
    }
}

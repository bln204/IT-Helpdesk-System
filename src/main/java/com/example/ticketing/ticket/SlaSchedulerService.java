package com.example.ticketing.ticket;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.auth.Notification;
import com.example.ticketing.auth.NotificationPreferences;
import com.example.ticketing.auth.NotificationPreferencesRepository;
import com.example.ticketing.auth.UserAccount;

/**
 * Scheduler kiểm tra và gửi SLA alerts.
 * Chạy định kỳ để check tickets có SLA warning/breach.
 */
@Service
public class SlaSchedulerService {
    
    private static final Logger log = LoggerFactory.getLogger(SlaSchedulerService.class);
    
    // Warning threshold: gửi warning khi còn 25% thời gian SLA
    private static final double WARNING_THRESHOLD = 0.75; // 75% of time used
    
    private final TicketRepository ticketRepository;
    private final SlaAlertLogRepository slaAlertLogRepository;
    private final NotificationPreferencesRepository preferencesRepository;
    private final TicketNotificationService notificationService;
    
    public SlaSchedulerService(
            TicketRepository ticketRepository,
            SlaAlertLogRepository slaAlertLogRepository,
            NotificationPreferencesRepository preferencesRepository,
            TicketNotificationService notificationService) {
        this.ticketRepository = ticketRepository;
        this.slaAlertLogRepository = slaAlertLogRepository;
        this.preferencesRepository = preferencesRepository;
        this.notificationService = notificationService;
    }
    
    /**
     * Kiểm tra SLA mỗi 15 phút.
     */
    @Scheduled(fixedRate = 900000) // 15 minutes
    @Transactional
    public void checkSlaCompliance() {
        log.info("Starting SLA compliance check...");
        
        int warningCount = 0;
        int breachCount = 0;
        
        // Lấy tất cả open tickets (paginated để tránh OOM)
        Page<Ticket> openTickets = ticketRepository.findOpenTicketsForSlaCheck(PageRequest.of(0, 100));
        
        for (Ticket ticket : openTickets) {
            // Check Response SLA
            boolean responseWarning = checkResponseSla(ticket);
            boolean responseBreach = checkResponseSlaBreach(ticket);
            if (responseWarning) warningCount++;
            if (responseBreach) breachCount++;
            
            // Check Resolution SLA
            boolean resolutionWarning = checkResolutionSla(ticket);
            boolean resolutionBreach = checkResolutionSlaBreach(ticket);
            if (resolutionWarning) warningCount++;
            if (resolutionBreach) breachCount++;
        }
        
        log.info("SLA check completed. Warnings: {}, Breaches: {}", warningCount, breachCount);
    }
    
    /**
     * Kiểm tra Response SLA warning.
     */
    private boolean checkResponseSla(Ticket ticket) {
        if (ticket.getSlaResponseAt() == null) return false;
        if (ticket.getFirstResponseAt() != null) return false; // Đã response
        
        LocalDateTime now = LocalDateTime.now();
        long totalHours = Duration.between(ticket.getCreatedAt(), ticket.getSlaResponseAt()).toHours();
        long elapsedHours = Duration.between(ticket.getCreatedAt(), now).toHours();
        
        // Kiểm tra đã qua threshold chưa
        if (elapsedHours >= totalHours * WARNING_THRESHOLD) {
            // Kiểm tra đã gửi warning chưa
            if (!slaAlertLogRepository.existsByTicketIdAndSlaTypeAndAlertType(
                    ticket.getId(), "RESPONSE", "WARNING")) {
                sendSlaWarning(ticket, "RESPONSE", totalHours, elapsedHours);
                return true;
            }
        }
        return false;
    }
    
    /**
     * Kiểm tra Response SLA breach.
     */
    private boolean checkResponseSlaBreach(Ticket ticket) {
        if (ticket.getSlaResponseAt() == null) return false;
        if (ticket.getFirstResponseAt() != null) return false; // Đã response
        
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(ticket.getSlaResponseAt())) {
            // Kiểm tra đã gửi breach alert chưa
            if (!slaAlertLogRepository.existsByTicketIdAndSlaTypeAndAlertType(
                    ticket.getId(), "RESPONSE", "BREACHED")) {
                sendSlaBreach(ticket, "RESPONSE");
                return true;
            }
        }
        return false;
    }
    
    /**
     * Kiểm tra Resolution SLA warning.
     */
    private boolean checkResolutionSla(Ticket ticket) {
        if (ticket.getSlaResolutionAt() == null) return false;
        if (ticket.isResolutionSLABreached()) return false; // Đã breach
        
        LocalDateTime now = LocalDateTime.now();
        long totalHours = Duration.between(ticket.getCreatedAt(), ticket.getSlaResolutionAt()).toHours();
        long elapsedHours = Duration.between(ticket.getCreatedAt(), now).toHours();
        
        if (elapsedHours >= totalHours * WARNING_THRESHOLD) {
            if (!slaAlertLogRepository.existsByTicketIdAndSlaTypeAndAlertType(
                    ticket.getId(), "RESOLUTION", "WARNING")) {
                sendSlaWarning(ticket, "RESOLUTION", totalHours, elapsedHours);
                return true;
            }
        }
        return false;
    }
    
    /**
     * Kiểm tra Resolution SLA breach.
     */
    private boolean checkResolutionSlaBreach(Ticket ticket) {
        if (ticket.getSlaResolutionAt() == null) return false;
        if (ticket.isResolutionSLABreached()) return false; // Đã breach
        
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(ticket.getSlaResolutionAt())) {
            if (!slaAlertLogRepository.existsByTicketIdAndSlaTypeAndAlertType(
                    ticket.getId(), "RESOLUTION", "BREACHED")) {
                sendSlaBreach(ticket, "RESOLUTION");
                return true;
            }
        }
        return false;
    }
    
    /**
     * Gửi SLA warning.
     */
    private void sendSlaWarning(Ticket ticket, String slaType, long totalHours, long elapsedHours) {
        log.warn("SLA Warning - Ticket {} - {} SLA: {}/{} hours", 
            ticket.getTicketNumber(), slaType, elapsedHours, totalHours);
        
        // Log alert
        SlaAlertLog alert = new SlaAlertLog();
        alert.setTicketId(ticket.getId());
        alert.setSlaType(slaType);
        alert.setAlertType("WARNING");
        alert.setThresholdHours((int) totalHours);
        alert.setActualHours((int) elapsedHours);
        alert.setNotified(true);
        slaAlertLogRepository.save(alert);
        
        // Gửi notification
        notificationService.notifySlaWarning(ticket, slaType);
    }
    
    /**
     * Gửi SLA breach.
     */
    private void sendSlaBreach(Ticket ticket, String slaType) {
        log.error("SLA BREACH - Ticket {} - {} SLA", ticket.getTicketNumber(), slaType);
        
        // Log alert
        SlaAlertLog alert = new SlaAlertLog();
        alert.setTicketId(ticket.getId());
        alert.setSlaType(slaType);
        alert.setAlertType("BREACHED");
        alert.setThresholdHours(0);
        alert.setActualHours(0);
        alert.setNotified(true);
        slaAlertLogRepository.save(alert);
        
        // Gửi notification
        notificationService.notifySlaBreached(ticket, slaType);
    }
    
    /**
     * Kiểm tra thủ công SLA cho một ticket.
     */
    @Transactional(readOnly = true)
    public SlaStatus checkTicketSla(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new TicketNotFoundException(ticketId));
        
        return buildSlaStatus(ticket);
    }
    
    /**
     * Build SLA status cho ticket.
     */
    public SlaStatus buildSlaStatus(Ticket ticket) {
        SlaStatus status = new SlaStatus();
        status.setTicketId(ticket.getId());
        status.setTicketNumber(ticket.getTicketNumber());
        
        LocalDateTime now = LocalDateTime.now();
        
        // Response SLA
        if (ticket.getSlaResponseAt() != null) {
            status.setResponseSlaTarget(ticket.getSlaResponseAt());
            status.setResponseHours(TicketTypes.SLAPriority.fromTicketPriority(ticket.getPriority()).getResponseHours());
            
            if (ticket.getFirstResponseAt() != null) {
                status.setFirstResponseAt(ticket.getFirstResponseAt());
                status.setResponseMet(true);
                status.setResponseHoursUsed(
                    (int) Duration.between(ticket.getCreatedAt(), ticket.getFirstResponseAt()).toHours());
            } else {
                status.setResponseMet(null);
                status.setResponseHoursUsed((int) Duration.between(ticket.getCreatedAt(), now).toHours());
                status.setResponseBreached(now.isAfter(ticket.getSlaResponseAt()));
            }
        }
        
        // Resolution SLA
        if (ticket.getSlaResolutionAt() != null) {
            status.setResolutionSlaTarget(ticket.getSlaResolutionAt());
            status.setResolutionHours(TicketTypes.SLAPriority.fromTicketPriority(ticket.getPriority()).getResolutionHours());
            
            if (ticket.getResolvedAt() != null) {
                status.setResolvedAt(ticket.getResolvedAt());
                status.setResolutionMet(true);
                status.setResolutionHoursUsed(
                    (int) Duration.between(ticket.getCreatedAt(), ticket.getResolvedAt()).toHours());
            } else {
                status.setResolutionMet(null);
                status.setResolutionHoursUsed((int) Duration.between(ticket.getCreatedAt(), now).toHours());
                status.setResolutionBreached(ticket.isResolutionSLABreached());
            }
        }
        
        return status;
    }
    
    /**
     * SLA Status DTO.
     */
    public static class SlaStatus {
        private Long ticketId;
        private String ticketNumber;
        private LocalDateTime responseSlaTarget;
        private Integer responseHours;
        private LocalDateTime firstResponseAt;
        private Integer responseHoursUsed;
        private Boolean responseMet;
        private Boolean responseBreached;
        private LocalDateTime resolutionSlaTarget;
        private Integer resolutionHours;
        private LocalDateTime resolvedAt;
        private Integer resolutionHoursUsed;
        private Boolean resolutionMet;
        private Boolean resolutionBreached;
        
        // Getters & Setters
        public Long getTicketId() { return ticketId; }
        public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
        public String getTicketNumber() { return ticketNumber; }
        public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }
        public LocalDateTime getResponseSlaTarget() { return responseSlaTarget; }
        public void setResponseSlaTarget(LocalDateTime responseSlaTarget) { this.responseSlaTarget = responseSlaTarget; }
        public Integer getResponseHours() { return responseHours; }
        public void setResponseHours(Integer responseHours) { this.responseHours = responseHours; }
        public LocalDateTime getFirstResponseAt() { return firstResponseAt; }
        public void setFirstResponseAt(LocalDateTime firstResponseAt) { this.firstResponseAt = firstResponseAt; }
        public Integer getResponseHoursUsed() { return responseHoursUsed; }
        public void setResponseHoursUsed(Integer responseHoursUsed) { this.responseHoursUsed = responseHoursUsed; }
        public Boolean getResponseMet() { return responseMet; }
        public void setResponseMet(Boolean responseMet) { this.responseMet = responseMet; }
        public Boolean getResponseBreached() { return responseBreached; }
        public void setResponseBreached(Boolean responseBreached) { this.responseBreached = responseBreached; }
        public LocalDateTime getResolutionSlaTarget() { return resolutionSlaTarget; }
        public void setResolutionSlaTarget(LocalDateTime resolutionSlaTarget) { this.resolutionSlaTarget = resolutionSlaTarget; }
        public Integer getResolutionHours() { return resolutionHours; }
        public void setResolutionHours(Integer resolutionHours) { this.resolutionHours = resolutionHours; }
        public LocalDateTime getResolvedAt() { return resolvedAt; }
        public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
        public Integer getResolutionHoursUsed() { return resolutionHoursUsed; }
        public void setResolutionHoursUsed(Integer resolutionHoursUsed) { this.resolutionHoursUsed = resolutionHoursUsed; }
        public Boolean getResolutionMet() { return resolutionMet; }
        public void setResolutionMet(Boolean resolutionMet) { this.resolutionMet = resolutionMet; }
        public Boolean getResolutionBreached() { return resolutionBreached; }
        public void setResolutionBreached(Boolean resolutionBreached) { this.resolutionBreached = resolutionBreached; }
    }
}

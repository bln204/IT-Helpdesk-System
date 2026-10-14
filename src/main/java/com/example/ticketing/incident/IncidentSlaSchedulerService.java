package com.example.ticketing.incident;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * Scheduler kiểm tra SLA cho incidents.
 * Chạy định kỳ để check incidents có SLA warning/breach.
 */
@Service
public class IncidentSlaSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(IncidentSlaSchedulerService.class);

    private final IncidentRepository incidentRepository;
    private final IncidentNotificationService incidentNotificationService;

    public IncidentSlaSchedulerService(
            IncidentRepository incidentRepository,
            IncidentNotificationService incidentNotificationService) {
        this.incidentRepository = incidentRepository;
        this.incidentNotificationService = incidentNotificationService;
    }

    /**
     * Kiểm tra SLA incidents mỗi 15 phút.
     */
    @Scheduled(fixedRate = 900000) // 15 minutes
    @Transactional
    public void checkIncidentSlaCompliance() {
        log.info("Starting Incident SLA compliance check...");

        int warningCount = 0;
        int breachCount = 0;

        try {
            // Check Response SLA
            List<Incident> responseCheckIncidents = incidentRepository.findActiveIncidentsForSlaCheck();
            for (Incident incident : responseCheckIncidents) {
                if (checkResponseSlaWarning(incident))
                    warningCount++;
                if (checkResponseSlaBreach(incident))
                    breachCount++;
            }

            // Check Resolution SLA
            List<Incident> resolutionCheckIncidents = incidentRepository.findActiveIncidentsForResolutionSlaCheck();
            for (Incident incident : resolutionCheckIncidents) {
                if (checkResolutionSlaWarning(incident))
                    warningCount++;
                if (checkResolutionSlaBreach(incident))
                    breachCount++;
            }
        } catch (Exception e) {
            log.error("Error during Incident SLA check: {}", e.getMessage(), e);
        }

        log.info("Incident SLA check completed. Warnings: {}, Breaches: {}", warningCount, breachCount);
    }

    /**
     * Kiểm tra Response SLA warning.
     */
    private boolean checkResponseSlaWarning(Incident incident) {
        if (incident.getResponseDeadline() == null)
            return false;
        if (Boolean.TRUE.equals(incident.getResponseSlaBreached()))
            return false;

        LocalDateTime now = LocalDateTime.now();
        // Warning khi còn 25% thời gian hoặc dưới 1 giờ
        long totalMinutes = java.time.Duration.between(incident.getCreatedAt(), incident.getResponseDeadline())
                .toMinutes();
        long remainingMinutes = java.time.Duration.between(now, incident.getResponseDeadline()).toMinutes();

        if (totalMinutes <= 0)
            return false;

        // Warning nếu còn dưới 25% thời gian hoặc dưới 60 phút
        double warningThreshold = 0.25;
        if (remainingMinutes <= 0)
            return false;
        if (remainingMinutes <= 60 || remainingMinutes <= totalMinutes * warningThreshold) {
            // Check đã gửi warning chưa bằng cách xem incident có đang trong trạng thái
            // warning không
            log.warn("Response SLA Warning - Incident {} - {} minutes remaining",
                    incident.getIncidentNumber(), remainingMinutes);
            incidentNotificationService.notifySlaWarning(incident, "RESPONSE");
            return true;
        }
        return false;
    }

    /**
     * Kiểm tra Response SLA breach.
     */
    private boolean checkResponseSlaBreach(Incident incident) {
        if (incident.getResponseDeadline() == null)
            return false;
        if (Boolean.TRUE.equals(incident.getResponseSlaBreached()))
            return false;

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(incident.getResponseDeadline())) {
            log.error("Response SLA BREACH - Incident {}", incident.getIncidentNumber());
            // Đánh dấu là breached
            incident.setResponseSlaBreached(true);
            incidentRepository.save(incident);
            incidentNotificationService.notifySlaBreached(incident, "RESPONSE");
            return true;
        }
        return false;
    }

    /**
     * Kiểm tra Resolution SLA warning.
     */
    private boolean checkResolutionSlaWarning(Incident incident) {
        if (incident.getResolutionDeadline() == null)
            return false;
        if (Boolean.TRUE.equals(incident.getResolutionSlaBreached()))
            return false;

        LocalDateTime now = LocalDateTime.now();
        // Warning khi còn 25% thời gian hoặc dưới 1 giờ
        long totalMinutes = java.time.Duration.between(incident.getCreatedAt(), incident.getResolutionDeadline())
                .toMinutes();
        long remainingMinutes = java.time.Duration.between(now, incident.getResolutionDeadline()).toMinutes();

        if (totalMinutes <= 0)
            return false;

        // Warning nếu còn dưới 25% thời gian hoặc dưới 60 phút
        double warningThreshold = 0.25;
        if (remainingMinutes <= 0)
            return false;
        if (remainingMinutes <= 60 || remainingMinutes <= totalMinutes * warningThreshold) {
            log.warn("Resolution SLA Warning - Incident {} - {} minutes remaining",
                    incident.getIncidentNumber(), remainingMinutes);
            incidentNotificationService.notifySlaWarning(incident, "RESOLUTION");
            return true;
        }
        return false;
    }

    /**
     * Kiểm tra Resolution SLA breach.
     */
    private boolean checkResolutionSlaBreach(Incident incident) {
        if (incident.getResolutionDeadline() == null)
            return false;
        if (Boolean.TRUE.equals(incident.getResolutionSlaBreached()))
            return false;

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(incident.getResolutionDeadline())) {
            log.error("Resolution SLA BREACH - Incident {}", incident.getIncidentNumber());
            // Đánh dấu là breached
            incident.setResolutionSlaBreached(true);
            incidentRepository.save(incident);
            incidentNotificationService.notifySlaBreached(incident, "RESOLUTION");
            return true;
        }
        return false;
    }

}

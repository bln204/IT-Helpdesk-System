package com.example.ticketing.ticket;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Entity log SLA alerts đã được gửi.
 */
@Entity
@Table(name = "sla_alert_log")
public class SlaAlertLog {
    
    public enum SlaType {
        RESPONSE,
        RESOLUTION
    }
    
    public enum AlertType {
        WARNING,
        BREACHED
    }
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;
    
    @Column(name = "sla_type", nullable = false, length = 20)
    private String slaType;
    
    @Column(name = "alert_type", nullable = false, length = 20)
    private String alertType;
    
    @Column(name = "threshold_hours", nullable = false)
    private Integer thresholdHours;
    
    @Column(name = "actual_hours", nullable = false)
    private Integer actualHours;
    
    @Column(nullable = false)
    private boolean notified = false;
    
    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    // ============ Getters & Setters ============
    
    public Long getId() { return id; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    
    public String getSlaType() { return slaType; }
    public void setSlaType(String slaType) { this.slaType = slaType; }
    
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    
    public Integer getThresholdHours() { return thresholdHours; }
    public void setThresholdHours(Integer thresholdHours) { this.thresholdHours = thresholdHours; }
    
    public Integer getActualHours() { return actualHours; }
    public void setActualHours(Integer actualHours) { this.actualHours = actualHours; }
    
    public boolean isNotified() { return notified; }
    public void setNotified(boolean notified) { 
        this.notified = notified;
        if (notified && this.notifiedAt == null) {
            this.notifiedAt = LocalDateTime.now();
        }
    }
    
    public LocalDateTime getNotifiedAt() { return notifiedAt; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
}

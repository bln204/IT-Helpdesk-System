package com.example.ticketing.team;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.department.Department;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Entity đại diện cho một nhóm IT (Team).
 * Ví dụ: Network Team, Hardware Team, Software Team, Security Team
 */
@Entity
@Table(name = "teams")
public class Team {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 120)
    private String name;
    
    @Column(nullable = false, unique = true, length = 20)
    private String code;
    
    @Column(length = 500)
    private String description;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    @JsonIgnoreProperties({"manager", "description", "enabled", "createdAt", "updatedAt"})
    private Department department;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    @JsonIgnoreProperties({"passwordHash", "department", "enabled", "approved", "authorities"})
    private UserAccount lead;
    
    @Column(nullable = false)
    private boolean enabled = true;
    
    @Column(name = "display_order")
    private Integer displayOrder = 0;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // ============ Round-robin tracking ============
    
    /**
     * User cuối cùng được assign trong round-robin.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_assigned_user_id")
    @JsonIgnoreProperties({"passwordHash", "department", "enabled", "approved", "authorities"})
    private UserAccount lastAssignedUser;
    
    /**
     * Thời điểm assign cuối cùng.
     */
    @Column(name = "last_assigned_at")
    private LocalDateTime lastAssignedAt;
    
    // ============ Lifecycle Callbacks ============
    
    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }
    
    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // ============ Getters & Setters ============
    
    public Long getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Department getDepartment() {
        return department;
    }
    
    public void setDepartment(Department department) {
        this.department = department;
    }
    
    public Long getDepartmentId() {
        return department != null ? department.getId() : null;
    }
    
    public String getDepartmentCode() {
        return department != null ? department.getCode() : null;
    }
    
    public UserAccount getLead() {
        return lead;
    }
    
    public void setLead(UserAccount lead) {
        this.lead = lead;
    }
    
    public Long getLeadId() {
        return lead != null ? lead.getId() : null;
    }
    
    public String getLeadName() {
        return lead != null ? lead.getDisplayName() : null;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public Integer getDisplayOrder() {
        return displayOrder;
    }
    
    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    // ============ Round-robin Getters & Setters ============
    
    public UserAccount getLastAssignedUser() {
        return lastAssignedUser;
    }
    
    public void setLastAssignedUser(UserAccount lastAssignedUser) {
        this.lastAssignedUser = lastAssignedUser;
    }
    
    public Long getLastAssignedUserId() {
        return lastAssignedUser != null ? lastAssignedUser.getId() : null;
    }
    
    public LocalDateTime getLastAssignedAt() {
        return lastAssignedAt;
    }
    
    public void setLastAssignedAt(LocalDateTime lastAssignedAt) {
        this.lastAssignedAt = lastAssignedAt;
    }
    
    // ============ DTO for API Response ============
    
    public static class TeamResponse {
        private Long id;
        private String name;
        private String code;
        private String description;
        private Long departmentId;
        private String departmentCode;
        private Long leadId;
        private String leadName;
        private boolean enabled;
        private Integer displayOrder;
        private LocalDateTime createdAt;
        private Long lastAssignedUserId;
        private String lastAssignedUserName;
        private LocalDateTime lastAssignedAt;
        private Integer memberCount;
        
        public TeamResponse() {}
        
        public TeamResponse(Team team) {
            this.id = team.getId();
            this.name = team.getName();
            this.code = team.getCode();
            this.description = team.getDescription();
            this.departmentId = team.getDepartmentId();
            this.departmentCode = team.getDepartmentCode();
            this.leadId = team.getLeadId();
            this.leadName = team.getLeadName();
            this.enabled = team.isEnabled();
            this.displayOrder = team.getDisplayOrder();
            this.createdAt = team.getCreatedAt();
            this.lastAssignedUserId = team.getLastAssignedUserId();
            this.lastAssignedUserName = team.getLastAssignedUser() != null ? team.getLastAssignedUser().getDisplayName() : null;
            this.lastAssignedAt = team.getLastAssignedAt();
        }
        
        // Getters
        public Long getId() { return id; }
        public String getName() { return name; }
        public String getCode() { return code; }
        public String getDescription() { return description; }
        public Long getDepartmentId() { return departmentId; }
        public String getDepartmentCode() { return departmentCode; }
        public Long getLeadId() { return leadId; }
        public String getLeadName() { return leadName; }
        public boolean isEnabled() { return enabled; }
        public Integer getDisplayOrder() { return displayOrder; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public Long getLastAssignedUserId() { return lastAssignedUserId; }
        public String getLastAssignedUserName() { return lastAssignedUserName; }
        public LocalDateTime getLastAssignedAt() { return lastAssignedAt; }
        public Integer getMemberCount() { return memberCount; }
        
        // Setters
        public void setId(Long id) { this.id = id; }
        public void setName(String name) { this.name = name; }
        public void setCode(String code) { this.code = code; }
        public void setDescription(String description) { this.description = description; }
        public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
        public void setDepartmentCode(String departmentCode) { this.departmentCode = departmentCode; }
        public void setLeadId(Long leadId) { this.leadId = leadId; }
        public void setLeadName(String leadName) { this.leadName = leadName; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public void setLastAssignedUserId(Long lastAssignedUserId) { this.lastAssignedUserId = lastAssignedUserId; }
        public void setLastAssignedUserName(String lastAssignedUserName) { this.lastAssignedUserName = lastAssignedUserName; }
        public void setLastAssignedAt(LocalDateTime lastAssignedAt) { this.lastAssignedAt = lastAssignedAt; }
        public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }
    }
}

package com.example.ticketing.ticket;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import com.example.ticketing.category.Category;
import com.example.ticketing.department.Department;
import com.example.ticketing.team.Team;

@Entity
@Table(name = "tickets")
public class Ticket {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_number", nullable = false, unique = true, length = 32)
    private String ticketNumber;
    
    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TicketTypes.TicketPriority priority;
    
    // ============ NEW: Category với Category entity (FK) ============
    // Đổi tên thành categoryEntity để tránh conflict với legacy setCategory(enum)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category categoryEntity;
    
    // ============ NEW: Subcategory (FK) ============
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategory_id")
    private Category subcategoryEntity;
    
    // ============ Legacy: category enum - giữ lại để tương thích ngược ============
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TicketTypes.TicketCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)  // Tăng từ 16 lên 24 cho new statuses
    private TicketTypes.TicketStatus status;

    @Column(name = "requester_name", nullable = false, length = 120)
    private String requesterName;

    @Column(name = "requester_username", nullable = false, length = 80)
    private String requesterUsername;

    @Column(name = "requester_email", length = 160)
    private String requesterEmail;

    // ============ Legacy: assignee_name - giữ lại để tương thích ngược ============
    @Column(name = "assignee_name", length = 120)
    private String assigneeName;
    
    // ============ NEW: assignee với UserAccount entity (FK) ============
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private UserAccount assignee;
    
    // ============ NEW: team_id cho multi-level assignment ============
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;
    
    // ============ NEW: SLA Fields ============
    
    /**
     * Thời hạn phản hồi SLA (First Response Time).
     */
    @Column(name = "sla_response_at")
    private LocalDateTime slaResponseAt;
    
    /**
     * Thời hạn giải quyết SLA (Resolution Time).
     */
    @Column(name = "sla_resolution_at")
    private LocalDateTime slaResolutionAt;
    
    /**
     * Thời điểm IT phản hồi lần đầu.
     */
    @Column(name = "first_response_at")
    private LocalDateTime firstResponseAt;
    
    /**
     * Số lần ticket được escalate.
     */
    @Column(name = "escalation_count")
    private Integer escalationCount = 0;
    
    /**
     * Số lần ticket được reopen.
     */
    @Column(name = "reopen_count")
    private Integer reopenCount = 0;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) {
            status = TicketTypes.TicketStatus.NEW;
        }
        if (escalationCount == null) {
            escalationCount = 0;
        }
        if (reopenCount == null) {
            reopenCount = 0;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // ============ Helper Methods ============
    
    /**
     * Kiểm tra SLA phản hồi có bị breached không.
     */
    public boolean isResponseSLABreached() {
        if (slaResponseAt == null) return false;
        return LocalDateTime.now().isAfter(slaResponseAt) && firstResponseAt == null;
    }
    
    /**
     * Kiểm tra SLA giải quyết có bị breached không.
     */
    public boolean isResolutionSLABreached() {
        if (slaResolutionAt == null) return false;
        return LocalDateTime.now().isAfter(slaResolutionAt) 
            && status != TicketTypes.TicketStatus.CLOSED 
            && status != TicketTypes.TicketStatus.RESOLVED;
    }
    
    /**
     * Tăng escalation count.
     */
    public void incrementEscalation() {
        if (escalationCount == null) escalationCount = 0;
        escalationCount++;
    }
    
    /**
     * Tăng reopen count.
     */
    public void incrementReopen() {
        if (reopenCount == null) reopenCount = 0;
        reopenCount++;
    }

    // ==================== Getters & Setters ====================

    public Long getId() {
        return id;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public void setTicketNumber(String ticketNumber) {
        this.ticketNumber = ticketNumber;
    }

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

    public TicketTypes.TicketPriority getPriority() {
        return priority;
    }

    public void setPriority(TicketTypes.TicketPriority priority) {
        this.priority = priority;
    }

    // ============ Legacy Category Getters & Setters ============
    
    /**
     * Get legacy category enum.
     */
    public TicketTypes.TicketCategory getCategory() {
        return category;
    }

    /**
     * Set legacy category enum.
     */
    public void setCategory(TicketTypes.TicketCategory category) {
        this.category = category;
    }
    
    /**
     * Get category - alias cho getCategory() để tương thích.
     */
    public TicketTypes.TicketCategory getCategoryEnum() {
        return category;
    }

    // ============ NEW: Category Entity Getters & Setters ============
    
    public Category getCategoryEntity() {
        return categoryEntity;
    }

    public void setCategoryEntity(Category categoryEntity) {
        this.categoryEntity = categoryEntity;
    }
    
    /**
     * Get category - trả về Category entity nếu có.
     */
    public Category getCategoryEntityDirect() {
        return categoryEntity;
    }
    
    public Long getCategoryId() {
        return categoryEntity != null ? categoryEntity.getId() : null;
    }
    
    public String getCategoryCode() {
        return categoryEntity != null ? categoryEntity.getCode() : null;
    }
    
    public String getCategoryName() {
        return categoryEntity != null ? categoryEntity.getName() : null;
    }

    // ============ Subcategory Getters & Setters ============

    public Category getSubcategoryEntity() {
        return subcategoryEntity;
    }

    public void setSubcategoryEntity(Category subcategoryEntity) {
        this.subcategoryEntity = subcategoryEntity;
    }
    
    /**
     * Get subcategory - alias cho getSubcategoryEntity().
     */
    public Category getSubcategory() {
        return subcategoryEntity;
    }

    public void setSubcategory(Category subcategory) {
        this.subcategoryEntity = subcategory;
    }
    
    public Long getSubcategoryId() {
        return subcategoryEntity != null ? subcategoryEntity.getId() : null;
    }
    
    public String getSubcategoryCode() {
        return subcategoryEntity != null ? subcategoryEntity.getCode() : null;
    }
    
    public String getSubcategoryName() {
        return subcategoryEntity != null ? subcategoryEntity.getName() : null;
    }

    public TicketTypes.TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketTypes.TicketStatus status) {
        this.status = status;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public void setRequesterName(String requesterName) {
        this.requesterName = requesterName;
    }

    public String getRequesterUsername() {
        return requesterUsername;
    }

    public void setRequesterUsername(String requesterUsername) {
        this.requesterUsername = requesterUsername;
    }

    public String getRequesterEmail() {
        return requesterEmail;
    }

    public void setRequesterEmail(String requesterEmail) {
        this.requesterEmail = requesterEmail;
    }

    // ============ Legacy Assignee Getters & Setters ============

    public String getAssigneeName() {
        return assigneeName;
    }

    public void setAssigneeName(String assigneeName) {
        this.assigneeName = assigneeName;
    }
    
    // ============ NEW: Assignee Entity Getters & Setters ============
    
    public UserAccount getAssignee() {
        return assignee;
    }
    
    public void setAssignee(UserAccount assignee) {
        this.assignee = assignee;
    }
    
    public Long getAssigneeId() {
        return assignee != null ? assignee.getId() : null;
    }
    
    public String getAssigneeUsername() {
        return assignee != null ? assignee.getUsername() : null;
    }
    
    public String getAssigneeEmail() {
        return assignee != null ? assignee.getEmail() : null;
    }
    
    // ============ Team Getters & Setters ============
    
    public Team getTeam() {
        return team;
    }
    
    public void setTeam(Team team) {
        this.team = team;
    }
    
    public Long getTeamId() {
        return team != null ? team.getId() : null;
    }
    
    public String getTeamCode() {
        return team != null ? team.getCode() : null;
    }
    
    public String getTeamName() {
        return team != null ? team.getName() : null;
    }

    // ============ Department Getters & Setters ============

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

    // ============ Timestamps Getters & Setters ============

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }
    
    // ============ SLA Getters & Setters ============
    
    public LocalDateTime getSlaResponseAt() {
        return slaResponseAt;
    }
    
    public void setSlaResponseAt(LocalDateTime slaResponseAt) {
        this.slaResponseAt = slaResponseAt;
    }
    
    public LocalDateTime getSlaResolutionAt() {
        return slaResolutionAt;
    }
    
    public void setSlaResolutionAt(LocalDateTime slaResolutionAt) {
        this.slaResolutionAt = slaResolutionAt;
    }
    
    public LocalDateTime getFirstResponseAt() {
        return firstResponseAt;
    }
    
    public void setFirstResponseAt(LocalDateTime firstResponseAt) {
        this.firstResponseAt = firstResponseAt;
    }
    
    public Integer getEscalationCount() {
        return escalationCount;
    }
    
    public void setEscalationCount(Integer escalationCount) {
        this.escalationCount = escalationCount;
    }
    
    public Integer getReopenCount() {
        return reopenCount;
    }
    
    public void setReopenCount(Integer reopenCount) {
        this.reopenCount = reopenCount;
    }
}

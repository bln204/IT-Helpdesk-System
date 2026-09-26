package com.example.ticketing.category;

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

import com.example.ticketing.team.Team;

/**
 * Entity mapping category với team để auto-assign ticket.
 * Một category có thể map với nhiều teams (sẽ chọn team có priority cao nhất).
 */
@Entity
@Table(name = "category_team_mapping")
public class CategoryTeamMapping {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;
    
    /**
     * Độ ưu tiên - team nào có priority cao hơn sẽ được chọn.
     * Priority cao nhất cho mapping cụ thể nhất (VD: Wi-Fi subcategory).
     */
    @Column(nullable = false)
    private Integer priority = 0;
    
    @Column(nullable = false)
    private boolean enabled = true;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
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
    
    public Category getCategory() {
        return category;
    }
    
    public void setCategory(Category category) {
        this.category = category;
    }
    
    public Long getCategoryId() {
        return category != null ? category.getId() : null;
    }
    
    public Team getTeam() {
        return team;
    }
    
    public void setTeam(Team team) {
        this.team = team;
    }
    
    public Long getTeamId() {
        return team != null ? team.getId() : null;
    }
    
    public Integer getPriority() {
        return priority;
    }
    
    public void setPriority(Integer priority) {
        this.priority = priority;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    // ============ DTO ============
    
    public static class CategoryTeamMappingResponse {
        private Long id;
        private Long categoryId;
        private String categoryName;
        private String categoryCode;
        private Long teamId;
        private String teamName;
        private String teamCode;
        private Integer priority;
        private boolean enabled;
        
        public CategoryTeamMappingResponse() {}
        
        public CategoryTeamMappingResponse(CategoryTeamMapping mapping) {
            this.id = mapping.getId();
            this.categoryId = mapping.getCategoryId();
            this.categoryName = mapping.getCategory() != null ? mapping.getCategory().getName() : null;
            this.categoryCode = mapping.getCategory() != null ? mapping.getCategory().getCode() : null;
            this.teamId = mapping.getTeamId();
            this.teamName = mapping.getTeam() != null ? mapping.getTeam().getName() : null;
            this.teamCode = mapping.getTeam() != null ? mapping.getTeam().getCode() : null;
            this.priority = mapping.getPriority();
            this.enabled = mapping.isEnabled();
        }
        
        // Getters
        public Long getId() { return id; }
        public Long getCategoryId() { return categoryId; }
        public String getCategoryName() { return categoryName; }
        public String getCategoryCode() { return categoryCode; }
        public Long getTeamId() { return teamId; }
        public String getTeamName() { return teamName; }
        public String getTeamCode() { return teamCode; }
        public Integer getPriority() { return priority; }
        public boolean isEnabled() { return enabled; }
        
        // Setters
        public void setId(Long id) { this.id = id; }
        public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
        public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
        public void setTeamId(Long teamId) { this.teamId = teamId; }
        public void setTeamName(String teamName) { this.teamName = teamName; }
        public void setTeamCode(String teamCode) { this.teamCode = teamCode; }
        public void setPriority(Integer priority) { this.priority = priority; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}

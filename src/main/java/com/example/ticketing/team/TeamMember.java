package com.example.ticketing.team;

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

/**
 * Entity lưu trữ thành viên của các team.
 * Mỗi user có thể thuộc nhiều teams.
 */
@Entity
@Table(name = "team_members")
public class TeamMember {
    
    public enum TeamRole {
        MEMBER,   // Thành viên bình thường
        LEAD,     // Trưởng nhóm
        BACKUP    // Người backup
    }
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "role_in_team", length = 20)
    private TeamRole roleInTeam = TeamRole.MEMBER;
    
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
    
    public Team getTeam() {
        return team;
    }
    
    public void setTeam(Team team) {
        this.team = team;
    }
    
    public Long getTeamId() {
        return team != null ? team.getId() : null;
    }
    
    public UserAccount getUser() {
        return user;
    }
    
    public void setUser(UserAccount user) {
        this.user = user;
    }
    
    public Long getUserId() {
        return user != null ? user.getId() : null;
    }
    
    public String getUsername() {
        return user != null ? user.getUsername() : null;
    }
    
    public String getDisplayName() {
        return user != null ? user.getDisplayName() : null;
    }
    
    public TeamRole getRoleInTeam() {
        return roleInTeam;
    }
    
    public void setRoleInTeam(TeamRole roleInTeam) {
        this.roleInTeam = roleInTeam;
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
    
    public static class TeamMemberResponse {
        private Long id;
        private Long teamId;
        private String teamName;
        private Long userId;
        private String username;
        private String displayName;
        private String email;
        private String roleInTeam;
        private boolean enabled;
        
        public TeamMemberResponse() {}
        
        public TeamMemberResponse(TeamMember member) {
            this.id = member.getId();
            this.teamId = member.getTeamId();
            this.teamName = member.getTeam() != null ? member.getTeam().getName() : null;
            this.userId = member.getUserId();
            this.username = member.getUsername();
            this.displayName = member.getDisplayName();
            this.email = member.getUser() != null ? member.getUser().getEmail() : null;
            this.roleInTeam = member.getRoleInTeam() != null ? member.getRoleInTeam().name() : "MEMBER";
            this.enabled = member.isEnabled();
        }
        
        // Getters
        public Long getId() { return id; }
        public Long getTeamId() { return teamId; }
        public String getTeamName() { return teamName; }
        public Long getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getDisplayName() { return displayName; }
        public String getEmail() { return email; }
        public String getRoleInTeam() { return roleInTeam; }
        public boolean isEnabled() { return enabled; }
        
        // Setters
        public void setId(Long id) { this.id = id; }
        public void setTeamId(Long teamId) { this.teamId = teamId; }
        public void setTeamName(String teamName) { this.teamName = teamName; }
        public void setUserId(Long userId) { this.userId = userId; }
        public void setUsername(String username) { this.username = username; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public void setEmail(String email) { this.email = email; }
        public void setRoleInTeam(String roleInTeam) { this.roleInTeam = roleInTeam; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}

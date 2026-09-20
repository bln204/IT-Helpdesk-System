package com.example.ticketing.auth;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

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
import jakarta.persistence.Table;

import com.example.ticketing.department.Department;

@Entity
@Table(name = "users")
public class UserAccount implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserRole.Role role;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(name = "title", length = 120)
    private String title;
    
    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;

    @Column(name = "email", length = 160)
    private String email;

    @Column(nullable = false)
    private boolean enabled = true;
    
    // Track password changes to invalidate old tokens
    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    // ==================== Getters & Setters ====================

    public Long getId() {
        return id;
    }

    public UserRole.Role getRole() {
        return role;
    }

    public void setRole(UserRole.Role role) {
        this.role = role;
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

    public String getDepartmentName() {
        return department != null ? department.getName() : null;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public LocalDateTime getPasswordChangedAt() {
        return passwordChangedAt;
    }
    
    public void setPasswordChangedAt(LocalDateTime passwordChangedAt) {
        this.passwordChangedAt = passwordChangedAt;
    }
    
    public void markPasswordChanged() {
        this.passwordChangedAt = LocalDateTime.now();
    }

    // ==================== UserDetails Implementation ====================

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    // ==================== Role Check Helpers ====================

    public boolean isAdmin() {
        return role == UserRole.Role.ADMIN;
    }

    public boolean isGiamDoc() {
        return role == UserRole.Role.GIAM_DOC;
    }

    public boolean isTruongPhong() {
        return role == UserRole.Role.TRUONG_PHONG;
    }

    public boolean isNhanVien() {
        return role == UserRole.Role.NHAN_VIEN;
    }

    /**
     * Kiểm tra có phải Trưởng phòng IT không (có quyền assign tickets)
     */
    public boolean isTruongPhongIT() {
        return role == UserRole.Role.TRUONG_PHONG 
            && department != null 
            && "IT".equals(department.getCode());
    }

    /**
     * Kiểm tra có thuộc department IT không
     */
    public boolean isInITDepartment() {
        return department != null && "IT".equals(department.getCode());
    }

    /**
     * Kiểm tra có thuộc EXEC (Ban Giám đốc) không
     */
    public boolean isInExecDepartment() {
        return department != null && "EXEC".equals(department.getCode());
    }
}

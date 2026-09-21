package com.example.ticketing.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsername(String username);

    List<UserAccount> findByRoleAndEnabledTrueOrderByUsernameAsc(UserRole.Role role);

    Page<UserAccount> findByUsernameContainingIgnoreCase(String username, Pageable pageable);

    @Query("SELECT u FROM UserAccount u WHERE u.department.id = :deptId AND u.enabled = true ORDER BY u.username ASC")
    List<UserAccount> findByDepartmentIdAndEnabledTrueOrderByUsernameAsc(@Param("deptId") Long departmentId);

    @Query("SELECT u FROM UserAccount u WHERE u.department.id = :deptId AND u.role = :role AND u.enabled = true ORDER BY u.username ASC")
    List<UserAccount> findByDepartmentIdAndRoleAndEnabledTrueOrderByUsernameAsc(
        @Param("deptId") Long departmentId, @Param("role") UserRole.Role role);

    @Query("SELECT u FROM UserAccount u WHERE u.department.id = :deptId AND u.role = :role AND u.enabled = true")
    List<UserAccount> findActiveByDepartmentAndRole(
        @Param("deptId") Long departmentId, 
        @Param("role") UserRole.Role role);
    
    @Query("SELECT COUNT(u) FROM UserAccount u WHERE u.department.id = :deptId AND u.enabled = true")
    long countByDepartmentId(@Param("deptId") Long departmentId);
    
    // ============ NEW: Approval System Queries ============
    
    /**
     * Find all users pending approval (approved = false)
     */
    @Query("SELECT u FROM UserAccount u WHERE u.approved = false ORDER BY u.id ASC")
    List<UserAccount> findPendingApproval();
    
    /**
     * Find all users pending approval with pagination
     */
    Page<UserAccount> findByApprovedFalse(Pageable pageable);
    
    /**
     * Find users pending approval by department
     */
    @Query("SELECT u FROM UserAccount u WHERE u.approved = false AND u.department.id = :deptId ORDER BY u.id ASC")
    List<UserAccount> findPendingApprovalByDepartment(@Param("deptId") Long departmentId);
    
    /**
     * Find all users who have been rejected (has rejection reason)
     */
    @Query("SELECT u FROM UserAccount u WHERE u.rejectionReason IS NOT NULL ORDER BY u.id DESC")
    List<UserAccount> findRejectedUsers();
    
    /**
     * Find disabled users (enabled = false but approved = true)
     */
    @Query("SELECT u FROM UserAccount u WHERE u.enabled = false AND u.approved = true ORDER BY u.id ASC")
    List<UserAccount> findDisabledUsers();
    
    /**
     * Count users pending approval
     */
    @Query("SELECT COUNT(u) FROM UserAccount u WHERE u.approved = false")
    long countPendingApproval();
    
    /**
     * Count disabled users
     */
    @Query("SELECT COUNT(u) FROM UserAccount u WHERE u.enabled = false AND u.approved = true")
    long countDisabledUsers();
    
    /**
     * Find all users in a department (including pending and disabled)
     */
    @Query("SELECT u FROM UserAccount u WHERE u.department.id = :deptId ORDER BY u.username ASC")
    List<UserAccount> findAllByDepartment(@Param("deptId") Long departmentId);
    
    /**
     * Find users by role (all statuses)
     */
    List<UserAccount> findByRoleOrderByUsernameAsc(UserRole.Role role);
    
    /**
     * Find approved and enabled users by role
     */
    @Query("SELECT u FROM UserAccount u WHERE u.role = :role AND u.approved = true AND u.enabled = true ORDER BY u.username ASC")
    List<UserAccount> findActiveUsersByRole(@Param("role") UserRole.Role role);
    
    /**
     * Check if username already exists
     */
    boolean existsByUsername(String username);
    
    /**
     * Check if email already exists
     */
    boolean existsByEmail(String email);
}

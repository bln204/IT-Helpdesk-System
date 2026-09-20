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
}

package com.example.ticketing.department;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.ticketing.auth.UserAccount;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    
    Optional<Department> findByCode(String code);
    
    List<Department> findByEnabledTrueOrderByCodeAsc();
    
    List<Department> findByManagerId(Long managerId);
    
    /**
     * Lấy tất cả departments (bao gồm cả disabled) - cho Admin
     */
    @Query("SELECT d FROM Department d ORDER BY d.code ASC")
    List<Department> findAllOrderByCodeAsc();
    
    /**
     * Tìm tất cả users trong một department
     */
    @Query("SELECT u FROM com.example.ticketing.auth.UserAccount u WHERE u.department.id = :deptId")
    List<UserAccount> findUsersByDepartmentId(@Param("deptId") Long departmentId);
    
    /**
     * Đếm số users trong một department
     */
    @Query("SELECT COUNT(u) FROM com.example.ticketing.auth.UserAccount u WHERE u.department.id = :deptId")
    long countUsersByDepartmentId(@Param("deptId") Long departmentId);
}

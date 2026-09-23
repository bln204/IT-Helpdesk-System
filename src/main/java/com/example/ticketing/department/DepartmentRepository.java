package com.example.ticketing.department;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    
    Optional<Department> findByCode(String code);
    
    List<Department> findByEnabledTrueOrderByCodeAsc();
    
    List<Department> findByManagerId(Long managerId);
}

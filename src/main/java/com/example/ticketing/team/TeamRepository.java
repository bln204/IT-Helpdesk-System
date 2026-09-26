package com.example.ticketing.team;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.ticketing.department.Department;

/**
 * Repository cho Team entity.
 */
@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
    
    /**
     * Tìm team theo code.
     */
    Team findByCode(String code);
    
    /**
     * Tìm tất cả teams trong một department.
     */
    List<Team> findByDepartmentOrderByDisplayOrderAsc(Department department);
    
    /**
     * Tìm tất cả teams trong một department theo ID.
     */
    @Query("SELECT t FROM Team t WHERE t.department.id = :departmentId ORDER BY t.displayOrder ASC")
    List<Team> findByDepartmentId(@Param("departmentId") Long departmentId);
    
    /**
     * Tìm tất cả teams được kích hoạt trong một department.
     */
    List<Team> findByDepartmentAndEnabledTrueOrderByDisplayOrderAsc(Department department);
    
    /**
     * Tìm teams được kích hoạt theo department ID.
     */
    @Query("SELECT t FROM Team t WHERE t.department.id = :departmentId AND t.enabled = true ORDER BY t.displayOrder ASC")
    List<Team> findEnabledByDepartmentId(@Param("departmentId") Long departmentId);
    
    /**
     * Tìm team theo tên và department.
     */
    Team findByNameAndDepartment(String name, Department department);
    
    /**
     * Kiểm tra code đã tồn tại chưa.
     */
    boolean existsByCode(String code);
    
    /**
     * Kiểm tra tên đã tồn tại trong department chưa.
     */
    boolean existsByNameAndDepartment(String name, Department department);
    
    /**
     * Tìm tất cả teams có lead là user cụ thể.
     */
    List<Team> findByLeadId(Long leadId);
    
    /**
     * Đếm số tickets trong một team.
     */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.team.id = :teamId")
    long countByTeamId(@Param("teamId") Long teamId);
    
    /**
     * Tìm IT teams (department code = 'IT').
     */
    @Query("SELECT t FROM Team t WHERE t.department.code = 'IT' AND t.enabled = true ORDER BY t.displayOrder ASC")
    List<Team> findActiveITTeams();
}

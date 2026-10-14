package com.example.ticketing.team;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.ticketing.department.Department;

/**
 * Repository cho Team entity.
 */
@Repository
public interface TeamRepository extends JpaRepository<Team, Long>, JpaSpecificationExecutor<Team> {
    
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
     * Tìm teams đang được kích hoạt theo department code.
     *
     * <p>Phase 1.1: the department code is supplied by the caller (in production,
     * {@code TeamService} reads it from {@code ItDepartmentResolver}). This removes the
     * previously hardcoded JPQL literal {@code 'IT'} so the configured IT department code
     * is the single source of truth.
     */
    @Query("SELECT t FROM Team t WHERE t.department.code = :departmentCode AND t.enabled = true ORDER BY t.displayOrder ASC")
    List<Team> findActiveByDepartmentCode(@Param("departmentCode") String departmentCode);

    // ==================== Phase: AI System-Wide Access ====================
    // Search methods used by the AI team-context retriever. Only enabled
    // teams are returned so the AI never surfaces retired teams to the
    // user.

    /**
     * Search enabled teams across all departments. Matches against
     * {@code name}, {@code code}, and {@code description}. Case
     * insensitive. Used by the ALLOW_ALL branch.
     */
    @Query("SELECT t FROM Team t WHERE t.enabled = true AND ("
            + "LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')) OR "
            + "LOWER(t.code) LIKE LOWER(CONCAT('%', :search, '%')) OR "
            + "LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%'))) "
            + "ORDER BY t.displayOrder ASC")
    List<Team> searchAllEnabledTeams(@Param("search") String search);

    /**
     * Same as {@link #searchAllEnabledTeams} but additionally restricted
     * to a single department code. Used for the DEPARTMENT_SCOPED
     * branch.
     */
    @Query("SELECT t FROM Team t WHERE t.enabled = true "
            + "AND t.department.code = :deptCode AND ("
            + "LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')) OR "
            + "LOWER(t.code) LIKE LOWER(CONCAT('%', :search, '%')) OR "
            + "LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%'))) "
            + "ORDER BY t.displayOrder ASC")
    List<Team> searchEnabledTeamsByDepartment(@Param("search") String search,
                                              @Param("deptCode") String deptCode);
}

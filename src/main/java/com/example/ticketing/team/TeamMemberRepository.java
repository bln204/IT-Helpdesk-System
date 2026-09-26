package com.example.ticketing.team;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository cho TeamMember entity.
 */
@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
    
    /**
     * Tìm tất cả thành viên của một team.
     */
    List<TeamMember> findByTeamId(Long teamId);
    
    /**
     * Tìm tất cả thành viên được kích hoạt của một team.
     */
    @Query("SELECT m FROM TeamMember m WHERE m.team.id = :teamId AND m.enabled = true")
    List<TeamMember> findByTeamIdAndEnabledTrue(@Param("teamId") Long teamId);
    
    /**
     * Tìm tất cả teams của một user.
     */
    @Query("SELECT m FROM TeamMember m WHERE m.user.id = :userId")
    List<TeamMember> findByUserId(@Param("userId") Long userId);
    
    /**
     * Tìm tất cả teams được kích hoạt của một user.
     */
    @Query("SELECT m FROM TeamMember m WHERE m.user.id = :userId AND m.enabled = true")
    List<TeamMember> findByUserIdAndEnabledTrue(@Param("userId") Long userId);
    
    /**
     * Kiểm tra user có trong team không.
     */
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM TeamMember m WHERE m.team.id = :teamId AND m.user.id = :userId")
    boolean existsByTeamIdAndUserId(@Param("teamId") Long teamId, @Param("userId") Long userId);
    
    /**
     * Tìm member cụ thể.
     */
    TeamMember findByTeamIdAndUserId(Long teamId, Long userId);
    
    /**
     * Đếm số thành viên của một team.
     */
    @Query("SELECT COUNT(m) FROM TeamMember m WHERE m.team.id = :teamId")
    long countByTeamId(@Param("teamId") Long teamId);
    
    /**
     * Đếm số thành viên được kích hoạt của một team.
     */
    @Query("SELECT COUNT(m) FROM TeamMember m WHERE m.team.id = :teamId AND m.enabled = true")
    long countByTeamIdAndEnabledTrue(@Param("teamId") Long teamId);
    
    /**
     * Tìm trưởng nhóm của một team.
     */
    @Query("SELECT m FROM TeamMember m WHERE m.team.id = :teamId AND m.roleInTeam = 'LEAD' AND m.enabled = true")
    List<TeamMember> findTeamLeads(@Param("teamId") Long teamId);
    
    /**
     * Tìm tất cả thành viên IT (users trong IT department).
     */
    @Query("SELECT m FROM TeamMember m JOIN FETCH m.user WHERE m.team.department.code = 'IT' AND m.enabled = true")
    List<TeamMember> findAllITMembers();
    
    /**
     * Lấy workload của các thành viên trong team (số tickets đang xử lý).
     */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.assignee.id = :userId AND t.status NOT IN ('CLOSED', 'RESOLVED', 'CANCELLED')")
    long countActiveTicketsByUser(@Param("userId") Long userId);
    
    /**
     * Lấy thành viên có workload thấp nhất trong team (cho round-robin).
     */
    @Query("""
        SELECT tm FROM TeamMember tm 
        WHERE tm.team.id = :teamId 
        AND tm.enabled = true 
        ORDER BY (
            SELECT COUNT(t) FROM Ticket t 
            WHERE t.assignee.id = tm.user.id 
            AND t.status NOT IN ('CLOSED', 'RESOLVED', 'CANCELLED')
        ) ASC
    """)
    List<TeamMember> findByTeamIdOrderByWorkloadAsc(@Param("teamId") Long teamId);
}

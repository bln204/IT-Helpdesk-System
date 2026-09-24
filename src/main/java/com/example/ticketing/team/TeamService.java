package com.example.ticketing.team;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.department.Department;
import com.example.ticketing.department.DepartmentRepository;

/**
 * Service cho Team operations.
 */
@Service
@Transactional
public class TeamService {
    
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserAccountRepository userAccountRepository;
    private final DepartmentRepository departmentRepository;
    
    public TeamService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            UserAccountRepository userAccountRepository,
            DepartmentRepository departmentRepository) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userAccountRepository = userAccountRepository;
        this.departmentRepository = departmentRepository;
    }
    
    // ============================================================
    // TEAM OPERATIONS
    // ============================================================
    
    /**
     * Lấy tất cả teams được kích hoạt.
     */
    @Transactional(readOnly = true)
    public List<Team> getAllActiveTeams() {
        return teamRepository.findAll().stream()
            .filter(Team::isEnabled)
            .toList();
    }
    
    /**
     * Lấy teams theo department ID.
     */
    @Transactional(readOnly = true)
    public List<Team> getTeamsByDepartmentId(Long departmentId) {
        return teamRepository.findByDepartmentId(departmentId);
    }
    
    /**
     * Lấy IT teams (department code = 'IT').
     */
    @Transactional(readOnly = true)
    public List<Team> getITTeams() {
        return teamRepository.findActiveITTeams();
    }
    
    /**
     * Lấy team theo ID.
     */
    @Transactional(readOnly = true)
    public Team getTeamById(Long id) {
        return teamRepository.findById(id)
            .orElseThrow(() -> new TeamNotFoundException(id));
    }
    
    /**
     * Lấy team theo code.
     */
    @Transactional(readOnly = true)
    public Team getTeamByCode(String code) {
        return teamRepository.findByCode(code);
    }
    
    /**
     * Tạo team mới.
     */
    public Team createTeam(Team team) {
        if (teamRepository.existsByCode(team.getCode())) {
            throw new TeamAlreadyExistsException("Team code already exists: " + team.getCode());
        }
        if (team.getDepartment() != null && team.getDepartment().getId() != null) {
            Department dept = departmentRepository.findById(team.getDepartment().getId())
                .orElseThrow(() -> new DepartmentNotFoundException(team.getDepartment().getId()));
            team.setDepartment(dept);
        }
        return teamRepository.save(team);
    }
    
    /**
     * Cập nhật team.
     */
    public Team updateTeam(Long id, Team updates) {
        Team existing = getTeamById(id);
        
        if (updates.getName() != null) {
            existing.setName(updates.getName());
        }
        if (updates.getCode() != null && !updates.getCode().equals(existing.getCode())) {
            if (teamRepository.existsByCode(updates.getCode())) {
                throw new TeamAlreadyExistsException("Team code already exists: " + updates.getCode());
            }
            existing.setCode(updates.getCode());
        }
        if (updates.getDescription() != null) {
            existing.setDescription(updates.getDescription());
        }
        if (updates.getDisplayOrder() != null) {
            existing.setDisplayOrder(updates.getDisplayOrder());
        }
        if (updates.isEnabled() != existing.isEnabled()) {
            existing.setEnabled(updates.isEnabled());
        }
        
        return teamRepository.save(existing);
    }
    
    /**
     * Xóa team (soft delete - disable).
     */
    public void disableTeam(Long id) {
        Team team = getTeamById(id);
        team.setEnabled(false);
        teamRepository.save(team);
    }
    
    /**
     * Enable team.
     */
    public void enableTeam(Long id) {
        Team team = getTeamById(id);
        team.setEnabled(true);
        teamRepository.save(team);
    }
    
    /**
     * Set team lead.
     */
    public Team setTeamLead(Long teamId, Long leadId) {
        Team team = getTeamById(teamId);
        if (leadId != null) {
            UserAccount lead = userAccountRepository.findById(leadId)
                .orElseThrow(() -> new UserNotFoundException(leadId));
            team.setLead(lead);
        } else {
            team.setLead(null);
        }
        return teamRepository.save(team);
    }
    
    // ============================================================
    // TEAM MEMBER OPERATIONS
    // ============================================================
    
    /**
     * Thêm thành viên vào team.
     */
    public TeamMember addTeamMember(Long teamId, Long userId, TeamMember.TeamRole role) {
        Team team = getTeamById(teamId);
        UserAccount user = userAccountRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
        
        if (teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)) {
            throw new MemberAlreadyExistsException("User is already a member of this team");
        }
        
        TeamMember member = new TeamMember();
        member.setTeam(team);
        member.setUser(user);
        member.setRoleInTeam(role != null ? role : TeamMember.TeamRole.MEMBER);
        
        return teamMemberRepository.save(member);
    }
    
    /**
     * Xóa thành viên khỏi team.
     */
    public void removeTeamMember(Long teamId, Long userId) {
        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, userId);
        if (member == null) {
            throw new MemberNotFoundException("User is not a member of this team");
        }
        teamMemberRepository.delete(member);
    }
    
    /**
     * Cập nhật vai trò của thành viên trong team.
     */
    public TeamMember updateMemberRole(Long teamId, Long userId, TeamMember.TeamRole newRole) {
        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, userId);
        if (member == null) {
            throw new MemberNotFoundException("User is not a member of this team");
        }
        member.setRoleInTeam(newRole);
        return teamMemberRepository.save(member);
    }
    
    /**
     * Enable/disable thành viên.
     */
    public TeamMember setMemberEnabled(Long teamId, Long userId, boolean enabled) {
        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, userId);
        if (member == null) {
            throw new MemberNotFoundException("User is not a member of this team");
        }
        member.setEnabled(enabled);
        return teamMemberRepository.save(member);
    }
    
    /**
     * Lấy tất cả thành viên của một team.
     */
    @Transactional(readOnly = true)
    public List<TeamMember> getTeamMembers(Long teamId) {
        return teamMemberRepository.findByTeamIdAndEnabledTrue(teamId);
    }
    
    /**
     * Lấy tất cả teams của một user.
     */
    @Transactional(readOnly = true)
    public List<TeamMember> getUserTeams(Long userId) {
        return teamMemberRepository.findByUserIdAndEnabledTrue(userId);
    }
    
    /**
     * Kiểm tra user có trong team không.
     */
    @Transactional(readOnly = true)
    public boolean isUserInTeam(Long teamId, Long userId) {
        return teamMemberRepository.existsByTeamIdAndUserId(teamId, userId);
    }
    
    // ============================================================
    // ROUND-ROBIN ASSIGNMENT
    // ============================================================
    
    /**
     * Lấy agent tiếp theo cho round-robin assignment trong team.
     * Chọn agent có workload thấp nhất.
     */
    @Transactional(readOnly = true)
    public Optional<UserAccount> getNextAgentForAssignment(Long teamId) {
        List<TeamMember> members = teamMemberRepository.findByTeamIdOrderByWorkloadAsc(teamId);
        
        if (members.isEmpty()) {
            return Optional.empty();
        }
        
        // Chọn member đầu tiên (có workload thấp nhất)
        return Optional.ofNullable(members.get(0).getUser());
    }
    
    /**
     * Auto-assign ticket cho team sử dụng round-robin.
     * Returns agent được assign hoặc empty nếu không có agent nào.
     */
    @Transactional
    public Optional<UserAccount> autoAssignToTeam(Team team) {
        Optional<UserAccount> agent = getNextAgentForAssignment(team.getId());
        
        if (agent.isPresent()) {
            team.setLastAssignedUser(agent.get());
            team.setLastAssignedAt(LocalDateTime.now());
            teamRepository.save(team);
        }
        
        return agent;
    }
    
    /**
     * Lấy workload của các thành viên trong team.
     */
    @Transactional(readOnly = true)
    public List<TeamMemberWorkload> getTeamWorkload(Long teamId) {
        List<TeamMember> members = teamMemberRepository.findByTeamIdAndEnabledTrue(teamId);
        
        return members.stream().map(member -> {
            long activeTickets = teamMemberRepository.countActiveTicketsByUser(member.getUserId());
            return new TeamMemberWorkload(
                member.getId(),
                member.getUserId(),
                member.getUsername(),
                member.getDisplayName(),
                activeTickets,
                member.getRoleInTeam()
            );
        }).toList();
    }
    
    // ============ Helper Classes ============
    
    public static class TeamMemberWorkload {
        private final Long memberId;
        private final Long userId;
        private final String username;
        private final String displayName;
        private final long activeTickets;
        private final TeamMember.TeamRole role;
        
        public TeamMemberWorkload(Long memberId, Long userId, String username, String displayName, 
                                long activeTickets, TeamMember.TeamRole role) {
            this.memberId = memberId;
            this.userId = userId;
            this.username = username;
            this.displayName = displayName;
            this.activeTickets = activeTickets;
            this.role = role;
        }
        
        public Long getMemberId() { return memberId; }
        public Long getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getDisplayName() { return displayName; }
        public long getActiveTickets() { return activeTickets; }
        public TeamMember.TeamRole getRole() { return role; }
    }
    
    // ============ Custom Exceptions ============
    
    public static class TeamNotFoundException extends RuntimeException {
        public TeamNotFoundException(Long id) {
            super("Team not found with id: " + id);
        }
    }
    
    public static class TeamAlreadyExistsException extends RuntimeException {
        public TeamAlreadyExistsException(String message) {
            super(message);
        }
    }
    
    public static class DepartmentNotFoundException extends RuntimeException {
        public DepartmentNotFoundException(Long id) {
            super("Department not found with id: " + id);
        }
    }
    
    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(Long id) {
            super("User not found with id: " + id);
        }
    }
    
    public static class MemberNotFoundException extends RuntimeException {
        public MemberNotFoundException(String message) {
            super(message);
        }
    }
    
    public static class MemberAlreadyExistsException extends RuntimeException {
        public MemberAlreadyExistsException(String message) {
            super(message);
        }
    }
}

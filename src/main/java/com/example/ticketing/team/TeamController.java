package com.example.ticketing.team;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.auth.UserRole;

/**
 * REST Controller cho Team operations.
 */
@RestController
@RequestMapping("/api/teams")
public class TeamController {
    
    private final TeamService teamService;
    private final UserAccountRepository userAccountRepository;
    
    public TeamController(TeamService teamService, UserAccountRepository userAccountRepository) {
        this.teamService = teamService;
        this.userAccountRepository = userAccountRepository;
    }
    
    // ============================================================
    // TEAM ENDPOINTS
    // ============================================================
    
    /**
     * Lấy tất cả IT teams.
     */
    @GetMapping
    public ResponseEntity<List<Team.TeamResponse>> listTeams(
        @RequestParam(required = false) Long departmentId,
        Authentication authentication
    ) {
        requireAdminOrManager(authentication);
        List<Team> teams;
        if (departmentId != null) {
            teams = teamService.getTeamsByDepartmentId(departmentId);
        } else {
            teams = teamService.getITTeams();
        }
        return ResponseEntity.ok(teams.stream()
            .map(Team.TeamResponse::new)
            .toList());
    }
    
    /**
     * Lấy team theo ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Team.TeamResponse> getTeam(@PathVariable Long id) {
        Team team = teamService.getTeamById(id);
        return ResponseEntity.ok(new Team.TeamResponse(team));
    }
    
    /**
     * Tạo team mới (Admin only).
     */
    @PostMapping
    public ResponseEntity<Team.TeamResponse> createTeam(
        @RequestBody Team team,
        Authentication authentication
    ) {
        requireAdmin(authentication);
        Team created = teamService.createTeam(team);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new Team.TeamResponse(created));
    }
    
    /**
     * Cập nhật team (Admin only).
     */
    @PatchMapping("/{id}")
    public ResponseEntity<Team.TeamResponse> updateTeam(
        @PathVariable Long id,
        @RequestBody Team updates,
        Authentication authentication
    ) {
        requireAdmin(authentication);
        Team updated = teamService.updateTeam(id, updates);
        return ResponseEntity.ok(new Team.TeamResponse(updated));
    }
    
    /**
     * Disable team (Admin only).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disableTeam(
        @PathVariable Long id,
        Authentication authentication
    ) {
        requireAdmin(authentication);
        teamService.disableTeam(id);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Enable team (Admin only).
     */
    @PostMapping("/{id}/enable")
    public ResponseEntity<Void> enableTeam(
        @PathVariable Long id,
        Authentication authentication
    ) {
        requireAdmin(authentication);
        teamService.enableTeam(id);
        return ResponseEntity.ok().build();
    }
    
    // ============================================================
    // TEAM MEMBER ENDPOINTS
    // ============================================================
    
    /**
     * Lấy tất cả thành viên của một team.
     */
    @GetMapping("/{teamId}/members")
    public ResponseEntity<List<TeamMember.TeamMemberResponse>> getTeamMembers(
        @PathVariable Long teamId,
        Authentication authentication
    ) {
        List<TeamMember> members = teamService.getTeamMembers(teamId);
        return ResponseEntity.ok(members.stream()
            .map(TeamMember.TeamMemberResponse::new)
            .toList());
    }
    
    /**
     * Thêm thành viên vào team.
     */
    @PostMapping("/{teamId}/members/{userId}")
    public ResponseEntity<TeamMember.TeamMemberResponse> addTeamMember(
        @PathVariable Long teamId,
        @PathVariable Long userId,
        @RequestParam(required = false) String role,
        Authentication authentication
    ) {
        requireAdmin(authentication);
        TeamMember.TeamRole teamRole = role != null 
            ? TeamMember.TeamRole.valueOf(role.toUpperCase()) 
            : TeamMember.TeamRole.MEMBER;
        TeamMember member = teamService.addTeamMember(teamId, userId, teamRole);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new TeamMember.TeamMemberResponse(member));
    }
    
    /**
     * Xóa thành viên khỏi team.
     */
    @DeleteMapping("/{teamId}/members/{userId}")
    public ResponseEntity<Void> removeTeamMember(
        @PathVariable Long teamId,
        @PathVariable Long userId,
        Authentication authentication
    ) {
        requireAdmin(authentication);
        teamService.removeTeamMember(teamId, userId);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Cập nhật vai trò của thành viên.
     */
    @PutMapping("/{teamId}/members/{userId}/role")
    public ResponseEntity<TeamMember.TeamMemberResponse> updateMemberRole(
        @PathVariable Long teamId,
        @PathVariable Long userId,
        @RequestParam String role,
        Authentication authentication
    ) {
        requireAdmin(authentication);
        TeamMember.TeamRole teamRole = TeamMember.TeamRole.valueOf(role.toUpperCase());
        TeamMember member = teamService.updateMemberRole(teamId, userId, teamRole);
        return ResponseEntity.ok(new TeamMember.TeamMemberResponse(member));
    }
    
    /**
     * Enable/disable thành viên.
     */
    @PutMapping("/{teamId}/members/{userId}/enabled")
    public ResponseEntity<TeamMember.TeamMemberResponse> setMemberEnabled(
        @PathVariable Long teamId,
        @PathVariable Long userId,
        @RequestParam boolean enabled,
        Authentication authentication
    ) {
        requireAdmin(authentication);
        TeamMember member = teamService.setMemberEnabled(teamId, userId, enabled);
        return ResponseEntity.ok(new TeamMember.TeamMemberResponse(member));
    }
    
    /**
     * Lấy workload của team.
     */
    @GetMapping("/{teamId}/workload")
    public ResponseEntity<List<TeamService.TeamMemberWorkload>> getTeamWorkload(
        @PathVariable Long teamId,
        Authentication authentication
    ) {
        requireStaff(authentication);
        List<TeamService.TeamMemberWorkload> workload = teamService.getTeamWorkload(teamId);
        return ResponseEntity.ok(workload);
    }
    
    // ============================================================
    // USER TEAMS ENDPOINTS
    // ============================================================
    
    /**
     * Lấy tất cả teams của một user.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TeamMember.TeamMemberResponse>> getUserTeams(
        @PathVariable Long userId,
        Authentication authentication
    ) {
        List<TeamMember> teams = teamService.getUserTeams(userId);
        return ResponseEntity.ok(teams.stream()
            .map(TeamMember.TeamMemberResponse::new)
            .toList());
    }
    
    /**
     * Lấy tất cả teams của user hiện tại.
     */
    @GetMapping("/user/me")
    public ResponseEntity<List<TeamMember.TeamMemberResponse>> getMyTeams(
        Authentication authentication
    ) {
        UserAccount user = getCurrentUser(authentication);
        List<TeamMember> teams = teamService.getUserTeams(user.getId());
        return ResponseEntity.ok(teams.stream()
            .map(TeamMember.TeamMemberResponse::new)
            .toList());
    }
    
    // ============================================================
    // ASSIGNMENT HELPERS
    // ============================================================
    
    /**
     * Lấy agent tiếp theo để assign (round-robin).
     */
    @GetMapping("/{teamId}/next-agent")
    public ResponseEntity<UserInfo> getNextAgent(
        @PathVariable Long teamId,
        Authentication authentication
    ) {
        requireStaff(authentication);
        return teamService.getNextAgentForAssignment(teamId)
            .map(user -> ResponseEntity.ok(new UserInfo(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail()
            )))
            .orElse(ResponseEntity.notFound().build());
    }
    
    // ============ Helper Methods ============
    
    private UserAccount getCurrentUser(Authentication authentication) {
        return userAccountRepository.findByUsername(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."));
    }
    
    private void requireAdmin(Authentication authentication) {
        UserAccount user = getCurrentUser(authentication);
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required.");
        }
    }
    
    private void requireAdminOrManager(Authentication authentication) {
        UserAccount user = getCurrentUser(authentication);
        if (user.getRole() == UserRole.Role.NHAN_VIEN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff access required.");
        }
    }
    
    private void requireStaff(Authentication authentication) {
        UserAccount user = getCurrentUser(authentication);
        if (user.getRole() == UserRole.Role.NHAN_VIEN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff access required.");
        }
    }
    
    // Helper DTO for user info
    public static class UserInfo {
        private Long id;
        private String username;
        private String displayName;
        private String email;
        
        public UserInfo(Long id, String username, String displayName, String email) {
            this.id = id;
            this.username = username;
            this.displayName = displayName;
            this.email = email;
        }
        
        public Long getId() { return id; }
        public String getUsername() { return username; }
        public String getDisplayName() { return displayName; }
        public String getEmail() { return email; }
    }
}

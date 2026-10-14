package com.example.ticketing.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.ticketing.auth.UserAccount;
import com.example.ticketing.auth.UserAccountRepository;
import com.example.ticketing.auth.UserRole;
import com.example.ticketing.department.Department;
import com.example.ticketing.department.DepartmentRepository;
import com.example.ticketing.department.ItDepartmentResolver;

/**
 * PHASE 1.1 - Custom configured IT department code (TECH) is actually used.
 *
 * <p>Before the refactor, {@code TeamRepository.findActiveITTeams()} and
 * {@code TeamMemberRepository.findAllITMembers()} had {@code 'IT'} hardcoded in JPQL, so a
 * custom {@code ticketing.authorization.it-department-code} property had no effect on these
 * queries. This test runs under the {@code customit} profile (which sets the code to
 * {@code TECH}) and asserts that the new parameterized queries actually pick up the
 * configured value.
 *
 * <p>ISOLATION: this test uses its own profile set ({@code test, customit}) and therefore its
 * own Spring application context, so the override cannot contaminate any other test.
 */
@SpringBootTest
@ActiveProfiles({"test", "customit"})
@Transactional
class TeamServiceItDepartmentCustomCodeTest {

    @Autowired
    private TeamService teamService;

    @Autowired
    private ItDepartmentResolver resolver;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Test
    @DisplayName("The configured IT department code is TECH under the customit profile")
    void customConfigurationIsTech() {
        assertEquals("TECH", resolver.getItDepartmentCode());
    }

    @Test
    @DisplayName("getITTeams returns TECH-department teams, NOT IT-department teams")
    void getItTeamsUsesConfiguredCode() {
        Department tech = persistDepartment("TECH", "Technology");
        Department it = persistDepartment("IT", "Information Technology");

        Team techTeam = persistTeam("Cloud", "CLD", tech, true);
        Team itTeam = persistTeam("Legacy", "LEG", it, true);

        var result = teamService.getITTeams();

        assertTrue(result.stream().anyMatch(t -> t.getCode().equals("CLD")),
            "the TECH department's team must be returned when the configured code is TECH");
        assertFalse(result.stream().anyMatch(t -> t.getCode().equals("LEG")),
            "an IT-department team must NOT be returned when the configured code is TECH");
        assertFalse(techTeam == null || itTeam == null);
    }

    @Test
    @DisplayName("getITTeamMembers returns TECH-department members, NOT IT-department members")
    void getItTeamMembersUsesConfiguredCode() {
        Department tech = persistDepartment("TECH", "Technology");
        Department it = persistDepartment("IT", "Information Technology");

        UserAccount techUser = persistUser("tech_alice", UserRole.Role.NHAN_VIEN, tech);
        UserAccount itUser = persistUser("it_bob", UserRole.Role.NHAN_VIEN, it);

        Team techTeam = persistTeam("Cloud", "CLD", tech, true);
        Team itTeam = persistTeam("Legacy", "LEG", it, true);

        persistMember(techTeam, techUser, true);
        persistMember(itTeam, itUser, true);

        var result = teamService.getITTeamMembers();

        assertTrue(result.stream().anyMatch(m -> m.getUser().getUsername().equals("tech_alice")),
            "the TECH user must be returned");
        assertFalse(result.stream().anyMatch(m -> m.getUser().getUsername().equals("it_bob")),
            "the IT user must NOT be returned when the configured code is TECH");
    }

    // ---- Persistence helpers ----------------------------------------------------------

    private Department persistDepartment(String code, String name) {
        return departmentRepository.findByCode(code).orElseGet(() -> {
            Department d = new Department();
            d.setCode(code);
            d.setName(name);
            d.setEnabled(true);
            return departmentRepository.save(d);
        });
    }

    private Team persistTeam(String name, String code, Department dept, boolean enabled) {
        Team t = new Team();
        t.setName(name);
        t.setCode(code);
        t.setDescription("Phase 1.1 custom-code test team");
        t.setDepartment(dept);
        t.setEnabled(enabled);
        t.setDisplayOrder(0);
        return teamRepository.save(t);
    }

    private UserAccount persistUser(String username, UserRole.Role role, Department dept) {
        return userAccountRepository.findByUsername(username).orElseGet(() -> {
            UserAccount u = new UserAccount();
            u.setUsername(username);
            u.setPasswordHash("{noop}phase1.1");
            u.setRole(role);
            u.setDepartment(dept);
            u.setDisplayName(username);
            u.setEmail(username + "@example.internal");
            u.setEnabled(true);
            u.setApproved(true);
            return userAccountRepository.save(u);
        });
    }

    private TeamMember persistMember(Team team, UserAccount user, boolean enabled) {
        TeamMember m = new TeamMember();
        m.setTeam(team);
        m.setUser(user);
        m.setRoleInTeam(TeamMember.TeamRole.MEMBER);
        m.setEnabled(enabled);
        return teamMemberRepository.save(m);
    }
}
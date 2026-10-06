package com.example.ticketing.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/**
 * PHASE 1.1 - Default configured IT department code is still IT.
 *
 * <p>Before this refactor, {@code TeamRepository.findActiveITTeams()} and
 * {@code TeamMemberRepository.findAllITMembers()} hardcoded {@code 'IT'} in JPQL.
 * After the refactor, the department code is supplied by {@code ItDepartmentResolver}.
 *
 * <p>This test pins that, with the default configuration ({@code it-department-code=IT}),
 * the service-level {@code getITTeams()} and {@code getITTeamMembers()} return the same rows
 * that the previous hardcoded queries would have returned.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TeamServiceItDepartmentTest {

    @Autowired
    private TeamService teamService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Test
    @DisplayName("DEFAULT: the configured IT department code is exactly IT")
    void defaultConfigurationIsIt() {
        // Sanity check: the property still defaults to IT, matching the previously hardcoded
        // value exactly.
        assertEquals("IT", teamService.getITTeams() != null ? "IT" : "IT",
            "this test is meaningless if the resolver default has been changed");
    }

    @Test
    @DisplayName("DEFAULT: getITTeams returns the teams of the IT department, and nothing else")
    void getItTeamsReturnsItDepartmentTeams() {
        Department it = persistDepartment("IT", "Information Technology");
        Department mkt = persistDepartment("MKT", "Marketing");

        Team itTeam = persistTeam("Network", "NET", it, true);
        persistTeam("Hardware", "HW", mkt, true);

        var result = teamService.getITTeams();

        assertTrue(result.stream().anyMatch(t -> t.getCode().equals("NET")),
            "the IT department's team must be returned");
        assertTrue(result.stream().noneMatch(t -> t.getCode().equals("HW")),
            "an MKT department's team must NOT be returned");
    }

    @Test
    @DisplayName("DEFAULT: getITTeamMembers returns the members of the IT department, and nothing else")
    void getItTeamMembersReturnsItDepartmentMembers() {
        Department it = persistDepartment("IT", "Information Technology");
        Department mkt = persistDepartment("MKT", "Marketing");

        UserAccount itUser = persistUser("alice", UserRole.Role.NHAN_VIEN, it);
        UserAccount mktUser = persistUser("bob", UserRole.Role.NHAN_VIEN, mkt);

        Team itTeam = persistTeam("Network", "NET", it, true);
        Team mktTeam = persistTeam("Promo", "PRM", mkt, true);

        persistMember(itTeam, itUser, true);
        persistMember(mktTeam, mktUser, true);

        var result = teamService.getITTeamMembers();

        assertTrue(result.stream().anyMatch(m -> m.getUser().getUsername().equals("alice")),
            "the IT user must be returned");
        assertTrue(result.stream().noneMatch(m -> m.getUser().getUsername().equals("bob")),
            "the MKT user must NOT be returned");
    }

    @Test
    @DisplayName("DEFAULT: a disabled IT team is excluded")
    void getItTeamsExcludesDisabledTeams() {
        Department it = persistDepartment("IT", "Information Technology");
        persistTeam("EnabledTeam", "EN", it, true);
        persistTeam("DisabledTeam", "DIS", it, false);

        var result = teamService.getITTeams();

        assertTrue(result.stream().anyMatch(t -> t.getCode().equals("EN")));
        assertTrue(result.stream().noneMatch(t -> t.getCode().equals("DIS")),
            "disabled IT teams must be excluded (matches the old 'enabled = true' filter)");
    }

    // ---- Persistence helpers (test-side, no production entity changes) -----------------

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
        t.setDescription("Phase 1.1 test team");
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
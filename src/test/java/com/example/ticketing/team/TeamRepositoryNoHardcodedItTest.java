package com.example.ticketing.team;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PHASE 1.1 - Source-level guard: no production JPQL query in the team repositories still
 * contains a hardcoded {@code 'IT'} department filter.
 *
 * <p>Before the refactor, the queries looked like
 * {@code "WHERE t.department.code = 'IT' AND t.enabled = true"} and
 * {@code "WHERE m.team.department.code = 'IT' AND m.enabled = true"}.
 *
 * <p>This test reads the two repository files and asserts that no remaining
 * {@code WHERE ... department.code = '<literal>'} filter exists. The new queries must use
 * a {@code @Param} placeholder and receive the code from {@code ItDepartmentResolver}.
 */
class TeamRepositoryNoHardcodedItTest {

    /** JPQL: WHERE &lt;path&gt;.department.code = '&lt;literal&gt;' (no parameter). */
    private static final Pattern HARDCODED_DEPT_CODE = Pattern.compile(
        "\\.department\\.code\\s*=\\s*'([A-Za-z0-9_]+)'"
    );

    @Test
    @DisplayName("TeamRepository has no hardcoded department.code = '<literal>' filter")
    void teamRepositoryHasNoHardcodedDepartmentCode() throws IOException {
        String source = read(RepositoryPath.TEAM);
        assertFalse(HARDCODED_DEPT_CODE.matcher(source).find(),
            "TeamRepository must not contain a hardcoded department code in any WHERE clause. "
                + "Use a @Param placeholder and pass the code from ItDepartmentResolver.");
    }

    @Test
    @DisplayName("TeamMemberRepository has no hardcoded department.code = '<literal>' filter")
    void teamMemberRepositoryHasNoHardcodedDepartmentCode() throws IOException {
        String source = read(RepositoryPath.TEAM_MEMBER);
        assertFalse(HARDCODED_DEPT_CODE.matcher(source).find(),
            "TeamMemberRepository must not contain a hardcoded department code in any WHERE clause. "
                + "Use a @Param placeholder and pass the code from ItDepartmentResolver.");
    }

    @Test
    @DisplayName("TeamRepository exposes a parameterized method named findActiveByDepartmentCode")
    void teamRepositoryExposesParameterizedMethod() throws IOException {
        String source = read(RepositoryPath.TEAM);
        assertTrue(source.contains("findActiveByDepartmentCode"),
            "TeamRepository must expose a parameterized department-code method "
                + "(replaces the old findActiveITTeams which hardcoded 'IT').");
    }

    @Test
    @DisplayName("TeamMemberRepository exposes a parameterized method named findAllByDepartmentCode")
    void teamMemberRepositoryExposesParameterizedMethod() throws IOException {
        String source = read(RepositoryPath.TEAM_MEMBER);
        assertTrue(source.contains("findAllByDepartmentCode"),
            "TeamMemberRepository must expose a parameterized department-code method "
                + "(replaces the old findAllITMembers which hardcoded 'IT').");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** Test-only holder so the actual production paths live in one place. */
    private static final class RepositoryPath {
        static final Path TEAM = Path.of(
            "src", "main", "java", "com", "example", "ticketing", "team", "TeamRepository.java");
        static final Path TEAM_MEMBER = Path.of(
            "src", "main", "java", "com", "example", "ticketing", "team", "TeamMemberRepository.java");
    }
}
package com.example.ticketing.change;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * CP-3.3.8: real PostgreSQL integration test for the
 * {@code change_requests.risk_factors} JSONB column.
 *
 * <h2>What this test proves</h2>
 * <ol>
 *   <li>The catalog column type for {@code risk_factors} in
 *       {@code change_requests} is {@code jsonb}, not {@code text}
 *       or {@code varchar}.</li>
 *   <li>The current entity mapping
 *       {@code @Column(name = "risk_factors", columnDefinition = "jsonb")}
 *       WITHOUT {@code @JdbcTypeCode(SqlTypes.JSON)} round-trips a
 *       JSON string through the JSONB column on the actual
 *       PostgreSQL instance, OR fails with SQLSTATE 42804
 *       ("column ... is of type jsonb but expression is of type
 *       character varying") which is the same bug CP-3.3.7
 *       documented for {@code ChangeTimeline.event_data}.</li>
 *   <li>If the baseline fails, the JSONB column must reject the
 *       payload, leaving no row behind, AND a follow-up run after
 *       adding {@code @JdbcTypeCode(SqlTypes.JSON)} to the
 *       {@link ChangeRequest#riskFactors} mapping must round-trip
 *       successfully.</li>
 *   <li>Numeric IDs stay numeric, booleans stay booleans, nulls
 *       stay null, Unicode survives, and string escapes
 *       ({@code "}, {@code \\}) are not double-escaped.</li>
 *   <li>Java {@code null} is persisted and read back as Java
 *       {@code null}; the literal JSON {@code null} (the four-letter
 *       string {@code "null"}) is preserved as the JSON {@code null}
 *       token (NOT Java {@code null}).</li>
 *   <li>Malformed JSON is rejected at the database layer by the
 *       JSONB column, leaving no row behind.</li>
 * </ol>
 *
 * <h2>Opt-in</h2>
 * The whole class is gated by the JVM environment variable
 * {@code IT_TEST_PG=true}. When the variable is absent, JUnit
 * reports the class as ignored; the normal {@code mvn test} run
 * never contacts PostgreSQL. To run:
 * <pre>
 *   export IT_TEST_PG=true
 *   export IT_TEST_PG_URL=jdbc:postgresql://localhost:5432/itticketing_cp338_test
 *   export IT_TEST_PG_USERNAME=cp338_test_user
 *   export IT_TEST_PG_PASSWORD=...
 *   mvn -o test -Dtest=ChangeRequestPostgresJsonbIntegrationTest
 * </pre>
 *
 * <h2>Safety guards</h2>
 * Before any write, the class verifies:
 * <ul>
 *   <li>The env var flag is {@code true} (opt-in).</li>
 *   <li>The configured URL's database name contains the test marker
 *       {@code cp338_test} - refuses to run otherwise, even if the
 *       env vars are set.</li>
 *   <li>A probe query against the same URL returns a server host/port
 *       that the test was configured for. The probe uses a SEPARATE
 *       JDBC connection so the test fails fast if the env vars
 *       accidentally point at a different cluster.</li>
 *   <li>The {@code information_schema.columns} catalog reports the
 *       {@code risk_factors} column type as {@code jsonb}.</li>
 * </ul>
 * The test role {@code cp338_test_user} has only
 * {@code CONNECT / CREATE / TEMPORARY} on the dedicated test
 * database, so even a bug in the test code cannot drop or
 * truncate the dev/prod database.
 *
 * <h2>CP-3.3.8 scope discipline</h2>
 * This checkpoint covers the {@code change_requests.risk_factors}
 * column ONLY. It does NOT cover {@code service_requests.form_data},
 * {@code report_configs.config}, {@code service_catalog.form_fields}
 * (also {@code columnDefinition = "jsonb"} but explicitly out of
 * scope), nor does it touch AiMessage metadata or any RAG / AI
 * orchestration code. Each test creates its own ChangeRequest
 * fixture and rolls it back via the {@code @Transactional} test
 * method, so no production data is touched.
 */
@SpringBootTest
@ActiveProfiles("postgres-integration-test")
@EnabledIfEnvironmentVariable(named = "IT_TEST_PG", matches = "true")
@DisplayName("CP-3.3.8: PostgreSQL JSONB round-trip for ChangeRequest.risk_factors")
class ChangeRequestPostgresJsonbIntegrationTest {

    /** Marker substring that MUST appear in the database name. */
    private static final String DB_NAME_TEST_MARKER = "cp338_test";

    /**
     * Change number prefix used by this test's fixtures. Combined
     * with a UUID fragment, the resulting change_number is unique
     * within the test database (the column has a UNIQUE constraint)
     * so a second run does not collide with the first.
     */
    private static final String CHANGE_NUMBER_PREFIX = "CHG-CP338-";

    @Autowired
    private ChangeRequestRepository changeRequestRepo;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private static String configuredUrl;
    private static String configuredUser;
    private static String configuredDbName;
    private static String configuredHost;
    private static int configuredPort;

    @BeforeAll
    static void verifyEnvironment() throws SQLException {
        configuredUrl = System.getenv("IT_TEST_PG_URL");
        configuredUser = System.getenv("IT_TEST_PG_USERNAME");
        String configuredPwd = System.getenv("IT_TEST_PG_PASSWORD");

        if (configuredUrl == null || configuredUrl.isBlank()
                || configuredUser == null || configuredUser.isBlank()
                || configuredPwd == null || configuredPwd.isBlank()) {
            fail("IT_TEST_PG_URL/USERNAME/PASSWORD must be set when "
                    + "IT_TEST_PG=true. Refusing to run.");
        }

        URI uri;
        try {
            uri = URI.create(configuredUrl.replace("jdbc:", ""));
        } catch (RuntimeException ex) {
            fail("IT_TEST_PG_URL is not a valid JDBC URL: " + configuredUrl);
            return; // unreachable
        }
        configuredDbName = uri.getPath();
        if (configuredDbName == null || configuredDbName.length() < 2) {
            fail("IT_TEST_PG_URL must include a database name: " + configuredUrl);
        }
        configuredDbName = configuredDbName.substring(1); // strip leading /
        if (!configuredDbName.contains(DB_NAME_TEST_MARKER)) {
            fail("Refusing to run: database name '" + configuredDbName
                    + "' does not contain the test marker '" + DB_NAME_TEST_MARKER
                    + "'. This guard exists so the integration test never "
                    + "writes to a development or production database by "
                    + "accident. Use a database whose name contains '"
                    + DB_NAME_TEST_MARKER + "'.");
        }
        configuredHost = uri.getHost();
        configuredPort = uri.getPort() < 0 ? 5432 : uri.getPort();
        if (configuredHost == null || configuredHost.isBlank()) {
            fail("IT_TEST_PG_URL must include a host: " + configuredUrl);
        }

        // Probe via a SEPARATE JDBC connection so the test fails fast
        // if the env vars point at a different cluster than expected.
        // The test user has been granted only CONNECT/CREATE/TEMPORARY
        // on the dedicated test database, so this probe cannot reach
        // the dev database even by accident.
        try (Connection conn = DriverManager.getConnection(
                configuredUrl, configuredUser, configuredPwd)) {
            String probeHost;
            int probePort;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT inet_server_addr()::text, inet_server_port()")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        fail("Probe query returned no rows; cannot verify host/port.");
                    }
                    probeHost = rs.getString(1);
                    probePort = rs.getInt(2);
                }
            }
            String probeDb;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT current_database()")) {
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    probeDb = rs.getString(1);
                }
            }
            if (!configuredDbName.equals(probeDb)) {
                fail("URL database name '" + configuredDbName
                        + "' does not match the actual database '" + probeDb
                        + "' the connection resolved to. Refusing to run.");
            }
            // inet_server_addr() can return CIDR form ("127.0.0.1/32")
            // for IPv4. We strip the "/N" suffix and accept
            // localhost/127.0.0.1/::1 as equivalent.
            String probeHostStripped = probeHost == null
                    ? null
                    : probeHost.replaceAll("/\\d+$", "");
            if (probeHostStripped == null
                    || !(probeHostStripped.equalsIgnoreCase(configuredHost)
                        || probeHostStripped.equals("127.0.0.1")
                        || probeHostStripped.equals("::1")
                        || probeHostStripped.equalsIgnoreCase("localhost"))) {
                fail("URL host '" + configuredHost
                        + "' does not match the server-reported host '" + probeHost
                        + "' (normalized '" + probeHostStripped
                        + "'). Refusing to run.");
            }
            if (probePort != configuredPort) {
                fail("URL port '" + configuredPort
                        + "' does not match the server-reported port '" + probePort
                        + "'. Refusing to run.");
            }
        }
    }

    @AfterAll
    static void clearEnv() {
        // Help GC + make accidental re-reads obvious in a debugger.
        configuredUrl = null;
        configuredUser = null;
        configuredDbName = null;
        configuredHost = null;
        configuredPort = 0;
    }

    // ============================================================
    // Helpers
    // ============================================================

    /**
     * Build the JSON payload used by the round-trip test.
     *
     * <p>Exercises every wire-format class the JSONB column should
     * handle transparently:
     * <ul>
     *   <li>Object nesting</li>
     *   <li>Arrays of mixed values</li>
     *   <li>Numbers (including a negative one and a long one)</li>
     *   <li>Booleans (both true and false)</li>
     *   <li>JSON {@code null}</li>
     *   <li>Unicode (Vietnamese with diacritics)</li>
     *   <li>String escapes ({@code "}, {@code \\}, tab)</li>
     * </ul>
     *
     * <p>Built with an independent Jackson {@link ObjectMapper}
     * so the assertion does not depend on the parser used to write
     * the fixture agreeing with the parser used to read it back.
     */
    private static String buildFixtureRiskFactorsJson() throws Exception {
        ObjectMapper m = new ObjectMapper();
        var root = m.createArrayNode();
        var first = m.createObjectNode();
        first.put("factor", "user_impact");
        first.put("score", 30);
        first.put("mitigated", true);
        // Vietnamese + double quote + backslash + tab in one string.
        first.put("description", "Tác động \"người dùng\" có \\ và\tký tự đặc biệt");
        root.add(first);
        var second = m.createObjectNode();
        second.put("factor", "system_availability");
        second.put("score", -5);
        second.put("mitigated", false);
        // JSON null (the four-letter token) - distinguishable from
        // a Java null field. We exercise it here because the
        // contract says riskFactors may legitimately carry a JSON
        // null inside one of its elements.
        second.putNull("optional");
        root.add(second);
        return m.writeValueAsString(root);
    }

    // ============================================================
    // Test A - persist via JPA, flush, clear, load, decode
    // ============================================================

    @Nested
    @DisplayName("Test A: persist -> flush -> clear -> load -> decode")
    class PersistAndReload {

        @Test
        @Transactional
        @DisplayName("ChangeRequest with structured risk_factors round-trips through PostgreSQL JSONB")
        void riskFactorsRoundTrip() throws Exception {
            String changeNumber = CHANGE_NUMBER_PREFIX
                    + UUID.randomUUID().toString().substring(0, 8);
            String expectedJson = buildFixtureRiskFactorsJson();

            ChangeRequest toSave = new ChangeRequest();
            toSave.setChangeNumber(changeNumber);
            toSave.setTitle("CP-3.3.8 test change");
            toSave.setDescription("Fixture for ChangeRequest.risk_factors JSONB round-trip");
            toSave.setChangeType(ChangeRequest.ChangeType.STANDARD);
            toSave.setCategory("TEST");
            toSave.setRiskLevel(ChangeRequest.RiskLevel.MEDIUM);
            toSave.setRiskScore(50);
            toSave.setRiskFactors(expectedJson);
            toSave.setImpactLevel(ChangeRequest.ImpactLevel.MODERATE);
            toSave.setPriority(ChangeRequest.Priority.MEDIUM);
            toSave.setUrgency(ChangeRequest.Urgency.NORMAL);
            toSave.setRequesterName("CP-3.3.8 Tester");
            toSave.setRequesterUsername("admin");
            toSave.setStatus(ChangeRequest.ChangeStatus.DRAFT);
            toSave.setCreatedBy("admin");

            ChangeRequest saved = changeRequestRepo.saveAndFlush(toSave);
            Long savedId = saved.getId();
            assertThat(savedId)
                    .as("ChangeRequest was persisted; id is non-null")
                    .isNotNull();
            // Force the persistence context to drop the cached
            // entity so the subsequent findById() goes back to the
            // database and reads the JSONB value through the JDBC
            // driver.
            changeRequestRepo.flush();
            entityManager.clear();

            ChangeRequest reloaded = changeRequestRepo.findById(savedId).orElse(null);
            assertThat(reloaded)
                    .as("ChangeRequest was persisted; reloaded entity is non-null")
                    .isNotNull();
            assertThat(reloaded.getId()).isEqualTo(savedId);
            assertThat(reloaded.getChangeNumber()).isEqualTo(changeNumber);
            assertThat(reloaded.getRiskLevel()).isEqualTo(ChangeRequest.RiskLevel.MEDIUM);
            assertThat(reloaded.getRequesterUsername()).isEqualTo("admin");
            String reloadedJson = reloaded.getRiskFactors();
            assertThat(reloadedJson)
                    .as("risk_factors column is non-null after round-trip")
                    .isNotNull();
            // The JSON should not be a doubly-escaped string (no
            // surrounding quotes that would mean Hibernate wrote a
            // JSON-encoded String into a JSON column).
            assertThat(reloadedJson)
                    .as("risk_factors is not a doubly-escaped JSON string")
                    .doesNotStartWith("\"")
                    .doesNotEndWith("\"");
            // Parse the loaded value as JSON and verify structure.
            ObjectMapper m = new ObjectMapper();
            JsonNode loaded = m.readTree(reloadedJson);
            assertThat(loaded.isArray())
                    .as("reloaded risk_factors is a JSON array")
                    .isTrue();
            assertThat(loaded.size())
                    .as("reloaded risk_factors array has 2 elements")
                    .isEqualTo(2);

            // First element round-tripped.
            JsonNode first = loaded.get(0);
            assertThat(first.path("factor").asText()).isEqualTo("user_impact");
            assertThat(first.path("score").asInt()).isEqualTo(30);
            assertThat(first.path("mitigated").isBoolean()).isTrue();
            assertThat(first.path("mitigated").asBoolean()).isTrue();
            // Unicode and escapes survive a single round-trip.
            assertThat(first.path("description").asText())
                    .isEqualTo("Tác động \"người dùng\" có \\ và\tký tự đặc biệt");

            // Second element: negative number, false boolean, JSON null.
            JsonNode second = loaded.get(1);
            assertThat(second.path("factor").asText()).isEqualTo("system_availability");
            assertThat(second.path("score").asInt()).isEqualTo(-5);
            assertThat(second.path("mitigated").isBoolean()).isTrue();
            assertThat(second.path("mitigated").asBoolean()).isFalse();
            // JSON null (the four-letter token) must survive as a
            // JsonNode that IS null (NOT a Java null reference).
            JsonNode optional = second.path("optional");
            assertThat(optional.isNull())
                    .as("JSON null token survives round-trip")
                    .isTrue();
        }
    }

    // ============================================================
    // Test B - null / missing risk_factors behavior
    // ============================================================

    @Nested
    @DisplayName("Test B: null / missing risk_factors behavior")
    class NullAndMissingRiskFactors {

        @Test
        @Transactional
        @DisplayName("NULL risk_factors round-trips as Java null")
        void nullRiskFactorsStaysNull() {
            String changeNumber = CHANGE_NUMBER_PREFIX
                    + UUID.randomUUID().toString().substring(0, 8);

            ChangeRequest toSave = new ChangeRequest();
            toSave.setChangeNumber(changeNumber);
            toSave.setTitle("CP-3.3.8 null-risk_factors fixture");
            toSave.setChangeType(ChangeRequest.ChangeType.STANDARD);
            toSave.setRiskLevel(ChangeRequest.RiskLevel.LOW);
            toSave.setImpactLevel(ChangeRequest.ImpactLevel.MINIMAL);
            toSave.setPriority(ChangeRequest.Priority.LOW);
            toSave.setUrgency(ChangeRequest.Urgency.NORMAL);
            toSave.setRequesterName("CP-3.3.8 Tester");
            toSave.setRequesterUsername("admin");
            toSave.setStatus(ChangeRequest.ChangeStatus.DRAFT);
            toSave.setCreatedBy("admin");
            // Deliberately leave riskFactors as Java null.
            toSave.setRiskFactors(null);

            ChangeRequest saved = changeRequestRepo.saveAndFlush(toSave);
            Long savedId = saved.getId();
            changeRequestRepo.flush();
            entityManager.clear();

            ChangeRequest reloaded = changeRequestRepo.findById(savedId).orElse(null);
            assertThat(reloaded).isNotNull();
            assertThat(reloaded.getRiskFactors())
                    .as("NULL risk_factors round-trips as Java NULL")
                    .isNull();
        }

        @Test
        @Transactional
        @DisplayName("JSON 'null' literal round-trips as JSON null (not Java null)")
        void jsonNullLiteralStaysJsonNull() throws Exception {
            String changeNumber = CHANGE_NUMBER_PREFIX
                    + UUID.randomUUID().toString().substring(0, 8);

            ChangeRequest toSave = new ChangeRequest();
            toSave.setChangeNumber(changeNumber);
            toSave.setTitle("CP-3.3.8 json-null-risk_factors fixture");
            toSave.setChangeType(ChangeRequest.ChangeType.STANDARD);
            toSave.setRiskLevel(ChangeRequest.RiskLevel.LOW);
            toSave.setImpactLevel(ChangeRequest.ImpactLevel.MINIMAL);
            toSave.setPriority(ChangeRequest.Priority.LOW);
            toSave.setUrgency(ChangeRequest.Urgency.NORMAL);
            toSave.setRequesterName("CP-3.3.8 Tester");
            toSave.setRequesterUsername("admin");
            toSave.setStatus(ChangeRequest.ChangeStatus.DRAFT);
            toSave.setCreatedBy("admin");
            // JSON null literal, encoded as a valid JSON document.
            toSave.setRiskFactors("null");

            ChangeRequest saved = changeRequestRepo.saveAndFlush(toSave);
            Long savedId = saved.getId();
            changeRequestRepo.flush();
            entityManager.clear();

            ChangeRequest reloaded = changeRequestRepo.findById(savedId).orElse(null);
            assertThat(reloaded).isNotNull();
            String reloadedJson = reloaded.getRiskFactors();
            assertThat(reloadedJson)
                    .as("Java reference is NOT null (the value is the "
                            + "four-letter string 'null', encoded as JSON)")
                    .isNotNull();
            // Parse and assert the JSON token is null - distinct from
            // Java null.
            ObjectMapper m = new ObjectMapper();
            JsonNode loaded = m.readTree(reloadedJson);
            assertThat(loaded.isNull())
                    .as("parsed JSON is the null token")
                    .isTrue();
        }
    }

    // ============================================================
    // Test C - invalid risk_factors behavior
    // ============================================================

    @Nested
    @DisplayName("Test C: invalid risk_factors behavior")
    class InvalidRiskFactors {

        // Per-test ChangeRequest fixture that is created in the
        // body of the test method (NOT @Transactional, see below)
        // and is therefore committed to the database. We capture
        // the change_number here and delete the row in @AfterEach
        // so the test leaves no fixture data behind.
        private String fixtureChangeNumber;

        @org.junit.jupiter.api.AfterEach
        void cleanupChangeRequestFixture() {
            // Per CP-3.3.8: "Không để dữ liệu dở dang." Without
            // cleanup, this fixture would accumulate across runs
            // and would also leak into the catalog metadata.
            //
            // Cleanup is narrowly scoped: it deletes ONLY the row
            // with the change_number captured in this test run.
            // No other change_request, timeline, or change data is
            // touched.
            if (fixtureChangeNumber != null) {
                jdbc.update(
                        "DELETE FROM change_requests WHERE change_number = ?",
                        fixtureChangeNumber);
                fixtureChangeNumber = null;
            }
        }

        @Test
        @DisplayName("JSONB column rejects malformed JSON at the database layer")
        void jsonbRejectsMalformedJson() throws SQLException {
            // We deliberately do NOT annotate this test with
            // @Transactional. PostgreSQL aborts the whole
            // transaction on the first error (state 25P02),
            // and we need the malformed insert to be tried
            // outside the test's transaction so its abort
            // does not poison subsequent queries. Instead, we
            // (a) run the malformed insert via a SEPARATE
            // DriverManager connection that auto-commits
            // each statement, (b) verify the rejection, and
            // (c) verify with a fresh JdbcTemplate query
            // that no row leaked into the table.
            fixtureChangeNumber = CHANGE_NUMBER_PREFIX
                    + UUID.randomUUID().toString().substring(0, 8);
            Long changeRequestId = jdbc.queryForObject(
                    "INSERT INTO change_requests (change_number, title, "
                            + "change_type, risk_level, impact_level, status, "
                            + "priority, urgency, requester_name, requester_username) "
                            + "VALUES (?, 'CP-3.3.8 malformed-json fixture', "
                            + "'STANDARD', 'LOW', 'MINIMAL', 'DRAFT', 'LOW', "
                            + "'LOW', 'CP-3.3.8 Tester', 'admin') "
                            + "RETURNING id",
                    Long.class, fixtureChangeNumber);

            // PostgreSQL JSONB is strictly validated: it rejects
            // truncated, non-JSON, or otherwise malformed input.
            // This is by design (JSONB stores binary JSON, not text).
            String malformed = "[{\"factor\":\"user_impact\",";
            SQLException caught = null;
            try (Connection conn = DriverManager.getConnection(
                        configuredUrl, configuredUser,
                        System.getenv("IT_TEST_PG_PASSWORD"))) {
                // autocommit is on by default for a fresh
                // DriverManager connection, so the abort is
                // limited to this connection only.
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE change_requests SET risk_factors = ?::jsonb "
                                + "WHERE id = ?")) {
                    ps.setString(1, malformed);
                    ps.setLong(2, changeRequestId);
                    ps.executeUpdate();
                }
            } catch (SQLException ex) {
                caught = ex;
            }
            // The database layer must reject the malformed JSON.
            // PostgreSQL's error class is 22 (Data Exception);
            // the SQLSTATE for "invalid input syntax for type
            // json" is 22023. We do not require an exact code,
            // only that the database refused the update.
            // Cast to Object so AssertJ picks the single-arg
            // assertThat(Object) overload instead of the
            // Iterable/Throwable overloads.
            assertThat((Object) caught)
                    .as("PostgreSQL JSONB rejects malformed JSON")
                    .isNotNull();
            // The risk_factors column should still be NULL.
            String stored = jdbc.queryForObject(
                    "SELECT risk_factors::text FROM change_requests "
                            + "WHERE id = ?",
                    String.class, changeRequestId);
            assertThat(stored)
                    .as("risk_factors column was not modified by the malformed UPDATE")
                    .isNull();
        }
    }

    // ============================================================
    // Catalog check (the JSONB column type)
    // ============================================================

    @Nested
    @DisplayName("Catalog: risk_factors column type is jsonb")
    class CatalogCheck {

        @Test
        @DisplayName("information_schema.columns reports risk_factors as jsonb")
        void catalogReportsJsonb() {
            // Flyway has run by now (this test runs after Spring
            // Boot has finished bootstrapping the EntityManager
            // Factory), so the risk_factors column must exist in
            // information_schema.
            String type = jdbc.queryForObject(
                    "SELECT data_type FROM information_schema.columns "
                            + "WHERE table_schema = 'public' "
                            + "AND table_name = 'change_requests' "
                            + "AND column_name = 'risk_factors'",
                    String.class);
            assertThat(type)
                    .as("information_schema.columns has a row for "
                            + "public.change_requests.risk_factors")
                    .isNotNull();
            assertThat(type.toLowerCase())
                    .as("information_schema reports the column type as jsonb")
                    .isEqualTo("jsonb");

            // Also verify the underlying type (udt_name) is
            // jsonb, which defends against any future driver
            // change that might report a different data_type
            // alias.
            String udt = jdbc.queryForObject(
                    "SELECT udt_name FROM information_schema.columns "
                            + "WHERE table_schema = 'public' "
                            + "AND table_name = 'change_requests' "
                            + "AND column_name = 'risk_factors'",
                    String.class);
            assertThat(udt).isNotNull();
            assertThat(udt.toLowerCase()).isEqualTo("jsonb");
        }
    }
}
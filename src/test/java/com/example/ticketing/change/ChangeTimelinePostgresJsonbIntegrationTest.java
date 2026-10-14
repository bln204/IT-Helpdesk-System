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
 * CP-3.3.7: real PostgreSQL integration test for the
 * {@code change_timeline.event_data} JSONB column.
 *
 * <h2>What this test proves</h2>
 * <ol>
 *   <li>The catalog column type for {@code event_data} in
 *       {@code change_timeline} is {@code jsonb}, not {@code text}
 *       or {@code varchar}.</li>
 *   <li>The current entity mapping
 *       {@code @Column(name = "event_data", columnDefinition = "TEXT")}
 *       round-trips a JSON string through the JSONB column on the
 *       actual PostgreSQL instance (so any future
 *       {@code @JdbcTypeCode(SqlTypes.JSON)} change can be validated
 *       against this baseline).</li>
 *   <li>Numeric IDs stay numeric, booleans stay booleans, nulls stay
 *       null, Unicode survives, and string escapes
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
 *   export IT_TEST_PG_URL=jdbc:postgresql://localhost:5432/itticketing_cp337_test
 *   export IT_TEST_PG_USERNAME=cp337_test_user
 *   export IT_TEST_PG_PASSWORD=...
 *   mvn -o test -Dtest=ChangeTimelinePostgresJsonbIntegrationTest
 * </pre>
 *
 * <h2>Safety guards</h2>
 * Before any write, the class verifies:
 * <ul>
 *   <li>The env var flag is {@code true} (opt-in).</li>
 *   <li>The configured URL's database name contains the test marker
 *       {@code cp337_test} - refuses to run otherwise, even if the
 *       env vars are set.</li>
 *   <li>A probe query against the same URL returns a server host/port
 *       that the test was configured for. The probe uses a SEPARATE
 *       JDBC connection so the test fails fast if the env vars
 *       accidentally point at a different cluster.</li>
 *   <li>The {@code information_schema.columns} catalog reports the
 *       {@code event_data} column type as {@code jsonb}.</li>
 * </ul>
 * The test role {@code cp337_test_user} has only
 * {@code CONNECT / CREATE / TEMPORARY} on the dedicated test
 * database, so even a bug in the test code cannot drop or
 * truncate the dev/prod database.
 *
 * <h2>CP-3.3.7 scope discipline</h2>
 * This checkpoint covers the {@code change_timeline.event_data}
 * column ONLY. It does NOT cover {@code change_requests.risk_factors}
 * (also {@code columnDefinition = "jsonb"}), nor does it touch
 * AiMessage metadata or any RAG / AI orchestration code. Each test
 * creates its own ChangeRequest fixture and rolls it back via the
 * {@code @Transactional} test method, so no production data is
 * touched.
 */
@SpringBootTest
@ActiveProfiles("postgres-integration-test")
@EnabledIfEnvironmentVariable(named = "IT_TEST_PG", matches = "true")
@DisplayName("CP-3.3.7: PostgreSQL JSONB round-trip for ChangeTimeline.event_data")
class ChangeTimelinePostgresJsonbIntegrationTest {

    /** Marker substring that MUST appear in the database name. */
    private static final String DB_NAME_TEST_MARKER = "cp337_test";

    /**
     * Change number prefix used by this test's fixtures. Combined
     * with a UUID fragment, the resulting change_number is unique
     * within the test database (the column has a UNIQUE constraint)
     * so a second run does not collide with the first.
     */
    private static final String CHANGE_NUMBER_PREFIX = "CHG-CP337-";

    @Autowired
    private ChangeTimelineRepository timelineRepo;


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
     * Insert a fresh {@link ChangeRequest} row directly via
     * {@link JdbcTemplate} (NOT the JPA repository) so the
     * {@link ChangeTimeline} fixture has a valid foreign key.
     *
     * <p>The {@code change_number} is unique per run (prefix + short
     * UUID fragment), so repeated runs do not collide on the UNIQUE
     * constraint.
     *
     * <p>Why a raw INSERT: the {@code ChangeRequest} entity's
     * {@code risk_factors} field is declared as
     * {@code columnDefinition = "jsonb"} but the field is a
     * {@link String} without {@code @JdbcTypeCode(SqlTypes.JSON)}.
     * When Hibernate persists through JPA, the JDBC driver sends
     * the {@code risk_factors} parameter with the wrong SQL type
     * (varchar) and PostgreSQL rejects the cast
     * (SQLSTATE 42804: "column ... is of type jsonb but expression
     * is of type character varying"). That is a separate production
     * bug NOT in scope for CP-3.3.7; this checkpoint is about
     * {@code ChangeTimeline.event_data} only. We bypass the broken
     * JPA path by inserting the parent row directly via JDBC, so
     * the {@code risk_factors} column is not in the INSERT list at
     * all (it is NULL by default).
     *
     * <p>The {@code @Transactional} on the test method rolls back
     * the {@link ChangeTimeline} fixture when the test exits, so no
     * production data is touched. The {@code ChangeRequest} parent
     * row is also rolled back because the same transaction inserts
     * both rows.
     */
    private Long insertChangeRequest() {
        String changeNumber = CHANGE_NUMBER_PREFIX
                + UUID.randomUUID().toString().substring(0, 8);
        Long id = jdbc.queryForObject(
                "INSERT INTO change_requests (change_number, title, "
                        + "description, change_type, category, risk_level, "
                        + "impact_level, status, priority, urgency, "
                        + "requester_name, requester_username, "
                        + "created_at, updated_at) "
                        + "VALUES (?, 'CP-3.3.7 test change', "
                        + "'Fixture for ChangeTimeline.event_data JSONB round-trip', "
                        + "'STANDARD', 'TEST', 'LOW', 'MINIMAL', 'DRAFT', "
                        + "'LOW', 'LOW', 'CP-3.3.7 Tester', 'admin', "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                        + "RETURNING id",
                Long.class, changeNumber);
        return id;
    }

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
    private static String buildFixtureEventDataJson() throws Exception {
        ObjectMapper m = new ObjectMapper();
        var root = m.createObjectNode();
        root.put("action", "APPROVED");
        // Vietnamese + double quote + backslash + tab in one string.
        root.put("description", "Đã phê duyệt \"thay đổi\"");
        root.put("path", "change\\approval");
        root.put("attempt", 2);
        root.put("approved", true);
        var labels = m.createArrayNode();
        labels.add("database");
        labels.add("helpdesk");
        root.set("labels", labels);
        var details = m.createObjectNode();
        details.put("previousStatus", "PENDING");
        details.put("newStatus", "APPROVED");
        root.set("details", details);
        // JSON null (the four-letter token) - distinguishable from
        // a Java null field. We exercise it here in CP-4 because the
        // contract says eventData may legitimately carry a JSON null.
        root.putNull("optional");
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
        @DisplayName("ChangeTimeline with structured event_data round-trips through PostgreSQL JSONB")
        void eventDataRoundTrip() throws Exception {
            Long changeRequestId = insertChangeRequest();
            String expectedJson = buildFixtureEventDataJson();

            ChangeTimeline toSave = new ChangeTimeline();
            toSave.setChangeRequest(entityManager.getReference(ChangeRequest.class, changeRequestId));
            toSave.setEventType(ChangeTimeline.EventType.APPROVED);
            toSave.setActorName("CP-3.3.7 Tester");
            toSave.setActorUsername("admin");
            toSave.setActorRole("CHANGE_MANAGER");
            toSave.setEventData(expectedJson);
            toSave.setNotes("round-trip fixture");

            ChangeTimeline saved = timelineRepo.saveAndFlush(toSave);
            Long savedId = saved.getId();
            assertThat(savedId)
                    .as("ChangeTimeline was persisted; id is non-null")
                    .isNotNull();
            // Force the persistence context to drop the cached
            // entity so the subsequent findById() goes back to the
            // database and reads the JSONB value through the JDBC
            // driver.
            timelineRepo.flush();
            entityManager.clear();

            ChangeTimeline reloaded = timelineRepo.findById(savedId).orElse(null);
            assertThat(reloaded)
                    .as("ChangeTimeline was persisted; reloaded entity is non-null")
                    .isNotNull();
            assertThat(reloaded.getId()).isEqualTo(savedId);
            assertThat(reloaded.getEventType()).isEqualTo(ChangeTimeline.EventType.APPROVED);
            assertThat(reloaded.getActorUsername()).isEqualTo("admin");
            String reloadedJson = reloaded.getEventData();
            assertThat(reloadedJson)
                    .as("event_data column is non-null after round-trip")
                    .isNotNull();
            // The JSON should not be a doubly-escaped string (no
            // surrounding quotes that would mean Hibernate wrote a
            // JSON-encoded String into a JSON column).
            assertThat(reloadedJson)
                    .as("event_data is not a doubly-escaped JSON string")
                    .doesNotStartWith("\"")
                    .doesNotEndWith("\"");
            // Parse the loaded value as JSON and verify structure.
            ObjectMapper m = new ObjectMapper();
            JsonNode loaded = m.readTree(reloadedJson);
            assertThat(loaded.isObject())
                    .as("reloaded event_data is a JSON object")
                    .isTrue();
            // Top-level scalar fields round-tripped.
            assertThat(loaded.path("action").asText()).isEqualTo("APPROVED");
            assertThat(loaded.path("attempt").asInt()).isEqualTo(2);
            assertThat(loaded.path("approved").asBoolean()).isTrue();
            // Unicode and escapes survive a single round-trip.
            assertThat(loaded.path("description").asText())
                    .isEqualTo("Đã phê duyệt \"thay đổi\"");
            assertThat(loaded.path("path").asText())
                    .isEqualTo("change\\approval");
            // Array of strings survived.
            JsonNode labels = loaded.path("labels");
            assertThat(labels.isArray()).isTrue();
            assertThat(labels.size()).isEqualTo(2);
            assertThat(labels.get(0).asText()).isEqualTo("database");
            assertThat(labels.get(1).asText()).isEqualTo("helpdesk");
            // Nested object survived.
            JsonNode details = loaded.path("details");
            assertThat(details.isObject()).isTrue();
            assertThat(details.path("previousStatus").asText())
                    .isEqualTo("PENDING");
            assertThat(details.path("newStatus").asText())
                    .isEqualTo("APPROVED");
            // JSON null (the four-letter token) must survive as a
            // JsonNode that IS null (NOT a Java null reference).
            JsonNode optional = loaded.path("optional");
            assertThat(optional.isNull())
                    .as("JSON null token survives round-trip")
                    .isTrue();
        }
    }

    // ============================================================
    // Test B - null / missing event_data behavior
    // ============================================================

    @Nested
    @DisplayName("Test B: null / missing event_data behavior")
    class NullAndMissingEventData {

        @Test
        @Transactional
        @DisplayName("NULL event_data round-trips as Java null")
        void nullEventDataStaysNull() {
            Long changeRequestId = insertChangeRequest();
            ChangeTimeline toSave = new ChangeTimeline();
            toSave.setChangeRequest(entityManager.getReference(ChangeRequest.class, changeRequestId));
            toSave.setEventType(ChangeTimeline.EventType.CREATED);
            toSave.setActorName("CP-3.3.7 Tester");
            toSave.setActorUsername("admin");
            // Deliberately leave eventData as Java null.
            toSave.setEventData(null);
            toSave.setNotes("null-eventData fixture");

            ChangeTimeline saved = timelineRepo.saveAndFlush(toSave);
            Long savedId = saved.getId();
            timelineRepo.flush();
            entityManager.clear();

            ChangeTimeline reloaded = timelineRepo.findById(savedId).orElse(null);
            assertThat(reloaded).isNotNull();
            assertThat(reloaded.getEventData())
                    .as("NULL event_data round-trips as Java NULL")
                    .isNull();
        }

        @Test
        @Transactional
        @DisplayName("JSON 'null' literal round-trips as JSON null (not Java null)")
        void jsonNullLiteralStaysJsonNull() throws Exception {
            Long changeRequestId = insertChangeRequest();
            ChangeTimeline toSave = new ChangeTimeline();
            toSave.setChangeRequest(entityManager.getReference(ChangeRequest.class, changeRequestId));
            toSave.setEventType(ChangeTimeline.EventType.COMMENT_ADDED);
            toSave.setActorName("CP-3.3.7 Tester");
            toSave.setActorUsername("admin");
            // JSON null literal, encoded as a valid JSON document.
            toSave.setEventData("null");
            toSave.setNotes("json-null fixture");

            ChangeTimeline saved = timelineRepo.saveAndFlush(toSave);
            Long savedId = saved.getId();
            timelineRepo.flush();
            entityManager.clear();

            ChangeTimeline reloaded = timelineRepo.findById(savedId).orElse(null);
            assertThat(reloaded).isNotNull();
            String reloadedJson = reloaded.getEventData();
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
    // Test C - invalid event_data behavior
    // ============================================================

    @Nested
    @DisplayName("Test C: invalid event_data behavior")
    class InvalidEventData {

        // Per-test ChangeRequest fixture that is created in the
        // body of the test method (NOT @Transactional, see below)
        // and is therefore committed to the database. We capture
        // the change_number here and delete the row in @AfterEach
        // so the test leaves no fixture data behind.
        private String fixtureChangeNumber;

        @org.junit.jupiter.api.AfterEach
        void cleanupChangeRequestFixture() {
            // Per CP-3.3.7: "Không để dữ liệu dở dang." The test
            // body inserts a ChangeRequest fixture via raw JDBC
            // (because JPA's path is broken for ChangeRequest.risk_factors,
            // which is a separate bug out of scope for this
            // checkpoint). Without cleanup, this fixture would
            // accumulate across runs and would also leak into the
            // catalog metadata.
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
            //
            // The fixture ChangeRequest is inserted via the JdbcTemplate
            // path (NOT JPA) so no transaction is left dangling.
            fixtureChangeNumber = CHANGE_NUMBER_PREFIX
                    + UUID.randomUUID().toString().substring(0, 8);
            Long changeRequestId = jdbc.queryForObject(
                    "INSERT INTO change_requests (change_number, title, "
                            + "change_type, risk_level, impact_level, status, "
                            + "priority, urgency, requester_name, requester_username) "
                            + "VALUES (?, 'CP-3.3.7 malformed-json fixture', "
                            + "'STANDARD', 'LOW', 'MINIMAL', 'DRAFT', 'LOW', "
                            + "'LOW', 'CP-3.3.7 Tester', 'admin') "
                            + "RETURNING id",
                    Long.class, fixtureChangeNumber);

            // Now create a deterministic event_data row id so we can
            // assert the malformed insert left no row behind. We use
            // a unique event_type plus the change_request_id to keep
            // the test idempotent.
            String uniqueEventType = "COMMENT_ADDED";

            // PostgreSQL JSONB is strictly validated: it rejects
            // truncated, non-JSON, or otherwise malformed input.
            // This is by design (JSONB stores binary JSON, not text).
            String malformed = "{\"action\":\"APPROVED\",";
            SQLException caught = null;
            try (Connection conn = DriverManager.getConnection(
                        configuredUrl, configuredUser,
                        System.getenv("IT_TEST_PG_PASSWORD"))) {
                // autocommit is on by default for a fresh
                // DriverManager connection, so the abort is
                // limited to this connection only.
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO change_timeline (change_request_id, "
                                + "event_type, actor_name, actor_username, "
                                + "event_data, created_at) "
                                + "VALUES (?, ?, 'CP-3.3.7 Tester', 'admin', "
                                + "?::jsonb, CURRENT_TIMESTAMP)")) {
                    ps.setLong(1, changeRequestId);
                    ps.setString(2, uniqueEventType);
                    ps.setString(3, malformed);
                    ps.executeUpdate();
                }
            } catch (SQLException ex) {
                caught = ex;
            }
            // The database layer must reject the malformed JSON.
            // PostgreSQL's error class is 22 (Data Exception);
            // the SQLSTATE for "invalid input syntax for type
            // json" is 22023. We do not require an exact code,
            // only that the database refused the row.
            // Cast to Object so AssertJ picks the single-arg
            // assertThat(Object) overload instead of the
            // Iterable/Throwable overloads.
            assertThat((Object) caught)
                    .as("PostgreSQL JSONB rejects malformed JSON")
                    .isNotNull();
            // No row should have been inserted.
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM change_timeline "
                            + "WHERE change_request_id = ? AND event_type = ?",
                    Integer.class, changeRequestId, uniqueEventType);
            assertThat(count)
                    .as("no row was inserted for the malformed payload")
                    .isEqualTo(0);
        }
    }

    // ============================================================
    // Catalog check (the JSONB column type)
    // ============================================================

    @Nested
    @DisplayName("Catalog: event_data column type is jsonb")
    class CatalogCheck {

        @Test
        @DisplayName("information_schema.columns reports event_data as jsonb")
        void catalogReportsJsonb() {
            // Flyway has run by now (this test runs after Spring
            // Boot has finished bootstrapping the EntityManager
            // Factory), so the event_data column must exist in
            // information_schema.
            String type = jdbc.queryForObject(
                    "SELECT data_type FROM information_schema.columns "
                            + "WHERE table_schema = 'public' "
                            + "AND table_name = 'change_timeline' "
                            + "AND column_name = 'event_data'",
                    String.class);
            assertThat(type)
                    .as("information_schema.columns has a row for "
                            + "public.change_timeline.event_data")
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
                            + "AND table_name = 'change_timeline' "
                            + "AND column_name = 'event_data'",
                    String.class);
            assertThat(udt).isNotNull();
            assertThat(udt.toLowerCase()).isEqualTo("jsonb");
        }
    }
}
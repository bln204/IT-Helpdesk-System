package com.example.ticketing.report;

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
import org.junit.jupiter.api.AfterEach;
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
 * CP-3.3.11: real PostgreSQL integration test for the
 * {@code report_configs.config} JSONB column.
 *
 * <h2>What this test proves</h2>
 * <ol>
 *   <li>The catalog column type for {@code config} in
 *       {@code report_configs} is {@code jsonb}.</li>
 *   <li>The current entity mapping
 *       {@code @Column(columnDefinition = "jsonb")} WITHOUT
 *       {@code @JdbcTypeCode(SqlTypes.JSON)} either round-trips a
 *       JSON string through the JSONB column on the actual
 *       PostgreSQL instance, OR fails with SQLSTATE 42804 -
 *       the same bug CP-3.3.7 documented for
 *       {@code change_timeline.event_data}, CP-3.3.8 for
 *       {@code change_requests.risk_factors}, CP-3.3.9 for
 *       {@code service_requests.form_data} and CP-3.3.10 for
 *       {@code service_catalog.form_fields}.</li>
 *   <li>Java {@code null} is persisted and read back as Java
 *       {@code null} when the field is left at its default.</li>
 *   <li>The seed rows inserted by V53 (5 default report
 *       templates with non-null JSON configs) survive the
 *       baseline run untouched - the test class never deletes
 *       or modifies them.</li>
 *   <li>The full JPA CRUD path through {@link ReportService}
 *       (create + update) round-trips JSON without producing
 *       the SQLSTATE 42804 binding error.</li>
 *   <li>Malformed JSON is rejected at the database layer by
 *       the JSONB column, leaving no row behind.</li>
 * </ol>
 *
 * <h2>Why a service-level test?</h2>
 * {@link ReportConfigRepository#save(Object)} delegates to
 * {@code EntityManager.persist} / {@code merge}, which is the
 * exact same binding path that {@code createReport(...)} and
 * {@code updateReport(...)} go through. Running the test
 * through {@code ReportService} exercises the production code
 * shape - service, repository and entity together - and is
 * stronger evidence than going around the service.
 *
 * <h2>Opt-in</h2>
 * The whole class is gated by the JVM environment variable
 * {@code IT_TEST_PG=true}. When the variable is absent, JUnit
 * reports the class as ignored; the normal {@code mvn test} run
 * never contacts PostgreSQL. To run:
 * <pre>
 *   export IT_TEST_PG=true
 *   export IT_TEST_PG_URL=jdbc:postgresql://localhost:5432/itticketing_cp3311_test
 *   export IT_TEST_PG_USERNAME=...
 *   export IT_TEST_PG_PASSWORD=...
 *   mvn -o test -Dtest=ReportConfigPostgresJsonbIntegrationTest
 * </pre>
 *
 * <h2>Safety guards</h2>
 * Before any write, the class verifies:
 * <ul>
 *   <li>The env var flag is {@code true} (opt-in).</li>
 *   <li>The configured URL's database name contains the test
 *       marker {@code cp3311_test} - refuses to run otherwise,
 *       even if the env vars are set.</li>
 *   <li>A probe query against the same URL returns a server
 *       host/port that the test was configured for. The probe
 *       uses a SEPARATE JDBC connection so the test fails fast
 *       if the env vars accidentally point at a different
 *       cluster.</li>
 *   <li>The {@code information_schema.columns} catalog reports
 *       the {@code config} column type as {@code jsonb}.</li>
 *   <li>The 5 seed rows from V53 are still present (the
 *       migration has run; no one has deleted them).</li>
 * </ul>
 *
 * <h2>CP-3.3.11 scope discipline</h2>
 * This checkpoint covers the {@code report_configs.config}
 * column ONLY. It does NOT cover
 * {@code service_requests.form_data} (CP-3.3.9, already fixed),
 * {@code change_timeline.event_data} (CP-3.3.7, already fixed),
 * {@code change_requests.risk_factors} (CP-3.3.8, already fixed),
 * {@code service_catalog.form_fields} (CP-3.3.10, already fixed),
 * {@code ai_message.metadata} (CP-3.3.5, already fixed), nor
 * any other JSONB column. The fixtures created here are
 * narrowly scoped (name prefix {@code CP3311-}) and cleaned up
 * per-test; the V53 seed data is never touched.
 */
@SpringBootTest
@ActiveProfiles("postgres-integration-test")
@EnabledIfEnvironmentVariable(named = "IT_TEST_PG", matches = "true")
@DisplayName("CP-3.3.11: PostgreSQL JSONB round-trip for ReportConfig.config")
class ReportConfigPostgresJsonbIntegrationTest {

    /** Marker substring that MUST appear in the database name. */
    private static final String DB_NAME_TEST_MARKER = "cp3311_test";

    /**
     * Name prefix used by this test's fixtures. Combined with a
     * UUID fragment, the resulting name is unique within the
     * test database. The {@code report_configs} table has no
     * UNIQUE constraint on {@code name}, so a fresh prefix keeps
     * logs and assertions easy to read and avoids accidental
     * cross-test pollution.
     */
    private static final String REPORT_NAME_PREFIX = "CP3311-";

    @Autowired
    private ReportService reportService;

    @Autowired
    private ReportConfigRepository reportConfigRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private static String configuredUrl;
    private static String configuredUser;
    private static String configuredDbName;
    private static String configuredHost;
    private static int configuredPort;
    private static int v53SeedRowCount;

    @BeforeAll
    static void verifyEnvironment() throws SQLException {
        // Probe-only verification: confirms the URL points at
        // the expected database, host, port and that the
        // connection is alive. Schema-aware checks
        // (information_schema, V53 seed rows) are deferred
        // to {@link #verifySchemaAfterSpringContext()} which
        // runs AFTER Spring Boot has bootstrapped the
        // EntityManagerFactory (and therefore AFTER Flyway
        // has applied the migrations). The probe cannot
        // assume the schema is present because this method
        // runs before Spring context initialisation.
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
        int q = configuredDbName.indexOf('?');
        if (q > 0) {
            configuredDbName = configuredDbName.substring(0, q);
        }
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

        // Probe via a SEPARATE JDBC connection so the test
        // fails fast if the env vars point at a different
        // cluster than expected.
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
            // inet_server_addr() can return CIDR form
            // ("127.0.0.1/32") for IPv4 or "::1/128" for
            // IPv6. We strip the "/N" suffix and accept
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

    /**
     * Schema-aware verification, deferred to a non-static
     * hook that runs AFTER Spring Boot has bootstrapped the
     * {@code EntityManagerFactory} (and therefore AFTER
     * Flyway has applied the migrations). Runs once per
     * test class instance, so it executes exactly once
     * before any nested test.
     */
    @org.junit.jupiter.api.BeforeEach
    void verifySchemaAfterSpringContext() {
        // V53 seed rows: the migration inserts 5 default
        // report templates. We refuse to run if those rows
        // are missing - that would mean either Flyway did
        // not run, or the database is not what the test
        // expects.
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_configs "
                        + "WHERE name IN ('Ticket Volume Report', "
                        + "  'SLA Compliance Report', "
                        + "  'Agent Performance', "
                        + "  'Asset Health Overview', "
                        + "  'Change Success Rate')",
                Integer.class);
        v53SeedRowCount = (count == null ? 0 : count);
        if (v53SeedRowCount != 5) {
            fail("Expected 5 V53 seed rows in report_configs but found "
                    + v53SeedRowCount + ". Flyway may not have run, or "
                    + "the database is the wrong one. Refusing to run.");
        }
    }

    @AfterAll
    static void clearEnv() {
        configuredUrl = null;
        configuredUser = null;
        configuredDbName = null;
        configuredHost = null;
        configuredPort = 0;
        v53SeedRowCount = 0;
    }

    // ============================================================
    // Per-test fixture cleanup
    // ============================================================

    /**
     * Last fixture name created during a test that writes to
     * {@code report_configs} but cannot rely on
     * {@code @Transactional} rollback (because the test has to
     * commit the row in order to verify it survives across a
     * flush + clear cycle). Captured here so {@code @AfterEach}
     * can remove exactly that row and nothing else.
     */
    private String lastFixtureName;

    @AfterEach
    void cleanupReportConfigFixture() {
        // Narrow-scoped cleanup: delete only the row with the
        // name captured in the current test, if any. The V53
        // seed rows and any unrelated data are never touched.
        if (lastFixtureName != null) {
            jdbc.update("DELETE FROM report_configs WHERE name = ?",
                    lastFixtureName);
            lastFixtureName = null;
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    /**
     * Build the JSON payload used by the round-trip test.
     *
     * <p>Exercises every wire-format class the JSONB column
     * should handle transparently:
     * <ul>
     *   <li>Nested object ({@code filters}) with key/value
     *       pairs</li>
     *   <li>String array ({@code groupBy}) and nested array
     *       ({@code metrics})</li>
     *   <li>Booleans (both true and false)</li>
     *   <li>Integers (positive, zero and negative)</li>
     *   <li>JSON {@code null}</li>
     *   <li>Unicode (Vietnamese with diacritics)</li>
     *   <li>String escapes ({@code "}, {@code \\}, tab)</li>
     * </ul>
     *
     * <p>Built with an independent Jackson {@link ObjectMapper}
     * so the assertion does not depend on the parser used to
     * write the fixture agreeing with the parser used to read
     * it back.
     */
    private static String buildFixtureConfigJson() throws Exception {
        ObjectMapper m = new ObjectMapper();
        var root = m.createObjectNode();
        root.put("chartType", "bar");
        root.put("groupBy", "day");
        // Unicode + double quote + backslash + tab in one string.
        root.put("title", "Báo cáo \"test\" có \\ và\tký tự đặc biệt");
        var metrics = m.createArrayNode();
        metrics.add("count");
        metrics.add("resolved");
        metrics.add("pending");
        root.set("metrics", metrics);
        var filters = m.createObjectNode();
        filters.put("dateRange", "last30days");
        filters.put("minCount", 0);
        filters.put("includeBreached", true);
        // JSON null (the four-letter token) - distinguishable
        // from a Java null field. We exercise it here because
        // the report contract says config may legitimately
        // carry a JSON null for an optional filter.
        filters.putNull("subCategory");
        root.set("filters", filters);
        return m.writeValueAsString(root);
    }

    /**
     * Build a fresh {@link ReportConfig} fixture with the
     * required NOT NULL constraints satisfied but with
     * {@code config} left as the caller's responsibility.
     *
     * <p>The {@code report_configs} table has no UNIQUE
     * constraint on {@code name}, so a
     * {@code REPORT_NAME_PREFIX + UUID} suffix is enough to
     * make the row identifiable in logs and to the
     * {@code @AfterEach} cleanup hook.
     */
    private static ReportConfig buildFixture(String configJson) {
        String name = REPORT_NAME_PREFIX
                + UUID.randomUUID().toString().substring(0, 8);
        ReportConfig r = new ReportConfig();
        r.setName(name);
        r.setDescription("CP-3.3.11 fixture for ReportConfig.config JSONB round-trip");
        r.setReportType(ReportConfig.ReportType.CUSTOM);
        r.setCategory("CP3311");
        r.setConfig(configJson);
        r.setIsScheduled(false);
        r.setOutputFormat("TABLE");
        r.setIsPublic(false);
        r.setAllowedRoles("[]");
        return r;
    }

    // ============================================================
    // Test A - persist via JPA, flush, clear, load, decode
    // ============================================================

    @Nested
    @DisplayName("Test A: persist -> flush -> clear -> load -> decode")
    class PersistAndReload {

        @Test
        @DisplayName("ReportConfig with structured config round-trips through PostgreSQL JSONB")
        void configRoundTrip() throws Exception {
            String expectedJson = buildFixtureConfigJson();
            ReportConfig toSave = buildFixture(expectedJson);
            lastFixtureName = toSave.getName();

            // Drive the create path through the production
            // service method, exactly as the REST controller
            // would. This goes through:
            //   ReportService.createReport
            //     -> ReportConfigRepository.save
            //       -> EntityManager.persist
            //         -> Hibernate INSERT
            // which is the binding path under investigation.
            ReportConfig saved = reportService.createReport(toSave, "cp3311-test");
            Long savedId = saved.getId();
            assertThat(savedId)
                    .as("ReportConfig was persisted; id is non-null")
                    .isNotNull();
            // Force the persistence context to drop the cached
            // entity so the subsequent findById() goes back to
            // the database and reads the JSONB value through
            // the JDBC driver.
            reportConfigRepository.flush();
            entityManager.clear();

            ReportConfig reloaded = reportConfigRepository.findById(savedId).orElse(null);
            assertThat(reloaded)
                    .as("ReportConfig was persisted; reloaded entity is non-null")
                    .isNotNull();
            assertThat(reloaded.getId()).isEqualTo(savedId);
            assertThat(reloaded.getName()).isEqualTo(saved.getName());
            assertThat(reloaded.getCategory()).isEqualTo("CP3311");
            String reloadedJson = reloaded.getConfig();
            assertThat(reloadedJson)
                    .as("config column is non-null after round-trip")
                    .isNotNull();
            // The JSON should not be a doubly-escaped string (no
            // surrounding quotes that would mean Hibernate wrote
            // a JSON-encoded String into a JSON column).
            assertThat(reloadedJson)
                    .as("config is not a doubly-escaped JSON string")
                    .doesNotStartWith("\"")
                    .doesNotEndWith("\"");
            // Parse the loaded value as JSON and verify
            // structure.
            ObjectMapper m = new ObjectMapper();
            JsonNode loaded = m.readTree(reloadedJson);
            assertThat(loaded.isObject())
                    .as("reloaded config is a JSON object")
                    .isTrue();
            assertThat(loaded.path("chartType").asText())
                    .as("chartType string survives round-trip")
                    .isEqualTo("bar");
            assertThat(loaded.path("groupBy").asText())
                    .isEqualTo("day");
            // Unicode and escapes survive a single round-trip.
            assertThat(loaded.path("title").asText())
                    .isEqualTo("Báo cáo \"test\" có \\ và\tký tự đặc biệt");

            JsonNode metrics = loaded.path("metrics");
            assertThat(metrics.isArray()).isTrue();
            assertThat(metrics.size()).isEqualTo(3);
            assertThat(metrics.get(0).asText()).isEqualTo("count");
            assertThat(metrics.get(1).asText()).isEqualTo("resolved");
            assertThat(metrics.get(2).asText()).isEqualTo("pending");

            JsonNode filters = loaded.path("filters");
            assertThat(filters.isObject()).isTrue();
            assertThat(filters.path("dateRange").asText())
                    .isEqualTo("last30days");
            assertThat(filters.path("minCount").asInt()).isEqualTo(0);
            assertThat(filters.path("includeBreached").isBoolean())
                    .isTrue();
            assertThat(filters.path("includeBreached").asBoolean())
                    .isTrue();
            // JSON null (the four-letter token) must survive
            // as a JsonNode that IS null (NOT a Java null
            // reference).
            JsonNode subCategory = filters.path("subCategory");
            assertThat(subCategory.isNull())
                    .as("JSON null token survives round-trip")
                    .isTrue();
        }

        @Test
        @DisplayName("ReportService.updateReport rewrites config without SQLSTATE 42804")
        void updateReportConfigRoundTrip() throws Exception {
            String initialJson = "{\"chartType\": \"line\", \"metrics\": [\"a\"]}";
            String updatedJson = buildFixtureConfigJson();
            ReportConfig seed = buildFixture(initialJson);
            lastFixtureName = seed.getName();
            ReportConfig created = reportService.createReport(seed, "cp3311-test");
            Long id = created.getId();

            // Build an "updates" payload using the same
            // production service path: a fresh ReportConfig
            // with the new config value, then updateReport.
            ReportConfig updates = new ReportConfig();
            updates.setName(created.getName());
            updates.setDescription("CP-3.3.11 update fixture");
            updates.setReportType(ReportConfig.ReportType.CUSTOM);
            updates.setCategory("CP3311");
            updates.setConfig(updatedJson);
            updates.setOutputFormat("TABLE");
            updates.setIsPublic(false);
            updates.setAllowedRoles("[]");
            reportService.updateReport(id, updates, "cp3311-test");
            reportConfigRepository.flush();
            entityManager.clear();

            ReportConfig reloaded = reportConfigRepository.findById(id).orElse(null);
            assertThat(reloaded).isNotNull();
            String reloadedJson = reloaded.getConfig();
            assertThat(reloadedJson)
                    .as("updated config is non-null after update")
                    .isNotNull();
            ObjectMapper m = new ObjectMapper();
            JsonNode loaded = m.readTree(reloadedJson);
            // Sanity: the updated config has the new chartType
            // (NOT the old one), proving the UPDATE was
            // committed to the JSONB column.
            assertThat(loaded.path("chartType").asText())
                    .as("update overwrote the prior JSON value")
                    .isEqualTo("bar");
            assertThat(loaded.path("title").asText())
                    .as("Unicode and escapes from update survive")
                    .isEqualTo("Báo cáo \"test\" có \\ và\tký tự đặc biệt");
        }
    }

    // ============================================================
    // Test B - null / missing config behavior
    // ============================================================

    @Nested
    @DisplayName("Test B: null / missing config behavior")
    class NullAndMissingConfig {

        @Test
        @Transactional
        @DisplayName("NULL config round-trips as Java null")
        void nullConfigStaysNull() {
            ReportConfig toSave = buildFixture(null);
            // Deliberately leave config as Java null. The
            // V53 seed templates always have a non-null
            // config, so this exercises the path the seeder
            // does NOT use.
            //
            // @Transactional makes this test a write-rollback
            // test: the row is created inside the transaction,
            // read back, asserted, and then rolled back so no
            // fixture leaks across runs.

            ReportConfig saved = reportService.createReport(toSave, "cp3311-test");
            Long savedId = saved.getId();
            reportConfigRepository.flush();
            entityManager.clear();

            ReportConfig reloaded = reportConfigRepository.findById(savedId).orElse(null);
            assertThat(reloaded).isNotNull();
            assertThat(reloaded.getConfig())
                    .as("NULL config round-trips as Java NULL")
                    .isNull();
        }

        @Test
        @Transactional
        @DisplayName("JSON 'null' literal round-trips as JSON null (not Java null)")
        void jsonNullLiteralStaysJsonNull() throws Exception {
            ReportConfig toSave = buildFixture("null");
            // JSON null literal, encoded as a valid JSON
            // document. The Java reference is a non-null
            // String holding the four characters n-u-l-l,
            // which the JSONB column should accept and the
            // JDBC driver should return unchanged.

            ReportConfig saved = reportService.createReport(toSave, "cp3311-test");
            Long savedId = saved.getId();
            reportConfigRepository.flush();
            entityManager.clear();

            ReportConfig reloaded = reportConfigRepository.findById(savedId).orElse(null);
            assertThat(reloaded).isNotNull();
            String reloadedJson = reloaded.getConfig();
            assertThat(reloadedJson)
                    .as("Java reference is NOT null (the value "
                            + "is the four-letter string 'null', "
                            + "encoded as JSON)")
                    .isNotNull();
            ObjectMapper m = new ObjectMapper();
            JsonNode loaded = m.readTree(reloadedJson);
            assertThat(loaded.isNull())
                    .as("parsed JSON is the null token")
                    .isTrue();
        }
    }

    // ============================================================
    // Test C - invalid config behavior
    // ============================================================

    @Nested
    @DisplayName("Test C: invalid config behavior")
    class InvalidConfig {

        @Test
        @DisplayName("JSONB column rejects malformed JSON at the database layer")
        void jsonbRejectsMalformedJson() throws SQLException {
            // We deliberately do NOT annotate this test with
            // @Transactional. PostgreSQL aborts the whole
            // transaction on the first error (state 25P02),
            // and we need the malformed insert to be tried
            // outside the test's transaction so its abort
            // does not poison subsequent queries. Instead, we
            // (a) capture a cleanup key for the row we will
            // insert, (b) try a malformed UPDATE via a
            // SEPARATE DriverManager connection that
            // auto-commits each statement, (c) verify the
            // rejection, and (d) verify with a fresh
            // JdbcTemplate query that the row was not
            // modified.
            String name = REPORT_NAME_PREFIX
                    + UUID.randomUUID().toString().substring(0, 8);
            lastFixtureName = name;
            Long reportId = jdbc.queryForObject(
                    "INSERT INTO report_configs (name, report_type, "
                            + "category, is_public, allowed_roles) "
                            + "VALUES (?, 'CUSTOM', 'CP3311', "
                            + "        FALSE, '[]') "
                            + "RETURNING id",
                    Long.class, name);

            // PostgreSQL JSONB is strictly validated: it
            // rejects truncated, non-JSON, or otherwise
            // malformed input. This is by design (JSONB
            // stores binary JSON, not text).
            String malformed = "{\"chartType\":";
            SQLException caught = null;
            try (Connection conn = DriverManager.getConnection(
                        configuredUrl, configuredUser,
                        System.getenv("IT_TEST_PG_PASSWORD"))) {
                // autocommit is on by default for a fresh
                // DriverManager connection, so the abort is
                // limited to this connection only.
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE report_configs SET config = ?::jsonb "
                                + "WHERE id = ?")) {
                    ps.setString(1, malformed);
                    ps.setLong(2, reportId);
                    ps.executeUpdate();
                }
            } catch (SQLException ex) {
                caught = ex;
            }
            // The database layer must reject the malformed
            // JSON. PostgreSQL's error class is 22 (Data
            // Exception); the SQLSTATE for "invalid input
            // syntax for type json" is 22023. We do not
            // require an exact code, only that the database
            // refused the update.
            // Cast to Object so AssertJ picks the
            // single-arg assertThat(Object) overload instead
            // of the Iterable/Throwable overloads.
            assertThat((Object) caught)
                    .as("PostgreSQL JSONB rejects malformed JSON")
                    .isNotNull();
            // The config column should still be NULL (we
            // never set it on the INSERT, and the malformed
            // UPDATE was rejected).
            String stored = jdbc.queryForObject(
                    "SELECT config::text FROM report_configs "
                            + "WHERE id = ?",
                    String.class, reportId);
            assertThat(stored)
                    .as("config column was not modified by the malformed UPDATE")
                    .isNull();
        }
    }

    // ============================================================
    // Catalog check (the JSONB column type)
    // ============================================================

    @Nested
    @DisplayName("Catalog: config column type is jsonb")
    class CatalogCheck {

        @Test
        @DisplayName("information_schema.columns reports config as jsonb")
        void catalogReportsJsonb() {
            // Flyway has run by now (this test runs after
            // Spring Boot has finished bootstrapping the
            // EntityManager Factory), so the config column
            // must exist in information_schema.
            String type = jdbc.queryForObject(
                    "SELECT data_type FROM information_schema.columns "
                            + "WHERE table_schema = 'public' "
                            + "AND table_name = 'report_configs' "
                            + "AND column_name = 'config'",
                    String.class);
            assertThat(type)
                    .as("information_schema.columns has a row for "
                            + "public.report_configs.config")
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
                            + "AND table_name = 'report_configs' "
                            + "AND column_name = 'config'",
                    String.class);
            assertThat(udt).isNotNull();
            assertThat(udt.toLowerCase()).isEqualTo("jsonb");
        }

        @Test
        @DisplayName("V53 seed rows with non-null JSON configs are present")
        void v53SeedRowsArePresent() {
            // Read the seeded rows back and verify the JSON
            // round-tripped through the migration's raw SQL
            // INSERT. This is a baseline-only check; after
            // the fix, the same query should return the same
            // content. We deliberately do not assert exact
            // equality with the migration text - we only
            // assert that the value is a non-null JSON object
            // and that the chartType is one of the seed
            // values.
            java.util.List<String> seededNames = jdbc.query(
                    "SELECT name FROM report_configs "
                            + "WHERE name IN ('Ticket Volume Report', "
                            + "  'SLA Compliance Report', "
                            + "  'Agent Performance', "
                            + "  'Asset Health Overview', "
                            + "  'Change Success Rate') "
                            + "ORDER BY name ASC",
                    (rs, rowNum) -> rs.getString(1));
            assertThat(seededNames)
                    .as("V53 seed rows are present and intact")
                    .hasSize(5);
            // The V53 INSERT specifies config = '{"chartType": "bar" | "gauge" | "table" | "donut", ...}'
            // for at least one of the rows. Verify that
            // chartType is a non-null string in at least one
            // row.
            String ticketVolumeConfig = jdbc.queryForObject(
                    "SELECT config::text FROM report_configs "
                            + "WHERE name = 'Ticket Volume Report'",
                    String.class);
            assertThat(ticketVolumeConfig)
                    .as("Ticket Volume Report seed config is non-null")
                    .isNotNull();
            // Parse and sanity check.
            try {
                ObjectMapper m = new ObjectMapper();
                JsonNode parsed = m.readTree(ticketVolumeConfig);
                assertThat(parsed.isObject())
                        .as("V53 seed config is a JSON object")
                        .isTrue();
                assertThat(parsed.path("chartType").asText())
                        .as("V53 seed config has chartType set")
                        .isIn("bar", "gauge", "table", "donut", "line");
            } catch (Exception ex) {
                fail("Failed to parse V53 seed config: "
                        + ex.getMessage());
            }
        }
    }
}
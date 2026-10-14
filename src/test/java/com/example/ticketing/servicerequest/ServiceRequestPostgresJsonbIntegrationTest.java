package com.example.ticketing.servicerequest;

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
 * CP-3.3.9: real PostgreSQL integration test for the
 * {@code service_requests.form_data} JSONB column.
 *
 * <h2>What this test proves</h2>
 * <ol>
 *   <li>The catalog column type for {@code form_data} in
 *       {@code service_requests} is {@code jsonb}, not {@code text}
 *       or {@code varchar}.</li>
 *   <li>The current entity mapping
 *       {@code @Column(name = "form_data", columnDefinition = "jsonb")}
 *       WITHOUT {@code @JdbcTypeCode(SqlTypes.JSON)} round-trips a
 *       JSON string through the JSONB column on the actual
 *       PostgreSQL instance, OR fails with SQLSTATE 42804
 *       ("column ... is of type jsonb but expression is of type
 *       character varying") which is the same bug CP-3.3.7
 *       documented for {@code change_timeline.event_data} and
 *       CP-3.3.8 documented for {@code change_requests.risk_factors}.</li>
 *   <li>If the baseline fails, the JSONB column must reject the
 *       payload, leaving no row behind, AND a follow-up run after
 *       adding {@code @JdbcTypeCode(SqlTypes.JSON)} to the
 *       {@link ServiceRequest#formData} mapping must round-trip
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
 *   export IT_TEST_PG_URL=jdbc:postgresql://localhost:5432/itticketing_cp339_test
 *   export IT_TEST_PG_USERNAME=cp339_test_user
 *   export IT_TEST_PG_PASSWORD=...
 *   mvn -o test -Dtest=ServiceRequestPostgresJsonbIntegrationTest
 * </pre>
 *
 * <h2>Safety guards</h2>
 * Before any write, the class verifies:
 * <ul>
 *   <li>The env var flag is {@code true} (opt-in).</li>
 *   <li>The configured URL's database name contains the test marker
 *       {@code cp339_test} - refuses to run otherwise, even if the
 *       env vars are set.</li>
 *   <li>A probe query against the same URL returns a server host/port
 *       that the test was configured for. The probe uses a SEPARATE
 *       JDBC connection so the test fails fast if the env vars
 *       accidentally point at a different cluster.</li>
 *   <li>The {@code information_schema.columns} catalog reports the
 *       {@code form_data} column type as {@code jsonb}.</li>
 * </ul>
 * The test role {@code cp339_test_user} has only
 * {@code CONNECT / CREATE / TEMPORARY} on the dedicated test
 * database, so even a bug in the test code cannot drop or
 * truncate the dev/prod database.
 *
 * <h2>CP-3.3.9 scope discipline</h2>
 * This checkpoint covers the {@code service_requests.form_data}
 * column ONLY. It does NOT cover
 * {@code service_catalog.form_fields} (also
 * {@code columnDefinition = "jsonb"} but explicitly out of scope,
 * see CP-3.3.8), nor does it touch AiMessage metadata or any
 * RAG / AI orchestration code. Each test creates its own
 * ServiceRequest fixture and rolls it back via the
 * {@code @Transactional} test method, so no production data is
 * touched.
 */
@SpringBootTest
@ActiveProfiles("postgres-integration-test")
@EnabledIfEnvironmentVariable(named = "IT_TEST_PG", matches = "true")
@DisplayName("CP-3.3.9: PostgreSQL JSONB round-trip for ServiceRequest.form_data")
class ServiceRequestPostgresJsonbIntegrationTest {

    /** Marker substring that MUST appear in the database name. */
    private static final String DB_NAME_TEST_MARKER = "cp339_test";

    /**
     * Request number prefix used by this test's fixtures. Combined
     * with a UUID fragment, the resulting request_number is unique
     * within the test database (the column has a UNIQUE constraint)
     * so a second run does not collide with the first.
     */
    private static final String REQUEST_NUMBER_PREFIX = "SR-CP339-";

    @Autowired
    private ServiceRequestRepository serviceRequestRepo;

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
        // strip query string (e.g. "?options=...") so the marker check
        // sees only the database name.
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
    private static String buildFixtureFormDataJson() throws Exception {
        ObjectMapper m = new ObjectMapper();
        var root = m.createObjectNode();
        root.put("server_name", "db-prod-01");
        root.put("access_level", "ADMIN");
        // Vietnamese + double quote + backslash + tab in one string.
        root.put("reason", "Cần truy cập \"server\" có \\ và\tký tự đặc biệt");
        root.put("approver_count", 2);
        root.put("urgent", true);
        var options = m.createArrayNode();
        options.add("read");
        options.add("write");
        options.add("admin");
        root.set("options", options);
        var meta = m.createObjectNode();
        meta.put("ip", "10.0.0.1");
        meta.put("port", 5432);
        meta.put("encrypted", false);
        root.set("meta", meta);
        // JSON null (the four-letter token) - distinguishable from
        // a Java null field. We exercise it here because the
        // contract says formData may legitimately carry a JSON null.
        root.putNull("optional");
        return m.writeValueAsString(root);
    }

    /**
     * Build a fresh {@link ServiceRequest} fixture with all
     * NOT NULL / FK constraints satisfied but with the form_data
     * field left as the caller's responsibility.
     *
     * <p>Why a helper: the ServiceRequest entity has many required
     * fields (request_number UNIQUE NOT NULL, title NOT NULL) and
     * the request_number must be unique per run (UNIQUE constraint).
     * Centralising the boilerplate keeps the actual test bodies short
     * and readable.
     *
     * <p>The helper deliberately does NOT save the entity, so each
     * test can decide what to persist and what to leave null.
     */
    private static ServiceRequest buildFixture(String formDataJson) {
        String requestNumber = REQUEST_NUMBER_PREFIX
                + UUID.randomUUID().toString().substring(0, 8);
        ServiceRequest sr = new ServiceRequest();
        sr.setRequestNumber(requestNumber);
        sr.setTitle("CP-3.3.9 test service request");
        sr.setDescription("Fixture for ServiceRequest.form_data JSONB round-trip");
        sr.setFormData(formDataJson);
        sr.setStatus(ServiceRequest.ServiceStatus.SUBMITTED);
        sr.setPriority(ServiceRequest.Priority.MEDIUM);
        sr.setRequesterName("CP-3.3.9 Tester");
        sr.setRequesterUsername("admin");
        sr.setCreatedBy("admin");
        return sr;
    }

    // ============================================================
    // Test A - persist via JPA, flush, clear, load, decode
    // ============================================================

    @Nested
    @DisplayName("Test A: persist -> flush -> clear -> load -> decode")
    class PersistAndReload {

        @Test
        @Transactional
        @DisplayName("ServiceRequest with structured form_data round-trips through PostgreSQL JSONB")
        void formDataRoundTrip() throws Exception {
            String expectedJson = buildFixtureFormDataJson();
            ServiceRequest toSave = buildFixture(expectedJson);

            ServiceRequest saved = serviceRequestRepo.saveAndFlush(toSave);
            Long savedId = saved.getId();
            assertThat(savedId)
                    .as("ServiceRequest was persisted; id is non-null")
                    .isNotNull();
            // Force the persistence context to drop the cached
            // entity so the subsequent findById() goes back to the
            // database and reads the JSONB value through the JDBC
            // driver.
            serviceRequestRepo.flush();
            entityManager.clear();

            ServiceRequest reloaded = serviceRequestRepo.findById(savedId).orElse(null);
            assertThat(reloaded)
                    .as("ServiceRequest was persisted; reloaded entity is non-null")
                    .isNotNull();
            assertThat(reloaded.getId()).isEqualTo(savedId);
            assertThat(reloaded.getRequestNumber()).isEqualTo(saved.getRequestNumber());
            assertThat(reloaded.getTitle()).isEqualTo("CP-3.3.9 test service request");
            assertThat(reloaded.getStatus()).isEqualTo(ServiceRequest.ServiceStatus.SUBMITTED);
            assertThat(reloaded.getRequesterUsername()).isEqualTo("admin");
            String reloadedJson = reloaded.getFormData();
            assertThat(reloadedJson)
                    .as("form_data column is non-null after round-trip")
                    .isNotNull();
            // The JSON should not be a doubly-escaped string (no
            // surrounding quotes that would mean Hibernate wrote a
            // JSON-encoded String into a JSON column).
            assertThat(reloadedJson)
                    .as("form_data is not a doubly-escaped JSON string")
                    .doesNotStartWith("\"")
                    .doesNotEndWith("\"");
            // Parse the loaded value as JSON and verify structure.
            ObjectMapper m = new ObjectMapper();
            JsonNode loaded = m.readTree(reloadedJson);
            assertThat(loaded.isObject())
                    .as("reloaded form_data is a JSON object")
                    .isTrue();

            // Top-level scalar fields round-tripped.
            assertThat(loaded.path("server_name").asText()).isEqualTo("db-prod-01");
            assertThat(loaded.path("access_level").asText()).isEqualTo("ADMIN");
            assertThat(loaded.path("approver_count").asInt()).isEqualTo(2);
            assertThat(loaded.path("urgent").isBoolean()).isTrue();
            assertThat(loaded.path("urgent").asBoolean()).isTrue();
            // Unicode and escapes survive a single round-trip.
            assertThat(loaded.path("reason").asText())
                    .isEqualTo("Cần truy cập \"server\" có \\ và\tký tự đặc biệt");

            // Array of strings survived.
            JsonNode options = loaded.path("options");
            assertThat(options.isArray()).isTrue();
            assertThat(options.size()).isEqualTo(3);
            assertThat(options.get(0).asText()).isEqualTo("read");
            assertThat(options.get(1).asText()).isEqualTo("write");
            assertThat(options.get(2).asText()).isEqualTo("admin");

            // Nested object survived.
            JsonNode meta = loaded.path("meta");
            assertThat(meta.isObject()).isTrue();
            assertThat(meta.path("ip").asText()).isEqualTo("10.0.0.1");
            assertThat(meta.path("port").asInt()).isEqualTo(5432);
            assertThat(meta.path("encrypted").isBoolean()).isTrue();
            assertThat(meta.path("encrypted").asBoolean()).isFalse();

            // JSON null (the four-letter token) must survive as a
            // JsonNode that IS null (NOT a Java null reference).
            JsonNode optional = loaded.path("optional");
            assertThat(optional.isNull())
                    .as("JSON null token survives round-trip")
                    .isTrue();
        }
    }

    // ============================================================
    // Test B - null / missing form_data behavior
    // ============================================================

    @Nested
    @DisplayName("Test B: null / missing form_data behavior")
    class NullAndMissingFormData {

        @Test
        @Transactional
        @DisplayName("NULL form_data round-trips as Java null")
        void nullFormDataStaysNull() {
            ServiceRequest toSave = buildFixture(null);
            // Deliberately leave formData as Java null.

            ServiceRequest saved = serviceRequestRepo.saveAndFlush(toSave);
            Long savedId = saved.getId();
            serviceRequestRepo.flush();
            entityManager.clear();

            ServiceRequest reloaded = serviceRequestRepo.findById(savedId).orElse(null);
            assertThat(reloaded).isNotNull();
            assertThat(reloaded.getFormData())
                    .as("NULL form_data round-trips as Java NULL")
                    .isNull();
        }

        @Test
        @Transactional
        @DisplayName("JSON 'null' literal round-trips as JSON null (not Java null)")
        void jsonNullLiteralStaysJsonNull() throws Exception {
            ServiceRequest toSave = buildFixture("null");
            // JSON null literal, encoded as a valid JSON document.

            ServiceRequest saved = serviceRequestRepo.saveAndFlush(toSave);
            Long savedId = saved.getId();
            serviceRequestRepo.flush();
            entityManager.clear();

            ServiceRequest reloaded = serviceRequestRepo.findById(savedId).orElse(null);
            assertThat(reloaded).isNotNull();
            String reloadedJson = reloaded.getFormData();
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
    // Test C - invalid form_data behavior
    // ============================================================

    @Nested
    @DisplayName("Test C: invalid form_data behavior")
    class InvalidFormData {

        // Per-test ServiceRequest fixture that is created in the
        // body of the test method (NOT @Transactional, see below)
        // and is therefore committed to the database. We capture
        // the request_number here and delete the row in @AfterEach
        // so the test leaves no fixture data behind.
        private String fixtureRequestNumber;

        @org.junit.jupiter.api.AfterEach
        void cleanupServiceRequestFixture() {
            // Per CP-3.3.9: "Không để dữ liệu dở dang." Without
            // cleanup, this fixture would accumulate across runs
            // and would also leak into the catalog metadata.
            //
            // Cleanup is narrowly scoped: it deletes ONLY the row
            // with the request_number captured in this test run.
            // No other service_request, comment, or workflow data
            // is touched.
            if (fixtureRequestNumber != null) {
                jdbc.update(
                        "DELETE FROM service_requests WHERE request_number = ?",
                        fixtureRequestNumber);
                fixtureRequestNumber = null;
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
            // (a) insert a fixture ServiceRequest via raw JDBC
            // (so we have an id to UPDATE), (b) run the
            // malformed UPDATE via a SEPARATE DriverManager
            // connection that auto-commits each statement, (c)
            // verify the rejection, and (d) verify with a fresh
            // JdbcTemplate query that the row was not modified.
            fixtureRequestNumber = REQUEST_NUMBER_PREFIX
                    + UUID.randomUUID().toString().substring(0, 8);
            Long serviceRequestId = jdbc.queryForObject(
                    "INSERT INTO service_requests (request_number, title, "
                            + "status, priority, requester_name, requester_username) "
                            + "VALUES (?, 'CP-3.3.9 malformed-json fixture', "
                            + "'SUBMITTED', 'MEDIUM', 'CP-3.3.9 Tester', 'admin') "
                            + "RETURNING id",
                    Long.class, fixtureRequestNumber);

            // PostgreSQL JSONB is strictly validated: it rejects
            // truncated, non-JSON, or otherwise malformed input.
            // This is by design (JSONB stores binary JSON, not text).
            String malformed = "{\"server_name\":\"db-prod-01\",";
            SQLException caught = null;
            try (Connection conn = DriverManager.getConnection(
                        configuredUrl, configuredUser,
                        System.getenv("IT_TEST_PG_PASSWORD"))) {
                // autocommit is on by default for a fresh
                // DriverManager connection, so the abort is
                // limited to this connection only.
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE service_requests SET form_data = ?::jsonb "
                                + "WHERE id = ?")) {
                    ps.setString(1, malformed);
                    ps.setLong(2, serviceRequestId);
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
            // The form_data column should still be NULL.
            String stored = jdbc.queryForObject(
                    "SELECT form_data::text FROM service_requests "
                            + "WHERE id = ?",
                    String.class, serviceRequestId);
            assertThat(stored)
                    .as("form_data column was not modified by the malformed UPDATE")
                    .isNull();
        }
    }

    // ============================================================
    // Catalog check (the JSONB column type)
    // ============================================================

    @Nested
    @DisplayName("Catalog: form_data column type is jsonb")
    class CatalogCheck {

        @Test
        @DisplayName("information_schema.columns reports form_data as jsonb")
        void catalogReportsJsonb() {
            // Flyway has run by now (this test runs after Spring
            // Boot has finished bootstrapping the EntityManager
            // Factory), so the form_data column must exist in
            // information_schema.
            String type = jdbc.queryForObject(
                    "SELECT data_type FROM information_schema.columns "
                            + "WHERE table_schema = 'public' "
                            + "AND table_name = 'service_requests' "
                            + "AND column_name = 'form_data'",
                    String.class);
            assertThat(type)
                    .as("information_schema.columns has a row for "
                            + "public.service_requests.form_data")
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
                            + "AND table_name = 'service_requests' "
                            + "AND column_name = 'form_data'",
                    String.class);
            assertThat(udt).isNotNull();
            assertThat(udt.toLowerCase()).isEqualTo("jsonb");
        }
    }
}
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
 * CP-3.3.10: real PostgreSQL integration test for the
 * {@code service_catalog.form_fields} JSONB column.
 *
 * <h2>What this test proves</h2>
 * <ol>
 *   <li>The catalog column type for {@code form_fields} in
 *       {@code service_catalog} is {@code jsonb}, not {@code text}
 *       or {@code varchar}.</li>
 *   <li>The current entity mapping
 *       {@code @Column(name = "form_fields", columnDefinition = "jsonb")}
 *       WITHOUT {@code @JdbcTypeCode(SqlTypes.JSON)} either
 *       round-trips a JSON string through the JSONB column on the
 *       actual PostgreSQL instance, OR fails with SQLSTATE 42804
 *       ("column ... is of type jsonb but expression is of type
 *       character varying") - the same bug CP-3.3.7 documented for
 *       {@code change_timeline.event_data}, CP-3.3.8 for
 *       {@code change_requests.risk_factors} and CP-3.3.9 for
 *       {@code service_requests.form_data}.</li>
 *   <li>Java {@code null} is persisted and read back as Java
 *       {@code null} (when the field is left at its default).</li>
 *   <li>The seed path (ServiceCatalogInitializer) is a real
 *       production write path that must not break the contract.
 *       Because the seeder never calls setFormFields, the seed
 *       INSERT ships {@code form_fields = NULL}; that path must
 *       succeed regardless of whether the JPA mapping has a JSON
 *       type hint.</li>
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
 *   export IT_TEST_PG_URL=jdbc:postgresql://localhost:5432/itticketing_cp3310_test
 *   export IT_TEST_PG_USERNAME=cp3310_test_user
 *   export IT_TEST_PG_PASSWORD=...
 *   mvn -o test -Dtest=ServiceCatalogItemPostgresJsonbIntegrationTest
 * </pre>
 *
 * <h2>Safety guards</h2>
 * Before any write, the class verifies:
 * <ul>
 *   <li>The env var flag is {@code true} (opt-in).</li>
 *   <li>The configured URL's database name contains the test marker
 *       {@code cp3310_test} - refuses to run otherwise, even if the
 *       env vars are set.</li>
 *   <li>A probe query against the same URL returns a server host/port
 *       that the test was configured for. The probe uses a SEPARATE
 *       JDBC connection so the test fails fast if the env vars
 *       accidentally point at a different cluster.</li>
 *   <li>The {@code information_schema.columns} catalog reports the
 *       {@code form_fields} column type as {@code jsonb}.</li>
 * </ul>
 *
 * <h2>CP-3.3.10 scope discipline</h2>
 * This checkpoint covers the {@code service_catalog.form_fields}
 * column ONLY. It does NOT cover
 * {@code service_requests.form_data} (CP-3.3.9, already fixed),
 * {@code change_timeline.event_data} (CP-3.3.7, already fixed),
 * {@code change_requests.risk_factors} (CP-3.3.8, already fixed),
 * nor {@code report_configs.config} (out of scope). Each test
 * creates its own ServiceCatalogItem fixture and rolls it back
 * via the {@code @Transactional} test method, so no production
 * data is touched.
 */
@SpringBootTest
@ActiveProfiles("postgres-integration-test")
@EnabledIfEnvironmentVariable(named = "IT_TEST_PG", matches = "true")
@DisplayName("CP-3.3.10: PostgreSQL JSONB round-trip for ServiceCatalogItem.form_fields")
class ServiceCatalogItemPostgresJsonbIntegrationTest {

    /** Marker substring that MUST appear in the database name. */
    private static final String DB_NAME_TEST_MARKER = "cp3310_test";

    /**
     * Service name prefix used by this test's fixtures. Combined
     * with a UUID fragment, the resulting name is unique within the
     * test database (so a second run does not collide with the
     * first). The catalog table has no UNIQUE constraint on
     * {@code name}, but a fresh prefix keeps logs and assertions
     * easy to read and avoids accidental cross-test pollution.
     */
    private static final String SERVICE_NAME_PREFIX = "CP3310-";

    @Autowired
    private ServiceCatalogRepository catalogRepository;

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
            // for IPv4 or "::1/128" for IPv6. We strip the "/N" suffix
            // and accept localhost/127.0.0.1/::1 as equivalent.
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
    private static String buildFixtureFormFieldsJson() throws Exception {
        ObjectMapper m = new ObjectMapper();
        var root = m.createArrayNode();
        var first = m.createObjectNode();
        first.put("name", "server_name");
        first.put("type", "text");
        first.put("required", true);
        // Vietnamese + double quote + backslash + tab in one string.
        first.put("label", "Tên máy chủ \"server\" có \\ và\tký tự đặc biệt");
        var firstOptions = m.createArrayNode();
        firstOptions.add("Read");
        firstOptions.add("Write");
        firstOptions.add("Admin");
        first.set("options", firstOptions);
        root.add(first);
        var second = m.createObjectNode();
        second.put("name", "access_level");
        second.put("type", "select");
        second.put("required", false);
        second.put("maxLength", -1);
        var secondOptions = m.createArrayNode();
        secondOptions.add("read");
        secondOptions.add("write");
        secondOptions.add("admin");
        second.set("options", secondOptions);
        // JSON null (the four-letter token) - distinguishable from
        // a Java null field. We exercise it here because the
        // contract says formFields may legitimately carry a JSON
        // null inside one of its elements.
        second.putNull("defaultValue");
        root.add(second);
        return m.writeValueAsString(root);
    }

    /**
     * Build a fresh {@link ServiceCatalogItem} fixture with the
     * required NOT NULL constraints satisfied but with
     * {@code formFields} left as the caller's responsibility.
     *
     * <p>The {@code service_catalog} table has no UNIQUE constraint
     * on {@code name}, so a {@code SERVICE_NAME_PREFIX + UUID}
     * suffix is enough to make the row identifiable in logs and to
     * the {@code @AfterEach} cleanup hook.
     */
    private static ServiceCatalogItem buildFixture(String formFieldsJson) {
        String name = SERVICE_NAME_PREFIX
                + UUID.randomUUID().toString().substring(0, 8);
        ServiceCatalogItem item = new ServiceCatalogItem();
        item.setName(name);
        item.setDescription("CP-3.3.10 fixture for ServiceCatalogItem.form_fields JSONB round-trip");
        item.setCategory("CP3310");
        item.setIcon("🧪");
        item.setColor("#000000");
        item.setResponseSlaMinutes(60);
        item.setResolutionSlaMinutes(120);
        item.setBillable(false);
        item.setIsPublic(false);
        item.setEnabled(true);
        item.setRequiresApproval(false);
        item.setFormFields(formFieldsJson);
        item.setCreatedBy("admin");
        return item;
    }

    // ============================================================
    // Test A - persist via JPA, flush, clear, load, decode
    // ============================================================

    @Nested
    @DisplayName("Test A: persist -> flush -> clear -> load -> decode")
    class PersistAndReload {

        @Test
        @Transactional
        @DisplayName("ServiceCatalogItem with structured form_fields round-trips through PostgreSQL JSONB")
        void formFieldsRoundTrip() throws Exception {
            String expectedJson = buildFixtureFormFieldsJson();
            ServiceCatalogItem toSave = buildFixture(expectedJson);

            ServiceCatalogItem saved = catalogRepository.saveAndFlush(toSave);
            Long savedId = saved.getId();
            assertThat(savedId)
                    .as("ServiceCatalogItem was persisted; id is non-null")
                    .isNotNull();
            // Force the persistence context to drop the cached
            // entity so the subsequent findById() goes back to the
            // database and reads the JSONB value through the JDBC
            // driver.
            catalogRepository.flush();
            entityManager.clear();

            ServiceCatalogItem reloaded = catalogRepository.findById(savedId).orElse(null);
            assertThat(reloaded)
                    .as("ServiceCatalogItem was persisted; reloaded entity is non-null")
                    .isNotNull();
            assertThat(reloaded.getId()).isEqualTo(savedId);
            assertThat(reloaded.getName()).isEqualTo(saved.getName());
            assertThat(reloaded.getCategory()).isEqualTo("CP3310");
            String reloadedJson = reloaded.getFormFields();
            assertThat(reloadedJson)
                    .as("form_fields column is non-null after round-trip")
                    .isNotNull();
            // The JSON should not be a doubly-escaped string (no
            // surrounding quotes that would mean Hibernate wrote a
            // JSON-encoded String into a JSON column).
            assertThat(reloadedJson)
                    .as("form_fields is not a doubly-escaped JSON string")
                    .doesNotStartWith("\"")
                    .doesNotEndWith("\"");
            // Parse the loaded value as JSON and verify structure.
            ObjectMapper m = new ObjectMapper();
            JsonNode loaded = m.readTree(reloadedJson);
            assertThat(loaded.isArray())
                    .as("reloaded form_fields is a JSON array")
                    .isTrue();
            assertThat(loaded.size())
                    .as("reloaded form_fields array has 2 elements")
                    .isEqualTo(2);

            // First element round-tripped.
            JsonNode first = loaded.get(0);
            assertThat(first.path("name").asText()).isEqualTo("server_name");
            assertThat(first.path("type").asText()).isEqualTo("text");
            assertThat(first.path("required").isBoolean()).isTrue();
            assertThat(first.path("required").asBoolean()).isTrue();
            // Unicode and escapes survive a single round-trip.
            assertThat(first.path("label").asText())
                    .isEqualTo("Tên máy chủ \"server\" có \\ và\tký tự đặc biệt");
            JsonNode firstOptions = first.path("options");
            assertThat(firstOptions.isArray()).isTrue();
            assertThat(firstOptions.size()).isEqualTo(3);
            assertThat(firstOptions.get(0).asText()).isEqualTo("Read");
            assertThat(firstOptions.get(1).asText()).isEqualTo("Write");
            assertThat(firstOptions.get(2).asText()).isEqualTo("Admin");

            // Second element: negative number, false boolean, JSON null.
            JsonNode second = loaded.get(1);
            assertThat(second.path("name").asText()).isEqualTo("access_level");
            assertThat(second.path("type").asText()).isEqualTo("select");
            assertThat(second.path("required").isBoolean()).isTrue();
            assertThat(second.path("required").asBoolean()).isFalse();
            assertThat(second.path("maxLength").asInt()).isEqualTo(-1);
            // JSON null (the four-letter token) must survive as a
            // JsonNode that IS null (NOT a Java null reference).
            JsonNode defaultValue = second.path("defaultValue");
            assertThat(defaultValue.isNull())
                    .as("JSON null token survives round-trip")
                    .isTrue();
        }
    }

    // ============================================================
    // Test B - null / missing form_fields behavior
    // ============================================================

    @Nested
    @DisplayName("Test B: null / missing form_fields behavior")
    class NullAndMissingFormFields {

        @Test
        @Transactional
        @DisplayName("NULL form_fields round-trips as Java null")
        void nullFormFieldsStaysNull() {
            ServiceCatalogItem toSave = buildFixture(null);
            // Deliberately leave formFields as Java null - this
            // mirrors the production seed path where the seeder
            // never calls setFormFields(...).

            ServiceCatalogItem saved = catalogRepository.saveAndFlush(toSave);
            Long savedId = saved.getId();
            catalogRepository.flush();
            entityManager.clear();

            ServiceCatalogItem reloaded = catalogRepository.findById(savedId).orElse(null);
            assertThat(reloaded).isNotNull();
            assertThat(reloaded.getFormFields())
                    .as("NULL form_fields round-trips as Java NULL")
                    .isNull();
        }

        @Test
        @Transactional
        @DisplayName("JSON 'null' literal round-trips as JSON null (not Java null)")
        void jsonNullLiteralStaysJsonNull() throws Exception {
            ServiceCatalogItem toSave = buildFixture("null");
            // JSON null literal, encoded as a valid JSON document.

            ServiceCatalogItem saved = catalogRepository.saveAndFlush(toSave);
            Long savedId = saved.getId();
            catalogRepository.flush();
            entityManager.clear();

            ServiceCatalogItem reloaded = catalogRepository.findById(savedId).orElse(null);
            assertThat(reloaded).isNotNull();
            String reloadedJson = reloaded.getFormFields();
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
    // Test C - invalid form_fields behavior
    // ============================================================

    @Nested
    @DisplayName("Test C: invalid form_fields behavior")
    class InvalidFormFields {

        // Per-test ServiceCatalogItem fixture that is created in the
        // body of the test method (NOT @Transactional, see below)
        // and is therefore committed to the database. We capture
        // the service name here and delete the row in @AfterEach
        // so the test leaves no fixture data behind.
        private String fixtureName;

        @org.junit.jupiter.api.AfterEach
        void cleanupServiceCatalogItemFixture() {
            // Per CP-3.3.10: "Không để dữ liệu dở dang." Without
            // cleanup, this fixture would accumulate across runs
            // and would also leak into the catalog metadata.
            //
            // Cleanup is narrowly scoped: it deletes ONLY the row
            // with the name captured in this test run.
            // No other service_catalog item is touched.
            if (fixtureName != null) {
                jdbc.update(
                        "DELETE FROM service_catalog WHERE name = ?",
                        fixtureName);
                fixtureName = null;
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
            // (a) insert a fixture ServiceCatalogItem via raw
            // JDBC (so we have an id to UPDATE), (b) run the
            // malformed UPDATE via a SEPARATE DriverManager
            // connection that auto-commits each statement, (c)
            // verify the rejection, and (d) verify with a fresh
            // JdbcTemplate query that the row was not modified.
            fixtureName = SERVICE_NAME_PREFIX
                    + UUID.randomUUID().toString().substring(0, 8);
            Long catalogItemId = jdbc.queryForObject(
                    "INSERT INTO service_catalog (name, category, "
                            + "response_sla_minutes, resolution_sla_minutes, "
                            + "is_public, enabled) "
                            + "VALUES (?, 'CP3310', 60, 120, FALSE, TRUE) "
                            + "RETURNING id",
                    Long.class, fixtureName);

            // PostgreSQL JSONB is strictly validated: it rejects
            // truncated, non-JSON, or otherwise malformed input.
            // This is by design (JSONB stores binary JSON, not text).
            String malformed = "[{\"name\":\"server_name\",";
            SQLException caught = null;
            try (Connection conn = DriverManager.getConnection(
                        configuredUrl, configuredUser,
                        System.getenv("IT_TEST_PG_PASSWORD"))) {
                // autocommit is on by default for a fresh
                // DriverManager connection, so the abort is
                // limited to this connection only.
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE service_catalog SET form_fields = ?::jsonb "
                                + "WHERE id = ?")) {
                    ps.setString(1, malformed);
                    ps.setLong(2, catalogItemId);
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
            // The form_fields column should still be NULL.
            String stored = jdbc.queryForObject(
                    "SELECT form_fields::text FROM service_catalog "
                            + "WHERE id = ?",
                    String.class, catalogItemId);
            assertThat(stored)
                    .as("form_fields column was not modified by the malformed UPDATE")
                    .isNull();
        }
    }

    // ============================================================
    // Catalog check (the JSONB column type)
    // ============================================================

    @Nested
    @DisplayName("Catalog: form_fields column type is jsonb")
    class CatalogCheck {

        @Test
        @DisplayName("information_schema.columns reports form_fields as jsonb")
        void catalogReportsJsonb() {
            // Flyway has run by now (this test runs after Spring
            // Boot has finished bootstrapping the EntityManager
            // Factory), so the form_fields column must exist in
            // information_schema.
            String type = jdbc.queryForObject(
                    "SELECT data_type FROM information_schema.columns "
                            + "WHERE table_schema = 'public' "
                            + "AND table_name = 'service_catalog' "
                            + "AND column_name = 'form_fields'",
                    String.class);
            assertThat(type)
                    .as("information_schema.columns has a row for "
                            + "public.service_catalog.form_fields")
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
                            + "AND table_name = 'service_catalog' "
                            + "AND column_name = 'form_fields'",
                    String.class);
            assertThat(udt).isNotNull();
            assertThat(udt.toLowerCase()).isEqualTo("jsonb");
        }
    }
}
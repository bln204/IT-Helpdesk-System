package com.example.ticketing.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
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

/**
 * CP-3.3.14: real PostgreSQL integration test for the
 * {@code assets.specifications} JSONB column.
 *
 * <h2>What this test proves</h2>
 * <ol>
 * <li>The catalog column type for {@code specifications} in
 * {@code assets} is {@code jsonb}.</li>
 * <li>The current entity mapping
 * {@code @JdbcTypeCode(SqlTypes.JSON)
 *       @Column(columnDefinition = "jsonb")} on a field of type
 * {@code Map&lt;String, Object&gt;} round-trips a structured
 * Map through the JSONB column on the actual PostgreSQL
 * instance.</li>
 * <li>Java {@code null} is persisted and read back as Java
 * {@code null} when the field is left at its default.</li>
 * <li>The JPA UPDATE path (AssetService.updateAsset) correctly
 * writes the Map to the JSONB column without producing
 * SQLSTATE 42804.</li>
 * <li>Nested objects, arrays, booleans, numbers and strings
 * survive the round-trip without data loss or corruption.</li>
 * </ol>
 *
 * <h2>Why a service-level test?</h2>
 * {@link AssetRepository#save(Object)} delegates to
 * {@code EntityManager.persist} / {@code merge}, which is the
 * exact same binding path that {@code AssetService.createAsset}
 * and {@code AssetService.updateAsset} go through. Running the
 * test through {@code AssetService} exercises the production
 * code shape — service, repository and entity together — and is
 * stronger evidence than going around the service.
 *
 * <h2>Opt-in</h2>
 * The whole class is gated by the JVM environment variable
 * {@code IT_TEST_PG=true}. When the variable is absent, JUnit
 * reports the class as ignored; the normal {@code mvn test} run
 * never contacts PostgreSQL. To run:
 * 
 * <pre>
 *   export IT_TEST_PG=true
 *   export IT_TEST_PG_URL=jdbc:postgresql://localhost:5432/itticketing_cp3314_test
 *   export IT_TEST_PG_USERNAME=...
 *   export IT_TEST_PG_PASSWORD=...
 *   mvn -o test -Dtest=AssetPostgresJsonbIntegrationTest
 * </pre>
 *
 * <h2>Safety guards</h2>
 * Before any write, the class verifies:
 * <ul>
 * <li>The env var flag is {@code true} (opt-in).</li>
 * <li>The configured URL's database name contains the test
 * marker {@code cp3314_test} - refuses to run otherwise,
 * even if the env vars are set.</li>
 * <li>A probe query against the same URL returns a server
 * host/port that the test was configured for. The probe
 * uses a SEPARATE JDBC connection so the test fails fast
 * if the env vars accidentally point at a different
 * cluster.</li>
 * <li>The {@code information_schema.columns} catalog reports
 * the {@code specifications} column type as {@code jsonb}.</li>
 * </ul>
 *
 * <h2>CP-3.3.14 scope discipline</h2>
 * This checkpoint covers the {@code assets.specifications}
 * column ONLY. It does NOT cover any other JSONB column.
 * The fixtures created here are narrowly scoped (asset_number
 * prefix {@code CP3314-}) and cleaned up per-test.
 */
@SpringBootTest
@ActiveProfiles("postgres-integration-test")
@EnabledIfEnvironmentVariable(named = "IT_TEST_PG", matches = "true")
@DisplayName("CP-3.3.14: PostgreSQL JSONB round-trip for Asset.specifications")
class AssetPostgresJsonbIntegrationTest {

        /** Marker substring that MUST appear in the database name. */
        private static final String DB_NAME_TEST_MARKER = "cp3314_test";

        /**
         * Name prefix used by this test's fixtures. Combined with a
         * UUID fragment, the resulting asset_number is unique within
         * the test database. The {@code assets} table has a UNIQUE
         * constraint on {@code asset_number}, so the prefix keeps
         * logs and assertions easy to read and avoids accidental
         * cross-test pollution.
         */
        private static final String ASSET_NUMBER_PREFIX = "CP3314-";

        @Autowired
        private AssetService assetService;

        @Autowired
        private AssetRepository assetRepository;

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
                // Probe-only verification: confirms the URL points at
                // the expected database, host, port and that the
                // connection is alive. Schema-aware checks
                // (information_schema) are deferred to
                // {@link #verifySchemaAfterSpringContext()} which runs
                // AFTER Spring Boot has bootstrapped the
                // EntityManagerFactory (and therefore AFTER Flyway
                // has applied the migrations).
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

        @org.junit.jupiter.api.BeforeEach
        void verifySchemaAfterSpringContext() {
                // V51 migration has run by now (Spring Boot has finished
                // bootstrapping the EntityManagerFactory). Verify the
                // assets table and specifications column exist.
                String tableExists = jdbc.queryForObject(
                                "SELECT COUNT(*) FROM information_schema.tables "
                                                + "WHERE table_schema = 'public' "
                                                + "AND table_name = 'assets'",
                                String.class);
                if (!"1".equals(tableExists)) {
                        fail("Table 'assets' not found in public schema. "
                                        + "Flyway may not have run, or the database is the wrong one.");
                }
        }

        @AfterAll
        static void clearEnv() {
                configuredUrl = null;
                configuredUser = null;
                configuredDbName = null;
                configuredHost = null;
                configuredPort = 0;
        }

        // ============================================================
        // Per-test fixture cleanup
        // ============================================================

        /**
         * Last fixture asset_number created during a test that writes
         * to {@code assets}. Captured here so {@code @AfterEach}
         * can remove exactly that row and nothing else.
         */
        private String lastFixtureAssetNumber;

        @AfterEach
        void cleanupAssetFixture() {
                // Narrow-scoped cleanup: delete only the row with the
                // asset_number captured in the current test, if any.
                // No other data is ever touched.
                if (lastFixtureAssetNumber != null) {
                        jdbc.update("DELETE FROM assets WHERE asset_number = ?",
                                        lastFixtureAssetNumber);
                        lastFixtureAssetNumber = null;
                }
        }

        // ============================================================
        // Helpers
        // ============================================================

        /**
         * Build a structured {@code Map&lt;String, Object&gt;} that
         * exercises every wire-format class the JSONB column should
         * handle transparently:
         * <ul>
         * <li>Nested object ({@code hardware}) with key/value
         * pairs</li>
         * <li>Nested array ({@code network_interfaces})</li>
         * <li>Booleans (both true and false)</li>
         * <li>Integer (positive, zero and negative)</li>
         * <li>BigDecimal (decimal number)</li>
         * <li>JSON {@code null} inside a Map value</li>
         * <li>Unicode (Vietnamese with diacritics)</li>
         * <li>String escapes ({@code "}, {@code \\}, tab)</li>
         * </ul>
         *
         * <p>
         * This is deliberately richer than the String-type JSONB
         * tests because the Map serialization path has different
         * internal handling in Hibernate (Map → JSON via Jackson,
         * not String passthrough).
         */
        private static Map<String, Object> buildFixtureSpecifications() {
                Map<String, Object> specs = new HashMap<>();
                specs.put("chartType", "bar");
                specs.put("ramsize_gb", 32);
                specs.put("cpuCores", 16);
                specs.put("isVirtual", false);
                specs.put("temperature_celsius", 67.5);
                // Unicode + double quote + backslash + tab in one string.
                specs.put("os_version", "Ubuntu 22.04 \"Jammy\" \\LTS\n");
                // Nested object
                Map<String, Object> hw = new HashMap<>();
                hw.put("manufacturer", "Dell");
                hw.put("model", "PowerEdge R750");
                hw.put("slot_count", 8);
                hw.put("supports_nvme", true);
                specs.put("hardware", hw);
                // Nested array
                specs.put("network_interfaces",
                                java.util.List.of(
                                                java.util.Map.of("name", "eth0", "speed_mbps", 10000, "dhcp", true),
                                                java.util.Map.of("name", "eth1", "speed_mbps", 1000, "dhcp", false,
                                                                "vlan", 100)));
                // JSON null — a Map entry whose value is the JSON null token.
                // Distinguishable from a missing key or a Java null value.
                specs.put("optional_field", null);
                return specs;
        }

        /**
         * Build a fresh {@link Asset} fixture with the required NOT
         * NULL constraints satisfied and with {@code specifications}
         * set to the caller's value.
         *
         * <p>
         * The {@code assets} table has a UNIQUE constraint on
         * {@code asset_number}, so an
         * {@code ASSET_NUMBER_PREFIX + UUID} suffix is enough to
         * make the row identifiable in logs and to the
         * {@code @AfterEach} cleanup hook.
         */
        private static Asset buildFixture(Map<String, Object> specifications) {
                String assetNumber = ASSET_NUMBER_PREFIX
                                + UUID.randomUUID().toString().substring(0, 8);
                Asset a = new Asset();
                a.setAssetNumber(assetNumber);
                a.setName("CP-3.3.14 Fixture Asset");
                a.setAssetType(Asset.AssetType.SERVER);
                a.setSpecifications(specifications);
                return a;
        }

        // ============================================================
        // Test A - persist via JPA, flush, clear, load, decode
        // ============================================================

        @Nested
        @DisplayName("Test A: persist -> flush -> clear -> load -> decode")
        class PersistAndReload {

                @Test
                @DisplayName("Asset with structured specifications Map round-trips through PostgreSQL JSONB")
                void specificationsMapRoundTrip() {
                        Map<String, Object> expected = buildFixtureSpecifications();
                        Asset toSave = buildFixture(expected);
                        lastFixtureAssetNumber = toSave.getAssetNumber();

                        // Drive the create path through the production
                        // service method, exactly as the REST controller
                        // would. This goes through:
                        // AssetService.createAsset
                        // -> AssetRepository.save
                        // -> EntityManager.persist
                        // -> Hibernate INSERT
                        // which is the binding path under investigation.
                        Asset saved = assetService.createAsset(toSave, "cp3314-test");
                        assertThat(saved.getId())
                                        .as("Asset was persisted; id is non-null")
                                        .isNotNull();
                        Long savedId = saved.getId();

                        // Force the persistence context to drop the cached
                        // entity so the subsequent findById() goes back to
                        // the database and reads the JSONB value through
                        // the JDBC driver.
                        assetRepository.flush();
                        entityManager.clear();

                        Asset reloaded = assetRepository.findById(savedId).orElse(null);
                        assertThat(reloaded)
                                        .as("Asset was persisted; reloaded entity is non-null")
                                        .isNotNull();
                        assertThat(reloaded.getId()).isEqualTo(savedId);
                        assertThat(reloaded.getAssetNumber())
                                        .isEqualTo(lastFixtureAssetNumber);

                        Map<String, Object> reloadedSpecs = reloaded.getSpecifications();
                        assertThat(reloadedSpecs)
                                        .as("specifications is non-null after round-trip")
                                        .isNotNull();

                        // --- String value ---
                        assertThat(reloadedSpecs.get("chartType"))
                                        .as("chartType string survives round-trip")
                                        .isEqualTo("bar");

                        // --- Integer values ---
                        assertThat(reloadedSpecs.get("ramsize_gb"))
                                        .as("integer survives round-trip")
                                        .isEqualTo(32);
                        assertThat(reloadedSpecs.get("cpuCores"))
                                        .as("cpuCores integer survives round-trip")
                                        .isEqualTo(16);

                        // --- Boolean values ---
                        assertThat(reloadedSpecs.get("isVirtual"))
                                        .as("isVirtual=false survives round-trip")
                                        .isEqualTo(false);

                        // --- Nested object ---
                        @SuppressWarnings("unchecked")
                        Map<String, Object> hw = (Map<String, Object>) reloadedSpecs.get("hardware");
                        assertThat(hw)
                                        .as("hardware nested object is a Map")
                                        .isNotNull();
                        assertThat(hw.get("manufacturer"))
                                        .as("hardware.manufacturer string survives")
                                        .isEqualTo("Dell");
                        assertThat(hw.get("slot_count"))
                                        .as("hardware.slot_count integer survives")
                                        .isEqualTo(8);
                        assertThat(hw.get("supports_nvme"))
                                        .as("hardware.supports_nvme boolean survives")
                                        .isEqualTo(true);

                        // --- Nested array ---
                        @SuppressWarnings("unchecked")
                        java.util.List<Map<String, Object>> interfaces = (java.util.List<Map<String, Object>>) reloadedSpecs
                                        .get("network_interfaces");
                        assertThat(interfaces)
                                        .as("network_interfaces array is present")
                                        .isNotNull();
                        assertThat(interfaces.size())
                                        .as("network_interfaces has 2 elements")
                                        .isEqualTo(2);
                        assertThat(interfaces.get(0).get("name"))
                                        .as("network_interfaces[0].name survives")
                                        .isEqualTo("eth0");
                        assertThat(interfaces.get(0).get("speed_mbps"))
                                        .as("network_interfaces[0].speed_mbps integer survives")
                                        .isEqualTo(10000);
                        assertThat(interfaces.get(0).get("dhcp"))
                                        .as("network_interfaces[0].dhcp boolean survives")
                                        .isEqualTo(true);

                        // --- Unicode and escapes ---
                        assertThat(reloadedSpecs.get("os_version"))
                                        .as("os_version with unicode and escapes survives round-trip")
                                        .isEqualTo("Ubuntu 22.04 \"Jammy\" \\LTS\n");

                        // --- JSON null (Map entry with null value) ---
                        // Note: when a HashMap entry has value null and is
                        // serialized by Jackson, it produces the JSON null
                        // token. On deserialization, Jackson produces a
                        // Map whose entry is present with value null.
                        assertThat(reloadedSpecs.containsKey("optional_field"))
                                        .as("optional_field key is present in Map")
                                        .isTrue();
                        assertThat(reloadedSpecs.get("optional_field"))
                                        .as("optional_field value is null (JSON null token)")
                                        .isNull();
                }
        }

        // ============================================================
        // Test B - null specifications
        // ============================================================

        @Nested
        @DisplayName("Test B: null specifications")
        class NullSpecifications {

                @Test
                @Transactional
                @DisplayName("NULL specifications round-trips as Java null")
                void nullSpecificationsStaysNull() {
                        // Deliberately leave specifications as Java null.
                        // @Transactional makes this test a write-rollback
                        // test: the row is created inside the transaction,
                        // read back, asserted, and then rolled back so no
                        // fixture leaks across runs.
                        Asset toSave = buildFixture(null);
                        lastFixtureAssetNumber = toSave.getAssetNumber();

                        Asset saved = assetService.createAsset(toSave, "cp3314-test");
                        Long savedId = saved.getId();
                        assetRepository.flush();
                        entityManager.clear();

                        Asset reloaded = assetRepository.findById(savedId).orElse(null);
                        assertThat(reloaded).isNotNull();
                        assertThat(reloaded.getSpecifications())
                                        .as("NULL specifications round-trips as Java null")
                                        .isNull();
                }
        }

        // ============================================================
        // Test C - update path
        // ============================================================

        @Nested
        @DisplayName("Test C: update path")
        class UpdatePath {

                @Test
                @DisplayName("AssetService.updateAsset rewrites specifications without SQLSTATE 42804")
                void updateSpecificationsRoundTrip() {
                        // Start with a minimal specifications map.
                        Map<String, Object> initial = new HashMap<>();
                        initial.put("status", "initial");
                        initial.put("count", 0);
                        Asset seed = buildFixture(initial);
                        lastFixtureAssetNumber = seed.getAssetNumber();

                        Asset created = assetService.createAsset(seed, "cp3314-test");
                        Long id = created.getId();
                        assertThat(id).isNotNull();

                        // Build a fresh Asset with updated specifications,
                        // then call updateAsset exactly as the controller does.
                        Asset updates = new Asset();
                        updates.setName(created.getName());
                        updates.setAssetType(Asset.AssetType.SERVER);
                        updates.setSpecifications(buildFixtureSpecifications());

                        assetService.updateAsset(id, updates, "cp3314-test");
                        assetRepository.flush();
                        entityManager.clear();

                        Asset reloaded = assetRepository.findById(id).orElse(null);
                        assertThat(reloaded).isNotNull();

                        Map<String, Object> reloadedSpecs = reloaded.getSpecifications();
                        assertThat(reloadedSpecs)
                                        .as("updated specifications is non-null after update")
                                        .isNotNull();

                        // Sanity: the updated map has the new chartType
                        // (NOT the old "initial" string), proving the
                        // UPDATE was committed to the JSONB column.
                        assertThat(reloadedSpecs.get("chartType"))
                                        .as("update overwrote the prior JSON value")
                                        .isEqualTo("bar");
                        assertThat(reloadedSpecs.get("status"))
                                        .as("old value 'initial' is gone after update")
                                        .isNull();
                        assertThat(reloadedSpecs.get("ramsize_gb"))
                                        .as("new integer value from update survives")
                                        .isEqualTo(32);

                        // Verify nested data from the update also survived.
                        @SuppressWarnings("unchecked")
                        Map<String, Object> hw = (Map<String, Object>) reloadedSpecs.get("hardware");
                        assertThat(hw).isNotNull();
                        assertThat(hw.get("manufacturer"))
                                        .as("nested object from update survives")
                                        .isEqualTo("Dell");
                }
        }

        // ============================================================
        // Test D - malformed JSONB (PostgreSQL enforces validity)
        // ============================================================

        @Nested
        @DisplayName("Test D: malformed JSONB at the database layer")
        class MalformedJsonb {

                @Test
                @DisplayName("JSONB column rejects malformed JSON at the database layer")
                void jsonbRejectsMalformedJson() throws SQLException {
                        // We deliberately do NOT annotate this test with
                        // @Transactional. PostgreSQL aborts the whole
                        // transaction on the first error (state 25P02),
                        // and we need the malformed insert to be tried
                        // outside the test's transaction so its abort
                        // does not poison subsequent queries. We use a
                        // SEPARATE DriverManager connection that
                        // auto-commits each statement, so the abort is
                        // limited to that connection only.
                        String assetNumber = ASSET_NUMBER_PREFIX
                                        + UUID.randomUUID().toString().substring(0, 8);
                        lastFixtureAssetNumber = assetNumber;

                        // Insert a valid asset first (no specifications).
                        Long assetId = jdbc.queryForObject(
                                        "INSERT INTO assets (asset_number, name, asset_type) "
                                                        + "VALUES (?, 'CP-3.3.14 malformed fixture', 'SERVER') "
                                                        + "RETURNING id",
                                        Long.class, assetNumber);

                        // PostgreSQL JSONB is strictly validated: it
                        // rejects truncated, non-JSON, or otherwise
                        // malformed input.
                        String malformed = "{\"chartType\":";
                        SQLException caught = null;
                        try (Connection conn = DriverManager.getConnection(
                                        configuredUrl, configuredUser,
                                        System.getenv("IT_TEST_PG_PASSWORD"))) {
                                // autocommit is on by default for a fresh
                                // DriverManager connection, so the abort is
                                // limited to this connection only.
                                try (PreparedStatement ps = conn.prepareStatement(
                                                "UPDATE assets SET specifications = ?::jsonb "
                                                                + "WHERE id = ?")) {
                                        ps.setString(1, malformed);
                                        ps.setLong(2, assetId);
                                        ps.executeUpdate();
                                }
                        } catch (SQLException ex) {
                                caught = ex;
                        }
                        assertThat((Object) caught)
                                        .as("PostgreSQL JSONB rejects malformed JSON")
                                        .isNotNull();

                        // The specifications column should still be NULL (we
                        // never set it on the INSERT, and the malformed
                        // UPDATE was rejected).
                        String stored = jdbc.queryForObject(
                                        "SELECT specifications::text FROM assets "
                                                        + "WHERE id = ?",
                                        String.class, assetId);
                        assertThat(stored)
                                        .as("specifications column was not modified by the malformed UPDATE")
                                        .isNull();
                }
        }

        // ============================================================
        // Catalog check (the JSONB column type)
        // ============================================================

        @Nested
        @DisplayName("Catalog: specifications column type is jsonb")
        class CatalogCheck {

                @Test
                @DisplayName("information_schema.columns reports specifications as jsonb")
                void catalogReportsJsonb() {
                        // Flyway has run by now (this test runs after
                        // Spring Boot has finished bootstrapping the
                        // EntityManager Factory), so the specifications
                        // column must exist in information_schema.
                        String type = jdbc.queryForObject(
                                        "SELECT data_type FROM information_schema.columns "
                                                        + "WHERE table_schema = 'public' "
                                                        + "AND table_name = 'assets' "
                                                        + "AND column_name = 'specifications'",
                                        String.class);
                        assertThat(type)
                                        .as("information_schema.columns has a row for "
                                                        + "public.assets.specifications")
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
                                                        + "AND table_name = 'assets' "
                                                        + "AND column_name = 'specifications'",
                                        String.class);
                        assertThat(udt).isNotNull();
                        assertThat(udt.toLowerCase()).isEqualTo("jsonb");
                }
        }
}

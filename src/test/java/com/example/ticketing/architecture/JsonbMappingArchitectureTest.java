package com.example.ticketing.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CP-3.3.12: Static regression test for JSONB mapping in JPA
 * entities. Audited and updated for CP-3.3.13.
 *
 * <h2>What this test guards against</h2>
 * The Hibernate JSONB binding bug that affected the following
 * columns before the CP-3.3.5/7/8/9/10/11 fixes:
 * <ul>
 *   <li>{@code service_catalog.form_fields}</li>
 *   <li>{@code service_requests.form_data}</li>
 *   <li>{@code change_requests.risk_factors}</li>
 *   <li>{@code change_timeline.event_data}</li>
 *   <li>{@code report_configs.config}</li>
 *   <li>{@code ai_message.metadata}</li>
 * </ul>
 * Each of those failed at runtime with SQLSTATE 42804
 * ("column is of type jsonb but expression is of type
 * character varying") because the entity declared
 * {@code @Column(columnDefinition = "jsonb")} without the
 * companion {@code @JdbcTypeCode(SqlTypes.JSON)} annotation.
 *
 * <p>This test is a static guard. It does not require a running
 * database, Spring context, or any testcontainers; it inspects
 * the compiled bytecode of entity classes directly via
 * reflection. Its goal is to FAIL the build if a future change
 * reintroduces the same shape of bug (a {@code @Column}
 * declaring a JSON/JSONB column but with no JSON JDBC type
 * mapping on the field or its associated property).
 *
 * <h2>What this test checks</h2>
 * <ol>
 *   <li>Every field on a {@code @Entity} that has
 *       {@code @Column(columnDefinition = ...)} where the
 *       definition contains {@code json} (case-insensitive,
 *       matching both {@code json} and {@code jsonb}) MUST
 *       have an appropriate JSON type mapping. The two
 *       acceptable mappings are:
 *       <ul>
 *         <li>{@code @JdbcTypeCode(SqlTypes.JSON)} directly
 *             on the field, OR</li>
 *         <li>A custom {@code org.hibernate.type.descriptor.jdbc.JdbcType}
 *             registered for the field (out of scope for
 *             the current codebase; the only such mapping
 *             used today is {@code @JdbcTypeCode}).</li>
 *       </ul>
 *   </li>
 *   <li>The test deliberately ignores field names, comments,
 *       and the position of annotations. It reads only the
 *       runtime metadata exposed by the Java reflection API
 *       and the {@code jakarta.persistence} / Hibernate
 *       annotations.</li>
 *   <li>The test never modifies production code. It only
 *       inspects classes that already exist on the classpath.</li>
 * </ol>
 *
 * <h2>What this test does NOT check</h2>
 * <ul>
 *   <li>It does not connect to PostgreSQL. The companion
 *       CP-3.3.5/7/8/9/10/11 integration tests cover the
 *       runtime behaviour. This test is a fast, deterministic
 *       guard that does not require any infrastructure.</li>
 *   <li>It does not require Spring to be running. It only
 *       uses reflection, which is part of the JDK.</li>
 *   <li>It does not check entity classes that are not in the
 *       {@code com.example.ticketing} package tree.</li>
 *   <li>It does not auto-fix or "soft-pass" any violation.
 *       Any entity field that has a JSON column but no JSON
 *       type mapping causes the test to fail with a precise
 *       error message naming the entity, field, and column.</li>
 * </ul>
 *
 * <h2>Discovery mechanism</h2>
 * Entity classes are discovered by walking the classpath
 * inside the application package tree and looking for
 * {@code .class} files that have the {@code @Entity}
 * annotation (read at the class level via reflection after
 * loading). This avoids adding any new dependency. The walk
 * covers both the typical Maven
 * {@code target/classes} layout and the rare
 * {@code target/test-classes} layout that some IDEs use.
 *
 * <h2>Negative test coverage</h2>
 * A separate negative test
 * ({@link #violationFixtureClassIsDetected()}) loads a
 * fixture class that intentionally violates the rule (a JSON
 * column with no JSON type mapping) and runs the same check
 * directly on that class, asserting that the check FAILS as
 * expected. This is the only way to prove the check is not
 * silently passing on bad data.
 */
@DisplayName("CP-3.3.13: static regression test for JSONB entity mapping (CP-3.3.12 audit)")
class JsonbMappingArchitectureTest {

    /**
     * Application package root scanned for {@code @Entity}
     * classes. Tightly scoped so the test does not pick up
     * classes from third-party libraries on the classpath.
     */
    private static final String APP_PACKAGE = "com.example.ticketing";

    /**
     * Returns true if the given {@code columnDefinition}
     * value refers to a JSON or JSONB column. The check is
     * case-insensitive and accepts the values {@code json},
     * {@code JSON}, {@code jsonb}, {@code JSONB} and any
     * surrounding whitespace. It deliberately does not
     * match {@code jsonpath} or other extensions. We do
     * not use a regex here because the legal values are
     * small and fixed: matching them with a regex would
     * invite subtle bugs around word boundaries in
     * {@code jsonb} (the {@code b} after {@code json} is
     * not a word boundary). A trimmed, lower-cased
     * comparison is both simpler and more correct.
     */
    private static boolean isJsonColumnDefinition(String def) {
        if (def == null) {
            return false;
        }
        String trimmed = def.trim().toLowerCase();
        return trimmed.equals("json") || trimmed.equals("jsonb");
    }

    // ============================================================
    // POSITIVE TEST - production entities
    // ============================================================

    @Test
    @DisplayName("Every @Entity field with a JSON/JSONB column has a JSON type mapping")
    void everyJsonbEntityFieldHasJsonTypeMapping() throws Exception {
        Set<String> entityClassNames = discoverEntityClassNames();
        // Sanity: the project must have at least one
        // @Entity class. If this fails, the discovery
        // mechanism is broken, not the project.
        assertThat(entityClassNames)
                .as("expected at least one @Entity class to be discovered "
                        + "in package " + APP_PACKAGE)
                .isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (String className : entityClassNames) {
            Class<?> entityClass = Class.forName(className, false,
                    Thread.currentThread().getContextClassLoader());
            for (Field field : entityClass.getDeclaredFields()) {
                if (hasJsonbColumn(field) && !hasJsonTypeMapping(field)) {
                    violations.add(formatViolation(entityClass, field));
                }
            }
        }
        if (!violations.isEmpty()) {
            fail("Found " + violations.size() + " JPA entity field(s) with a "
                    + "JSON/JSONB column but no JSON type mapping. Each of "
                    + "these will fail at runtime with SQLSTATE 42804 when "
                    + "the field is written. Apply "
                    + "@JdbcTypeCode(SqlTypes.JSON) above the field, or "
                    + "register a custom JdbcType.\n"
                    + String.join("\n", violations));
        }
    }

    // ============================================================
    // POSITIVE TEST - known-good JSONB mappings
    // ============================================================

    /**
     * Sanity check: the seven mappings that were fixed by
     * CP-3.3.5/7/8/9/10/11 MUST still be present and MUST
     * still carry the {@code @JdbcTypeCode(SqlTypes.JSON)}
     * annotation. If any of these is renamed or un-annotated,
     * this test fails immediately with a precise message.
     * This is a belt-and-suspenders check that protects the
     * inventory documented in
     * {@code docs/architecture/jsonb-mapping-inventory.md}.
     */
    @Test
    @DisplayName("Known-good JSONB mappings are still annotated")
    void knownGoodJsonbMappingsAreStillAnnotated() {
        // Each entry: (fully qualified class, field name)
        // chosen because the field's @Column has
        // columnDefinition = "jsonb" in the production
        // code as of CP-3.3.12.
        String[][] knownMappings = {
                {"com.example.ticketing.servicerequest.ServiceCatalogItem",
                        "formFields"},
                {"com.example.ticketing.servicerequest.ServiceRequest",
                        "formData"},
                {"com.example.ticketing.change.ChangeRequest",
                        "riskFactors"},
                {"com.example.ticketing.change.ChangeTimeline",
                        "eventData"},
                {"com.example.ticketing.report.ReportConfig",
                        "config"},
                {"com.example.ticketing.ai.session.AiMessage",
                        "metadata"},
                // Asset.specifications uses Map<String, Object>
                // and is also covered by the same rule.
                {"com.example.ticketing.asset.Asset",
                        "specifications"},
        };
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        List<String> failures = new ArrayList<>();
        for (String[] mapping : knownMappings) {
            String fqcn = mapping[0];
            String fieldName = mapping[1];
            try {
                Class<?> entityClass = Class.forName(fqcn, false, cl);
                Field field = entityClass.getDeclaredField(fieldName);
                if (!hasJsonbColumn(field)) {
                    failures.add(fqcn + "#" + fieldName
                            + ": columnDefinition no longer references a "
                            + "JSON/JSONB column");
                    continue;
                }
                if (!hasJsonTypeMapping(field)) {
                    failures.add(fqcn + "#" + fieldName
                            + ": missing @JdbcTypeCode(SqlTypes.JSON) on a "
                            + "JSON/JSONB field");
                }
            } catch (ClassNotFoundException ex) {
                failures.add(fqcn + ": entity class not found on classpath");
            } catch (NoSuchFieldException ex) {
                failures.add(fqcn + "#" + fieldName
                        + ": field not found - was it renamed?");
            }
        }
        if (!failures.isEmpty()) {
            fail("Known-good JSONB mapping(s) regressed:\n"
                    + String.join("\n", failures));
        }
    }

    // ============================================================
    // NEGATIVE TEST - fixture class is detected
    // ============================================================

    /**
     * A negative test that proves the check is not silently
     * passing. The fixture class
     * {@link JsonbMappingViolationFixture} is loaded via
     * reflection and run through the same JSONB-column
     * detection. The fixture deliberately violates the rule
     * (a field with {@code @Column(columnDefinition = "jsonb")}
     * but no JSON type mapping). The check MUST flag it.
     *
     * <p>This is the only way to prove the check actually
     * fires; a positive-only test could pass on an empty
     * codebase or with a trivially wrong check that always
     * returns "no violation".
     */
    @Test
    @DisplayName("Detection mechanism flags a deliberately broken fixture class")
    void violationFixtureClassIsDetected() {
        Field badField;
        try {
            Class<?> fixtureClass = Class.forName(
                    "com.example.ticketing.architecture."
                            + "JsonbMappingViolationFixture");
            badField = fixtureClass.getDeclaredField("badJsonbField");
        } catch (ClassNotFoundException | NoSuchFieldException ex) {
            fail("Negative test fixture is missing - "
                    + "JsonbMappingViolationFixture#badJsonbField: "
                    + ex.getMessage());
            return;
        }
        // Sanity: the fixture must actually be a JSONB
        // column. If this assertion fails, the negative
        // test is meaningless.
        assertThat(hasJsonbColumn(badField))
                .as("fixture field has @Column with a JSON/JSONB "
                        + "columnDefinition")
                .isTrue();
        // Sanity: the fixture must NOT have a JSON type
        // mapping. If this fails, the negative test is
        // testing the wrong thing.
        assertThat(hasJsonTypeMapping(badField))
                .as("fixture field deliberately omits "
                        + "@JdbcTypeCode(SqlTypes.JSON)")
                .isFalse();
        // The check MUST flag this combination as a
        // violation.
        assertThat(hasJsonbColumn(badField) && !hasJsonTypeMapping(badField))
                .as("JSONB column without JSON type mapping is detected as "
                        + "a violation")
                .isTrue();
    }

    // ============================================================
    // Helpers
    // ============================================================

    /**
     * Return true if the field's {@code @Column} annotation
     * declares a JSON or JSONB column. The check is
     * case-insensitive and matches both
     * {@code @Column(columnDefinition = "jsonb")} and
     * {@code @Column(columnDefinition = "JSON")}.
     *
     * <p>Field-level access is used so private fields are
     * not silently skipped.
     */
    private static boolean hasJsonbColumn(Field field) {
        Column column = field.getAnnotation(Column.class);
        if (column == null) {
            return false;
        }
        return isJsonColumnDefinition(column.columnDefinition());
    }

    /**
     * Return true if the field has a Hibernate JSON JDBC
     * type mapping. The only such mapping in the current
     * codebase is {@code @JdbcTypeCode(SqlTypes.JSON)}. If
     * a custom JdbcType is ever introduced, this method
     * will be extended; the comment in
     * {@link JsonbMappingArchitectureTest} header documents
     * the extension point.
     */
    private static boolean hasJsonTypeMapping(Field field) {
        JdbcTypeCode jt = field.getAnnotation(JdbcTypeCode.class);
        if (jt != null && jt.value() == SqlTypes.JSON) {
            return true;
        }
        return false;
    }

    private static String formatViolation(Class<?> entityClass, Field field) {
        Column column = field.getAnnotation(Column.class);
        String columnName = (column == null || column.name() == null
                || column.name().isBlank())
                ? "(default: " + field.getName() + ")"
                : column.name();
        return "  - " + entityClass.getName() + "#" + field.getName()
                + " -> column '" + columnName
                + "' declared as " + column.columnDefinition()
                + " but field has no @JdbcTypeCode(SqlTypes.JSON)";
    }

    // ============================================================
    // Entity class discovery
    // ============================================================

    /**
     * Discover all classes under the application package
     * that are annotated with {@code @Entity}. Discovery is
     * performed by walking the classpath URLs that contain
     * the application package and looking for {@code .class}
     * files. We do not load every class on the classpath -
     * only those inside the {@link #APP_PACKAGE} directory
     * tree.
     *
     * <p>This avoids adding a new dependency (no Reflections
     * library, no Spring's
     * {@code ClassPathScanningCandidateComponentProvider}
     * that needs a running ApplicationContext). It runs
     * entirely against compiled bytecode in the build
     * output directory.
     */
    private static Set<String> discoverEntityClassNames() throws IOException {
        Set<String> result = new TreeSet<>();
        String packagePath = APP_PACKAGE.replace('.', '/');
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = cl.getResources(packagePath);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            String protocol = url.getProtocol();
            if ("file".equals(protocol)) {
                File directory = new File(URLDecoder.decode(
                        url.getFile(), StandardCharsets.UTF_8));
                scanDirectoryForEntityClasses(directory, APP_PACKAGE, result);
            }
            // JAR resources (jar:file:.../foo.jar!/com/...) are
            // not currently scanned. The project uses Maven
            // and the test classpath is the unpacked
            // target/classes directory at test time, so the
            // 'file' protocol covers the relevant paths. If
            // the test classpath ever shifts to JAR scanning,
            // this method must be extended to read JAR
            // entries.
        }
        return result;
    }

    /**
     * Recursively walk a directory looking for
     * {@code .class} files under the given package. Each
     * class is loaded (without initialisation) and the
     * {@code @Entity} annotation is checked at the class
     * level.
     */
    private static void scanDirectoryForEntityClasses(
            File directory, String packageName, Set<String> out) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectoryForEntityClasses(
                        file, packageName + "." + file.getName(), out);
            } else if (file.getName().endsWith(".class")) {
                String simpleName = file.getName()
                        .substring(0, file.getName().length() - ".class".length());
                String fqcn = packageName + "." + simpleName;
                if (isEntityClass(fqcn)) {
                    out.add(fqcn);
                }
            }
        }
    }

    /**
     * Return true if the class with the given fully
     * qualified name has the {@code @Entity} annotation.
     * Class loading is done without initialisation
     * ({@code initialize=false}) so the test does not
     * trigger static initialisers, Spring configuration
     * processing, or any side effects.
     */
    private static boolean isEntityClass(String fqcn) {
        try {
            Class<?> klass = Class.forName(fqcn, false,
                    Thread.currentThread().getContextClassLoader());
            return klass.isAnnotationPresent(Entity.class);
        } catch (Throwable ex) {
            // We deliberately swallow all errors here so
            // that classes that fail to load (e.g.
            // third-party classes that we do not need to
            // scan) do not break entity discovery. The
            // discovered classes are the ones that load
            // successfully and carry @Entity.
            return false;
        }
    }
}
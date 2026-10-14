package com.example.ticketing.architecture;

import jakarta.persistence.Column;

/**
 * CP-3.3.12 negative-test fixture.
 *
 * <p>This class is a deliberate violation of the JSONB
 * mapping rule: a {@code @Column(columnDefinition = "jsonb")}
 * on a {@code String} field WITHOUT
 * {@code @JdbcTypeCode(SqlTypes.JSON)}. It is used by
 * {@link JsonbMappingArchitectureTest#violationFixtureClassIsDetected()}
 * to prove that the static check actually fires on bad
 * data.
 *
 * <h2>Important: do NOT add this class to the production
 * classpath</h2>
 * This file lives under {@code src/test/java/...} and is
 * therefore compiled into {@code target/test-classes} only.
 * It is never packaged with the production JAR. If you
 * need a similar negative fixture for a different
 * annotation, add a NEW class in the same package - do not
 * add real entity fields to this one and do not move this
 * class to {@code src/main}.
 */
class JsonbMappingViolationFixture {

    /**
     * Deliberately broken: a JSONB column declared on a
     * plain {@code String} field with no Hibernate JSON
     * type mapping. Loading this class with the same check
     * the production entities go through MUST flag it.
     */
    @Column(columnDefinition = "jsonb")
    private String badJsonbField;
}

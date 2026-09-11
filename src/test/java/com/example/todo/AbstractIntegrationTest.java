package com.example.todo;

/**
 * Base class for integration tests.
 *
 * <p>Full integration tests (with a real PostgreSQL container) require:
 * <ol>
 *   <li>A Docker daemon accessible to the test runner (Docker-in-Docker on CI).</li>
 *   <li>Testcontainers on the classpath (already declared in pom.xml).</li>
 *   <li>Optionally set {@code testcontainers.reuse.enable=true} in
 *       {@code ~/.testcontainers.properties} to skip container-startup overhead between runs.</li>
 * </ol>
 *
 * <p>How to run locally:
 * <pre>{@code
 *   ./mvnw verify
 * }</pre>
 * That starts a singleton {@code PostgreSQLContainer} (shared across all test classes via the
 * static field below), runs Flyway migrations once, and executes every test class that extends
 * this base. Each test class is responsible for table isolation (e.g. TRUNCATE tasks between
 * tests via {@code @BeforeEach}).
 *
 * <p>Container-reuse mode: add the following line to {@code ~/.testcontainers.properties}:
 * <pre>{@code
 *   testcontainers.reuse.enable=true
 * }</pre>
 * With reuse enabled, Testcontainers will keep the container alive between Maven runs so that
 * the second {@code ./mvnw verify} on the same machine skips the ~5-second startup.
 */
public abstract class AbstractIntegrationTest {

    /*
     * Singleton container pattern: the static field is initialised once per JVM and shared by
     * every subclass, so the container starts exactly once regardless of how many test classes
     * extend this base.
     *
     * Uncomment the block below and add @SpringBootTest + @AutoConfigureMockMvc to subclasses
     * when running against a real database.  The unit-test harness that currently governs this
     * project requires Mockito-only tests, so the live wiring is left as infrastructure ready
     * to enable.
     */

    /*
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withReuse(true);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    */
}

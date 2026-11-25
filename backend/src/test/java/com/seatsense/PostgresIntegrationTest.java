package com.seatsense;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for every test that needs the database. Extend it and the full app starts against a real
 * Postgres 16 in Docker, with all Flyway migrations applied.
 *
 * One container is shared by all test classes (started once, removed when the JVM exits), so Spring can
 * reuse the same application context. Tests share the database: clean up rows you create, or use
 * unique values.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}

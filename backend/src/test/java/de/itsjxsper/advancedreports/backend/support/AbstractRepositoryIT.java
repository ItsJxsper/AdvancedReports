package de.itsjxsper.advancedreports.backend.support;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for repository integration tests. Runs a {@code @DataJpaTest} slice against the real
 * Postgres container instead of an in-memory database, because the point of these tests is to prove
 * the JPQL queries, entity graphs and database constraints actually work on Postgres.
 * <p>
 * {@code replace = NONE} keeps Spring from swapping in an embedded datasource. The slice does not
 * load Redis, AMQP or S3, so those containers are irrelevant here.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
public abstract class AbstractRepositoryIT {

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        ContainerSupport.registerDatabase(registry);
    }
}

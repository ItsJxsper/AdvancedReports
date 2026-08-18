package de.itsjxsper.advancedreports.backend.support;

import de.itsjxsper.advancedreports.backend.ratelimit.aspect.RateLimitAspect;
import io.lettuce.core.RedisClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * Base class for end-to-end tests: the whole application on a random port, talking to the real
 * Postgres, RabbitMQ, Redis and MinIO containers over the network.
 * <p>
 * The {@code s3} profile is activated here because {@code S3Config} is gated behind it; without the
 * profile there is no {@code S3Client} bean and every screenshot upload fails. {@code @ActiveProfiles}
 * also overrides the {@code spring.profiles.active=dev} from {@code application.properties}, which
 * would otherwise try to bring up the docker-compose stack.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "s3"})
public abstract class AbstractE2ETest {

    /**
     * Identity used for the {@code X-Server-UUID} header, and therefore for the server bucket.
     */
    protected static final String RATE_LIMIT_SERVER_ID = UUID.randomUUID().toString();

    /**
     * Identity used for the {@code X-Player-UUID} header.
     */
    protected static final String RATE_LIMIT_PLAYER_ID = UUID.randomUUID().toString();

    /**
     * Identity used for the {@code X-Discord-ID} header.
     */
    protected static final String RATE_LIMIT_DISCORD_ID = "123456789012345678";

    @LocalServerPort
    protected int port;

    @Autowired
    protected DataSource dataSource;

    @Autowired
    protected RedisClient redisClient;

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        ContainerSupport.registerAll(registry);
    }

    /**
     * The web server runs on its own threads, so a test-managed transaction would never be seen by
     * the request handling and could not be rolled back. Truncating up front is the reliable way to
     * isolate end-to-end tests from each other; flushing Redis keeps rate-limit buckets from one test
     * throttling the next.
     */
    @BeforeEach
    void resetState() {
        DatabaseCleaner.clean(dataSource);
        RedisCleaner.flush(redisClient);
    }

    /**
     * A client without any rate-limit headers. Configured not to throw on 4xx/5xx so tests can assert
     * on error responses and on the {@code ApiErrorResponse} body.
     */
    protected RestClient.Builder clientBuilder() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                });
    }

    /**
     * A client carrying all three rate-limit headers, so every endpoint is reachable regardless of
     * which identities its {@code @RateLimited} annotation demands.
     */
    protected RestClient client() {
        return clientBuilder()
                .defaultHeader(RateLimitAspect.HEADER_SERVER_UUID, RATE_LIMIT_SERVER_ID)
                .defaultHeader(RateLimitAspect.HEADER_PLAYER_UUID, RATE_LIMIT_PLAYER_ID)
                .defaultHeader(RateLimitAspect.HEADER_DISCORD_ID, RATE_LIMIT_DISCORD_ID)
                .build();
    }
}

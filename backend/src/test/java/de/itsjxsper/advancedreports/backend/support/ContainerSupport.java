package de.itsjxsper.advancedreports.backend.support;

import com.redis.testcontainers.RedisContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.net.URI;

/**
 * Holds the infrastructure containers for the whole test suite.
 * <p>
 * The containers are {@code static} and started exactly once in the class initializer, so every
 * integration and end-to-end test reuses the same Postgres, RabbitMQ, Redis and MinIO instance
 * instead of paying container startup per test class. Ryuk shuts them down when the JVM exits.
 */
public final class ContainerSupport {

    public static final String S3_BUCKET = "advancedreports-test";
    public static final String S3_REGION = "eu-central-1";

    public static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

    public static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:latest"));

    public static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:latest"));

    public static final MinIOContainer MINIO =
            new MinIOContainer(DockerImageName.parse("minio/minio:latest"));

    static {
        Startables.deepStart(POSTGRES, RABBITMQ, REDIS, MINIO).join();
        createBucket();
    }

    private ContainerSupport() {
    }

    /**
     * Registers only the datasource properties — enough for {@code @DataJpaTest} slices, which do
     * not load the Redis, AMQP or S3 configuration at all.
     */
    public static void registerDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    /**
     * Registers every property the full application context needs.
     * <p>
     * Redis has to go through {@code @DynamicPropertySource} rather than {@code @ServiceConnection}:
     * {@code RedisConfig} reads the raw {@code spring.data.redis.*} properties via {@code @Value}
     * instead of consuming the {@code RedisConnectionDetails} bean that a service connection would
     * contribute, so a service connection alone would leave it pointing at {@code localhost:6379}.
     */
    public static void registerAll(DynamicPropertyRegistry registry) {
        registerDatabase(registry);

        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);

        registry.add("spring.data.redis.host", REDIS::getRedisHost);
        registry.add("spring.data.redis.port", REDIS::getRedisPort);

        registry.add("aws.s3.bucket", () -> S3_BUCKET);
        registry.add("aws.s3.region", () -> S3_REGION);
        registry.add("aws.s3.endpoint-url", MINIO::getS3URL);
        registry.add("aws.s3.access-key", MINIO::getUserName);
        registry.add("aws.s3.secret-key", MINIO::getPassword);
    }

    /**
     * An S3 client pointed at the MinIO container, for tests that need to verify object storage
     * side effects directly instead of through the REST API.
     */
    public static S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(S3_REGION))
                .endpointOverride(URI.create(MINIO.getS3URL()))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword())))
                .build();
    }

    private static void createBucket() {
        try (S3Client client = s3Client()) {
            client.createBucket(CreateBucketRequest.builder().bucket(S3_BUCKET).build());
        }
    }
}

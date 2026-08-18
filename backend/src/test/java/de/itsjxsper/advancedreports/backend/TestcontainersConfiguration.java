package de.itsjxsper.advancedreports.backend;

import com.redis.testcontainers.RedisContainer;
import de.itsjxsper.advancedreports.backend.support.ContainerSupport;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * Container wiring for {@link TestBackendApplication}, i.e. running the application locally against
 * throwaway containers instead of the docker-compose stack.
 * <p>
 * Automated tests do not use this class — they go through
 * {@link de.itsjxsper.advancedreports.backend.support.AbstractE2ETest} so that all test classes share
 * one set of containers.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgreSQLContainer() {
        return ContainerSupport.POSTGRES;
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitMQContainer() {
        return ContainerSupport.RABBITMQ;
    }

    @Bean
    RedisContainer redisContainer() {
        return ContainerSupport.REDIS;
    }

    @Bean
    MinIOContainer minioContainer() {
        return ContainerSupport.MINIO;
    }

    /**
     * Redis and S3 cannot rely on {@code @ServiceConnection}: {@code RedisConfig} and
     * {@code S3ScreenshotStorageService} read the raw properties through {@code @Value} instead of
     * consuming the connection-details beans, so the values have to be published as properties.
     */
    @Bean
    DynamicPropertyRegistrar containerPropertyRegistrar(RedisContainer redis, MinIOContainer minio) {
        return registry -> {
            registry.add("spring.data.redis.host", redis::getRedisHost);
            registry.add("spring.data.redis.port", redis::getRedisPort);
            registry.add("aws.s3.bucket", () -> ContainerSupport.S3_BUCKET);
            registry.add("aws.s3.region", () -> ContainerSupport.S3_REGION);
            registry.add("aws.s3.endpoint-url", minio::getS3URL);
            registry.add("aws.s3.access-key", minio::getUserName);
            registry.add("aws.s3.secret-key", minio::getPassword);
        };
    }
}

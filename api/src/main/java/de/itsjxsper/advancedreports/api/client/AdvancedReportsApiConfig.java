package de.itsjxsper.advancedreports.api.client;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Immutable configuration for {@link AdvancedReportsApi}.
 * <p>
 * Built via Lombok's {@code @Builder} on the private constructor. Validation of required
 * fields and resolution of default values both happen here, so any {@link AdvancedReportsApiConfig}
 * instance that was successfully constructed is guaranteed to be valid and fully populated.
 * <p>
 * Usage:
 * <pre>{@code
 * AdvancedReportsApiConfig config = AdvancedReportsApiConfig.builder()
 *     .baseUrl("http://localhost:8080")
 *     .apiKey("your-secret-key")
 *     .serverUuid(serverUuid)
 *     .build();
 *
 * AdvancedReportsApi api = new AdvancedReportsApi(config);
 * }</pre>
 */
@Getter
public final class AdvancedReportsApiConfig {

    private final String baseUrl;
    private final UUID serverUuid;
    private final int maxRetries;
    private final int threadPoolSize;
    private final int maxIdleConnections;
    private final long keepAliveMinutes;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration callTimeout;
    private final Executor executor;

    @Builder
    private AdvancedReportsApiConfig(String baseUrl,
                                     UUID serverUuid,
                                     Integer maxRetries,
                                     Integer threadPoolSize,
                                     Integer maxIdleConnections,
                                     Long keepAliveMinutes,
                                     Duration connectTimeout,
                                     Duration readTimeout,
                                     Duration callTimeout,
                                     Executor executor) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("baseUrl must not be blank");
        }
        if (serverUuid == null) {
            throw new IllegalStateException("serverUuid must not be null");
        }

        this.baseUrl = baseUrl;
        this.serverUuid = serverUuid;
        // Defaults in case they weren't set via the builder (Lombok otherwise passes null/0).
        this.maxRetries = maxRetries != null ? maxRetries : 3;
        this.threadPoolSize = threadPoolSize != null ? threadPoolSize : 4;
        this.maxIdleConnections = maxIdleConnections != null ? maxIdleConnections : 5;
        this.keepAliveMinutes = keepAliveMinutes != null ? keepAliveMinutes : 5;
        this.connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(5);
        this.readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(10);
        this.callTimeout = callTimeout != null ? callTimeout : Duration.ofSeconds(15);
        this.executor = executor;
    }
}
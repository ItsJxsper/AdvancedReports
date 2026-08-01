package de.itsjxsper.advancedreports.api.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.itsjxsper.advancedreports.api.interceptor.RetryOn429Interceptor;
import de.itsjxsper.advancedreports.api.interceptor.ServerHeaderInterceptor;
import lombok.Getter;
import okhttp3.OkHttpClient;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Central entry point for the AdvancedReports backend API.
 * <p>
 * Bundles the domain-specific clients ({@link #reports()}, {@link #screenshots()}, ...)
 * and configures the underlying {@link OkHttpClient} once with:
 * <ul>
 *     <li>{@link ServerHeaderInterceptor} – global {@code X-Server-UUID} header</li>
 *     <li>{@link RetryOn429Interceptor} – automatic retry in the event of rate limiting</li>
 *     <li>a dedicated {@link ExecutorService}, so that blocking HTTP calls
 *         do not block the {@code ForkJoinPool.commonPool()} or the Bukkit main thread</li>
 * </ul>
 * <p>
 * Usage (plugin/bot side):
 * <pre>{@code
 * AdvancedReportsApi api = AdvancedReportsApi.builder()
 *     .baseUrl(‘http://localhost:8080’)
 *     .serverUuid(serverUuid)
 *     .build();
 *
 * api.reports().createReport(dto, reporterUuid)
 *     .thenAccept(report -> ...)
 *     .exceptionally(ex -> { ...; return null; });
 *
 * // When shutting down the plugin:
 * api.shutdown();
 * }</pre>
 */
public final class AdvancedReportsApi {

    private final ExecutorService executorService;

    @Getter
    private final CategoryApiClient categoryApiClient;

    @Getter
    private final DiscordPlayerApiClient discordPlayerApiClient;

    @Getter
    private final PlayerApiClient playerApiClient;

    @Getter
    private final ReportsApiClient reportsApiClient;

    public AdvancedReportsApi(AdvancedReportsApiConfig config) {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        this.executorService = config.getExecutor() != null
                ? null // externally managed Executor, we do not close it ourselves
                : Executors.newFixedThreadPool(config.getThreadPoolSize(), r -> {
            Thread thread = new Thread(r, "advancedreports-api-worker");
            thread.setDaemon(true);
            return thread;
        });

        Executor effectiveExecutor = config.getExecutor() != null ? config.getExecutor() : this.executorService;


        OkHttpClient httpClient = AdvancedReportsHttpClientFactory.create(config);

        String baseUrl = config.getBaseUrl();

        this.categoryApiClient = new CategoryApiClient(httpClient, baseUrl, objectMapper, effectiveExecutor);
        this.discordPlayerApiClient = new DiscordPlayerApiClient(httpClient, baseUrl, objectMapper, effectiveExecutor);
        this.playerApiClient = new PlayerApiClient(httpClient, baseUrl, objectMapper, effectiveExecutor);
        this.reportsApiClient = new ReportsApiClient(httpClient, baseUrl, objectMapper, effectiveExecutor);
    }

    /**
     * Shuts down the internally created thread pool. Should be called on plugin/bot shutdown
     * (e.g., in {@code onDisable()}). If no internal executor was created (because one was
     * passed into the config), this call is a no-op – responsibility for its lifecycle then
     * lies with the caller.
     */
    public void shutdown() {
        if (this.executorService != null) {
            this.executorService.shutdown();
        }
    }
}

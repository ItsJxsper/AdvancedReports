package de.itsjxsper.advancedreports.api.client;

import de.itsjxsper.advancedreports.api.interceptor.RetryOn429Interceptor;
import de.itsjxsper.advancedreports.api.interceptor.ServerHeaderInterceptor;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

import java.util.concurrent.TimeUnit;

/**
 * Builds the {@link OkHttpClient} used by {@link AdvancedReportsApi}, wired up with:
 * <ul>
 *     <li>{@link ServerHeaderInterceptor} – global {@code X-Server-UUID}/{@code X-API-Key} header</li>
 *     <li>{@link RetryOn429Interceptor} – automatic retry on rate limiting</li>
 *     <li>a {@link ConnectionPool} and connect/read/call timeouts, all taken from
 *         {@link AdvancedReportsApiConfig}</li>
 * </ul>
 * <p>
 * Stateless – every call to {@link #create(AdvancedReportsApiConfig)} builds a fresh client.
 */
final class AdvancedReportsHttpClientFactory {

    private AdvancedReportsHttpClientFactory() {
        // utility class, no instances
    }

    static OkHttpClient create(AdvancedReportsApiConfig config) {
        return new OkHttpClient.Builder()
                .addInterceptor(new ServerHeaderInterceptor(config.getServerUuid()))
                .addInterceptor(new RetryOn429Interceptor(config.getMaxRetries()))
                .connectionPool(new ConnectionPool(
                        config.getMaxIdleConnections(),
                        config.getKeepAliveMinutes(),
                        TimeUnit.MINUTES))
                .connectTimeout(config.getConnectTimeout())
                .readTimeout(config.getReadTimeout())
                .callTimeout(config.getCallTimeout())
                .build();
    }
}
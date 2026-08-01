package de.itsjxsper.advancedreports.api.interceptor;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.UUID;

/**
 * Adds the static, server-wide headers to every outgoing request:
 * <ul>
 *     <li>{@code X-Server-UUID} – identifies the Minecraft server to the backend</li>
 * </ul>
 * <p>
 * Dynamic headers (player/Discord UUID) are NOT set here but
 * are passed explicitly for each request method (see {@link de.itsjxsper.advancedreports.api.client.AbstractApiClient}).
 * This avoids ThreadLocal issues with CompletableFuture/asynchronous thread pools.
 */
public class ServerHeaderInterceptor implements Interceptor {

    private final String serverUuid;

    public ServerHeaderInterceptor(UUID serverUuid) {
        this.serverUuid = serverUuid.toString();
    }

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request original = chain.request();

        Request.Builder builder = original.newBuilder()
                .header("X-Server-UUID", serverUuid);

        return chain.proceed(builder.build());
    }
}

package de.itsjxsper.advancedreports.api.interceptor;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Handles {@code 429 Too Many Requests} responses from the backend
 * ({@code RateLimitAspect} + {@code RateLimiterService}).
 * <p>
 * Reads the {@code Retry-After} header (in seconds) or, if not present,
 * falls back to a fixed backoff, waits, and repeats the request up to
 * {@code maxRetries} times. After that, the last 429 response is passed through,
 * so that the caller can wrap it in an {@code ApiRequestException}.
 */
public class RetryOn429Interceptor implements Interceptor {

    private static final long DEFAULT_BACKOFF_MS = 1000L;

    private final int maxRetries;

    public RetryOn429Interceptor(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);

        int attempt = 0;
        while (response.code() == 429 && attempt < maxRetries) {
            long waitMs = resolveRetryAfterMillis(response);

            response.close();

            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("The retry timeout has been interrupted", e);
            }

            attempt++;
            response = chain.proceed(request);
        }

        return response;
    }

    private long resolveRetryAfterMillis(Response response) {
        String retryAfterHeader = response.header("Retry-After");

        if (retryAfterHeader != null) {
            try {
                return Long.parseLong(retryAfterHeader.trim()) * 1000L;
            } catch (NumberFormatException ignored) {
                // Not a numeric value (e.g. HTTP-date format) -> use the fallback
            }
        }

        String remainingHeader = firstNonNull(
                response.header("X-RateLimit-Server-Remaining"),
                response.header("X-RateLimit-Player-Remaining"),
                response.header("X-RateLimit-Discord-Remaining")
        );

        if (remainingHeader != null) {
            return DEFAULT_BACKOFF_MS;
        }

        return DEFAULT_BACKOFF_MS;
    }

    private String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}

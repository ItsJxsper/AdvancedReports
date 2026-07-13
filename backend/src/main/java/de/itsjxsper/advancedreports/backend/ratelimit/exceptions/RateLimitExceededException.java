package de.itsjxsper.advancedreports.backend.ratelimit.exceptions;

import java.util.concurrent.TimeUnit;

public class RateLimitExceededException extends RuntimeException {
    private final long retryAfterMs;

    public RateLimitExceededException(String id, long nanosToWait) {
        super("Rate limit exceeded for: " + id);
        this.retryAfterMs = TimeUnit.NANOSECONDS.toMillis(nanosToWait);
    }

    public long getRetryAfterMs() {
        return retryAfterMs;
    }
}

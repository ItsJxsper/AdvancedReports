package de.itsjxsper.advancedreports.backend.support;

import io.lettuce.core.RedisClient;

/**
 * Wipes the Bucket4j rate-limit buckets from Redis.
 * <p>
 * Buckets are keyed by identity ({@code rl:player:<uuid>}) and survive across tests, so a test that
 * deliberately exhausts a bucket would otherwise poison every later test using the same identity.
 */
public final class RedisCleaner {

    private RedisCleaner() {
    }

    public static void flush(RedisClient redisClient) {
        try (var connection = redisClient.connect()) {
            connection.sync().flushall();
        }
    }
}

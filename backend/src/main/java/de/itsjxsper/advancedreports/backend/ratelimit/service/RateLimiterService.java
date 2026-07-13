package de.itsjxsper.advancedreports.backend.ratelimit.service;

import de.itsjxsper.advancedreports.backend.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private static final String PLAYER_PREFIX = "rl:player:";
    private static final String SERVER_PREFIX = "rl:server:";
    private static final String DISCORD_PREFIX = "rl:discord:";
    private final RateLimitProperties props;
    private final LettuceBasedProxyManager<String> proxyManager;

    public ConsumptionProbe tryConsumeServer(String serverUuid) {
        return getBucket(SERVER_PREFIX + serverUuid, props.getServerRequestsPerSecond())
                .tryConsumeAndReturnRemaining(1);
    }

    public ConsumptionProbe tryConsumePlayer(String playerUuid) {
        return getBucket(PLAYER_PREFIX + playerUuid, props.getPlayerRequestsPerSecond())
                .tryConsumeAndReturnRemaining(1);
    }

    public ConsumptionProbe tryConsumeDiscord(String discordId) {
        return getBucket(DISCORD_PREFIX + discordId, props.getDiscordRequestsPerSecond())
                .tryConsumeAndReturnRemaining(1);
    }

    private BucketProxy getBucket(String key, int requestsPerSecond) {
        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerSecond)
                        .refillIntervally(requestsPerSecond, Duration.ofSeconds(1))
                        .build())
                .build();

        return proxyManager.builder().build(key, () -> config);
    }
}
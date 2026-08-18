package de.itsjxsper.advancedreports.backend.ratelimit.service;

import de.itsjxsper.advancedreports.backend.config.RateLimitProperties;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimiterService")
class RateLimiterServiceTest {

    @Mock
    private LettuceBasedProxyManager<String> proxyManager;

    @Mock
    private RemoteBucketBuilder<String> bucketBuilder;

    @Mock
    private BucketProxy bucketProxy;

    private RateLimitProperties properties;
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        rateLimiterService = new RateLimiterService(properties, proxyManager);

        when(proxyManager.builder()).thenReturn(bucketBuilder);
        when(bucketBuilder.build(anyString(), any(Supplier.class))).thenReturn(bucketProxy);
        when(bucketProxy.tryConsumeAndReturnRemaining(1)).thenReturn(ConsumptionProbe.consumed(4, 0));
    }

    /**
     * The bucket configuration is handed over as a {@link Supplier}, so it only materialises when the
     * proxy manager asks for it. Resolving it here is what lets the tests assert on the capacity.
     */
    private BucketConfiguration capturedConfiguration() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Supplier<BucketConfiguration>> supplier = ArgumentCaptor.forClass(Supplier.class);
        verify(bucketBuilder).build(anyString(), supplier.capture());
        return supplier.getValue().get();
    }

    private String capturedKey() {
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(bucketBuilder).build(key.capture(), any(Supplier.class));
        return key.getValue();
    }

    @Nested
    @DisplayName("tryConsumeServer")
    class TryConsumeServer {

        @Test
        @DisplayName("nutzt den Redis-Key rl:server:<uuid>")
        void shouldUseServerKeyPrefix() {
            rateLimiterService.tryConsumeServer("server-1");

            assertThat(capturedKey()).isEqualTo("rl:server:server-1");
        }

        @Test
        @DisplayName("konfiguriert das Bucket mit den Server-Requests pro Sekunde")
        void shouldUseServerCapacity() {
            properties.setServerRequestsPerSecond(250);

            rateLimiterService.tryConsumeServer("server-1");

            assertThat(capturedConfiguration().getBandwidths()[0].getCapacity()).isEqualTo(250);
        }

        @Test
        @DisplayName("gibt die Probe des Buckets zurück")
        void shouldReturnProbe() {
            ConsumptionProbe probe = rateLimiterService.tryConsumeServer("server-1");

            assertThat(probe.isConsumed()).isTrue();
            assertThat(probe.getRemainingTokens()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("tryConsumePlayer")
    class TryConsumePlayer {

        @Test
        @DisplayName("nutzt den Redis-Key rl:player:<uuid>")
        void shouldUsePlayerKeyPrefix() {
            rateLimiterService.tryConsumePlayer("player-1");

            assertThat(capturedKey()).isEqualTo("rl:player:player-1");
        }

        @Test
        @DisplayName("konfiguriert das Bucket mit den Player-Requests pro Sekunde")
        void shouldUsePlayerCapacity() {
            properties.setPlayerRequestsPerSecond(7);

            rateLimiterService.tryConsumePlayer("player-1");

            assertThat(capturedConfiguration().getBandwidths()[0].getCapacity()).isEqualTo(7);
        }

        @Test
        @DisplayName("gibt eine abgelehnte Probe zurück, wenn das Bucket leer ist")
        void shouldReturnRejectedProbe() {
            when(bucketProxy.tryConsumeAndReturnRemaining(1))
                    .thenReturn(ConsumptionProbe.rejected(0, 500_000_000L, 500_000_000L));

            ConsumptionProbe probe = rateLimiterService.tryConsumePlayer("player-1");

            assertThat(probe.isConsumed()).isFalse();
            assertThat(probe.getNanosToWaitForRefill()).isEqualTo(500_000_000L);
        }
    }

    @Nested
    @DisplayName("tryConsumeDiscord")
    class TryConsumeDiscord {

        @Test
        @DisplayName("nutzt den Redis-Key rl:discord:<id>")
        void shouldUseDiscordKeyPrefix() {
            rateLimiterService.tryConsumeDiscord("123456789");

            assertThat(capturedKey()).isEqualTo("rl:discord:123456789");
        }

        @Test
        @DisplayName("konfiguriert das Bucket mit den Discord-Requests pro Sekunde")
        void shouldUseDiscordCapacity() {
            properties.setDiscordRequestsPerSecond(9);

            rateLimiterService.tryConsumeDiscord("123456789");

            assertThat(capturedConfiguration().getBandwidths()[0].getCapacity()).isEqualTo(9);
        }
    }

}

package de.itsjxsper.advancedreports.backend.ratelimit.aspect;

import de.itsjxsper.advancedreports.backend.ratelimit.annotation.RateLimited;
import de.itsjxsper.advancedreports.backend.ratelimit.exceptions.MissingHeaderException;
import de.itsjxsper.advancedreports.backend.ratelimit.exceptions.RateLimitExceededException;
import de.itsjxsper.advancedreports.backend.ratelimit.service.RateLimiterService;
import io.github.bucket4j.ConsumptionProbe;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitAspect")
class RateLimitAspectTest {

    private static final String SERVER_UUID = "44444444-4444-4444-4444-444444444444";
    private static final String PLAYER_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String DISCORD_ID = "123456789012345678";

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    private RateLimitAspect aspect;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private static RateLimited annotationOf(String methodName) {
        try {
            return Handlers.class.getDeclaredMethod(methodName).getAnnotation(RateLimited.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    @BeforeEach
    void setUp() {
        aspect = new RateLimitAspect(rateLimiterService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * Carrier for real {@link RateLimited} annotation instances. Reading them off methods is more
     * faithful than mocking the annotation, because the default values of the annotation itself are
     * part of what is being tested.
     */
    @SuppressWarnings("unused")
    private static final class Handlers {

        @RateLimited
        void serverOnly() {
        }

        @RateLimited(playerUuid = true)
        void serverAndPlayer() {
        }

        @RateLimited(serverUuid = false, discordUserId = true)
        void discordOnly() {
        }

        @RateLimited(serverUuid = false)
        void nothing() {
        }
    }

    @Nested
    @DisplayName("Server limit")
    class ServerLimit {

        @Test
        @DisplayName("lets the call through and sets X-RateLimit-Server-Remaining")
        void shouldProceedAndSetRemainingHeader() throws Throwable {
            request.addHeader(RateLimitAspect.HEADER_SERVER_UUID, SERVER_UUID);
            when(rateLimiterService.tryConsumeServer(SERVER_UUID))
                    .thenReturn(ConsumptionProbe.consumed(99, 0));
            when(joinPoint.proceed()).thenReturn("ok");

            Object result = aspect.handleRateLimit(joinPoint, annotationOf("serverOnly"));

            assertThat(result).isEqualTo("ok");
            assertThat(response.getHeader("X-RateLimit-Server-Remaining")).isEqualTo("99");
            verify(joinPoint).proceed();
        }

        @Test
        @DisplayName("throws MissingHeaderException when X-Server-UUID is missing")
        void shouldRejectMissingHeader() {
            assertThatThrownBy(() -> aspect.handleRateLimit(joinPoint, annotationOf("serverOnly")))
                    .isInstanceOf(MissingHeaderException.class)
                    .hasMessageContaining(RateLimitAspect.HEADER_SERVER_UUID);

            verifyNoInteractions(rateLimiterService);
        }

        @Test
        @DisplayName("throws MissingHeaderException when X-Server-UUID is empty")
        void shouldRejectBlankHeader() {
            request.addHeader(RateLimitAspect.HEADER_SERVER_UUID, "   ");

            assertThatThrownBy(() -> aspect.handleRateLimit(joinPoint, annotationOf("serverOnly")))
                    .isInstanceOf(MissingHeaderException.class);
        }

        @Test
        @DisplayName("throws RateLimitExceededException when the bucket is empty")
        void shouldRejectWhenBucketExhausted() throws Throwable {
            request.addHeader(RateLimitAspect.HEADER_SERVER_UUID, SERVER_UUID);
            when(rateLimiterService.tryConsumeServer(SERVER_UUID))
                    .thenReturn(ConsumptionProbe.rejected(0, 750_000_000L, 750_000_000L));

            assertThatThrownBy(() -> aspect.handleRateLimit(joinPoint, annotationOf("serverOnly")))
                    .isInstanceOf(RateLimitExceededException.class)
                    .hasMessageContaining(SERVER_UUID);

            verify(joinPoint, org.mockito.Mockito.never()).proceed();
        }

        @Test
        @DisplayName("sets the remaining header even when rejecting")
        void shouldSetRemainingHeaderOnRejection() {
            request.addHeader(RateLimitAspect.HEADER_SERVER_UUID, SERVER_UUID);
            when(rateLimiterService.tryConsumeServer(SERVER_UUID))
                    .thenReturn(ConsumptionProbe.rejected(0, 1_000L, 1_000L));

            assertThatThrownBy(() -> aspect.handleRateLimit(joinPoint, annotationOf("serverOnly")))
                    .isInstanceOf(RateLimitExceededException.class);

            assertThat(response.getHeader("X-RateLimit-Server-Remaining")).isEqualTo("0");
        }

        @Test
        @DisplayName("converts the wait time from nanoseconds to milliseconds")
        void shouldConvertRetryAfterToMillis() {
            request.addHeader(RateLimitAspect.HEADER_SERVER_UUID, SERVER_UUID);
            when(rateLimiterService.tryConsumeServer(SERVER_UUID))
                    .thenReturn(ConsumptionProbe.rejected(0, 750_000_000L, 750_000_000L));

            assertThatThrownBy(() -> aspect.handleRateLimit(joinPoint, annotationOf("serverOnly")))
                    .isInstanceOf(RateLimitExceededException.class)
                    .extracting(e -> ((RateLimitExceededException) e).getRetryAfterMs())
                    .isEqualTo(750L);
        }
    }

    @Nested
    @DisplayName("Server and player limit combined")
    class ServerAndPlayerLimit {

        @Test
        @DisplayName("checks both buckets and sets both remaining headers")
        void shouldCheckBothBuckets() throws Throwable {
            request.addHeader(RateLimitAspect.HEADER_SERVER_UUID, SERVER_UUID);
            request.addHeader(RateLimitAspect.HEADER_PLAYER_UUID, PLAYER_UUID);
            when(rateLimiterService.tryConsumeServer(SERVER_UUID))
                    .thenReturn(ConsumptionProbe.consumed(80, 0));
            when(rateLimiterService.tryConsumePlayer(PLAYER_UUID))
                    .thenReturn(ConsumptionProbe.consumed(3, 0));

            aspect.handleRateLimit(joinPoint, annotationOf("serverAndPlayer"));

            assertThat(response.getHeader("X-RateLimit-Server-Remaining")).isEqualTo("80");
            assertThat(response.getHeader("X-RateLimit-Player-Remaining")).isEqualTo("3");
            verify(joinPoint).proceed();
        }

        @Test
        @DisplayName("throws MissingHeaderException when only X-Player-UUID is missing")
        void shouldRejectMissingPlayerHeader() throws Throwable {
            request.addHeader(RateLimitAspect.HEADER_SERVER_UUID, SERVER_UUID);
            when(rateLimiterService.tryConsumeServer(SERVER_UUID))
                    .thenReturn(ConsumptionProbe.consumed(80, 0));

            assertThatThrownBy(() -> aspect.handleRateLimit(joinPoint, annotationOf("serverAndPlayer")))
                    .isInstanceOf(MissingHeaderException.class)
                    .hasMessageContaining(RateLimitAspect.HEADER_PLAYER_UUID);

            verify(joinPoint, org.mockito.Mockito.never()).proceed();
        }
    }

    @Nested
    @DisplayName("Discord limit")
    class DiscordLimit {

        @Test
        @DisplayName("checks only the Discord bucket when serverUuid is switched off")
        void shouldOnlyCheckDiscordBucket() throws Throwable {
            request.addHeader(RateLimitAspect.HEADER_DISCORD_ID, DISCORD_ID);
            when(rateLimiterService.tryConsumeDiscord(DISCORD_ID))
                    .thenReturn(ConsumptionProbe.consumed(4, 0));

            aspect.handleRateLimit(joinPoint, annotationOf("discordOnly"));

            assertThat(response.getHeader("X-RateLimit-Discord-Remaining")).isEqualTo("4");
            assertThat(response.getHeader("X-RateLimit-Server-Remaining")).isNull();
            verify(rateLimiterService, org.mockito.Mockito.never()).tryConsumeServer(
                    org.mockito.ArgumentMatchers.anyString());
        }

        @Test
        @DisplayName("throws MissingHeaderException when X-Discord-ID is missing")
        void shouldRejectMissingDiscordHeader() {
            assertThatThrownBy(() -> aspect.handleRateLimit(joinPoint, annotationOf("discordOnly")))
                    .isInstanceOf(MissingHeaderException.class)
                    .hasMessageContaining(RateLimitAspect.HEADER_DISCORD_ID);
        }

        @Test
        @DisplayName("throws RateLimitExceededException when the Discord bucket is empty")
        void shouldRejectWhenDiscordBucketExhausted() {
            request.addHeader(RateLimitAspect.HEADER_DISCORD_ID, DISCORD_ID);
            when(rateLimiterService.tryConsumeDiscord(DISCORD_ID))
                    .thenReturn(ConsumptionProbe.rejected(0, 1_000L, 1_000L));

            assertThatThrownBy(() -> aspect.handleRateLimit(joinPoint, annotationOf("discordOnly")))
                    .isInstanceOf(RateLimitExceededException.class);
        }
    }

    @Nested
    @DisplayName("Without an active limit")
    class NoLimit {

        @Test
        @DisplayName("lets a call through without any header")
        void shouldProceedWithoutAnyHeader() throws Throwable {
            when(joinPoint.proceed()).thenReturn("ok");

            assertThat(aspect.handleRateLimit(joinPoint, annotationOf("nothing"))).isEqualTo("ok");

            verifyNoInteractions(rateLimiterService);
        }
    }
}

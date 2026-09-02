package de.itsjxsper.advancedreports.backend.e2e;

import de.itsjxsper.advancedreports.backend.ratelimit.aspect.RateLimitAspect;
import de.itsjxsper.advancedreports.backend.support.AbstractE2ETest;
import de.itsjxsper.advancedreports.common.enums.exceptions.api.ApiErrorCode;
import de.itsjxsper.advancedreports.common.model.exceptions.ApiErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the Bucket4j/Redis rate limiter through real HTTP requests.
 * <p>
 * The limits are lowered to 2 requests per second via {@code @TestPropertySource} — the shared test
 * configuration uses 1000/s so that the other end-to-end tests never throttle each other. Because this
 * class carries its own property overrides it gets its own application context, but it still shares the
 * Redis container, hence the {@code RedisCleaner} call inherited from the base class.
 */
@TestPropertySource(properties = {
        "rate-limit.server-requests-per-second=2",
        "rate-limit.player-requests-per-second=2",
        "rate-limit.discord-requests-per-second=2"
})
@DisplayName("E2E: Rate Limiting")
class RateLimitE2ETest extends AbstractE2ETest {

    @Nested
    @DisplayName("Missing headers")
    class MissingHeaders {

        @Test
        @DisplayName("answers 400 MISSING_HEADER when X-Server-UUID is missing")
        void shouldRejectMissingServerHeader() {
            ResponseEntity<ApiErrorResponse> response = clientBuilder().build().get()
                    .uri("/api/v1/categories/count")
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.MISSING_HEADER);
            assertThat(response.getBody().message()).contains("X-Server-UUID");
        }

        @Test
        @DisplayName("answers 400 MISSING_HEADER when X-Player-UUID is missing on reports")
        void shouldRejectMissingPlayerHeader() {
            ResponseEntity<ApiErrorResponse> response = clientBuilder()
                    .defaultHeader(RateLimitAspect.HEADER_SERVER_UUID, UUID.randomUUID().toString())
                    .build()
                    .get()
                    .uri("/api/v1/reports/count")
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.MISSING_HEADER);
            assertThat(response.getBody().message()).contains("X-Player-UUID");
        }

        @Test
        @DisplayName("answers 400 MISSING_HEADER when X-Discord-ID is missing on screenshots")
        void shouldRejectMissingDiscordHeader() {
            ResponseEntity<ApiErrorResponse> response = clientBuilder().build().get()
                    .uri("/api/v1/screenshots/count")
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.MISSING_HEADER);
            assertThat(response.getBody().message()).contains("X-Discord-ID");
        }

        @Test
        @DisplayName("answers 400 MISSING_HEADER when X-Server-UUID is empty")
        void shouldRejectBlankServerHeader() {
            ResponseEntity<ApiErrorResponse> response = clientBuilder()
                    .defaultHeader(RateLimitAspect.HEADER_SERVER_UUID, "   ")
                    .build()
                    .get()
                    .uri("/api/v1/categories/count")
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.MISSING_HEADER);
        }
    }

    @Nested
    @DisplayName("Exhausted bucket")
    class ExhaustedBucket {

        @Test
        @DisplayName("answers 429 RATE_LIMIT_EXCEEDED as soon as the server bucket is empty")
        void shouldReturnTooManyRequests() {
            String serverUuid = UUID.randomUUID().toString();
            var limited = clientBuilder()
                    .defaultHeader(RateLimitAspect.HEADER_SERVER_UUID, serverUuid)
                    .build();

            // Capacity is 2, so the third call within the same second has to be rejected.
            assertThat(limited.get().uri("/api/v1/categories/count").retrieve()
                    .toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(limited.get().uri("/api/v1/categories/count").retrieve()
                    .toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.OK);

            ResponseEntity<ApiErrorResponse> third = limited.get()
                    .uri("/api/v1/categories/count")
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(third.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(third.getBody().code()).isEqualTo(ApiErrorCode.RATE_LIMIT_EXCEEDED);
            assertThat(third.getBody().message()).contains(serverUuid);
        }

        @Test
        @DisplayName("keeps a separate bucket per server UUID")
        void shouldTrackBucketsPerIdentity() {
            String exhausted = UUID.randomUUID().toString();
            var first = clientBuilder()
                    .defaultHeader(RateLimitAspect.HEADER_SERVER_UUID, exhausted)
                    .build();

            first.get().uri("/api/v1/categories/count").retrieve().toBodilessEntity();
            first.get().uri("/api/v1/categories/count").retrieve().toBodilessEntity();
            assertThat(first.get().uri("/api/v1/categories/count").retrieve()
                    .toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

            // A different server must not notice any of it.
            var second = clientBuilder()
                    .defaultHeader(RateLimitAspect.HEADER_SERVER_UUID, UUID.randomUUID().toString())
                    .build();

            assertThat(second.get().uri("/api/v1/categories/count").retrieve()
                    .toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("Remaining headers")
    class RemainingHeaders {

        @Test
        @DisplayName("counts X-RateLimit-Server-Remaining down with every call")
        void shouldCountDownRemainingTokens() {
            var limited = clientBuilder()
                    .defaultHeader(RateLimitAspect.HEADER_SERVER_UUID, UUID.randomUUID().toString())
                    .build();

            ResponseEntity<Void> first = limited.get()
                    .uri("/api/v1/categories/count")
                    .retrieve()
                    .toBodilessEntity();
            ResponseEntity<Void> second = limited.get()
                    .uri("/api/v1/categories/count")
                    .retrieve()
                    .toBodilessEntity();

            assertThat(first.getHeaders().getFirst("X-RateLimit-Server-Remaining")).isEqualTo("1");
            assertThat(second.getHeaders().getFirst("X-RateLimit-Server-Remaining")).isEqualTo("0");
        }

        @Test
        @DisplayName("sets both the server and the player header for reports")
        void shouldSetBothRemainingHeaders() {
            ResponseEntity<Void> response = clientBuilder()
                    .defaultHeader(RateLimitAspect.HEADER_SERVER_UUID, UUID.randomUUID().toString())
                    .defaultHeader(RateLimitAspect.HEADER_PLAYER_UUID, UUID.randomUUID().toString())
                    .build()
                    .get()
                    .uri("/api/v1/reports/count")
                    .retrieve()
                    .toBodilessEntity();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().getFirst("X-RateLimit-Server-Remaining")).isEqualTo("1");
            assertThat(response.getHeaders().getFirst("X-RateLimit-Player-Remaining")).isEqualTo("1");
        }

        @Test
        @DisplayName("sets the Discord header on screenshot endpoints and no server header")
        void shouldOnlySetDiscordHeaderForScreenshots() {
            ResponseEntity<Void> response = clientBuilder()
                    .defaultHeader(RateLimitAspect.HEADER_DISCORD_ID, "987654321098765432")
                    .build()
                    .get()
                    .uri("/api/v1/screenshots/count")
                    .retrieve()
                    .toBodilessEntity();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().getFirst("X-RateLimit-Discord-Remaining")).isEqualTo("1");
            assertThat(response.getHeaders().getFirst("X-RateLimit-Server-Remaining")).isNull();
        }

        @Test
        @DisplayName("returns the remaining header on the rejected request as well")
        void shouldSetRemainingHeaderOnRejection() {
            var limited = clientBuilder()
                    .defaultHeader(RateLimitAspect.HEADER_SERVER_UUID, UUID.randomUUID().toString())
                    .build();

            limited.get().uri("/api/v1/categories/count").retrieve().toBodilessEntity();
            limited.get().uri("/api/v1/categories/count").retrieve().toBodilessEntity();

            ResponseEntity<ApiErrorResponse> rejected = limited.get()
                    .uri("/api/v1/categories/count")
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(rejected.getHeaders().getFirst("X-RateLimit-Server-Remaining")).isEqualTo("0");
        }
    }
}

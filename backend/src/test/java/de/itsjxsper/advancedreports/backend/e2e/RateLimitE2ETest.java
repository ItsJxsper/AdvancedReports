package de.itsjxsper.advancedreports.backend.e2e;

import de.itsjxsper.advancedreports.common.enums.exceptions.api.ApiErrorCode;
import de.itsjxsper.advancedreports.common.model.exceptions.ApiErrorResponse;
import de.itsjxsper.advancedreports.backend.ratelimit.aspect.RateLimitAspect;
import de.itsjxsper.advancedreports.backend.support.AbstractE2ETest;
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
    @DisplayName("Fehlende Header")
    class MissingHeaders {

        @Test
        @DisplayName("antwortet mit 400 MISSING_HEADER, wenn X-Server-UUID fehlt")
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
        @DisplayName("antwortet mit 400 MISSING_HEADER, wenn bei Reports X-Player-UUID fehlt")
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
        @DisplayName("antwortet mit 400 MISSING_HEADER, wenn bei Screenshots X-Discord-ID fehlt")
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
        @DisplayName("antwortet mit 400 MISSING_HEADER, wenn X-Server-UUID leer ist")
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
    @DisplayName("Erschöpftes Bucket")
    class ExhaustedBucket {

        @Test
        @DisplayName("antwortet mit 429 RATE_LIMIT_EXCEEDED, sobald das Server-Bucket leer ist")
        void shouldReturnTooManyRequests() {
            String serverUuid = UUID.randomUUID().toString();
            var limited = clientBuilder()
                    .defaultHeader(RateLimitAspect.HEADER_SERVER_UUID, serverUuid)
                    .build();

            // Kapazität ist 2, der dritte Aufruf innerhalb derselben Sekunde muss abgelehnt werden.
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
        @DisplayName("führt für jede Server-UUID ein eigenes Bucket")
        void shouldTrackBucketsPerIdentity() {
            String exhausted = UUID.randomUUID().toString();
            var first = clientBuilder()
                    .defaultHeader(RateLimitAspect.HEADER_SERVER_UUID, exhausted)
                    .build();

            first.get().uri("/api/v1/categories/count").retrieve().toBodilessEntity();
            first.get().uri("/api/v1/categories/count").retrieve().toBodilessEntity();
            assertThat(first.get().uri("/api/v1/categories/count").retrieve()
                    .toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

            // Ein anderer Server darf davon nichts merken.
            var second = clientBuilder()
                    .defaultHeader(RateLimitAspect.HEADER_SERVER_UUID, UUID.randomUUID().toString())
                    .build();

            assertThat(second.get().uri("/api/v1/categories/count").retrieve()
                    .toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("Remaining-Header")
    class RemainingHeaders {

        @Test
        @DisplayName("zählt X-RateLimit-Server-Remaining mit jedem Aufruf herunter")
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
        @DisplayName("setzt bei Reports sowohl den Server- als auch den Player-Header")
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
        @DisplayName("setzt den Discord-Header bei Screenshot-Endpunkten und keinen Server-Header")
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
        @DisplayName("liefert den Remaining-Header auch bei der abgelehnten Anfrage")
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

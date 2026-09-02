package de.itsjxsper.advancedreports.backend.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.itsjxsper.advancedreports.backend.support.AbstractE2ETest;
import de.itsjxsper.advancedreports.common.enums.exceptions.api.ApiErrorCode;
import de.itsjxsper.advancedreports.common.exceptions.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the wire contract between the backend and the clients that will consume it.
 * <p>
 * Rather than asserting on JSON by hand, the raw error bodies are fed through
 * {@link ApiException#fromHttpResponse} from the {@code common} module — the exact code path the future
 * {@code api} client, the Paper plugin and the Discord bot will use. If the backend ever changes the
 * shape of its error responses, this is where it shows up.
 */
@DisplayName("E2E: error contract with the common module")
class ErrorContractE2ETest extends AbstractE2ETest {

    /**
     * {@code ApiException} takes a Jackson 2 mapper, which is what the {@code common} module compiles against.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ApiException callAndParse(String uri) {
        ResponseEntity<String> response = client().get()
                .uri(uri)
                .retrieve()
                .toEntity(String.class);

        return ApiException.fromHttpResponse(
                response.getStatusCode().value(), response.getBody(), OBJECT_MAPPER);
    }

    @Nested
    @DisplayName("404 responses")
    class NotFoundResponses {

        @Test
        @DisplayName("an unknown report is recognised as NotFound on the client side")
        void shouldParseReportNotFound() {
            ApiException exception = callAndParse("/api/v1/reports/9999");

            assertThat(exception.getHttpStatus()).isEqualTo(404);
            assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.REPORT_NOT_FOUND);
            assertThat(exception.isNotFound()).isTrue();
            assertThat(exception.isRateLimited()).isFalse();
            assertThat(exception.getMessage()).isEqualTo("Report with ID 9999 was not found");
        }

        @Test
        @DisplayName("an unknown category is recognised as NotFound on the client side")
        void shouldParseCategoryNotFound() {
            ApiException exception = callAndParse("/api/v1/categories/9999");

            assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.CATEGORY_NOT_FOUND);
            assertThat(exception.isNotFound()).isTrue();
        }

        @Test
        @DisplayName("an unknown player is recognised as NotFound on the client side")
        void shouldParsePlayerNotFound() {
            ApiException exception = callAndParse("/api/v1/player/" + UUID.randomUUID());

            assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.PLAYER_NOT_FOUND);
            assertThat(exception.isNotFound()).isTrue();
        }

        @Test
        @DisplayName("an unknown server is recognised as NotFound on the client side")
        void shouldParseServerNotFound() {
            ApiException exception = callAndParse("/api/v1/servers/" + UUID.randomUUID());

            assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.SERVER_NOT_FOUND);
            assertThat(exception.isNotFound()).isTrue();
        }

        @Test
        @DisplayName("an unknown screenshot is recognised as NotFound on the client side")
        void shouldParseScreenshotNotFound() {
            ApiException exception = callAndParse("/api/v1/screenshots/9999");

            assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.SCREENSHOT_NOT_FOUND);
            assertThat(exception.isNotFound()).isTrue();
        }

        @Test
        @DisplayName("an unknown Discord link is recognised as NotFound on the client side")
        void shouldParseDiscordUserNotFound() {
            ApiException exception = callAndParse("/api/v1/discord-players/9999");

            assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.DISCORD_USER_NOT_FOUND);
            assertThat(exception.isNotFound()).isTrue();
        }
    }

    @Nested
    @DisplayName("Further status codes")
    class OtherStatusCodes {

        @Test
        @DisplayName("a missing header is reported as MISSING_HEADER")
        void shouldParseMissingHeader() {
            ResponseEntity<String> response = clientBuilder().build().get()
                    .uri("/api/v1/categories/count")
                    .retrieve()
                    .toEntity(String.class);

            ApiException exception = ApiException.fromHttpResponse(
                    response.getStatusCode().value(), response.getBody(), OBJECT_MAPPER);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.MISSING_HEADER);
            assertThat(exception.isNotFound()).isFalse();
            assertThat(exception.isRateLimited()).isFalse();
        }

        @Test
        @DisplayName("a duplicate name is reported as CATEGORY_ALREADY_EXISTS")
        void shouldParseConflict() {
            de.itsjxsper.advancedreports.backend.support.ApiFixtures.createCategory(client(), "cheating");

            ResponseEntity<String> response = client().post()
                    .uri("/api/v1/categories")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(de.itsjxsper.advancedreports.backend.support.TestDataFactory
                            .categoryDto("cheating"))
                    .retrieve()
                    .toEntity(String.class);

            ApiException exception = ApiException.fromHttpResponse(
                    response.getStatusCode().value(), response.getBody(), OBJECT_MAPPER);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.CATEGORY_ALREADY_EXISTS);
            assertThat(exception.isNotFound()).isFalse();
        }

        @Test
        @DisplayName("a screenshot that was never uploaded is reported as SCREENSHOT_UPLOAD_INCOMPLETE")
        void shouldParseScreenshotUploadIncomplete() {
            var uploadUrl = client().post()
                    .uri("/api/v1/screenshots/upload-url")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(new de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotUploadRequestDto(
                            "screenshot.png", "image/png", 1024L))
                    .retrieve()
                    .toEntity(de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotUploadUrlDto.class)
                    .getBody();

            ResponseEntity<String> response = client().post()
                    .uri("/api/v1/screenshots/{id}/complete", uploadUrl.screenshotId())
                    .retrieve()
                    .toEntity(String.class);

            ApiException exception = ApiException.fromHttpResponse(
                    response.getStatusCode().value(), response.getBody(), OBJECT_MAPPER);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.SCREENSHOT_UPLOAD_INCOMPLETE);
            assertThat(exception.isNotFound()).isFalse();
            assertThat(exception.isRateLimited()).isFalse();
        }
    }

    @Nested
    @DisplayName("JSON shape")
    class JsonShape {

        @Test
        @DisplayName("consists of exactly the fields status, code and message")
        void shouldContainExactlyThreeFields() throws Exception {
            ResponseEntity<String> response = client().get()
                    .uri("/api/v1/reports/9999")
                    .retrieve()
                    .toEntity(String.class);

            var tree = OBJECT_MAPPER.readTree(response.getBody());

            assertThat(tree.fieldNames()).toIterable()
                    .containsExactlyInAnyOrder("status", "code", "message");
            assertThat(tree.get("status").asInt()).isEqualTo(404);
            assertThat(tree.get("code").asText()).isEqualTo("REPORT_NOT_FOUND");
        }

        @Test
        @DisplayName("returns the error code as text, not as a number")
        void shouldSerialiseErrorCodeAsString() throws Exception {
            ResponseEntity<String> response = client().get()
                    .uri("/api/v1/reports/9999")
                    .retrieve()
                    .toEntity(String.class);

            // As an ordinal the contract would be brittle: a new enum value in the middle would
            // shift every existing code.
            assertThat(OBJECT_MAPPER.readTree(response.getBody()).get("code").isTextual()).isTrue();
        }
    }
}

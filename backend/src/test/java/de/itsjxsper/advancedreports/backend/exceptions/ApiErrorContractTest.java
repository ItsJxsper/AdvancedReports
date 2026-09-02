package de.itsjxsper.advancedreports.backend.exceptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.itsjxsper.advancedreports.common.enums.exceptions.api.ApiErrorCode;
import de.itsjxsper.advancedreports.common.exceptions.ApiException;
import de.itsjxsper.advancedreports.common.model.exceptions.ApiErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the contract between the backend and the {@code common} module.
 * <p>
 * The error catalogue used to exist twice - once in {@code backend.exceptions} and once in
 * {@code common} - with nothing but reflection tests holding the two copies in sync. The backend copy
 * is gone; {@code GlobalExceptionHandler} now emits the {@code common} types directly, so the compiler
 * enforces what those drift tests used to check by hand.
 * <p>
 * What is left here is the part the compiler still cannot see: that what the backend serialises is what
 * {@code ApiException.fromHttpResponse} on the client side actually parses back out.
 */
@DisplayName("API error contract between backend and common")
class ApiErrorContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Nested
    @DisplayName("ApiErrorCode")
    class ErrorCodes {

        @Test
        @DisplayName("covers every not-found code through ApiException#isNotFound")
        void shouldRecogniseEveryNotFoundCode() {
            // ApiException#isNotFound enumerates the *_NOT_FOUND codes by hand - otherwise a new
            // code silently falls off the end there.
            Arrays.stream(ApiErrorCode.values())
                    .filter(code -> code.name().endsWith("_NOT_FOUND"))
                    .forEach(code -> {
                        ApiException exception = new ApiException(404, code, "not found");
                        assertThat(exception.isNotFound())
                                .as("ApiException#isNotFound has to know %s", code)
                                .isTrue();
                    });
        }
    }

    @Nested
    @DisplayName("ApiErrorResponse")
    class ErrorResponse {

        @Test
        @DisplayName("is understood by ApiException.fromHttpResponse from the common module")
        void shouldBeParsableByApiException() throws Exception {
            ApiErrorResponse response = new ApiErrorResponse(
                    404, ApiErrorCode.REPORT_NOT_FOUND, "Report with ID 1 was not found");
            String json = OBJECT_MAPPER.writeValueAsString(response);

            ApiException exception = ApiException.fromHttpResponse(404, json, OBJECT_MAPPER);

            assertThat(exception.getHttpStatus()).isEqualTo(404);
            assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.REPORT_NOT_FOUND);
            assertThat(exception.getMessage()).isEqualTo("Report with ID 1 was not found");
            assertThat(exception.isNotFound()).isTrue();
            assertThat(exception.isRateLimited()).isFalse();
        }

        @Test
        @DisplayName("is recognised as a rate-limit error on a 429")
        void shouldBeRecognisedAsRateLimited() throws Exception {
            ApiErrorResponse response = new ApiErrorResponse(
                    429, ApiErrorCode.RATE_LIMIT_EXCEEDED, "Rate limit exceeded for: player-1");
            String json = OBJECT_MAPPER.writeValueAsString(response);

            ApiException exception = ApiException.fromHttpResponse(429, json, OBJECT_MAPPER);

            assertThat(exception.isRateLimited()).isTrue();
            assertThat(exception.isNotFound()).isFalse();
        }

        @Test
        @DisplayName("survives a code an older client does not know yet")
        void shouldSurviveUnknownErrorCode() {
            // The new codes from this phase reach older plugin and bot versions as an unknown
            // string - parsing must not blow up on that.
            String json = """
                    {"status":400,"code":"EIN_CODE_DEN_ES_NOCH_NICHT_GAB","message":"kaputt"}""";

            ApiException exception = ApiException.fromHttpResponse(400, json, OBJECT_MAPPER);

            assertThat(exception.getHttpStatus()).isEqualTo(400);
        }
    }
}

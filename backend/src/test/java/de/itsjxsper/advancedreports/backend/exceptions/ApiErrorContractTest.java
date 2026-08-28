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
@DisplayName("API-Fehlervertrag zwischen backend und common")
class ApiErrorContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Nested
    @DisplayName("ApiErrorCode")
    class ErrorCodes {

        @Test
        @DisplayName("deckt jeden Not-Found-Code über ApiException#isNotFound ab")
        void shouldRecogniseEveryNotFoundCode() {
            // ApiException#isNotFound zaehlt die *_NOT_FOUND-Codes von Hand auf - ein neuer Code
            // faellt dort sonst still hinten runter.
            Arrays.stream(ApiErrorCode.values())
                    .filter(code -> code.name().endsWith("_NOT_FOUND"))
                    .forEach(code -> {
                        ApiException exception = new ApiException(404, code, "not found");
                        assertThat(exception.isNotFound())
                                .as("ApiException#isNotFound muss %s kennen", code)
                                .isTrue();
                    });
        }
    }

    @Nested
    @DisplayName("ApiErrorResponse")
    class ErrorResponse {

        @Test
        @DisplayName("wird von ApiException.fromHttpResponse aus dem common-Modul verstanden")
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
        @DisplayName("wird bei einem 429 als Rate-Limit-Fehler erkannt")
        void shouldBeRecognisedAsRateLimited() throws Exception {
            ApiErrorResponse response = new ApiErrorResponse(
                    429, ApiErrorCode.RATE_LIMIT_EXCEEDED, "Rate limit exceeded for: player-1");
            String json = OBJECT_MAPPER.writeValueAsString(response);

            ApiException exception = ApiException.fromHttpResponse(429, json, OBJECT_MAPPER);

            assertThat(exception.isRateLimited()).isTrue();
            assertThat(exception.isNotFound()).isFalse();
        }

        @Test
        @DisplayName("überlebt einen Code, den ein älterer Client noch nicht kennt")
        void shouldSurviveUnknownErrorCode() {
            // Die neuen Codes aus dieser Phase erreichen aeltere Plugin-/Bot-Versionen als
            // unbekannter String - das darf beim Parsen nicht knallen.
            String json = """
                    {"status":400,"code":"EIN_CODE_DEN_ES_NOCH_NICHT_GAB","message":"kaputt"}""";

            ApiException exception = ApiException.fromHttpResponse(400, json, OBJECT_MAPPER);

            assertThat(exception.getHttpStatus()).isEqualTo(400);
        }
    }
}

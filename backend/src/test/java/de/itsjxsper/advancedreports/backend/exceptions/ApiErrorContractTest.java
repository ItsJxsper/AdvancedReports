package de.itsjxsper.advancedreports.backend.exceptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.itsjxsper.advancedreports.common.exceptions.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the contract between the backend and the {@code common} module.
 * <p>
 * The error catalogue exists twice: {@code backend.exceptions.ApiErrorCode} is what
 * {@code GlobalExceptionHandler} emits, and {@code common.enums.exceptions.api.ApiErrorCode} is what
 * clients (the future {@code api} module, plugin and Discord bot) parse via
 * {@code ApiException.fromHttpResponse}. Nothing in the compiler links the two, so they can silently
 * drift apart — which is exactly what these tests are here to prevent.
 */
@DisplayName("API-Fehlervertrag zwischen backend und common")
class ApiErrorContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Nested
    @DisplayName("ApiErrorCode")
    class ErrorCodes {

        @Test
        @DisplayName("enthält in beiden Modulen exakt die gleichen Konstanten")
        void shouldHaveIdenticalConstants() {
            List<String> backendCodes = Arrays.stream(ApiErrorCode.values()).map(Enum::name).toList();
            List<String> commonCodes = Arrays.stream(
                            de.itsjxsper.advancedreports.common.enums.exceptions.api.ApiErrorCode.values())
                    .map(Enum::name)
                    .toList();

            assertThat(backendCodes)
                    .as("Jeder vom Backend gesendete Fehlercode muss von den Clients erkannt werden")
                    .containsExactlyInAnyOrderElementsOf(commonCodes);
        }

        @Test
        @DisplayName("hält die gleiche Deklarationsreihenfolge ein")
        void shouldKeepTheSameOrder() {
            List<String> backendCodes = Arrays.stream(ApiErrorCode.values()).map(Enum::name).toList();
            List<String> commonCodes = Arrays.stream(
                            de.itsjxsper.advancedreports.common.enums.exceptions.api.ApiErrorCode.values())
                    .map(Enum::name)
                    .toList();

            assertThat(backendCodes)
                    .as("Gleiche Reihenfolge haelt die Ordinalwerte stabil, falls jemals binaer serialisiert wird")
                    .isEqualTo(commonCodes);
        }
    }

    @Nested
    @DisplayName("ApiErrorResponse")
    class ErrorResponse {

        @Test
        @DisplayName("hat in beiden Modulen die gleichen JSON-Felder")
        void shouldHaveIdenticalComponents() {
            List<String> backendComponents = componentNames(ApiErrorResponse.class);
            List<String> commonComponents = componentNames(
                    de.itsjxsper.advancedreports.common.model.exceptions.ApiErrorResponse.class);

            assertThat(backendComponents).isEqualTo(commonComponents);
        }

        @Test
        @DisplayName("wird von ApiException.fromHttpResponse aus dem common-Modul verstanden")
        void shouldBeParsableByApiException() throws Exception {
            ApiErrorResponse response = new ApiErrorResponse(
                    404, ApiErrorCode.REPORT_NOT_FOUND, "Report with ID 1 was not found");
            String json = OBJECT_MAPPER.writeValueAsString(response);

            ApiException exception = ApiException.fromHttpResponse(404, json, OBJECT_MAPPER);

            assertThat(exception.getHttpStatus()).isEqualTo(404);
            assertThat(exception.getErrorCode())
                    .isEqualTo(de.itsjxsper.advancedreports.common.enums.exceptions.api.ApiErrorCode.REPORT_NOT_FOUND);
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

        private List<String> componentNames(Class<?> recordClass) {
            return Arrays.stream(recordClass.getRecordComponents())
                    .map(RecordComponent::getName)
                    .toList();
        }
    }
}

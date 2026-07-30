package de.itsjxsper.advancedreports.common.exceptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.itsjxsper.advancedreports.common.enums.exceptions.api.ApiErrorCode;
import de.itsjxsper.advancedreports.common.model.api.ApiErrorResponse;
import lombok.Getter;

/**
 * Thrown when an API request to the AdvancedReports backend fails –
 * either due to a network error (no HTTP response received) or because the
 * backend returned a non-2xx response.
 * <p>
 * Centralised, shared error type for all modules that use the {@code api} client
 * (plugin, Discord bot). Maps {@link ApiErrorResponse} in a type-safe manner,
 * so that callers can handle {@link ApiErrorCode} using {@code switch},
 * rather than comparing strings.
 */
@Getter
public class ApiException extends RuntimeException {

    private final int httpStatus;
    private final ApiErrorCode errorCode;

    /**
     * For pure network errors (timeout, connection lost, etc.) – there was no
     * HTTP response from the backend, so there is neither a status code nor a {@link ApiErrorCode}.
     */
    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = -1;
        this.errorCode = null;
    }

    /**
     * For Backend responses with a known {@link ApiErrorResponse} body.
     */
    public ApiException(int httpStatus, ApiErrorCode errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    /**
     * Constructs an {@link ApiException} from a raw backend error response.
     * Expects a JSON body in the format of {@link ApiErrorResponse}
     * ({@code {‘status’: ..., “code”: ..., ‘message’: ...}}). If the body is empty,
     * is not valid JSON, or if {@code code} contains an enum value unknown to the client
     * (e.g., because the backend is using a newer version of {@code common}),
     * a generic message with a status code is returned instead of
     * causing the deserialization to fail outright.
     */
    public static ApiException fromHttpResponse(int httpStatus, String rawBody, ObjectMapper objectMapper) {
        if (rawBody != null && !rawBody.isBlank()) {
            try {
                ApiErrorResponse errorResponse = objectMapper.readValue(rawBody, ApiErrorResponse.class);
                String message = errorResponse.message() != null ? errorResponse.message() : rawBody;
                return new ApiException(httpStatus, errorResponse.code(), message);
            } catch (Exception ignored) {
                // The body was not valid ApiErrorResponse JSON (e.g. proxy error page,
                // unknown enum value) -> generic fallback below.
            }
        }

        return new ApiException(httpStatus, null, "The backend responded with the status:" + httpStatus);
    }

    public boolean isRateLimited() {
        return httpStatus == 429;
    }

    public boolean isNotFound() {
        return errorCode == ApiErrorCode.REPORT_NOT_FOUND
                || errorCode == ApiErrorCode.PLAYER_NOT_FOUND
                || errorCode == ApiErrorCode.CATEGORY_NOT_FOUND
                || errorCode == ApiErrorCode.SERVER_NOT_FOUND
                || errorCode == ApiErrorCode.SCREENSHOT_NOT_FOUND
                || errorCode == ApiErrorCode.DISCORD_USER_NOT_FOUND
                || httpStatus == 404;
    }
}

package de.itsjxsper.advancedreports.common.model.api;

import de.itsjxsper.advancedreports.common.enums.exceptions.api.ApiErrorCode;

import java.io.Serializable;

/**
 * Standardized error response format for the backend, as returned by the
 * {@code GlobalExceptionHandler} for any non-2xx response.
 * Used both on the server side (serialization) and on the client side
 * (deserialization into {@code AbstractApiClient}/{@code ApiException})
 * so that the backend and clients (plugin, Discord bot) share exactly
 * the same JSON contract.
 */
public record ApiErrorResponse(
        int status,
        ApiErrorCode code,
        String message
) implements Serializable {
}

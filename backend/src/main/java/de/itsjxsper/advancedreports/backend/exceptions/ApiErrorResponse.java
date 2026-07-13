package de.itsjxsper.advancedreports.backend.exceptions;

public record ApiErrorResponse(
        int status,
        ApiErrorCode code,
        String message
) {
}


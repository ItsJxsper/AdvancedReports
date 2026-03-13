package de.itsjxsper.advancedreports.exception;

public record ApiErrorResponse(
        int status,
        ApiErrorCode code,
        String message
) {
}


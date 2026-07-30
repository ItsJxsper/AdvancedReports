package de.itsjxsper.advancedreports.common.enums.exceptions.api;

/**
 * Central error code catalogue shared between the backend, the API module (plugin/bot)
 * and the Discord bot. Used both in {@code ApiErrorResponse}
 * (backend → client) and in {@code ApiException} (client-side
 * evaluation), so that all modules recognise the same set of error codes
 * and can react to them in a type-safe manner (e.g. {@code switch}
 * without string comparisons).
 */
public enum ApiErrorCode {
    METHOD_NOT_ALLOWED,
    METHOD_ARGUMENT_TYPE_MISMATCH,
    ILLEGAL_ARGUMENT,
    UNSUPPORTED_OPERATION,
    MISSING_REQUEST_PARAMETER,
    PLAYER_ALREADY_EXISTS,
    PLAYER_NOT_FOUND,
    DISCORD_USER_NOT_FOUND,
    CATEGORY_ALREADY_EXISTS,
    CATEGORY_NOT_FOUND,
    SERVER_NOT_FOUND,
    SCREENSHOT_NOT_FOUND,
    SCREENSHOT_STORAGE_ERROR,
    REPORT_NOT_FOUND,
    RATE_LIMIT_EXCEEDED,
    MISSING_HEADER,
    INTERNAL_SERVER_ERROR
}

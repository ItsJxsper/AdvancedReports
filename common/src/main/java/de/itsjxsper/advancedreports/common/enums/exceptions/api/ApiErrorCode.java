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
    /**
     * The HTTP method used is not allowed for this endpoint.
     */
    METHOD_NOT_ALLOWED,
    /**
     * A method argument has the wrong type.
     */
    METHOD_ARGUMENT_TYPE_MISMATCH,
    /**
     * An illegal argument was provided.
     */
    ILLEGAL_ARGUMENT,
    /**
     * The operation is not supported.
     */
    UNSUPPORTED_OPERATION,
    /**
     * A required request parameter is missing.
     */
    MISSING_REQUEST_PARAMETER,
    /**
     * The player already exists.
     */
    PLAYER_ALREADY_EXISTS,
    /**
     * The player was not found.
     */
    PLAYER_NOT_FOUND,
    /**
     * The Discord user was not found.
     */
    DISCORD_USER_NOT_FOUND,
    /**
     * The category already exists.
     */
    CATEGORY_ALREADY_EXISTS,
    /**
     * The category was not found.
     */
    CATEGORY_NOT_FOUND,
    /**
     * The server was not found.
     */
    SERVER_NOT_FOUND,
    /**
     * The screenshot was not found.
     */
    SCREENSHOT_NOT_FOUND,
    /**
     * An error occurred while storing the screenshot.
     */
    SCREENSHOT_STORAGE_ERROR,
    /**
     * The screenshot upload was never completed, so the object is missing in storage.
     */
    SCREENSHOT_UPLOAD_INCOMPLETE,
    /**
     * The report was not found.
     */
    REPORT_NOT_FOUND,
    /**
     * The rate limit has been exceeded.
     */
    RATE_LIMIT_EXCEEDED,
    /**
     * A required header is missing.
     */
    MISSING_HEADER,
    /**
     * An internal server error occurred.
     */
    INTERNAL_SERVER_ERROR,
    /**
     * The request body failed bean validation. The message names the offending fields.
     */
    VALIDATION_FAILED,
    /**
     * The request body could not be parsed, e.g. malformed JSON.
     */
    MALFORMED_REQUEST,
    /**
     * The request Content-Type is not supported by the endpoint.
     */
    UNSUPPORTED_MEDIA_TYPE,
    /**
     * The endpoint cannot produce any of the media types the client accepts.
     */
    NOT_ACCEPTABLE,
    /**
     * No endpoint exists for the requested path.
     */
    RESOURCE_NOT_FOUND,
    /**
     * The request conflicts with the current state, e.g. a unique constraint violation.
     */
    CONFLICT,
    /**
     * The uploaded payload exceeds the configured maximum size.
     */
    PAYLOAD_TOO_LARGE
}

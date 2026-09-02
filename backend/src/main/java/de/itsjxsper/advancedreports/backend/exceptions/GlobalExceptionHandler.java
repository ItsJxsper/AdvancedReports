package de.itsjxsper.advancedreports.backend.exceptions;

import de.itsjxsper.advancedreports.backend.category.exceptions.CategoryAlreadyExistException;
import de.itsjxsper.advancedreports.backend.category.exceptions.CategoryInUseException;
import de.itsjxsper.advancedreports.backend.category.exceptions.CategoryNotFoundException;
import de.itsjxsper.advancedreports.backend.discord.exceptions.DiscordPlayerAlreadyExistException;
import de.itsjxsper.advancedreports.backend.discord.exceptions.DiscordUserNotFoundException;
import de.itsjxsper.advancedreports.backend.player.exception.PlayerAlreadyExistException;
import de.itsjxsper.advancedreports.backend.player.exception.PlayerNotFoundException;
import de.itsjxsper.advancedreports.backend.ratelimit.exceptions.MissingHeaderException;
import de.itsjxsper.advancedreports.backend.ratelimit.exceptions.RateLimitExceededException;
import de.itsjxsper.advancedreports.backend.reports.exceptions.ReportNotFoundException;
import de.itsjxsper.advancedreports.backend.screenshot.exceptions.ScreenshotNotFoundException;
import de.itsjxsper.advancedreports.backend.screenshot.exceptions.ScreenshotStorageException;
import de.itsjxsper.advancedreports.backend.screenshot.exceptions.ScreenshotUploadIncompleteException;
import de.itsjxsper.advancedreports.backend.server.exceptions.ServerNotFoundException;
import de.itsjxsper.advancedreports.common.enums.exceptions.api.ApiErrorCode;
import de.itsjxsper.advancedreports.common.model.exceptions.ApiErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Maps every exception that escapes a controller onto the shared {@link ApiErrorResponse} contract.
 * <p>
 * This advice extends {@link ResponseEntityExceptionHandler} on purpose. Without it, the
 * {@code @ExceptionHandler(Exception.class)} fallback at the bottom of this class wins over Spring's
 * own {@code DefaultHandlerExceptionResolver} - the {@code ExceptionHandlerExceptionResolver} runs
 * first - and every framework exception (failed {@code @Valid}, malformed JSON, wrong HTTP method,
 * unknown URL) is answered with 500 instead of its correct 4xx status. Extending the base class
 * routes those exceptions through {@link #handleExceptionInternal} instead, where they keep their
 * status and get a proper {@link ApiErrorCode}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // --- Framework exceptions (via ResponseEntityExceptionHandler) -----------------------------

    /**
     * Renders the field errors of a failed {@code @Valid} into the message, so a client can tell
     * which field it got wrong without a second round trip.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        String details = Stream.concat(
                        ex.getBindingResult().getFieldErrors().stream()
                                .map(error -> error.getField() + ": " + error.getDefaultMessage()),
                        ex.getBindingResult().getGlobalErrors().stream()
                                .map(error -> error.getObjectName() + ": " + error.getDefaultMessage()))
                .sorted()
                .collect(Collectors.joining("; "));

        String message = details.isBlank()
                ? "Request validation failed"
                : "Request validation failed: " + details;

        log.warn("Validation failed: {}", message);
        return new ResponseEntity<>(
                new ApiErrorResponse(HttpStatus.BAD_REQUEST.value(), ApiErrorCode.VALIDATION_FAILED, message),
                headers,
                HttpStatus.BAD_REQUEST);
    }

    /**
     * Single funnel for every exception {@link ResponseEntityExceptionHandler} knows about. The base
     * class would emit an RFC 7807 {@code ProblemDetail} here; this override keeps the response on
     * the {@link ApiErrorResponse} contract that the plugin and the Discord bot parse.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request
    ) {
        ApiErrorCode code = resolveFrameworkCode(ex, statusCode);

        if (statusCode.is5xxServerError()) {
            log.error("{} -> {} {}", ex.getClass().getSimpleName(), statusCode.value(), code, ex);
        } else {
            log.warn("{} -> {} {}: {}", ex.getClass().getSimpleName(), statusCode.value(), code, ex.getMessage());
        }

        return new ResponseEntity<>(
                new ApiErrorResponse(statusCode.value(), code, frameworkMessage(ex, statusCode)),
                headers,
                statusCode);
    }

    private ApiErrorCode resolveFrameworkCode(Exception ex, HttpStatusCode statusCode) {
        return switch (ex) {
            case HttpRequestMethodNotSupportedException ignored -> ApiErrorCode.METHOD_NOT_ALLOWED;
            case HttpMediaTypeNotSupportedException ignored -> ApiErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case HttpMediaTypeNotAcceptableException ignored -> ApiErrorCode.NOT_ACCEPTABLE;
            case HttpMessageNotReadableException ignored -> ApiErrorCode.MALFORMED_REQUEST;
            case MissingRequestHeaderException ignored -> ApiErrorCode.MISSING_HEADER;
            case MissingServletRequestParameterException ignored -> ApiErrorCode.MISSING_REQUEST_PARAMETER;
            case NoResourceFoundException ignored -> ApiErrorCode.RESOURCE_NOT_FOUND;
            case MaxUploadSizeExceededException ignored -> ApiErrorCode.PAYLOAD_TOO_LARGE;
            default -> statusCode.is4xxClientError()
                    ? ApiErrorCode.ILLEGAL_ARGUMENT
                    : ApiErrorCode.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * Framework messages are mostly safe to forward ("Request method 'PUT' is not supported"), but a
     * parser error carries payload fragments and internal type names, so those get a fixed text.
     */
    private String frameworkMessage(Exception ex, HttpStatusCode statusCode) {
        if (ex instanceof HttpMessageNotReadableException) {
            return "Malformed request body.";
        }
        if (ex instanceof NoResourceFoundException) {
            return "No endpoint found for the requested path.";
        }
        if (statusCode.is5xxServerError()) {
            return "An unexpected error occurred.";
        }
        return ex.getMessage();
    }

    // --- Own exceptions ------------------------------------------------------------------------

    /**
     * More specific than the {@code TypeMismatchException} handler inherited from the base class,
     * so this one wins for a path variable that does not parse.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("MethodArgumentTypeMismatchException: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ApiErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("IllegalArgumentException: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ApiErrorCode.ILLEGAL_ARGUMENT, e.getMessage());
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedOperationException(UnsupportedOperationException e) {
        log.warn("UnsupportedOperationException: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ApiErrorCode.UNSUPPORTED_OPERATION, e.getMessage());
    }

    /**
     * A concurrent duplicate slips past every check-then-insert in the services. Without this the
     * constraint violation would surface as 500 rather than the 409 the endpoints document.
     * The driver message names tables and constraints, so it is not forwarded.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.warn("DataIntegrityViolationException: {}", e.getMostSpecificCause().getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ApiErrorCode.CONFLICT,
                "The request conflicts with the current state of the resource.");
    }

    @ExceptionHandler(PlayerAlreadyExistException.class)
    public ResponseEntity<ApiErrorResponse> handlePlayerAlreadyExistException(PlayerAlreadyExistException e) {
        log.warn("PlayerAlreadyExistException: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ApiErrorCode.PLAYER_ALREADY_EXISTS, e.getMessage());
    }

    @ExceptionHandler(PlayerNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handlePlayerNotFoundException(PlayerNotFoundException e) {
        log.warn("PlayerNotFoundException: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ApiErrorCode.PLAYER_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DiscordUserNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleDiscordUserNotFoundException(DiscordUserNotFoundException e) {
        log.warn("DiscordUserNotFoundException: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ApiErrorCode.DISCORD_USER_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DiscordPlayerAlreadyExistException.class)
    public ResponseEntity<ApiErrorResponse> handleDiscordPlayerAlreadyExistException(DiscordPlayerAlreadyExistException e) {
        log.warn("DiscordPlayerAlreadyExistException: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ApiErrorCode.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(CategoryAlreadyExistException.class)
    public ResponseEntity<ApiErrorResponse> handleCategoryAlreadyExistException(CategoryAlreadyExistException e) {
        log.warn("CategoryAlreadyExistException: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ApiErrorCode.CATEGORY_ALREADY_EXISTS, e.getMessage());
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleCategoryNotFoundException(CategoryNotFoundException e) {
        log.warn("CategoryNotFoundException: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ApiErrorCode.CATEGORY_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(CategoryInUseException.class)
    public ResponseEntity<ApiErrorResponse> handleCategoryInUseException(CategoryInUseException e) {
        log.warn("CategoryInUseException: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ApiErrorCode.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(ServerNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleServerNotFoundException(ServerNotFoundException e) {
        log.warn("ServerNotFoundException: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ApiErrorCode.SERVER_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ScreenshotNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleScreenshotNotFoundException(ScreenshotNotFoundException e) {
        log.warn("ScreenshotNotFoundException: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ApiErrorCode.SCREENSHOT_NOT_FOUND, e.getMessage());
    }

    /**
     * S3 being unreachable is an infrastructure fault, not a client error - logged at ERROR with the
     * stack trace, unlike the 4xx cases above.
     */
    @ExceptionHandler(ScreenshotStorageException.class)
    public ResponseEntity<ApiErrorResponse> handleScreenshotStorageException(ScreenshotStorageException e) {
        log.error("ScreenshotStorageException", e);
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.SCREENSHOT_STORAGE_ERROR, e.getMessage());
    }

    /**
     * The client asked to use a screenshot whose bytes never reached S3 - a state conflict, not a
     * missing resource, so the caller can retry the upload against the same object key.
     */
    @ExceptionHandler(ScreenshotUploadIncompleteException.class)
    public ResponseEntity<ApiErrorResponse> handleScreenshotUploadIncompleteException(ScreenshotUploadIncompleteException e) {
        log.warn("ScreenshotUploadIncompleteException: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, ApiErrorCode.SCREENSHOT_UPLOAD_INCOMPLETE, e.getMessage());
    }

    @ExceptionHandler(ReportNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleReportNotFoundException(ReportNotFoundException e) {
        log.warn("ReportNotFoundException: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ApiErrorCode.REPORT_NOT_FOUND, e.getMessage());
    }

    /**
     * The wait time was already computed by the aspect - passing it on as {@code Retry-After} saves
     * every client from guessing when to come back.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimitExceededException(RateLimitExceededException e) {
        log.warn("RateLimitExceededException: {}", e.getMessage());

        // Retry-After is defined in whole seconds; anything below one second rounds up to 1
        long retryAfterSeconds = Math.max(1L, (e.getRetryAfterMs() + 999L) / 1000L);

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
                .body(new ApiErrorResponse(
                        HttpStatus.TOO_MANY_REQUESTS.value(), ApiErrorCode.RATE_LIMIT_EXCEEDED, e.getMessage()));
    }

    @ExceptionHandler(MissingHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeaderException(MissingHeaderException e) {
        log.warn("MissingHeaderException: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ApiErrorCode.MISSING_HEADER, e.getMessage());
    }

    /**
     * Last resort. Anything reaching this is genuinely unexpected, so it keeps the stack trace in the
     * log and reveals nothing about the cause to the caller.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception e) {
        log.error("UnexpectedException", e);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.");
    }

    private ResponseEntity<ApiErrorResponse> buildErrorResponse(HttpStatus status, ApiErrorCode code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(status.value(), code, message));
    }
}

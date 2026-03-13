package de.itsjxsper.advancedreports.exception;

import de.itsjxsper.advancedreports.player.exception.PlayerAlreadyExistException;
import de.itsjxsper.advancedreports.player.exception.PlayerNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.MethodNotAllowedException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodNotAllowedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowedException(MethodNotAllowedException e) {
        log.error("MethodNotAllowedException", e);
        return buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, ApiErrorCode.METHOD_NOT_ALLOWED, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.error("MethodArgumentTypeMismatchException", e);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ApiErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("IllegalArgumentException", e);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ApiErrorCode.ILLEGAL_ARGUMENT, e.getMessage());
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedOperationException(UnsupportedOperationException e) {
        log.error("UnsupportedOperationException", e);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ApiErrorCode.UNSUPPORTED_OPERATION, e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.error("MissingServletRequestParameterException", e);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ApiErrorCode.MISSING_REQUEST_PARAMETER, e.getMessage());
    }

    @ExceptionHandler(PlayerAlreadyExistException.class)
    public ResponseEntity<ApiErrorResponse> handlePlayerAlreadyExistException(PlayerAlreadyExistException e) {
        log.error("PlayerAlreadyExistException", e);
        return buildErrorResponse(HttpStatus.CONFLICT, ApiErrorCode.PLAYER_ALREADY_EXISTS, e.getMessage());
    }

    @ExceptionHandler(PlayerNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handlePlayerNotFoundException(PlayerNotFoundException e) {
        log.error("PlayerNotFoundException", e);
        return buildErrorResponse(HttpStatus.NOT_FOUND, ApiErrorCode.PLAYER_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception e) {
        log.error("UnexpectedException", e);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    private ResponseEntity<ApiErrorResponse> buildErrorResponse(HttpStatus status, ApiErrorCode code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(status.value(), code, message));
    }
}

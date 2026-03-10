package de.itsjxsper.advancedreports.exception;

import de.itsjxsper.advancedreports.player.exception.PlayerAlreadyExistException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.MethodNotAllowedException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodNotAllowedException.class)
    public ResponseEntity<Map<String, String>> handleMethodNotAllowedException(MethodNotAllowedException e) {
        log.error("MethodNotAllowedException", e);

        Map<String, String> response = new HashMap<>();
        response.put("error", "Method Not Allowed");
        response.put("message", e.getMessage());

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.error("MethodArgumentTypeMismatchException", e);

        Map<String, String> response = new HashMap<>();
        response.put("error", "Method Argument Type Mismatch");
        response.put("message", e.getMessage());

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("IllegalArgumentException", e);

        Map<String, String> response = new HashMap<>();
        response.put("error", "Illegal Argument");
        response.put("message", e.getMessage());

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<Map<String, String>> handleUnsupportedOperationException(UnsupportedOperationException e) {
        log.error("UnsupportedOperationException", e);

        Map<String, String> response = new HashMap<>();
        response.put("error", "Unsupported Operation");
        response.put("message", e.getMessage());

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.error("MissingServletRequestParameterException", e);

        Map<String, String> response = new HashMap<>();
        response.put("error", "Missing Servlet Request Parameter");
        response.put("message", e.getMessage());

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(PlayerAlreadyExistException.class)
    public ResponseEntity<Map<String, String>> handlePlayerAlreadyExistException(PlayerAlreadyExistException e) {
        log.error("PlayerAlreadyExistException", e);

        Map<String, String> response = new HashMap<>();
        response.put("error", "Player Already Exists");
        response.put("message", "A player with the given UUID already exists.");

        return ResponseEntity.badRequest().body(response);
    }

}

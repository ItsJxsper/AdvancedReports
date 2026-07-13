package de.itsjxsper.advancedreports.backend.ratelimit.exceptions;

public class MissingHeaderException extends RuntimeException {
    public MissingHeaderException(String header) {
        super("Missing required header: " + header);
    }
}

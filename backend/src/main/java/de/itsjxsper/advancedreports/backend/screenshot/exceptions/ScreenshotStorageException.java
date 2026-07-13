package de.itsjxsper.advancedreports.backend.screenshot.exceptions;

public class ScreenshotStorageException extends RuntimeException {

    public ScreenshotStorageException(String message) {
        super(message);
    }

    public ScreenshotStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}


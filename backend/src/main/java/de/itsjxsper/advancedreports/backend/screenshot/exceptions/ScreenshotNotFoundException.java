package de.itsjxsper.advancedreports.backend.screenshot.exceptions;

public class ScreenshotNotFoundException extends RuntimeException {

    public ScreenshotNotFoundException(Long screenshotId) {
        super("Screenshot with ID " + screenshotId + " was not found");
    }
}


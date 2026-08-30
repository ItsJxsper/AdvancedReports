package de.itsjxsper.advancedreports.backend.screenshot.exceptions;

public class ScreenshotUploadIncompleteException extends RuntimeException {

    public ScreenshotUploadIncompleteException(Long screenshotId) {
        super("Screenshot with ID " + screenshotId + " has not been uploaded to S3 yet");
    }
}

package de.itsjxsper.advancedreports.backend.screenshot.model;

import java.io.Serializable;

public record ScreenshotDownloadDto(
        String filename,
        String contentType,
        byte[] content
) implements Serializable {
}


package de.itsjxsper.advancedreports.common.model.screenshot;

import java.io.Serializable;

public record ScreenshotDownloadDto(
        String filename,
        String contentType,
        byte[] content
) implements Serializable {
}


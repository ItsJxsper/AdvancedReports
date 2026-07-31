package de.itsjxsper.advancedreports.common.model.screenshot;

import java.io.Serializable;

/**
 * Data Transfer Object for downloading a screenshot.
 *
 * @param filename    the name of the file
 * @param contentType the MIME type of the content
 * @param content     the raw byte content of the screenshot
 */
public record ScreenshotDownloadDto(
        String filename,
        String contentType,
        byte[] content
) implements Serializable {
}


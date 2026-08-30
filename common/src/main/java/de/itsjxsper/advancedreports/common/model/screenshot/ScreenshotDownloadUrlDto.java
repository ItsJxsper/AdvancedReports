package de.itsjxsper.advancedreports.common.model.screenshot;

import java.io.Serializable;
import java.time.Instant;

/**
 * Data Transfer Object containing a presigned URL for downloading a screenshot
 * directly from S3, bypassing the backend.
 *
 * @param screenshotId     the identifier of the screenshot
 * @param downloadUrl      the presigned URL the file can be fetched from
 * @param originalFilename the original filename of the screenshot
 * @param contentType      the MIME type of the screenshot
 * @param expiresAt        the instant at which the presigned URL stops being valid
 */
public record ScreenshotDownloadUrlDto(
        Long screenshotId,
        String downloadUrl,
        String originalFilename,
        String contentType,
        Instant expiresAt
) implements Serializable {
}

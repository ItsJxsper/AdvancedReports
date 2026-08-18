package de.itsjxsper.advancedreports.common.model.screenshot;

import de.itsjxsper.advancedreports.common.enums.screenshot.UploadStatus;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * Data Transfer Object for screenshot information.
 *
 * @param id               the unique identifier of the screenshot
 * @param s3Url            the URL to the screenshot in S3 storage
 * @param s3ObjectKey      the key of the object in S3 storage
 * @param originalFilename the original filename of the screenshot
 * @param contentType      the MIME type of the screenshot
 * @param fileSizeBytes    the size of the screenshot file in bytes
 * @param uploadStatus     the current upload status of the screenshot
 */
public record ScreenshotDto(
        Long id,
        @Size(max = 2048, message = "Screenshot URL must not exceed 2048 characters")
        String s3Url,
        String s3ObjectKey,
        String originalFilename,
        String contentType,
        Long fileSizeBytes,
        UploadStatus uploadStatus
) implements Serializable {
}


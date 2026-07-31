package de.itsjxsper.advancedreports.common.model.screenshot;

import de.itsjxsper.advancedreports.common.enums.screenshot.UploadStatus;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * Data Transfer Object for updating screenshot information.
 *
 * @param s3Url            the new S3 URL
 * @param s3ObjectKey      the new S3 object key
 * @param originalFilename the original filename
 * @param contentType      the MIME type
 * @param fileSizeBytes    the file size in bytes
 * @param uploadStatus     the new upload status
 */
public record ScreenshotUpdateDto(
        @Size(max = 2048, message = "Screenshot URL must not exceed 2048 characters")
        String s3Url,
        String s3ObjectKey,
        String originalFilename,
        String contentType,
        long fileSizeBytes,
        UploadStatus uploadStatus
) implements Serializable {
}


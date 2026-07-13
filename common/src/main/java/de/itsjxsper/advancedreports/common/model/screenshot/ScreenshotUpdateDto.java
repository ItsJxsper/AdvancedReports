package de.itsjxsper.advancedreports.common.model.screenshot;

import de.itsjxsper.advancedreports.common.enums.screenshot.UploadStatus;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

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


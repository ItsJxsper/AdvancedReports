package de.itsjxsper.advancedreports.backend.screenshot.model;

import de.itsjxsper.advancedreports.backend.screenshot.enums.UploadStatus;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

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


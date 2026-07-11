package de.itsjxsper.advancedreports.backend.screenshot.model;

import de.itsjxsper.advancedreports.backend.screenshot.enums.UploadStatus;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.Optional;

public record ScreenshotUpdateDto(
        @Size(max = 2048, message = "Screenshot URL must not exceed 2048 characters")
        Optional<String> s3Url,
        Optional<String> s3ObjectKey,
        Optional<String> originalFilename,
        Optional<String> contentType,
        Optional<Long> fileSizeBytes,
        Optional<UploadStatus> uploadStatus
) implements Serializable {
}


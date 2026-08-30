package de.itsjxsper.advancedreports.common.model.screenshot;

import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * Data Transfer Object requesting a presigned upload URL for a screenshot.
 * The declared file size is signed into the presigned request, so the actual
 * upload is rejected by S3 if the body length differs.
 * <p>
 * The file size is validated by the backend rather than by a bean validation
 * annotation, because it is checked against a configurable maximum and has to
 * surface as an {@code ILLEGAL_ARGUMENT} error response.
 *
 * @param originalFilename the original filename of the screenshot
 * @param contentType      the MIME type the file will be uploaded with
 * @param fileSizeBytes    the exact size of the file in bytes, greater than zero
 */
public record ScreenshotUploadRequestDto(
        @Size(max = 255, message = "Original filename must not exceed 255 characters")
        String originalFilename,
        @Size(max = 255, message = "Content type must not exceed 255 characters")
        String contentType,
        long fileSizeBytes
) implements Serializable {
}

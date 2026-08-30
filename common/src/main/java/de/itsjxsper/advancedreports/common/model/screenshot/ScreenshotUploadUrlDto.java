package de.itsjxsper.advancedreports.common.model.screenshot;

import de.itsjxsper.advancedreports.common.enums.screenshot.UploadStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * Data Transfer Object containing everything a client needs to upload a screenshot
 * directly to S3, bypassing the backend. The client sends the file body to
 * {@code uploadUrl} using {@code httpMethod} and must reproduce every header in
 * {@code requiredHeaders} exactly, otherwise the signature check fails.
 *
 * @param screenshotId    the identifier of the screenshot metadata row created for this upload
 * @param s3ObjectKey     the key the object will be stored under
 * @param uploadUrl       the presigned URL to send the file body to
 * @param httpMethod      the HTTP method the presigned URL was signed for
 * @param requiredHeaders the headers that were signed and must be sent with the upload
 * @param expiresAt       the instant at which the presigned URL stops being valid
 * @param uploadStatus    the current upload status, {@code PENDING} until the upload is confirmed
 */
public record ScreenshotUploadUrlDto(
        Long screenshotId,
        String s3ObjectKey,
        String uploadUrl,
        String httpMethod,
        Map<String, String> requiredHeaders,
        Instant expiresAt,
        UploadStatus uploadStatus
) implements Serializable {
}

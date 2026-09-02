package de.itsjxsper.advancedreports.api.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.itsjxsper.advancedreports.api.model.PageResponse;
import de.itsjxsper.advancedreports.common.model.screenshot.*;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * REST client for {@code /api/v1/screenshots} (see {@code ScreenshotController}).
 * Endpoints use {@code @RateLimited(serverUuid = false, discordUserId = true)},
 * so the {@code X-Discord-ID} header is required for each call.
 * <p>
 * Screenshot files are never sent through the backend. Uploading is a three-step flow: request a
 * presigned URL, PUT the bytes straight to S3, then confirm the upload.
 * {@link #uploadScreenshot(String, byte[], MediaType, long)} chains all three.
 */
public class ScreenshotsApiClient extends AbstractApiClient {

    private static final String BASE_PATH = "/api/v1/screenshots";

    public ScreenshotsApiClient(OkHttpClient httpClient, String baseUrl, ObjectMapper objectMapper, Executor executor) {
        super(httpClient, baseUrl, objectMapper, executor);
    }

    public CompletableFuture<PageResponse<ScreenshotDto>> getScreenshots(int page, int size, long discordUserId) {
        String path = BASE_PATH + "?page=" + page + "&size=" + size;
        return getAsync(path, new TypeReference<PageResponse<ScreenshotDto>>() {
        }, discordHeader(discordUserId));
    }

    public CompletableFuture<ScreenshotDto> createScreenshot(ScreenshotUpdateDto dto, long discordUserId) {
        return postAsync(BASE_PATH, dto, ScreenshotDto.class, discordHeader(discordUserId));
    }

    /**
     * Corresponds to {@code POST /api/v1/screenshots/upload-url}.
     * Reserves the screenshot metadata and returns the presigned URL the file is uploaded to.
     */
    public CompletableFuture<ScreenshotUploadUrlDto> requestUploadUrl(ScreenshotUploadRequestDto dto, long discordUserId) {
        return postAsync(BASE_PATH + "/upload-url", dto, ScreenshotUploadUrlDto.class, discordHeader(discordUserId));
    }

    /**
     * Corresponds to {@code POST /api/v1/screenshots/{id}/complete}.
     * The backend verifies the object in S3 before marking the screenshot as uploaded.
     */
    public CompletableFuture<ScreenshotDto> completeUpload(long screenshotId, long discordUserId) {
        return postAsync(BASE_PATH + "/" + screenshotId + "/complete", ScreenshotDto.class, discordHeader(discordUserId));
    }

    /**
     * Uploads a screenshot end to end: requests a presigned URL, sends the bytes directly to S3
     * and confirms the upload with the backend.
     */
    public CompletableFuture<ScreenshotDto> uploadScreenshot(String filename, byte[] fileContent,
                                                             MediaType contentType, long discordUserId) {
        ScreenshotUploadRequestDto uploadRequest = new ScreenshotUploadRequestDto(
                filename,
                contentType != null ? contentType.toString() : null,
                fileContent.length
        );

        return requestUploadUrl(uploadRequest, discordUserId)
                .thenCompose(uploadUrl -> putAbsoluteAsync(uploadUrl.uploadUrl(), fileContent, uploadUrl.requiredHeaders())
                        .thenCompose(ignored -> completeUpload(uploadUrl.screenshotId(), discordUserId)));
    }

    public CompletableFuture<ScreenshotDto> getScreenshot(long screenshotId, long discordUserId) {
        return getAsync(BASE_PATH + "/" + screenshotId, ScreenshotDto.class, discordHeader(discordUserId));
    }

    /**
     * Corresponds to {@code GET /api/v1/screenshots/{id}/download-url}.
     * Returns a short-lived presigned URL the file can be fetched from directly.
     */
    public CompletableFuture<ScreenshotDownloadUrlDto> getDownloadUrl(long screenshotId, long discordUserId) {
        return getAsync(BASE_PATH + "/" + screenshotId + "/download-url", ScreenshotDownloadUrlDto.class,
                discordHeader(discordUserId));
    }

    /**
     * Corresponds to {@code GET /api/v1/screenshots/{id}/download}, which redirects to a presigned
     * S3 URL. OkHttp follows the redirect, so the raw image bytes are still returned;
     * filename/Content-Type are provided by {@link #getScreenshot}.
     */
    public CompletableFuture<byte[]> downloadScreenshot(long screenshotId, long discordUserId) {
        return downloadAsync(BASE_PATH + "/" + screenshotId + "/download", discordHeader(discordUserId));
    }

    public CompletableFuture<ScreenshotDto> updateScreenshot(long screenshotId, ScreenshotUpdateDto dto, long discordUserId) {
        return patchAsync(BASE_PATH + "/" + screenshotId, dto, ScreenshotDto.class, discordHeader(discordUserId));
    }

    public CompletableFuture<Void> deleteScreenshot(long screenshotId, long discordUserId) {
        return deleteAsync(BASE_PATH + "/" + screenshotId, discordHeader(discordUserId));
    }

    public CompletableFuture<Long> countScreenshots(long discordUserId) {
        return getAsync(BASE_PATH + "/count", Long.class, discordHeader(discordUserId));
    }

    private Map<String, String> discordHeader(long discordUserId) {
        return Map.of("X-Discord-ID", String.valueOf(discordUserId));
    }
}

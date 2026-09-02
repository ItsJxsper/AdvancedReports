package de.itsjxsper.advancedreports.backend.e2e;

import de.itsjxsper.advancedreports.backend.ratelimit.aspect.RateLimitAspect;
import de.itsjxsper.advancedreports.backend.support.AbstractE2ETest;
import de.itsjxsper.advancedreports.backend.support.ApiFixtures;
import de.itsjxsper.advancedreports.backend.support.ContainerSupport;
import de.itsjxsper.advancedreports.common.enums.exceptions.api.ApiErrorCode;
import de.itsjxsper.advancedreports.common.enums.screenshot.UploadStatus;
import de.itsjxsper.advancedreports.common.model.exceptions.ApiErrorResponse;
import de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotDownloadUrlDto;
import de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotDto;
import de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotUploadRequestDto;
import de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotUploadUrlDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Screenshot endpoints against a real MinIO container, with the {@code s3} profile active.
 * <p>
 * The file itself never travels through the backend: the tests request a presigned URL, PUT the bytes
 * straight to MinIO over plain HTTP and only then confirm the upload. The assertions deliberately reach
 * into the bucket with an {@link S3Client} as well as going through REST, because a confirmation that
 * returns 200 without an object behind it would otherwise look like a success.
 */
@DisplayName("E2E: Screenshots")
class ScreenshotE2ETest extends AbstractE2ETest {

    private static final byte[] PNG_BYTES = "not-a-real-png-but-good-enough".getBytes(StandardCharsets.UTF_8);

    /**
     * java.net.http manages these headers itself and throws when you set them.
     */
    private static final Set<String> RESTRICTED_HEADERS =
            Set.of("host", "content-length", "connection", "expect", "upgrade");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private ScreenshotUploadUrlDto requestUploadUrl(String filename, long fileSizeBytes) {
        return client().post()
                .uri("/api/v1/screenshots/upload-url")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ScreenshotUploadRequestDto(filename, "image/png", fileSizeBytes))
                .retrieve()
                .toEntity(ScreenshotUploadUrlDto.class)
                .getBody();
    }

    private HttpResponse<String> putToPresignedUrl(ScreenshotUploadUrlDto uploadUrl, byte[] content) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(uploadUrl.uploadUrl()))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content));

        uploadUrl.requiredHeaders().forEach((name, value) -> {
            if (!RESTRICTED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                request.header(name, value);
            }
        });

        try {
            return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private ResponseEntity<ScreenshotDto> complete(Long screenshotId) {
        return client().post()
                .uri("/api/v1/screenshots/{id}/complete", screenshotId)
                .retrieve()
                .toEntity(ScreenshotDto.class);
    }

    /**
     * The whole three-step flow, as a client would run it.
     */
    private ScreenshotDto upload(String filename, byte[] content) {
        ScreenshotUploadUrlDto uploadUrl = requestUploadUrl(filename, content.length);
        assertThat(putToPresignedUrl(uploadUrl, content).statusCode()).isEqualTo(200);
        return complete(uploadUrl.screenshotId()).getBody();
    }

    private HttpResponse<byte[]> get(String url, String... headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).GET();
        for (int i = 0; i < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }

        try {
            return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * A backend GET that does not follow redirects, so the 302 itself can be asserted.
     * {@code RestClient} would swallow it.
     */
    private HttpResponse<byte[]> getFromBackend(String path) {
        return get("http://localhost:" + port + path,
                RateLimitAspect.HEADER_DISCORD_ID, RATE_LIMIT_DISCORD_ID);
    }

    private boolean objectExists(String objectKey) {
        try (S3Client s3 = ContainerSupport.s3Client()) {
            s3.headObject(builder -> builder.bucket(ContainerSupport.S3_BUCKET).key(objectKey));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Nested
    @DisplayName("Upload")
    class Upload {

        @Test
        @DisplayName("reserves the metadata as PENDING and returns a presigned upload URL")
        void shouldReserveMetadataAsPending() {
            ResponseEntity<ScreenshotUploadUrlDto> response = client().post()
                    .uri("/api/v1/screenshots/upload-url")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ScreenshotUploadRequestDto("screenshot.png", "image/png", PNG_BYTES.length))
                    .retrieve()
                    .toEntity(ScreenshotUploadUrlDto.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            ScreenshotUploadUrlDto uploadUrl = response.getBody();
            assertThat(uploadUrl.screenshotId()).isNotNull();
            assertThat(uploadUrl.httpMethod()).isEqualTo("PUT");
            assertThat(uploadUrl.uploadStatus()).isEqualTo(UploadStatus.PENDING);
            assertThat(uploadUrl.expiresAt()).isNotNull();
            assertThat(uploadUrl.uploadUrl())
                    .as("The URL points straight at MinIO, not at the backend")
                    .startsWith(ContainerSupport.MINIO.getS3URL())
                    .contains("X-Amz-Signature");
            assertThat(uploadUrl.s3ObjectKey())
                    .matches("screenshots/\\d{4}-\\d{2}-\\d{2}/[0-9a-f-]{36}-screenshot\\.png");

            assertThat(objectExists(uploadUrl.s3ObjectKey()))
                    .as("Before the upload there must be no object in the bucket yet")
                    .isFalse();
        }

        @Test
        @DisplayName("uploads the file straight into the bucket through the presigned URL")
        void shouldUploadFileToBucketViaPresignedUrl() {
            ScreenshotUploadUrlDto uploadUrl = requestUploadUrl("screenshot.png", PNG_BYTES.length);

            assertThat(putToPresignedUrl(uploadUrl, PNG_BYTES).statusCode()).isEqualTo(200);

            assertThat(objectExists(uploadUrl.s3ObjectKey()))
                    .as("The object has to really be in the bucket without the backend having seen the bytes")
                    .isTrue();
        }

        @Test
        @DisplayName("confirms the upload and takes the actual size from S3")
        void shouldCompleteUploadWithRealMetadata() {
            ScreenshotUploadUrlDto uploadUrl = requestUploadUrl("screenshot.png", PNG_BYTES.length);
            putToPresignedUrl(uploadUrl, PNG_BYTES);

            ResponseEntity<ScreenshotDto> response = complete(uploadUrl.screenshotId());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            ScreenshotDto completed = response.getBody();
            assertThat(completed.id()).isEqualTo(uploadUrl.screenshotId());
            assertThat(completed.s3ObjectKey()).isEqualTo(uploadUrl.s3ObjectKey());
            assertThat(completed.originalFilename()).isEqualTo("screenshot.png");
            assertThat(completed.contentType()).isEqualTo("image/png");
            assertThat(completed.fileSizeBytes()).isEqualTo(PNG_BYTES.length);
            assertThat(completed.uploadStatus()).isEqualTo(UploadStatus.SUCCESS);
        }

        @Test
        @DisplayName("returns the S3 URL of the uploaded image")
        void shouldReturnStorageUri() {
            ScreenshotDto uploaded = upload("screenshot.png", PNG_BYTES);

            assertThat(uploaded.s3Url()).isNotNull().contains(ContainerSupport.S3_BUCKET);
        }

        @Test
        @DisplayName("sanitises special characters in the file name for the object key")
        void shouldSanitizeFilename() {
            ScreenshotDto uploaded = upload("Böse Datei!.PNG", PNG_BYTES);

            assertThat(uploaded.s3ObjectKey()).matches("screenshots/[^/]+/[0-9a-f-]{36}-[a-z0-9._-]+");
            assertThat(uploaded.originalFilename()).isEqualTo("Böse Datei!.PNG");
        }

        @Test
        @DisplayName("rejects a file size of zero")
        void shouldRejectEmptyFile() {
            ResponseEntity<ApiErrorResponse> response = client().post()
                    .uri("/api/v1/screenshots/upload-url")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ScreenshotUploadRequestDto("screenshot.png", "image/png", 0L))
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.ILLEGAL_ARGUMENT);
        }

        @Test
        @DisplayName("rejects files above the configured maximum")
        void shouldRejectOversizedFile() {
            ResponseEntity<ApiErrorResponse> response = client().post()
                    .uri("/api/v1/screenshots/upload-url")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ScreenshotUploadRequestDto("screenshot.png", "image/png", 10_485_761L))
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.ILLEGAL_ARGUMENT);
        }

        @Test
        @DisplayName("answers 409 SCREENSHOT_UPLOAD_INCOMPLETE when nothing was ever uploaded")
        void shouldRejectCompletionWithoutUpload() {
            ScreenshotUploadUrlDto uploadUrl = requestUploadUrl("screenshot.png", PNG_BYTES.length);

            ResponseEntity<ApiErrorResponse> response = client().post()
                    .uri("/api/v1/screenshots/{id}/complete", uploadUrl.screenshotId())
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.SCREENSHOT_UPLOAD_INCOMPLETE);

            ResponseEntity<ScreenshotDto> metadata = client().get()
                    .uri("/api/v1/screenshots/{id}", uploadUrl.screenshotId())
                    .retrieve()
                    .toEntity(ScreenshotDto.class);

            assertThat(metadata.getBody().uploadStatus())
                    .as("The failed upload has to stay recognisable as FAILED")
                    .isEqualTo(UploadStatus.FAILED);
        }

        @Test
        @DisplayName("is idempotent when the client repeats the confirmation")
        void shouldBeIdempotent() {
            ScreenshotDto uploaded = upload("screenshot.png", PNG_BYTES);

            ResponseEntity<ScreenshotDto> second = complete(uploaded.id());

            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(second.getBody().uploadStatus()).isEqualTo(UploadStatus.SUCCESS);
        }

        @Test
        @DisplayName("answers 404 SCREENSHOT_NOT_FOUND when the id is unknown")
        void shouldReturnNotFoundForUnknownId() {
            ResponseEntity<ApiErrorResponse> response = client().post()
                    .uri("/api/v1/screenshots/9999/complete")
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.SCREENSHOT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Download")
    class Download {

        @Test
        @DisplayName("returns a presigned URL through which the bytes come back unchanged")
        void shouldDownloadUploadedBytesViaPresignedUrl() {
            ScreenshotDto uploaded = upload("screenshot.png", PNG_BYTES);

            ResponseEntity<ScreenshotDownloadUrlDto> response = client().get()
                    .uri("/api/v1/screenshots/{id}/download-url", uploaded.id())
                    .retrieve()
                    .toEntity(ScreenshotDownloadUrlDto.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            ScreenshotDownloadUrlDto downloadUrl = response.getBody();
            assertThat(downloadUrl.screenshotId()).isEqualTo(uploaded.id());
            assertThat(downloadUrl.originalFilename()).isEqualTo("screenshot.png");
            assertThat(downloadUrl.contentType()).isEqualTo("image/png");
            assertThat(downloadUrl.downloadUrl())
                    .startsWith(ContainerSupport.MINIO.getS3URL())
                    .contains("X-Amz-Signature");

            HttpResponse<byte[]> fetched = get(downloadUrl.downloadUrl());
            assertThat(fetched.statusCode()).isEqualTo(200);
            assertThat(fetched.body()).isEqualTo(PNG_BYTES);
            assertThat(fetched.headers().firstValue("content-type")).hasValue("image/png");
            assertThat(fetched.headers().firstValue("content-disposition"))
                    .hasValueSatisfying(disposition -> assertThat(disposition).contains("screenshot.png"));
        }

        @Test
        @DisplayName("redirects /download with 302 to the presigned S3 URL")
        void shouldRedirectToPresignedUrl() {
            ScreenshotDto uploaded = upload("screenshot.png", PNG_BYTES);

            HttpResponse<byte[]> response =
                    getFromBackend("/api/v1/screenshots/" + uploaded.id() + "/download");

            assertThat(response.statusCode()).isEqualTo(302);
            assertThat(response.headers().firstValue("location"))
                    .hasValueSatisfying(location -> assertThat(location)
                            .startsWith(ContainerSupport.MINIO.getS3URL())
                            .contains("X-Amz-Signature"));
            assertThat(response.body())
                    .as("The backend no longer proxies any bytes")
                    .isEmpty();
        }

        @Test
        @DisplayName("answers 404 SCREENSHOT_NOT_FOUND for an unknown id")
        void shouldReturnNotFoundForUnknownId() {
            ResponseEntity<ApiErrorResponse> response = client().get()
                    .uri("/api/v1/screenshots/9999/download-url")
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.SCREENSHOT_NOT_FOUND);
        }

        @Test
        @DisplayName("answers 409 SCREENSHOT_UPLOAD_INCOMPLETE while the upload is PENDING")
        void shouldReturnConflictWhilePending() {
            ScreenshotUploadUrlDto uploadUrl = requestUploadUrl("screenshot.png", PNG_BYTES.length);

            ResponseEntity<ApiErrorResponse> response = client().get()
                    .uri("/api/v1/screenshots/{id}/download-url", uploadUrl.screenshotId())
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.SCREENSHOT_UPLOAD_INCOMPLETE);
        }

        @Test
        @DisplayName("hands out a URL that returns 404 when the object is missing from the bucket")
        void shouldPresignUrlThatFailsWhenObjectMissing() {
            // Metadata without a matching object - exactly the state after a screenshot was deleted
            // manually in the bucket. The backend keeps signing, S3 refuses on retrieval.
            ScreenshotDto orphan = ApiFixtures.createScreenshot(client());

            ResponseEntity<ScreenshotDownloadUrlDto> response = client().get()
                    .uri("/api/v1/screenshots/{id}/download-url", orphan.id())
                    .retrieve()
                    .toEntity(ScreenshotDownloadUrlDto.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(get(response.getBody().downloadUrl()).statusCode()).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("Metadata and deletion")
    class MetadataAndDeletion {

        @Test
        @DisplayName("reads the metadata of an uploaded screenshot")
        void shouldReadMetadata() {
            ScreenshotDto uploaded = upload("screenshot.png", PNG_BYTES);

            ResponseEntity<ScreenshotDto> response = client().get()
                    .uri("/api/v1/screenshots/{id}", uploaded.id())
                    .retrieve()
                    .toEntity(ScreenshotDto.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().s3ObjectKey()).isEqualTo(uploaded.s3ObjectKey());
        }

        @Test
        @DisplayName("updates a screenshot's metadata")
        void shouldUpdateMetadata() {
            ScreenshotDto uploaded = upload("screenshot.png", PNG_BYTES);

            ResponseEntity<ScreenshotDto> response = client().patch()
                    .uri("/api/v1/screenshots/{id}", uploaded.id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotUpdateDto(
                            null, null, "umbenannt.png", null, 0L, UploadStatus.FAILED))
                    .retrieve()
                    .toEntity(ScreenshotDto.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().originalFilename()).isEqualTo("umbenannt.png");
            assertThat(response.getBody().uploadStatus()).isEqualTo(UploadStatus.FAILED);
            assertThat(response.getBody().s3ObjectKey())
                    .as("The object key must not be lost by a partial update")
                    .isEqualTo(uploaded.s3ObjectKey());
        }

        @Test
        @DisplayName("deletes both the metadata and the object in the bucket")
        void shouldDeleteMetadataAndObject() {
            ScreenshotDto uploaded = upload("screenshot.png", PNG_BYTES);
            assertThat(objectExists(uploaded.s3ObjectKey())).isTrue();

            assertThat(client().delete()
                    .uri("/api/v1/screenshots/{id}", uploaded.id())
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            assertThat(client().get()
                    .uri("/api/v1/screenshots/{id}", uploaded.id())
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

            assertThat(objectExists(uploaded.s3ObjectKey()))
                    .as("The object has to disappear from the bucket too, otherwise garbage is left behind")
                    .isFalse();
        }

        @Test
        @DisplayName("counts the persisted screenshots")
        void shouldCountScreenshots() {
            upload("a.png", PNG_BYTES);
            upload("b.png", PNG_BYTES);

            ResponseEntity<Long> response = client().get()
                    .uri("/api/v1/screenshots/count")
                    .retrieve()
                    .toEntity(Long.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(2L);
        }

        @Test
        @DisplayName("lists screenshots with pagination")
        void shouldListScreenshotsPaged() {
            upload("a.png", PNG_BYTES);
            upload("b.png", PNG_BYTES);
            upload("c.png", PNG_BYTES);

            ResponseEntity<String> response = client().get()
                    .uri("/api/v1/screenshots?page=0&size=2")
                    .retrieve()
                    .toEntity(String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("\"totalElements\":3");
        }
    }

    @Nested
    @DisplayName("Bucket interaction")
    class BucketInteraction {

        @Test
        @DisplayName("confirms an object that was put straight into the bucket")
        void shouldCompleteObjectPutDirectly() {
            // Proves the confirmation really checks against S3 instead of trusting the client.
            ScreenshotUploadUrlDto uploadUrl = requestUploadUrl("screenshot.png", PNG_BYTES.length);
            byte[] content = "direkt-in-den-bucket".getBytes(StandardCharsets.UTF_8);

            try (S3Client s3 = ContainerSupport.s3Client()) {
                s3.putObject(PutObjectRequest.builder()
                                .bucket(ContainerSupport.S3_BUCKET)
                                .key(uploadUrl.s3ObjectKey())
                                .contentType("image/png")
                                .build(),
                        software.amazon.awssdk.core.sync.RequestBody.fromBytes(content));
            }

            ResponseEntity<ScreenshotDto> response = complete(uploadUrl.screenshotId());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            ScreenshotDto completed = response.getBody();
            assertThat(completed.uploadStatus()).isEqualTo(UploadStatus.SUCCESS);
            assertThat(completed.fileSizeBytes())
                    .as("The actual size from S3 replaces the one announced by the client")
                    .isEqualTo(content.length);
        }

        @Test
        @DisplayName("reports an error when bucket access fails for a missing object")
        void shouldFailForMissingObject() {
            assertThatThrownBy(() -> {
                try (S3Client s3 = ContainerSupport.s3Client()) {
                    s3.getObjectAsBytes(builder -> builder
                            .bucket(ContainerSupport.S3_BUCKET)
                            .key("screenshots/does-not/exist.png"));
                }
            }).isInstanceOf(NoSuchKeyException.class);
        }
    }
}

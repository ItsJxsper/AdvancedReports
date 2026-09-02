package de.itsjxsper.advancedreports.backend.screenshot.service;

import de.itsjxsper.advancedreports.backend.screenshot.exceptions.ScreenshotStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("S3ScreenshotStorageService")
class S3ScreenshotStorageServiceTest {

    private static final String BUCKET = "advancedreports";
    private static final Instant EXPIRES_AT = Instant.parse("2026-01-01T00:15:00Z");

    @Mock
    private ObjectProvider<S3Client> s3ClientProvider;

    @Mock
    private ObjectProvider<S3Presigner> s3PresignerProvider;

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3ScreenshotStorageService service;

    private static SdkHttpRequest signedRequest(SdkHttpMethod method, String path) {
        return SdkHttpRequest.builder()
                .method(method)
                .protocol("https")
                .host(BUCKET + ".s3.eu-central-1.amazonaws.com")
                .encodedPath(path)
                .appendRawQueryParameter("X-Amz-Signature", "abc123")
                .build();
    }

    @BeforeEach
    void setUp() {
        service = new S3ScreenshotStorageService(s3ClientProvider, s3PresignerProvider);
        when(s3ClientProvider.getIfAvailable()).thenReturn(s3Client);
        when(s3PresignerProvider.getIfAvailable()).thenReturn(s3Presigner);
        configure(BUCKET, "eu-central-1", "");
    }

    /**
     * The bucket, region, endpoint and expiry durations are {@code @Value}-injected fields, so outside
     * of a Spring context they have to be set reflectively.
     */
    private void configure(String bucket, String region, String endpointUrl) {
        ReflectionTestUtils.setField(service, "bucket", bucket);
        ReflectionTestUtils.setField(service, "region", region);
        ReflectionTestUtils.setField(service, "endpointUrl", endpointUrl);
        ReflectionTestUtils.setField(service, "uploadExpiry", Duration.ofMinutes(15));
        ReflectionTestUtils.setField(service, "downloadExpiry", Duration.ofMinutes(5));
    }

    @Nested
    @DisplayName("presignUpload")
    class PresignUpload {

        private PresignedPutObjectRequest presignedPut() {
            return PresignedPutObjectRequest.builder()
                    .expiration(EXPIRES_AT)
                    .isBrowserExecutable(false)
                    .signedHeaders(Map.of(
                            "content-type", List.of("image/png"),
                            "content-length", List.of("1024")))
                    .httpRequest(signedRequest(SdkHttpMethod.PUT, "/screenshots/2026-01-01/abc-screenshot.png"))
                    .build();
        }

        @Test
        @DisplayName("signs a PUT to a dated, unique key")
        void shouldPresignPutUnderDatedKey() {
            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedPut());

            var presigned = service.presignUpload("screenshot.png", "image/png", 1024L);

            ArgumentCaptor<PutObjectPresignRequest> request =
                    ArgumentCaptor.forClass(PutObjectPresignRequest.class);
            verify(s3Presigner).presignPutObject(request.capture());

            PutObjectRequest putObjectRequest = request.getValue().putObjectRequest();
            assertThat(putObjectRequest.bucket()).isEqualTo(BUCKET);
            assertThat(putObjectRequest.contentType()).isEqualTo("image/png");
            assertThat(putObjectRequest.key())
                    .matches("screenshots/" + LocalDate.now() + "/[0-9a-f-]{36}-screenshot\\.png");
            assertThat(request.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(15));

            assertThat(presigned.objectKey()).isEqualTo(putObjectRequest.key());
            assertThat(presigned.originalFilename()).isEqualTo("screenshot.png");
            assertThat(presigned.contentType()).isEqualTo("image/png");
            assertThat(presigned.uploadUrl()).contains("X-Amz-Signature=abc123");
            assertThat(presigned.expiresAt()).isEqualTo(EXPIRES_AT);
            assertThat(presigned.storageUri())
                    .isEqualTo("https://" + BUCKET + ".s3.eu-central-1.amazonaws.com/" + presigned.objectKey());
        }

        @Test
        @DisplayName("signs the file size as Content-Length so S3 rejects differing uploads")
        void shouldSignContentLength() {
            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedPut());

            service.presignUpload("screenshot.png", "image/png", 1024L);

            ArgumentCaptor<PutObjectPresignRequest> request =
                    ArgumentCaptor.forClass(PutObjectPresignRequest.class);
            verify(s3Presigner).presignPutObject(request.capture());

            assertThat(request.getValue().putObjectRequest().contentLength()).isEqualTo(1024L);
        }

        @Test
        @DisplayName("returns the signed headers the client has to send")
        void shouldReturnRequiredHeaders() {
            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedPut());

            var presigned = service.presignUpload("screenshot.png", "image/png", 1024L);

            assertThat(presigned.requiredHeaders())
                    .containsEntry("content-type", "image/png")
                    .containsEntry("content-length", "1024");
        }

        @Test
        @DisplayName("sanitises path segments and special characters in the file name")
        void shouldSanitizeFilename() {
            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedPut());

            var presigned = service.presignUpload("../Böse Datei!.PNG", "image/png", 1L);

            // Umlauts, spaces and "!" become "_", the name ends up entirely lower case, and every
            // path separator is gone - so the file name can no longer break out of the
            // "screenshots/<date>/" prefix.
            assertThat(presigned.objectKey()).matches("screenshots/[^/]+/[0-9a-f-]{36}-[a-z0-9._-]+");
            assertThat(presigned.objectKey()).endsWith("-.._b_se_datei_.png");
            assertThat(presigned.originalFilename())
                    .as("The original name is kept unchanged for display")
                    .isEqualTo("../Böse Datei!.PNG");
        }

        @Test
        @DisplayName("derives the content type from the file name when none was sent")
        void shouldGuessContentTypeFromFilename() {
            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedPut());

            assertThat(service.presignUpload("screenshot.png", null, 1L).contentType()).isEqualTo("image/png");
        }

        @Test
        @DisplayName("falls back to application/octet-stream when the type is unknown")
        void shouldFallBackToOctetStream() {
            when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedPut());

            assertThat(service.presignUpload("screenshot.unknownext", null, 1L).contentType())
                    .isEqualTo("application/octet-stream");
        }

        @Test
        @DisplayName("throws IllegalArgumentException for a file size of zero or less")
        void shouldRejectNonPositiveFileSize() {
            assertThatThrownBy(() -> service.presignUpload("screenshot.png", "image/png", 0L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("greater than zero");

            verifyNoInteractions(s3Presigner);
        }
    }

    @Nested
    @DisplayName("presignDownload")
    class PresignDownload {

        private PresignedGetObjectRequest presignedGet() {
            return PresignedGetObjectRequest.builder()
                    .expiration(EXPIRES_AT)
                    .isBrowserExecutable(true)
                    .signedHeaders(Map.of("host", List.of(BUCKET + ".s3.eu-central-1.amazonaws.com")))
                    .httpRequest(signedRequest(SdkHttpMethod.GET, "/screenshots/2026-01-01/abc-screenshot.png"))
                    .build();
        }

        @Test
        @DisplayName("signs a GET including file name and content type")
        void shouldPresignGetWithFilename() {
            when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedGet());

            var presigned = service.presignDownload("a/b.png", "screenshot.png", "image/png");

            ArgumentCaptor<GetObjectPresignRequest> request =
                    ArgumentCaptor.forClass(GetObjectPresignRequest.class);
            verify(s3Presigner).presignGetObject(request.capture());

            GetObjectRequest getObjectRequest = request.getValue().getObjectRequest();
            assertThat(getObjectRequest.bucket()).isEqualTo(BUCKET);
            assertThat(getObjectRequest.key()).isEqualTo("a/b.png");
            assertThat(getObjectRequest.responseContentType()).isEqualTo("image/png");
            assertThat(getObjectRequest.responseContentDisposition()).contains("screenshot.png");
            assertThat(request.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(5));

            assertThat(presigned.downloadUrl()).contains("X-Amz-Signature=abc123");
            assertThat(presigned.originalFilename()).isEqualTo("screenshot.png");
            assertThat(presigned.contentType()).isEqualTo("image/png");
            assertThat(presigned.expiresAt()).isEqualTo(EXPIRES_AT);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for an empty object key")
        void shouldRejectBlankObjectKey() {
            assertThatThrownBy(() -> service.presignDownload("  ", "screenshot.png", "image/png"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be blank");

            verifyNoInteractions(s3Presigner);
        }
    }

    @Nested
    @DisplayName("headObject")
    class HeadObject {

        @Test
        @DisplayName("returns the size and content type of the stored object")
        void shouldReturnObjectMetadata() {
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenReturn(HeadObjectResponse.builder()
                            .contentLength(2048L)
                            .contentType("image/png")
                            .build());

            Optional<S3ScreenshotStorageService.ObjectMetadata> result = service.headObject("a/b.png");

            assertThat(result).isPresent();
            assertThat(result.get().contentLength()).isEqualTo(2048L);
            assertThat(result.get().contentType()).isEqualTo("image/png");
        }

        @Test
        @DisplayName("returns an empty Optional when the object does not exist")
        void shouldReturnEmptyForMissingObject() {
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenThrow(NoSuchKeyException.builder().message("no such key").build());

            assertThat(service.headObject("a/b.png")).isEmpty();
        }

        @Test
        @DisplayName("returns an empty Optional when S3 answers with 404")
        void shouldReturnEmptyForNotFoundStatus() {
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenThrow(S3Exception.builder().statusCode(404).message("not found").build());

            assertThat(service.headObject("a/b.png")).isEmpty();
        }

        @Test
        @DisplayName("wraps every other S3Exception in a ScreenshotStorageException")
        void shouldWrapS3Exception() {
            when(s3Client.headObject(any(HeadObjectRequest.class)))
                    .thenThrow(S3Exception.builder().statusCode(403).message("access denied").build());

            assertThatThrownBy(() -> service.headObject("a/b.png"))
                    .isInstanceOf(ScreenshotStorageException.class)
                    .hasMessageContaining("Failed to read screenshot metadata from S3");
        }

        @Test
        @DisplayName("throws IllegalArgumentException for an empty object key")
        void shouldRejectBlankObjectKey() {
            assertThatThrownBy(() -> service.headObject("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be blank");

            verifyNoInteractions(s3Client);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("deletes the object from the bucket")
        void shouldDeleteObject() {
            service.delete("screenshots/2026-01-01/abc-screenshot.png");

            ArgumentCaptor<DeleteObjectRequest> request =
                    ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(request.capture());

            assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
            assertThat(request.getValue().key()).isEqualTo("screenshots/2026-01-01/abc-screenshot.png");
        }

        @Test
        @DisplayName("does nothing when no object key was passed")
        void shouldIgnoreBlankObjectKey() {
            service.delete("  ");

            verifyNoInteractions(s3Client);
        }

        @Test
        @DisplayName("wraps an S3Exception in a ScreenshotStorageException")
        void shouldWrapS3Exception() {
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .thenThrow(S3Exception.builder().message("boom").build());

            assertThatThrownBy(() -> service.delete("a/b.png"))
                    .isInstanceOf(ScreenshotStorageException.class)
                    .hasMessageContaining("Failed to delete screenshot from S3");
        }
    }

    @Nested
    @DisplayName("buildStorageUri")
    class BuildStorageUri {

        @Test
        @DisplayName("uses the configured endpoint URL and strips the trailing slash")
        void shouldUseEndpointUrl() {
            configure(BUCKET, "eu-central-1", "http://localhost:9000/");

            assertThat(service.buildStorageUri("a/b.png"))
                    .isEqualTo("http://localhost:9000/" + BUCKET + "/a/b.png");
        }

        @Test
        @DisplayName("builds a regional AWS URL when there is no endpoint")
        void shouldUseRegionalAwsUrl() {
            configure(BUCKET, "eu-central-1", "");

            assertThat(service.buildStorageUri("a/b.png"))
                    .isEqualTo("https://" + BUCKET + ".s3.eu-central-1.amazonaws.com/a/b.png");
        }

        @Test
        @DisplayName("falls back to an s3:// scheme without an endpoint and without a region")
        void shouldFallBackToS3Scheme() {
            configure(BUCKET, "", "");

            assertThat(service.buildStorageUri("a/b.png")).isEqualTo("s3://" + BUCKET + "/a/b.png");
        }
    }

    @Nested
    @DisplayName("Configuration errors")
    class ConfigurationErrors {

        @Test
        @DisplayName("throws ScreenshotStorageException when no S3Client is available")
        void shouldFailWithoutS3Client() {
            when(s3ClientProvider.getIfAvailable()).thenReturn(null);

            assertThatThrownBy(() -> service.delete("a/b.png"))
                    .isInstanceOf(ScreenshotStorageException.class)
                    .hasMessageContaining("AWS S3 is not configured");
        }

        @Test
        @DisplayName("throws ScreenshotStorageException when no S3Presigner is available")
        void shouldFailWithoutS3Presigner() {
            when(s3PresignerProvider.getIfAvailable()).thenReturn(null);

            assertThatThrownBy(() -> service.presignUpload("screenshot.png", "image/png", 1L))
                    .isInstanceOf(ScreenshotStorageException.class)
                    .hasMessageContaining("AWS S3 is not configured");
        }

        @Test
        @DisplayName("throws ScreenshotStorageException when no bucket is configured")
        void shouldFailWithoutBucket() {
            configure("", "eu-central-1", "");

            assertThatThrownBy(() -> service.delete("a/b.png"))
                    .isInstanceOf(ScreenshotStorageException.class)
                    .hasMessageContaining("bucket is not configured");
        }
    }
}

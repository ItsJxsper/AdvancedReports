package de.itsjxsper.advancedreports.backend.screenshot.service;

import de.itsjxsper.advancedreports.backend.screenshot.exceptions.ScreenshotStorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Grants access to the screenshot bucket without ever handling the file itself: the upload and download
 * transfers happen directly between the client and S3 over presigned URLs. Only object metadata
 * ({@link #headObject}) and deletion still go through the backend.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3ScreenshotStorageService {

    private final ObjectProvider<S3Client> s3ClientProvider;
    private final ObjectProvider<S3Presigner> s3PresignerProvider;

    @Value("${aws.s3.bucket:}")
    private String bucket;

    @Value("${aws.s3.region:eu-central-1}")
    private String region;

    @Value("${aws.s3.endpoint-url:}")
    private String endpointUrl;

    @Value("${aws.s3.presign.upload-expiry:PT15M}")
    private Duration uploadExpiry;

    @Value("${aws.s3.presign.download-expiry:PT15M}")
    private Duration downloadExpiry;

    /**
     * Signs a PUT that the client uses to send the file directly to S3. The declared size is signed
     * as Content-Length, so S3 itself rejects a body of a different length.
     */
    public PresignedUpload presignUpload(String originalFilename, String contentType, long fileSizeBytes) {
        if (fileSizeBytes <= 0) {
            throw new IllegalArgumentException("Screenshot file size must be greater than zero");
        }

        String resolvedFilename = normalizeFilename(originalFilename);
        String resolvedContentType = resolveContentType(contentType, resolvedFilename);
        String objectKey = buildObjectKey(resolvedFilename);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(requireBucket())
                .key(objectKey)
                .contentType(resolvedContentType)
                .contentLength(fileSizeBytes)
                .build();

        PresignedPutObjectRequest presignedRequest = requirePresigner().presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(uploadExpiry)
                        .putObjectRequest(putObjectRequest)
                        .build());

        log.debug("Presigned upload for objectKey={} expiresAt={}", objectKey, presignedRequest.expiration());

        return new PresignedUpload(
                objectKey,
                buildStorageUri(objectKey),
                resolvedFilename,
                resolvedContentType,
                presignedRequest.url().toExternalForm(),
                flattenHeaders(presignedRequest.signedHeaders()),
                presignedRequest.expiration()
        );
    }

    /**
     * Signs a GET that the client uses to fetch the file directly from S3.
     */
    public PresignedDownload presignDownload(String objectKey, String originalFilename, String contentType) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Screenshot object key must not be blank");
        }

        String resolvedFilename = normalizeFilename(originalFilename);
        String resolvedContentType = resolveContentType(contentType, resolvedFilename);

        String contentDisposition = ContentDisposition.attachment()
                .filename(resolvedFilename, StandardCharsets.UTF_8)
                .build()
                .toString();

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(requireBucket())
                .key(objectKey)
                .responseContentType(resolvedContentType)
                .responseContentDisposition(contentDisposition)
                .build();

        PresignedGetObjectRequest presignedRequest = requirePresigner().presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(downloadExpiry)
                        .getObjectRequest(getObjectRequest)
                        .build());

        log.debug("Presigned download for objectKey={} expiresAt={}", objectKey, presignedRequest.expiration());

        return new PresignedDownload(
                presignedRequest.url().toExternalForm(),
                resolvedFilename,
                resolvedContentType,
                presignedRequest.expiration()
        );
    }

    /**
     * Reads the metadata of the stored object to confirm that a client-side upload actually landed.
     * Returns an empty optional when the object is not present in the bucket.
     */
    @SuppressWarnings("resource")
    public Optional<ObjectMetadata> headObject(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Screenshot object key must not be blank");
        }

        S3Client client = requireClient();

        try {
            HeadObjectResponse response = client.headObject(HeadObjectRequest.builder()
                    .bucket(requireBucket())
                    .key(objectKey)
                    .build());

            return Optional.of(new ObjectMetadata(
                    response.contentLength() != null ? response.contentLength() : 0L,
                    response.contentType()
            ));
        } catch (NoSuchKeyException e) {
            log.debug("Screenshot object objectKey={} is not present in S3", objectKey);
            return Optional.empty();
        } catch (S3Exception e) {
            // HeadObject returns no body, so "not present" sometimes arrives as a bare 404 only.
            if (e.statusCode() == 404) {
                log.debug("Screenshot object objectKey={} is not present in S3", objectKey);
                return Optional.empty();
            }
            throw new ScreenshotStorageException("Failed to read screenshot metadata from S3", e);
        }
    }

    @SuppressWarnings("resource")
    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        S3Client client = requireClient();

        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(requireBucket())
                    .key(objectKey)
                    .build());
        } catch (S3Exception e) {
            throw new ScreenshotStorageException("Failed to delete screenshot from S3", e);
        }
    }

    public String buildStorageUri(String objectKey) {
        String resolvedBucket = requireBucket();

        if (endpointUrl != null && !endpointUrl.isBlank()) {
            return endpointUrl.replaceAll("/$", "") + "/" + resolvedBucket + "/" + objectKey;
        }

        if (region != null && !region.isBlank()) {
            return "https://" + resolvedBucket + ".s3." + region + ".amazonaws.com/" + objectKey;
        }

        return "s3://" + resolvedBucket + "/" + objectKey;
    }

    private S3Client requireClient() {
        S3Client client = s3ClientProvider.getIfAvailable();
        if (client == null) {
            throw new ScreenshotStorageException("AWS S3 is not configured. Please set aws.s3.bucket and related properties.");
        }
        return client;
    }

    private S3Presigner requirePresigner() {
        S3Presigner presigner = s3PresignerProvider.getIfAvailable();
        if (presigner == null) {
            throw new ScreenshotStorageException("AWS S3 is not configured. Please set aws.s3.bucket and related properties.");
        }
        return presigner;
    }

    private String requireBucket() {
        if (bucket == null || bucket.isBlank()) {
            throw new ScreenshotStorageException("AWS S3 bucket is not configured");
        }
        return bucket;
    }

    // The client has to send every signed header exactly like this, or signature validation fails.
    private Map<String, String> flattenHeaders(Map<String, List<String>> signedHeaders) {
        Map<String, String> headers = new LinkedHashMap<>();
        signedHeaders.forEach((name, values) -> headers.put(name, String.join(",", values)));
        return Map.copyOf(headers);
    }

    private String buildObjectKey(String filename) {
        String safeFilename = sanitizeFilename(filename);
        return "screenshots/" + LocalDate.now() + "/" + UUID.randomUUID() + "-" + safeFilename;
    }

    private String normalizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "screenshot.png";
        }
        return filename;
    }

    private String sanitizeFilename(String filename) {
        String cleaned = Objects.requireNonNullElse(filename, "screenshot.png")
                .replaceAll("[\\\\/]+", "_")
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        return cleaned.isBlank() ? "screenshot.png" : cleaned.toLowerCase(Locale.ROOT);
    }

    private String resolveContentType(String contentType, String filename) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }

        String fromName = URLConnection.guessContentTypeFromName(filename);
        return fromName != null ? fromName : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    public record PresignedUpload(
            String objectKey,
            String storageUri,
            String originalFilename,
            String contentType,
            String uploadUrl,
            Map<String, String> requiredHeaders,
            Instant expiresAt
    ) {
    }

    public record PresignedDownload(
            String downloadUrl,
            String originalFilename,
            String contentType,
            Instant expiresAt
    ) {
    }

    public record ObjectMetadata(
            long contentLength,
            String contentType
    ) {
    }
}

package de.itsjxsper.advancedreports.backend.screenshot.service;

import de.itsjxsper.advancedreports.backend.screenshot.exceptions.ScreenshotStorageException;
import de.itsjxsper.advancedreports.backend.screenshot.model.ScreenshotDownloadDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3ScreenshotStorageService {

    private final ObjectProvider<S3Client> s3ClientProvider;

    @Value("${aws.s3.bucket:}")
    private String bucket;

    @Value("${aws.s3.region:eu-central-1}")
    private String region;

    @Value("${aws.s3.endpoint-url:}")
    private String endpointUrl;

    public StoredScreenshot upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Screenshot file must not be empty");
        }

        String originalFilename = normalizeFilename(file.getOriginalFilename());
        String contentType = resolveContentType(file.getContentType(), originalFilename);
        String objectKey = buildObjectKey(originalFilename);

        try {
            putObject(objectKey, contentType, file.getBytes());
            return new StoredScreenshot(objectKey, buildStorageUri(objectKey), originalFilename, contentType, file.getSize());
        } catch (IOException e) {
            throw new ScreenshotStorageException("Failed to read screenshot file for upload", e);
        }
    }

    public StoredScreenshot uploadFromUrl(String sourceUrl, String originalFilename) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("Source URL must not be blank");
        }

        try {
            URL url = URI.create(sourceUrl).toURL();
            String resolvedFilename = normalizeFilename(
                    originalFilename != null ? originalFilename : extractFilenameFromPath(url.getPath())
            );
            byte[] content;
            try (var inputStream = url.openStream()) {
                content = inputStream.readAllBytes();
            }
            String contentType = resolveContentType(URLConnection.guessContentTypeFromName(resolvedFilename), resolvedFilename);
            String objectKey = buildObjectKey(resolvedFilename);
            putObject(objectKey, contentType, content);
            return new StoredScreenshot(objectKey, buildStorageUri(objectKey), resolvedFilename, contentType, content.length);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid screenshot URL: " + sourceUrl, e);
        } catch (IOException e) {
            throw new ScreenshotStorageException("Failed to download screenshot from source URL", e);
        }
    }

    @SuppressWarnings("resource")
    public ScreenshotDownloadDto download(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Screenshot object key must not be blank");
        }

        S3Client client = requireClient();

        try {
            ResponseBytes<GetObjectResponse> responseBytes = client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(requireBucket())
                    .key(objectKey)
                    .build());

            String contentType = responseBytes.response().contentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            return new ScreenshotDownloadDto(
                    extractFilename(objectKey),
                    contentType,
                    responseBytes.asByteArray()
            );
        } catch (S3Exception e) {
            throw new ScreenshotStorageException("Failed to download screenshot from S3", e);
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

    @SuppressWarnings("resource")
    private void putObject(String objectKey, String contentType, byte[] content) {
        S3Client client = requireClient();

        try {
            client.putObject(PutObjectRequest.builder()
                            .bucket(requireBucket())
                            .key(objectKey)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (S3Exception e) {
            throw new ScreenshotStorageException("Failed to upload screenshot to S3", e);
        }
    }

    private S3Client requireClient() {
        S3Client client = s3ClientProvider.getIfAvailable();
        if (client == null) {
            throw new ScreenshotStorageException("AWS S3 is not configured. Please set aws.s3.bucket and related properties.");
        }
        return client;
    }

    private String requireBucket() {
        if (bucket == null || bucket.isBlank()) {
            throw new ScreenshotStorageException("AWS S3 bucket is not configured");
        }
        return bucket;
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

    private String extractFilenameFromPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == path.length() - 1) {
            return path;
        }

        return path.substring(lastSlash + 1);
    }

    private String extractFilename(String objectKey) {
        int lastSlash = objectKey.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == objectKey.length() - 1) {
            return objectKey;
        }
        return objectKey.substring(lastSlash + 1);
    }

    private String resolveContentType(String contentType, String filename) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }

        String fromName = URLConnection.guessContentTypeFromName(filename);
        return fromName != null ? fromName : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    public record StoredScreenshot(
            String objectKey,
            String storageUri,
            String originalFilename,
            String contentType,
            long fileSizeBytes
    ) {
    }
}





package de.itsjxsper.advancedreports.backend.screenshot.service;

import de.itsjxsper.advancedreports.backend.screenshot.exceptions.ScreenshotStorageException;
import de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotDownloadDto;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("S3ScreenshotStorageService")
class S3ScreenshotStorageServiceTest {

    private static final String BUCKET = "advancedreports";

    @Mock
    private ObjectProvider<S3Client> s3ClientProvider;

    @Mock
    private S3Client s3Client;

    private S3ScreenshotStorageService service;

    @BeforeEach
    void setUp() {
        service = new S3ScreenshotStorageService(s3ClientProvider);
        when(s3ClientProvider.getIfAvailable()).thenReturn(s3Client);
        configure(BUCKET, "eu-central-1", "");
    }

    /**
     * The bucket, region and endpoint are {@code @Value}-injected fields, so outside of a Spring
     * context they have to be set reflectively.
     */
    private void configure(String bucket, String region, String endpointUrl) {
        ReflectionTestUtils.setField(service, "bucket", bucket);
        ReflectionTestUtils.setField(service, "region", region);
        ReflectionTestUtils.setField(service, "endpointUrl", endpointUrl);
    }

    @Nested
    @DisplayName("upload")
    class Upload {

        @Test
        @DisplayName("legt das Objekt unter einem datierten, eindeutigen Key ab")
        void shouldUploadUnderDatedKey() {
            MockMultipartFile file =
                    new MockMultipartFile("file", "screenshot.png", "image/png", "png-bytes".getBytes());

            var stored = service.upload(file);

            ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client).putObject(request.capture(), any(RequestBody.class));

            assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
            assertThat(request.getValue().contentType()).isEqualTo("image/png");
            assertThat(request.getValue().key())
                    .matches("screenshots/" + LocalDate.now() + "/[0-9a-f-]{36}-screenshot\\.png");

            assertThat(stored.objectKey()).isEqualTo(request.getValue().key());
            assertThat(stored.originalFilename()).isEqualTo("screenshot.png");
            assertThat(stored.contentType()).isEqualTo("image/png");
            assertThat(stored.fileSizeBytes()).isEqualTo("png-bytes".getBytes().length);
        }

        @Test
        @DisplayName("entschärft Pfadanteile und Sonderzeichen im Dateinamen")
        void shouldSanitizeFilename() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "../Böse Datei!.PNG", "image/png", "x".getBytes());

            var stored = service.upload(file);

            // Umlaute, Leerzeichen und "!" werden zu "_", der Name landet komplett in Kleinschreibung,
            // und alle Pfadtrenner sind verschwunden - der Dateiname kann also nicht mehr aus dem
            // "screenshots/<datum>/"-Präfix ausbrechen.
            assertThat(stored.objectKey())
                    .isEqualTo("screenshots/" + LocalDate.now() + "/"
                            + stored.objectKey().split("/")[2].substring(0, 36) + "-.._b_se_datei_.png");
            assertThat(stored.objectKey()).matches("screenshots/[^/]+/[0-9a-f-]{36}-[a-z0-9._-]+");
            assertThat(stored.originalFilename())
                    .as("Der Originalname bleibt für die Anzeige unverändert erhalten")
                    .isEqualTo("../Böse Datei!.PNG");
        }

        @Test
        @DisplayName("leitet den Content-Type aus dem Dateinamen ab, wenn keiner mitgeschickt wurde")
        void shouldGuessContentTypeFromFilename() {
            MockMultipartFile file =
                    new MockMultipartFile("file", "screenshot.png", null, "x".getBytes());

            assertThat(service.upload(file).contentType()).isEqualTo("image/png");
        }

        @Test
        @DisplayName("fällt auf application/octet-stream zurück, wenn der Typ unbekannt ist")
        void shouldFallBackToOctetStream() {
            MockMultipartFile file =
                    new MockMultipartFile("file", "screenshot.unknownext", null, "x".getBytes());

            assertThat(service.upload(file).contentType()).isEqualTo("application/octet-stream");
        }

        @Test
        @DisplayName("wirft IllegalArgumentException bei einer leeren Datei")
        void shouldRejectEmptyFile() {
            MockMultipartFile empty = new MockMultipartFile("file", "screenshot.png", "image/png", new byte[0]);

            assertThatThrownBy(() -> service.upload(empty))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be empty");

            verifyNoInteractions(s3Client);
        }

        @Test
        @DisplayName("wirft IllegalArgumentException, wenn keine Datei übergeben wurde")
        void shouldRejectNullFile() {
            assertThatThrownBy(() -> service.upload(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("verpackt eine S3Exception in eine ScreenshotStorageException")
        void shouldWrapS3Exception() {
            MockMultipartFile file =
                    new MockMultipartFile("file", "screenshot.png", "image/png", "x".getBytes());
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenThrow(S3Exception.builder().message("access denied").build());

            assertThatThrownBy(() -> service.upload(file))
                    .isInstanceOf(ScreenshotStorageException.class)
                    .hasMessageContaining("Failed to upload screenshot to S3");
        }
    }

    @Nested
    @DisplayName("download")
    class Download {

        private ResponseBytes<GetObjectResponse> responseBytes(String contentType, byte[] content) {
            return ResponseBytes.fromByteArray(
                    GetObjectResponse.builder().contentType(contentType).build(), content);
        }

        @Test
        @DisplayName("liefert Dateiname, Content-Type und Inhalt zurück")
        void shouldDownloadObject() {
            byte[] content = "png-bytes".getBytes();
            when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                    .thenReturn(responseBytes("image/png", content));

            ScreenshotDownloadDto result = service.download("screenshots/2026-01-01/abc-screenshot.png");

            assertThat(result.filename()).isEqualTo("abc-screenshot.png");
            assertThat(result.contentType()).isEqualTo("image/png");
            assertThat(result.content()).isEqualTo(content);
        }

        @Test
        @DisplayName("fällt auf application/octet-stream zurück, wenn S3 keinen Content-Type liefert")
        void shouldFallBackToOctetStream() {
            when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                    .thenReturn(responseBytes(null, "x".getBytes()));

            assertThat(service.download("a/b.png").contentType()).isEqualTo("application/octet-stream");
        }

        @Test
        @DisplayName("wirft IllegalArgumentException bei leerem Object-Key")
        void shouldRejectBlankObjectKey() {
            assertThatThrownBy(() -> service.download("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be blank");
        }

        @Test
        @DisplayName("verpackt eine S3Exception in eine ScreenshotStorageException")
        void shouldWrapS3Exception() {
            when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                    .thenThrow(S3Exception.builder().message("no such key").build());

            assertThatThrownBy(() -> service.download("a/b.png"))
                    .isInstanceOf(ScreenshotStorageException.class)
                    .hasMessageContaining("Failed to download screenshot from S3");
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("löscht das Objekt aus dem Bucket")
        void shouldDeleteObject() {
            service.delete("screenshots/2026-01-01/abc-screenshot.png");

            ArgumentCaptor<DeleteObjectRequest> request =
                    ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(request.capture());

            assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
            assertThat(request.getValue().key()).isEqualTo("screenshots/2026-01-01/abc-screenshot.png");
        }

        @Test
        @DisplayName("tut nichts, wenn kein Object-Key übergeben wurde")
        void shouldIgnoreBlankObjectKey() {
            service.delete("  ");

            verifyNoInteractions(s3Client);
        }

        @Test
        @DisplayName("verpackt eine S3Exception in eine ScreenshotStorageException")
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
        @DisplayName("nutzt die konfigurierte Endpoint-URL und entfernt den Slash am Ende")
        void shouldUseEndpointUrl() {
            configure(BUCKET, "eu-central-1", "http://localhost:9000/");

            assertThat(service.buildStorageUri("a/b.png"))
                    .isEqualTo("http://localhost:9000/" + BUCKET + "/a/b.png");
        }

        @Test
        @DisplayName("baut ohne Endpoint eine regionale AWS-URL")
        void shouldUseRegionalAwsUrl() {
            configure(BUCKET, "eu-central-1", "");

            assertThat(service.buildStorageUri("a/b.png"))
                    .isEqualTo("https://" + BUCKET + ".s3.eu-central-1.amazonaws.com/a/b.png");
        }

        @Test
        @DisplayName("fällt ohne Endpoint und ohne Region auf ein s3://-Schema zurück")
        void shouldFallBackToS3Scheme() {
            configure(BUCKET, "", "");

            assertThat(service.buildStorageUri("a/b.png")).isEqualTo("s3://" + BUCKET + "/a/b.png");
        }
    }

    @Nested
    @DisplayName("Konfigurationsfehler")
    class ConfigurationErrors {

        @Test
        @DisplayName("wirft ScreenshotStorageException, wenn kein S3Client verfügbar ist")
        void shouldFailWithoutS3Client() {
            when(s3ClientProvider.getIfAvailable()).thenReturn(null);

            assertThatThrownBy(() -> service.delete("a/b.png"))
                    .isInstanceOf(ScreenshotStorageException.class)
                    .hasMessageContaining("AWS S3 is not configured");
        }

        @Test
        @DisplayName("wirft ScreenshotStorageException, wenn kein Bucket konfiguriert ist")
        void shouldFailWithoutBucket() {
            configure("", "eu-central-1", "");

            assertThatThrownBy(() -> service.delete("a/b.png"))
                    .isInstanceOf(ScreenshotStorageException.class)
                    .hasMessageContaining("bucket is not configured");
        }
    }
}

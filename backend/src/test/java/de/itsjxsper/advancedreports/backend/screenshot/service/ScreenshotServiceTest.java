package de.itsjxsper.advancedreports.backend.screenshot.service;

import de.itsjxsper.advancedreports.backend.messaging.events.ScreenshotReadyEvent;
import de.itsjxsper.advancedreports.backend.screenshot.data.entity.ScreenshotEntity;
import de.itsjxsper.advancedreports.backend.screenshot.data.repository.ScreenshotRepository;
import de.itsjxsper.advancedreports.backend.screenshot.exceptions.ScreenshotNotFoundException;
import de.itsjxsper.advancedreports.backend.screenshot.exceptions.ScreenshotUploadIncompleteException;
import de.itsjxsper.advancedreports.backend.screenshot.mapper.ScreenshotMapper;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.enums.screenshot.UploadStatus;
import de.itsjxsper.advancedreports.common.model.screenshot.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScreenshotService")
class ScreenshotServiceTest {

    private static final Long SCREENSHOT_ID = 9L;
    private static final String OBJECT_KEY = "screenshots/2026-01-01/abc-screenshot.png";
    private static final String STORAGE_URI = "https://example.invalid/" + OBJECT_KEY;
    private static final long MAX_UPLOAD_SIZE_BYTES = 10_485_760L;

    @Mock
    private ScreenshotRepository screenshotRepository;

    @Mock
    private S3ScreenshotStorageService screenshotStorageService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ScreenshotMapper screenshotMapper;

    @InjectMocks
    private ScreenshotService screenshotService;

    private ScreenshotEntity screenshotEntity;
    private ScreenshotDto screenshotDto;

    @BeforeEach
    void setUp() {
        // The @Value field is not populated without a Spring context and would otherwise stay 0.
        ReflectionTestUtils.setField(screenshotService, "maxUploadSizeBytes", MAX_UPLOAD_SIZE_BYTES);

        screenshotEntity = TestDataFactory.screenshot(OBJECT_KEY);
        screenshotEntity.setId(SCREENSHOT_ID);
        screenshotEntity.setS3Url(STORAGE_URI);
        screenshotDto = new ScreenshotDto(SCREENSHOT_ID, STORAGE_URI,
                OBJECT_KEY, "screenshot.png", "image/png", 1024L, UploadStatus.SUCCESS);
    }

    @Nested
    @DisplayName("createScreenshot")
    class CreateScreenshot {

        @Test
        @DisplayName("creates metadata and keeps the upload status that was sent")
        void shouldCreateScreenshotWithGivenStatus() {
            ScreenshotUpdateDto dto = TestDataFactory.screenshotUpdateDto(OBJECT_KEY);

            when(screenshotMapper.partialUpdateScreenshotEntity(eq(dto), any(ScreenshotEntity.class)))
                    .thenReturn(screenshotEntity);
            when(screenshotRepository.save(screenshotEntity)).thenReturn(screenshotEntity);
            when(screenshotMapper.toScreenshotDto(screenshotEntity)).thenReturn(screenshotDto);

            assertThat(screenshotService.createScreenshot(dto)).isEqualTo(screenshotDto);
            assertThat(screenshotEntity.getUploadStatus()).isEqualTo(UploadStatus.SUCCESS);
        }

        @Test
        @DisplayName("sets the upload status to SUCCESS when none was sent")
        void shouldDefaultUploadStatusToSuccess() {
            ScreenshotUpdateDto dto = new ScreenshotUpdateDto(null, OBJECT_KEY, "screenshot.png",
                    "image/png", 1024L, null);
            screenshotEntity.setUploadStatus(null);

            when(screenshotMapper.partialUpdateScreenshotEntity(eq(dto), any(ScreenshotEntity.class)))
                    .thenReturn(screenshotEntity);
            when(screenshotRepository.save(screenshotEntity)).thenReturn(screenshotEntity);
            when(screenshotMapper.toScreenshotDto(screenshotEntity)).thenReturn(screenshotDto);

            screenshotService.createScreenshot(dto);

            assertThat(screenshotEntity.getUploadStatus()).isEqualTo(UploadStatus.SUCCESS);
        }
    }

    @Nested
    @DisplayName("requestUpload")
    class RequestUpload {

        private final ScreenshotUploadRequestDto request =
                new ScreenshotUploadRequestDto("screenshot.png", "image/png", 1024L);

        private S3ScreenshotStorageService.PresignedUpload presigned() {
            return new S3ScreenshotStorageService.PresignedUpload(
                    OBJECT_KEY, STORAGE_URI, "screenshot.png", "image/png",
                    "https://example.invalid/presigned-put",
                    Map.of("content-type", "image/png", "content-length", "1024"),
                    Instant.parse("2026-01-01T00:15:00Z"));
        }

        private void stubPresignAndSave() {
            when(screenshotStorageService.presignUpload("screenshot.png", "image/png", 1024L))
                    .thenReturn(presigned());
            when(screenshotRepository.save(any(ScreenshotEntity.class)))
                    .thenAnswer(invocation -> {
                        ScreenshotEntity entity = invocation.getArgument(0);
                        entity.setId(SCREENSHOT_ID);
                        return entity;
                    });
        }

        @Test
        @DisplayName("creates the metadata as PENDING and returns the presigned upload URL")
        void shouldReserveMetadataAndReturnUploadUrl() {
            stubPresignAndSave();

            ScreenshotUploadUrlDto result = screenshotService.requestUpload(request);

            assertThat(result.screenshotId()).isEqualTo(SCREENSHOT_ID);
            assertThat(result.s3ObjectKey()).isEqualTo(OBJECT_KEY);
            assertThat(result.uploadUrl()).isEqualTo("https://example.invalid/presigned-put");
            assertThat(result.httpMethod()).isEqualTo("PUT");
            assertThat(result.requiredHeaders()).containsEntry("content-type", "image/png");
            assertThat(result.expiresAt()).isEqualTo(Instant.parse("2026-01-01T00:15:00Z"));
            assertThat(result.uploadStatus()).isEqualTo(UploadStatus.PENDING);

            ArgumentCaptor<ScreenshotEntity> saved = ArgumentCaptor.forClass(ScreenshotEntity.class);
            verify(screenshotRepository).save(saved.capture());

            ScreenshotEntity entity = saved.getValue();
            assertThat(entity.getS3ObjectKey()).isEqualTo(OBJECT_KEY);
            assertThat(entity.getOriginalFilename()).isEqualTo("screenshot.png");
            assertThat(entity.getContentType()).isEqualTo("image/png");
            assertThat(entity.getFileSizeBytes()).isEqualTo(1024L);
            assertThat(entity.getUploadStatus()).isEqualTo(UploadStatus.PENDING);
        }

        @Test
        @DisplayName("copies the storage URI into the s3Url field")
        void shouldStoreStorageUri() {
            stubPresignAndSave();

            screenshotService.requestUpload(request);

            ArgumentCaptor<ScreenshotEntity> saved = ArgumentCaptor.forClass(ScreenshotEntity.class);
            verify(screenshotRepository).save(saved.capture());
            assertThat(saved.getValue().getS3Url()).isEqualTo(STORAGE_URI);
        }

        @Test
        @DisplayName("rejects a file size of zero or less")
        void shouldRejectNonPositiveFileSize() {
            ScreenshotUploadRequestDto empty = new ScreenshotUploadRequestDto("screenshot.png", "image/png", 0L);

            assertThatThrownBy(() -> screenshotService.requestUpload(empty))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("greater than zero");

            verifyNoInteractions(screenshotStorageService, screenshotRepository);
        }

        @Test
        @DisplayName("rejects files above the configured maximum")
        void shouldRejectFileSizeAboveMaximum() {
            ScreenshotUploadRequestDto tooLarge = new ScreenshotUploadRequestDto(
                    "screenshot.png", "image/png", MAX_UPLOAD_SIZE_BYTES + 1);

            assertThatThrownBy(() -> screenshotService.requestUpload(tooLarge))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds the maximum");

            verifyNoInteractions(screenshotStorageService, screenshotRepository);
        }
    }

    @Nested
    @DisplayName("completeUpload")
    class CompleteUpload {

        @BeforeEach
        void markPending() {
            screenshotEntity.setUploadStatus(UploadStatus.PENDING);
            screenshotEntity.setFileSizeBytes(1024L);
        }

        @Test
        @DisplayName("confirms the object in S3 and takes over the actual metadata")
        void shouldVerifyObjectAndStoreRealMetadata() {
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.of(screenshotEntity));
            when(screenshotStorageService.headObject(OBJECT_KEY))
                    .thenReturn(Optional.of(new S3ScreenshotStorageService.ObjectMetadata(2048L, "image/jpeg")));
            when(screenshotRepository.save(screenshotEntity)).thenReturn(screenshotEntity);
            when(screenshotMapper.toScreenshotDto(screenshotEntity)).thenReturn(screenshotDto);

            assertThat(screenshotService.completeUpload(SCREENSHOT_ID)).isEqualTo(screenshotDto);

            assertThat(screenshotEntity.getUploadStatus()).isEqualTo(UploadStatus.SUCCESS);
            assertThat(screenshotEntity.getFileSizeBytes())
                    .as("The size announced by the client is replaced by the real one")
                    .isEqualTo(2048L);
            assertThat(screenshotEntity.getContentType()).isEqualTo("image/jpeg");
        }

        @Test
        @DisplayName("publishes a ScreenshotReadyEvent after a successful upload")
        void shouldPublishScreenshotReadyEvent() {
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.of(screenshotEntity));
            when(screenshotStorageService.headObject(OBJECT_KEY))
                    .thenReturn(Optional.of(new S3ScreenshotStorageService.ObjectMetadata(2048L, "image/png")));
            when(screenshotRepository.save(screenshotEntity)).thenReturn(screenshotEntity);
            when(screenshotMapper.toScreenshotDto(screenshotEntity)).thenReturn(screenshotDto);

            screenshotService.completeUpload(SCREENSHOT_ID);

            ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
            verify(rabbitTemplate).convertAndSend(anyString(), anyString(), event.capture());

            assertThat(event.getValue()).isInstanceOf(ScreenshotReadyEvent.class);
            ScreenshotReadyEvent readyEvent = (ScreenshotReadyEvent) event.getValue();
            assertThat(readyEvent.getEvent()).isEqualTo("screenshot.ready");
            assertThat(readyEvent.getScreenshotId()).isEqualTo(SCREENSHOT_ID);
            assertThat(readyEvent.getS3Url()).isEqualTo(STORAGE_URI);
        }

        @Test
        @DisplayName("is idempotent and sends no second event for an already confirmed upload")
        void shouldBeIdempotent() {
            screenshotEntity.setUploadStatus(UploadStatus.SUCCESS);
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.of(screenshotEntity));
            when(screenshotMapper.toScreenshotDto(screenshotEntity)).thenReturn(screenshotDto);

            assertThat(screenshotService.completeUpload(SCREENSHOT_ID)).isEqualTo(screenshotDto);

            verifyNoInteractions(screenshotStorageService, rabbitTemplate);
            verify(screenshotRepository, never()).save(any());
        }

        @Test
        @DisplayName("marks the screenshot FAILED when the object is not in S3")
        void shouldMarkAsFailedWhenObjectIsMissing() {
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.of(screenshotEntity));
            when(screenshotStorageService.headObject(OBJECT_KEY)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> screenshotService.completeUpload(SCREENSHOT_ID))
                    .isInstanceOf(ScreenshotUploadIncompleteException.class);

            assertThat(screenshotEntity.getUploadStatus()).isEqualTo(UploadStatus.FAILED);
            verify(screenshotRepository).save(screenshotEntity);
            verifyNoInteractions(rabbitTemplate);
        }

        @Test
        @DisplayName("throws ScreenshotNotFoundException when the screenshot does not exist")
        void shouldThrowWhenNotFound() {
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> screenshotService.completeUpload(SCREENSHOT_ID))
                    .isInstanceOf(ScreenshotNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getDownloadUrl")
    class GetDownloadUrl {

        @Test
        @DisplayName("returns a presigned download URL")
        void shouldReturnPresignedDownloadUrl() {
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.of(screenshotEntity));
            when(screenshotStorageService.presignDownload(OBJECT_KEY, "screenshot.png", "image/png"))
                    .thenReturn(new S3ScreenshotStorageService.PresignedDownload(
                            "https://example.invalid/presigned-get", "screenshot.png", "image/png",
                            Instant.parse("2026-01-01T00:15:00Z")));

            ScreenshotDownloadUrlDto result = screenshotService.getDownloadUrl(SCREENSHOT_ID);

            assertThat(result.screenshotId()).isEqualTo(SCREENSHOT_ID);
            assertThat(result.downloadUrl()).isEqualTo("https://example.invalid/presigned-get");
            assertThat(result.originalFilename()).isEqualTo("screenshot.png");
            assertThat(result.contentType()).isEqualTo("image/png");
            assertThat(result.expiresAt()).isEqualTo(Instant.parse("2026-01-01T00:15:00Z"));
        }

        @Test
        @DisplayName("throws ScreenshotNotFoundException when the screenshot does not exist")
        void shouldThrowWhenNotFound() {
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> screenshotService.getDownloadUrl(SCREENSHOT_ID))
                    .isInstanceOf(ScreenshotNotFoundException.class);
        }

        @Test
        @DisplayName("throws ScreenshotUploadIncompleteException while the upload is unconfirmed")
        void shouldThrowWhenUploadIsPending() {
            screenshotEntity.setUploadStatus(UploadStatus.PENDING);
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.of(screenshotEntity));

            assertThatThrownBy(() -> screenshotService.getDownloadUrl(SCREENSHOT_ID))
                    .isInstanceOf(ScreenshotUploadIncompleteException.class);

            verifyNoInteractions(screenshotStorageService);
        }

        @Test
        @DisplayName("throws ScreenshotUploadIncompleteException when no object key is set")
        void shouldThrowWhenObjectKeyMissing() {
            screenshotEntity.setS3ObjectKey("  ");
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.of(screenshotEntity));

            assertThatThrownBy(() -> screenshotService.getDownloadUrl(SCREENSHOT_ID))
                    .isInstanceOf(ScreenshotUploadIncompleteException.class);

            verifyNoInteractions(screenshotStorageService);
        }
    }

    @Nested
    @DisplayName("updateScreenshot")
    class UpdateScreenshot {

        @Test
        @DisplayName("updates an existing screenshot")
        void shouldUpdateScreenshot() {
            ScreenshotUpdateDto dto = TestDataFactory.screenshotUpdateDto(OBJECT_KEY);

            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.of(screenshotEntity));
            when(screenshotMapper.partialUpdateScreenshotEntity(dto, screenshotEntity))
                    .thenReturn(screenshotEntity);
            when(screenshotRepository.save(screenshotEntity)).thenReturn(screenshotEntity);
            when(screenshotMapper.toScreenshotDto(screenshotEntity)).thenReturn(screenshotDto);

            assertThat(screenshotService.updateScreenshot(SCREENSHOT_ID, dto)).isEqualTo(screenshotDto);
        }

        @Test
        @DisplayName("returns null when the screenshot does not exist")
        void shouldReturnNullWhenNotFound() {
            ScreenshotUpdateDto dto = TestDataFactory.screenshotUpdateDto(OBJECT_KEY);
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.empty());

            assertThat(screenshotService.updateScreenshot(SCREENSHOT_ID, dto)).isNull();
            verify(screenshotRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteScreenshot")
    class DeleteScreenshot {

        @Test
        @DisplayName("deletes the S3 object and the metadata")
        void shouldDeleteObjectAndEntity() {
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.of(screenshotEntity));

            screenshotService.deleteScreenshot(SCREENSHOT_ID);

            verify(screenshotStorageService).delete(OBJECT_KEY);
            verify(screenshotRepository).delete(screenshotEntity);
        }

        @Test
        @DisplayName("deletes no S3 object when no object key is set")
        void shouldNotTouchStorageWithoutObjectKey() {
            screenshotEntity.setS3ObjectKey(null);
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.of(screenshotEntity));

            screenshotService.deleteScreenshot(SCREENSHOT_ID);

            verifyNoInteractions(screenshotStorageService);
            verify(screenshotRepository).delete(screenshotEntity);
        }

        @Test
        @DisplayName("does nothing when the screenshot does not exist")
        void shouldDoNothingWhenNotFound() {
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.empty());

            screenshotService.deleteScreenshot(SCREENSHOT_ID);

            verifyNoInteractions(screenshotStorageService);
            verify(screenshotRepository, never()).delete(any(ScreenshotEntity.class));
        }
    }

    @Nested
    @DisplayName("getScreenshot")
    class ReadOperations {

        @Test
        @DisplayName("returns the metadata for the id")
        void shouldReturnScreenshot() {
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.of(screenshotEntity));
            when(screenshotMapper.toScreenshotDto(screenshotEntity)).thenReturn(screenshotDto);

            assertThat(screenshotService.getScreenshot(SCREENSHOT_ID)).isEqualTo(screenshotDto);
        }

        @Test
        @DisplayName("returns null when the metadata does not exist")
        void shouldReturnNullWhenNotFound() {
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.empty());

            assertThat(screenshotService.getScreenshot(SCREENSHOT_ID)).isNull();
        }
    }

    @Nested
    @DisplayName("getScreenshots and countScreenshots")
    class ListOperations {

        @Test
        @DisplayName("returns a paginated list")
        void shouldReturnPagedScreenshots() {
            Pageable pageable = PageRequest.of(0, 10);
            when(screenshotRepository.findAll(pageable))
                    .thenReturn(new PageImpl<>(List.of(screenshotEntity)));
            when(screenshotMapper.toScreenshotDto(screenshotEntity)).thenReturn(screenshotDto);

            Page<ScreenshotDto> result = screenshotService.getScreenshots(pageable);

            assertThat(result.getContent()).containsExactly(screenshotDto);
        }

        @Test
        @DisplayName("returns the total number of screenshots")
        void shouldCountScreenshots() {
            when(screenshotRepository.count()).thenReturn(4L);

            assertThat(screenshotService.countScreenshots()).isEqualTo(4L);
        }
    }
}

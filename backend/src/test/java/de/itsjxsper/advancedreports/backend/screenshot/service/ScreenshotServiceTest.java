package de.itsjxsper.advancedreports.backend.screenshot.service;

import de.itsjxsper.advancedreports.backend.screenshot.data.entity.ScreenshotEntity;
import de.itsjxsper.advancedreports.backend.screenshot.data.repository.ScreenshotRepository;
import de.itsjxsper.advancedreports.backend.screenshot.exceptions.ScreenshotNotFoundException;
import de.itsjxsper.advancedreports.backend.screenshot.mapper.ScreenshotMapper;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.enums.screenshot.UploadStatus;
import de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotDownloadDto;
import de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotDto;
import de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotUpdateDto;
import org.junit.jupiter.api.*;
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
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScreenshotService")
class ScreenshotServiceTest {

    private static final Long SCREENSHOT_ID = 9L;
    private static final String OBJECT_KEY = "screenshots/2026-01-01/abc-screenshot.png";

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
        screenshotEntity = TestDataFactory.screenshot(OBJECT_KEY);
        screenshotEntity.setId(SCREENSHOT_ID);
        screenshotDto = new ScreenshotDto(SCREENSHOT_ID, "https://example.invalid/" + OBJECT_KEY,
                OBJECT_KEY, "screenshot.png", "image/png", 1024L, UploadStatus.SUCCESS);
    }

    @Nested
    @DisplayName("createScreenshot")
    class CreateScreenshot {

        @Test
        @DisplayName("legt Metadaten an und übernimmt den mitgeschickten Upload-Status")
        void shouldCreateScreenshotWithGivenStatus() {
            ScreenshotUpdateDto dto = TestDataFactory.screenshotUpdateDto(OBJECT_KEY);

            when(screenshotMapper.partialUpdateScreenshotEntity(
                    org.mockito.ArgumentMatchers.eq(dto), any(ScreenshotEntity.class)))
                    .thenReturn(screenshotEntity);
            when(screenshotRepository.save(screenshotEntity)).thenReturn(screenshotEntity);
            when(screenshotMapper.toScreenshotDto(screenshotEntity)).thenReturn(screenshotDto);

            assertThat(screenshotService.createScreenshot(dto)).isEqualTo(screenshotDto);
            assertThat(screenshotEntity.getUploadStatus()).isEqualTo(UploadStatus.SUCCESS);
        }

        @Test
        @DisplayName("setzt den Upload-Status auf SUCCESS, wenn keiner mitgeschickt wurde")
        void shouldDefaultUploadStatusToSuccess() {
            ScreenshotUpdateDto dto = new ScreenshotUpdateDto(null, OBJECT_KEY, "screenshot.png",
                    "image/png", 1024L, null);
            screenshotEntity.setUploadStatus(null);

            when(screenshotMapper.partialUpdateScreenshotEntity(
                    org.mockito.ArgumentMatchers.eq(dto), any(ScreenshotEntity.class)))
                    .thenReturn(screenshotEntity);
            when(screenshotRepository.save(screenshotEntity)).thenReturn(screenshotEntity);
            when(screenshotMapper.toScreenshotDto(screenshotEntity)).thenReturn(screenshotDto);

            screenshotService.createScreenshot(dto);

            assertThat(screenshotEntity.getUploadStatus()).isEqualTo(UploadStatus.SUCCESS);
        }
    }

    @Nested
    @DisplayName("uploadScreenshot")
    class UploadScreenshot {

        private final MockMultipartFile file =
                new MockMultipartFile("file", "screenshot.png", "image/png", "png-bytes".getBytes());

        private S3ScreenshotStorageService.StoredScreenshot stored() {
            return new S3ScreenshotStorageService.StoredScreenshot(
                    OBJECT_KEY, "https://example.invalid/" + OBJECT_KEY, "screenshot.png", "image/png", 1024L);
        }

        @Test
        @DisplayName("lädt die Datei hoch und überträgt die Storage-Metadaten in die Entity")
        void shouldUploadAndPersistMetadata() {
            when(screenshotStorageService.upload(file)).thenReturn(stored());
            when(screenshotRepository.save(any(ScreenshotEntity.class))).thenReturn(screenshotEntity);
            when(screenshotMapper.toScreenshotDto(screenshotEntity)).thenReturn(screenshotDto);

            assertThat(screenshotService.uploadScreenshot(file)).isEqualTo(screenshotDto);

            ArgumentCaptor<ScreenshotEntity> saved = ArgumentCaptor.forClass(ScreenshotEntity.class);
            verify(screenshotRepository).save(saved.capture());

            ScreenshotEntity entity = saved.getValue();
            assertThat(entity.getS3ObjectKey()).isEqualTo(OBJECT_KEY);
            assertThat(entity.getOriginalFilename()).isEqualTo("screenshot.png");
            assertThat(entity.getContentType()).isEqualTo("image/png");
            assertThat(entity.getFileSizeBytes()).isEqualTo(1024L);
            assertThat(entity.getUploadStatus()).isEqualTo(UploadStatus.SUCCESS);
        }

        @Test
        @Disabled("BUG: ScreenshotService#applyStoredScreenshot "
                + "(screenshot/service/ScreenshotService.java:314) uebertraegt objectKey, "
                + "originalFilename, contentType und fileSizeBytes, laesst aber storageUri liegen. "
                + "s3Url bleibt nach einem Upload daher null, obwohl das Feld Teil von ScreenshotDto "
                + "ist und ScreenshotReadyEvent es transportieren soll.")
        @DisplayName("übernimmt die Storage-URI in das Feld s3Url")
        void shouldStoreStorageUri() {
            when(screenshotStorageService.upload(file)).thenReturn(stored());
            when(screenshotRepository.save(any(ScreenshotEntity.class))).thenReturn(screenshotEntity);
            when(screenshotMapper.toScreenshotDto(screenshotEntity)).thenReturn(screenshotDto);

            screenshotService.uploadScreenshot(file);

            ArgumentCaptor<ScreenshotEntity> saved = ArgumentCaptor.forClass(ScreenshotEntity.class);
            verify(screenshotRepository).save(saved.capture());
            assertThat(saved.getValue().getS3Url()).isEqualTo("https://example.invalid/" + OBJECT_KEY);
        }

        @Test
        @Disabled("BUG: ScreenshotService laesst sich ein RabbitTemplate injizieren, veroeffentlicht "
                + "aber nie ein ScreenshotReadyEvent. Das Event ist in messaging/events definiert und "
                + "im README als 'screenshot.ready' fuer den Discord-Bot dokumentiert, wird jedoch "
                + "von keiner Stelle im Backend gesendet.")
        @DisplayName("veröffentlicht ein ScreenshotReadyEvent nach erfolgreichem Upload")
        void shouldPublishScreenshotReadyEvent() {
            when(screenshotStorageService.upload(file)).thenReturn(stored());
            when(screenshotRepository.save(any(ScreenshotEntity.class))).thenReturn(screenshotEntity);
            when(screenshotMapper.toScreenshotDto(screenshotEntity)).thenReturn(screenshotDto);

            screenshotService.uploadScreenshot(file);

            verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        }
    }

    @Nested
    @DisplayName("updateScreenshot")
    class UpdateScreenshot {

        @Test
        @DisplayName("aktualisiert einen bestehenden Screenshot")
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
        @DisplayName("wirft ScreenshotNotFoundException, wenn der Screenshot nicht existiert")
        void shouldThrowWhenNotFound() {
            ScreenshotUpdateDto dto = TestDataFactory.screenshotUpdateDto(OBJECT_KEY);
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> screenshotService.updateScreenshot(SCREENSHOT_ID, dto))
                    .isInstanceOf(ScreenshotNotFoundException.class);
            verify(screenshotRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteScreenshot")
    class DeleteScreenshot {

        @Test
        @DisplayName("löscht das S3-Objekt und die Metadaten")
        void shouldDeleteObjectAndEntity() {
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.of(screenshotEntity));

            screenshotService.deleteScreenshot(SCREENSHOT_ID);

            verify(screenshotStorageService).delete(OBJECT_KEY);
            verify(screenshotRepository).delete(screenshotEntity);
        }

        @Test
        @DisplayName("löscht kein S3-Objekt, wenn kein Object-Key gesetzt ist")
        void shouldNotTouchStorageWithoutObjectKey() {
            screenshotEntity.setS3ObjectKey(null);
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.of(screenshotEntity));

            screenshotService.deleteScreenshot(SCREENSHOT_ID);

            verifyNoInteractions(screenshotStorageService);
            verify(screenshotRepository).delete(screenshotEntity);
        }

        @Test
        @DisplayName("wirft ScreenshotNotFoundException, wenn der Screenshot nicht existiert")
        void shouldThrowWhenDeletingUnknownScreenshot() {
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> screenshotService.deleteScreenshot(SCREENSHOT_ID))
                    .isInstanceOf(ScreenshotNotFoundException.class);

            verifyNoInteractions(screenshotStorageService);
            verify(screenshotRepository, never()).delete(any(ScreenshotEntity.class));
        }
    }

    @Nested
    @DisplayName("getScreenshot und downloadScreenshot")
    class ReadOperations {

        @Test
        @DisplayName("liefert die Metadaten zur id zurück")
        void shouldReturnScreenshot() {
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.of(screenshotEntity));
            when(screenshotMapper.toScreenshotDto(screenshotEntity)).thenReturn(screenshotDto);

            assertThat(screenshotService.getScreenshot(SCREENSHOT_ID)).isEqualTo(screenshotDto);
        }

        @Test
        @DisplayName("wirft ScreenshotNotFoundException, wenn die Metadaten nicht existieren")
        void shouldThrowWhenMetadataMissing() {
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> screenshotService.getScreenshot(SCREENSHOT_ID))
                    .isInstanceOf(ScreenshotNotFoundException.class);
        }

        @Test
        @DisplayName("lädt den Dateiinhalt aus dem Storage")
        void shouldDownloadContent() {
            byte[] content = "png-bytes".getBytes();
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.of(screenshotEntity));
            when(screenshotStorageService.download(OBJECT_KEY))
                    .thenReturn(new ScreenshotDownloadDto("screenshot.png", "image/png", content));

            assertThat(screenshotService.downloadScreenshot(SCREENSHOT_ID)).isEqualTo(content);
        }

        @Test
        @DisplayName("wirft ScreenshotNotFoundException, wenn der Screenshot nicht existiert")
        void shouldThrowForDownloadWhenNotFound() {
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> screenshotService.downloadScreenshot(SCREENSHOT_ID))
                    .isInstanceOf(ScreenshotNotFoundException.class);
        }

        @Test
        @DisplayName("wirft IllegalStateException, wenn der Screenshot keinen Object-Key hat")
        void shouldThrowWhenObjectKeyMissing() {
            screenshotEntity.setS3ObjectKey("  ");
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.of(screenshotEntity));

            assertThatThrownBy(() -> screenshotService.downloadScreenshot(SCREENSHOT_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no S3 object key");
        }
    }

    @Nested
    @DisplayName("getScreenshots und countScreenshots")
    class ListOperations {

        @Test
        @DisplayName("liefert eine paginierte Liste zurück")
        void shouldReturnPagedScreenshots() {
            Pageable pageable = PageRequest.of(0, 10);
            when(screenshotRepository.findAll(pageable))
                    .thenReturn(new PageImpl<>(List.of(screenshotEntity)));
            when(screenshotMapper.toScreenshotDto(screenshotEntity)).thenReturn(screenshotDto);

            Page<ScreenshotDto> result = screenshotService.getScreenshots(pageable);

            assertThat(result.getContent()).containsExactly(screenshotDto);
        }

        @Test
        @DisplayName("gibt die Gesamtanzahl der Screenshots zurück")
        void shouldCountScreenshots() {
            when(screenshotRepository.count()).thenReturn(4L);

            assertThat(screenshotService.countScreenshots()).isEqualTo(4L);
        }
    }
}

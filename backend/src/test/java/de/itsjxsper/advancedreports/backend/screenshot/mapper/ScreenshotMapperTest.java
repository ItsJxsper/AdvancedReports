package de.itsjxsper.advancedreports.backend.screenshot.mapper;

import de.itsjxsper.advancedreports.backend.screenshot.data.entity.ScreenshotEntity;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.enums.screenshot.UploadStatus;
import de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotDto;
import de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotUpdateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScreenshotMapper")
class ScreenshotMapperTest {

    private static final String OBJECT_KEY = "screenshots/2026-01-01/abc-screenshot.png";

    private final ScreenshotMapper mapper = new ScreenshotMapperImpl();

    private ScreenshotEntity entity;

    @BeforeEach
    void setUp() {
        entity = TestDataFactory.screenshot(OBJECT_KEY);
        entity.setId(9L);
        entity.setS3Url("https://example.invalid/" + OBJECT_KEY);
    }

    @Nested
    @DisplayName("toScreenshotDto")
    class ToDto {

        @Test
        @DisplayName("überträgt alle Metadatenfelder")
        void shouldMapAllFields() {
            ScreenshotDto dto = mapper.toScreenshotDto(entity);

            assertThat(dto.id()).isEqualTo(9L);
            assertThat(dto.s3Url()).isEqualTo("https://example.invalid/" + OBJECT_KEY);
            assertThat(dto.s3ObjectKey()).isEqualTo(OBJECT_KEY);
            assertThat(dto.originalFilename()).isEqualTo("screenshot.png");
            assertThat(dto.contentType()).isEqualTo("image/png");
            assertThat(dto.fileSizeBytes()).isEqualTo(1024L);
            assertThat(dto.uploadStatus()).isEqualTo(UploadStatus.SUCCESS);
        }

        @Test
        @DisplayName("liefert null für eine null-Entity")
        void shouldMapNullToNull() {
            assertThat(mapper.toScreenshotDto(null)).isNull();
        }
    }

    @Nested
    @DisplayName("toScreenshotEntity")
    class ToEntity {

        @Test
        @DisplayName("baut eine Entity aus einem ScreenshotUpdateDto")
        void shouldMapFromUpdateDto() {
            ScreenshotUpdateDto dto = TestDataFactory.screenshotUpdateDto(OBJECT_KEY);

            ScreenshotEntity result = mapper.toScreenshotEntity(dto);

            assertThat(result.getS3ObjectKey()).isEqualTo(OBJECT_KEY);
            assertThat(result.getOriginalFilename()).isEqualTo("screenshot.png");
            assertThat(result.getContentType()).isEqualTo("image/png");
            assertThat(result.getFileSizeBytes()).isEqualTo(1024L);
            assertThat(result.getUploadStatus()).isEqualTo(UploadStatus.SUCCESS);
        }

        @Test
        @DisplayName("baut eine Entity aus einem vollständigen ScreenshotDto")
        void shouldMapFromDto() {
            ScreenshotDto dto = new ScreenshotDto(9L, "https://example.invalid/x", OBJECT_KEY,
                    "screenshot.png", "image/png", 2048L, UploadStatus.PENDING);

            ScreenshotEntity result = mapper.toScreenshotEntity(dto);

            assertThat(result.getId()).isEqualTo(9L);
            assertThat(result.getFileSizeBytes()).isEqualTo(2048L);
            assertThat(result.getUploadStatus()).isEqualTo(UploadStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("partialUpdateScreenshotEntity")
    class PartialUpdate {

        @Test
        @DisplayName("überschreibt nur die gesetzten Felder eines ScreenshotUpdateDto")
        void shouldIgnoreNullValuesFromUpdateDto() {
            ScreenshotUpdateDto dto = new ScreenshotUpdateDto(null, null, null, null,
                    4096L, UploadStatus.FAILED);

            ScreenshotEntity result = mapper.partialUpdateScreenshotEntity(dto, entity);

            assertThat(result.getUploadStatus()).isEqualTo(UploadStatus.FAILED);
            assertThat(result.getFileSizeBytes()).isEqualTo(4096L);
            // Untouched, because the DTO carried null.
            assertThat(result.getS3ObjectKey()).isEqualTo(OBJECT_KEY);
            assertThat(result.getOriginalFilename()).isEqualTo("screenshot.png");
            assertThat(result.getContentType()).isEqualTo("image/png");
        }

        @Test
        @DisplayName("überschreibt nur die gesetzten Felder eines ScreenshotDto")
        void shouldIgnoreNullValuesFromDto() {
            ScreenshotDto dto = new ScreenshotDto(null, null, null, "neu.png", null, null, null);

            ScreenshotEntity result = mapper.partialUpdateScreenshotEntity(dto, entity);

            assertThat(result.getOriginalFilename()).isEqualTo("neu.png");
            assertThat(result.getUploadStatus()).isEqualTo(UploadStatus.SUCCESS);
            assertThat(result.getS3ObjectKey()).isEqualTo(OBJECT_KEY);
        }
    }
}

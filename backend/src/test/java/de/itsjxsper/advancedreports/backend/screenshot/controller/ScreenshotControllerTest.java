package de.itsjxsper.advancedreports.backend.screenshot.controller;

import de.itsjxsper.advancedreports.backend.screenshot.exceptions.ScreenshotStorageException;
import de.itsjxsper.advancedreports.backend.screenshot.exceptions.ScreenshotNotFoundException;
import de.itsjxsper.advancedreports.backend.screenshot.service.ScreenshotService;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.enums.screenshot.UploadStatus;
import de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScreenshotController.class)
@ActiveProfiles("test")
@DisplayName("ScreenshotController")
class ScreenshotControllerTest {

    private static final String OBJECT_KEY = "screenshots/2026-01-01/abc-screenshot.png";
    private final ScreenshotDto screenshotDto = new ScreenshotDto(9L,
            "https://example.invalid/" + OBJECT_KEY, OBJECT_KEY, "screenshot.png",
            "image/png", 1024L, UploadStatus.SUCCESS);
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private ScreenshotService screenshotService;

    @Nested
    @DisplayName("GET /api/v1/screenshots")
    class GetAllScreenshots {

        @Test
        @DisplayName("liefert 200 mit einer paginierten Liste")
        void shouldReturnPagedScreenshots() throws Exception {
            when(screenshotService.getScreenshots(any()))
                    .thenReturn(new PageImpl<>(List.of(screenshotDto)));

            mockMvc.perform(get("/api/v1/screenshots"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(9))
                    .andExpect(jsonPath("$.content[0].uploadStatus").value("SUCCESS"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/screenshots")
    class CreateScreenshot {

        @Test
        @DisplayName("liefert 201 mit den angelegten Metadaten")
        void shouldCreateScreenshot() throws Exception {
            when(screenshotService.createScreenshot(any())).thenReturn(screenshotDto);

            mockMvc.perform(post("/api/v1/screenshots")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    TestDataFactory.screenshotUpdateDto(OBJECT_KEY))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.s3ObjectKey").value(OBJECT_KEY));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/screenshots/upload")
    class UploadScreenshot {

        @Test
        @DisplayName("liefert 201 mit den Metadaten des hochgeladenen Bildes")
        void shouldUploadScreenshot() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "screenshot.png", "image/png", "png-bytes".getBytes());
            when(screenshotService.uploadScreenshot(any())).thenReturn(screenshotDto);

            mockMvc.perform(multipart("/api/v1/screenshots/upload").file(file))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.originalFilename").value("screenshot.png"))
                    .andExpect(jsonPath("$.contentType").value("image/png"));
        }

        @Test
        @DisplayName("liefert 503 SCREENSHOT_STORAGE_ERROR, wenn S3 nicht konfiguriert ist")
        void shouldReturnServiceUnavailableOnStorageError() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "screenshot.png", "image/png", "png-bytes".getBytes());
            when(screenshotService.uploadScreenshot(any()))
                    .thenThrow(new ScreenshotStorageException("AWS S3 is not configured"));

            mockMvc.perform(multipart("/api/v1/screenshots/upload").file(file))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("SCREENSHOT_STORAGE_ERROR"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/screenshots/{screenshotId}")
    class GetScreenshot {

        @Test
        @DisplayName("liefert 200 mit den Metadaten")
        void shouldReturnScreenshot() throws Exception {
            when(screenshotService.getScreenshot(9L)).thenReturn(screenshotDto);

            mockMvc.perform(get("/api/v1/screenshots/9"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fileSizeBytes").value(1024));
        }

        @Test
        @DisplayName("liefert 404 SCREENSHOT_NOT_FOUND für eine unbekannte ID")
        void shouldReturnNotFound() throws Exception {
            when(screenshotService.getScreenshot(99L)).thenThrow(new ScreenshotNotFoundException(99L));

            mockMvc.perform(get("/api/v1/screenshots/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SCREENSHOT_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Screenshot with ID 99 was not found"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/screenshots/{screenshotId}/download")
    class DownloadScreenshot {

        @Test
        @DisplayName("liefert 200 mit Bytes, Content-Type und Content-Disposition")
        void shouldDownloadScreenshot() throws Exception {
            byte[] bytes = "png-bytes".getBytes();
            when(screenshotService.getScreenshot(9L)).thenReturn(screenshotDto);
            when(screenshotService.downloadScreenshot(9L)).thenReturn(bytes);

            mockMvc.perform(get("/api/v1/screenshots/9/download"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                            org.hamcrest.Matchers.containsString("screenshot.png")))
                    .andExpect(content().bytes(bytes));
        }

        @Test
        @DisplayName("fällt auf application/octet-stream zurück, wenn kein Content-Type bekannt ist")
        void shouldFallBackToOctetStream() throws Exception {
            ScreenshotDto withoutContentType = new ScreenshotDto(9L, null, OBJECT_KEY,
                    "screenshot.png", null, 1024L, UploadStatus.SUCCESS);
            when(screenshotService.getScreenshot(9L)).thenReturn(withoutContentType);
            when(screenshotService.downloadScreenshot(9L)).thenReturn("x".getBytes());

            mockMvc.perform(get("/api/v1/screenshots/9/download"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/octet-stream"));
        }

        @Test
        @DisplayName("liefert 404 SCREENSHOT_NOT_FOUND, wenn keine Metadaten existieren")
        void shouldReturnNotFoundWithoutMetadata() throws Exception {
            when(screenshotService.getScreenshot(99L)).thenThrow(new ScreenshotNotFoundException(99L));

            mockMvc.perform(get("/api/v1/screenshots/99/download"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SCREENSHOT_NOT_FOUND"));
        }

    }

    @Nested
    @DisplayName("PATCH /api/v1/screenshots/{screenshotId}")
    class UpdateScreenshot {

        @Test
        @DisplayName("liefert 200 mit den aktualisierten Metadaten")
        void shouldUpdateScreenshot() throws Exception {
            when(screenshotService.updateScreenshot(eq(9L), any())).thenReturn(screenshotDto);

            mockMvc.perform(patch("/api/v1/screenshots/9")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    TestDataFactory.screenshotUpdateDto(OBJECT_KEY))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(9));
        }

        @Test
        @DisplayName("liefert 404 SCREENSHOT_NOT_FOUND für eine unbekannte ID")
        void shouldReturnNotFoundOnUpdate() throws Exception {
            when(screenshotService.updateScreenshot(eq(99L), any())).thenThrow(new ScreenshotNotFoundException(99L));

            mockMvc.perform(patch("/api/v1/screenshots/99")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    TestDataFactory.screenshotUpdateDto(OBJECT_KEY))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SCREENSHOT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("DELETE und count")
    class DeleteAndCount {

        @Test
        @DisplayName("DELETE liefert 204 ohne Body")
        void shouldDeleteScreenshot() throws Exception {
            mockMvc.perform(delete("/api/v1/screenshots/9"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(screenshotService).deleteScreenshot(9L);
        }

        @Test
        @DisplayName("GET /count liefert 200 mit der Gesamtanzahl")
        void shouldReturnCount() throws Exception {
            when(screenshotService.countScreenshots()).thenReturn(4L);

            mockMvc.perform(get("/api/v1/screenshots/count"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("4"));
        }
    }
}

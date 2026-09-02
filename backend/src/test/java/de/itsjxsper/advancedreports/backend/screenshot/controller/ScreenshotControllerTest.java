package de.itsjxsper.advancedreports.backend.screenshot.controller;

import de.itsjxsper.advancedreports.backend.screenshot.exceptions.ScreenshotNotFoundException;
import de.itsjxsper.advancedreports.backend.screenshot.exceptions.ScreenshotStorageException;
import de.itsjxsper.advancedreports.backend.screenshot.exceptions.ScreenshotUploadIncompleteException;
import de.itsjxsper.advancedreports.backend.screenshot.service.ScreenshotService;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.enums.screenshot.UploadStatus;
import de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotDownloadUrlDto;
import de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotDto;
import de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotUploadRequestDto;
import de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotUploadUrlDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
    private static final Instant EXPIRES_AT = Instant.parse("2026-01-01T00:15:00Z");
    private final ScreenshotDto screenshotDto = new ScreenshotDto(9L,
            "https://example.invalid/" + OBJECT_KEY, OBJECT_KEY, "screenshot.png",
            "image/png", 1024L, UploadStatus.SUCCESS);
    private final ScreenshotDownloadUrlDto downloadUrlDto = new ScreenshotDownloadUrlDto(9L,
            "https://example.invalid/presigned-get", "screenshot.png", "image/png", EXPIRES_AT);
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
        @DisplayName("returns 200 with a paginated list")
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
        @DisplayName("returns 201 with the created metadata")
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
    @DisplayName("POST /api/v1/screenshots/upload-url")
    class RequestUploadUrl {

        private final ScreenshotUploadRequestDto request =
                new ScreenshotUploadRequestDto("screenshot.png", "image/png", 1024L);

        @Test
        @DisplayName("returns 201 with the presigned upload URL and status PENDING")
        void shouldReturnUploadUrl() throws Exception {
            when(screenshotService.requestUpload(any())).thenReturn(new ScreenshotUploadUrlDto(
                    9L, OBJECT_KEY, "https://example.invalid/presigned-put", "PUT",
                    Map.of("content-type", "image/png"), EXPIRES_AT, UploadStatus.PENDING));

            mockMvc.perform(post("/api/v1/screenshots/upload-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.screenshotId").value(9))
                    .andExpect(jsonPath("$.uploadUrl").value("https://example.invalid/presigned-put"))
                    .andExpect(jsonPath("$.httpMethod").value("PUT"))
                    .andExpect(jsonPath("$.requiredHeaders['content-type']").value("image/png"))
                    .andExpect(jsonPath("$.uploadStatus").value("PENDING"));
        }

        @Test
        @DisplayName("returns 400 ILLEGAL_ARGUMENT when the file is too large")
        void shouldReturnBadRequestForOversizedFile() throws Exception {
            when(screenshotService.requestUpload(any()))
                    .thenThrow(new IllegalArgumentException("Screenshot file size 99 exceeds the maximum of 10 bytes"));

            mockMvc.perform(post("/api/v1/screenshots/upload-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ILLEGAL_ARGUMENT"));
        }

        @Test
        @DisplayName("returns 503 SCREENSHOT_STORAGE_ERROR when S3 is not configured")
        void shouldReturnServiceUnavailableOnStorageError() throws Exception {
            when(screenshotService.requestUpload(any()))
                    .thenThrow(new ScreenshotStorageException("AWS S3 is not configured"));

            mockMvc.perform(post("/api/v1/screenshots/upload-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("SCREENSHOT_STORAGE_ERROR"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/screenshots/{screenshotId}/complete")
    class CompleteUpload {

        @Test
        @DisplayName("returns 200 with the confirmed metadata")
        void shouldCompleteUpload() throws Exception {
            when(screenshotService.completeUpload(9L)).thenReturn(screenshotDto);

            mockMvc.perform(post("/api/v1/screenshots/9/complete"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadStatus").value("SUCCESS"))
                    .andExpect(jsonPath("$.fileSizeBytes").value(1024));
        }

        @Test
        @DisplayName("returns 409 SCREENSHOT_UPLOAD_INCOMPLETE when the object is not in S3")
        void shouldReturnConflictWhenObjectIsMissing() throws Exception {
            when(screenshotService.completeUpload(9L))
                    .thenThrow(new ScreenshotUploadIncompleteException(9L));

            mockMvc.perform(post("/api/v1/screenshots/9/complete"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SCREENSHOT_UPLOAD_INCOMPLETE"))
                    .andExpect(jsonPath("$.message")
                            .value("Screenshot with ID 9 has not been uploaded to S3 yet"));
        }

        @Test
        @DisplayName("returns 404 SCREENSHOT_NOT_FOUND when the screenshot does not exist")
        void shouldReturnNotFound() throws Exception {
            when(screenshotService.completeUpload(99L))
                    .thenThrow(new ScreenshotNotFoundException(99L));

            mockMvc.perform(post("/api/v1/screenshots/99/complete"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SCREENSHOT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/screenshots/{screenshotId}")
    class GetScreenshot {

        @Test
        @DisplayName("returns 200 with the metadata")
        void shouldReturnScreenshot() throws Exception {
            when(screenshotService.getScreenshot(9L)).thenReturn(screenshotDto);

            mockMvc.perform(get("/api/v1/screenshots/9"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fileSizeBytes").value(1024));
        }

        @Test
        @DisplayName("returns 404 SCREENSHOT_NOT_FOUND when the service returns null")
        void shouldReturnNotFoundOnNull() throws Exception {
            when(screenshotService.getScreenshot(99L)).thenReturn(null);

            mockMvc.perform(get("/api/v1/screenshots/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SCREENSHOT_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Screenshot with ID 99 was not found"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/screenshots/{screenshotId}/download-url")
    class GetDownloadUrl {

        @Test
        @DisplayName("returns 200 with the presigned download URL")
        void shouldReturnDownloadUrl() throws Exception {
            when(screenshotService.getDownloadUrl(9L)).thenReturn(downloadUrlDto);

            mockMvc.perform(get("/api/v1/screenshots/9/download-url"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.screenshotId").value(9))
                    .andExpect(jsonPath("$.downloadUrl").value("https://example.invalid/presigned-get"))
                    .andExpect(jsonPath("$.originalFilename").value("screenshot.png"))
                    .andExpect(jsonPath("$.contentType").value("image/png"));
        }

        @Test
        @DisplayName("returns 409 SCREENSHOT_UPLOAD_INCOMPLETE while the upload is unconfirmed")
        void shouldReturnConflictWhilePending() throws Exception {
            when(screenshotService.getDownloadUrl(9L))
                    .thenThrow(new ScreenshotUploadIncompleteException(9L));

            mockMvc.perform(get("/api/v1/screenshots/9/download-url"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SCREENSHOT_UPLOAD_INCOMPLETE"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/screenshots/{screenshotId}/download")
    class DownloadScreenshot {

        @Test
        @DisplayName("redirects with 302 to the presigned S3 URL instead of proxying bytes")
        void shouldRedirectToPresignedUrl() throws Exception {
            when(screenshotService.getDownloadUrl(9L)).thenReturn(downloadUrlDto);

            mockMvc.perform(get("/api/v1/screenshots/9/download"))
                    .andExpect(status().isFound())
                    .andExpect(header().string(HttpHeaders.LOCATION, "https://example.invalid/presigned-get"))
                    .andExpect(content().string(""));
        }

        @Test
        @DisplayName("returns 404 SCREENSHOT_NOT_FOUND when no metadata exists")
        void shouldReturnNotFoundWithoutMetadata() throws Exception {
            when(screenshotService.getDownloadUrl(99L))
                    .thenThrow(new ScreenshotNotFoundException(99L));

            mockMvc.perform(get("/api/v1/screenshots/99/download"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SCREENSHOT_NOT_FOUND"));
        }

        @Test
        @DisplayName("returns 409 SCREENSHOT_UPLOAD_INCOMPLETE when the upload was never confirmed")
        void shouldReturnConflictWhenUploadIncomplete() throws Exception {
            when(screenshotService.getDownloadUrl(9L))
                    .thenThrow(new ScreenshotUploadIncompleteException(9L));

            mockMvc.perform(get("/api/v1/screenshots/9/download"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SCREENSHOT_UPLOAD_INCOMPLETE"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/screenshots/{screenshotId}")
    class UpdateScreenshot {

        @Test
        @DisplayName("returns 200 with the updated metadata")
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
        @DisplayName("returns 404 SCREENSHOT_NOT_FOUND when the service returns null")
        void shouldReturnNotFoundOnNull() throws Exception {
            when(screenshotService.updateScreenshot(eq(99L), any())).thenReturn(null);

            mockMvc.perform(patch("/api/v1/screenshots/99")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    TestDataFactory.screenshotUpdateDto(OBJECT_KEY))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SCREENSHOT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("DELETE and count")
    class DeleteAndCount {

        @Test
        @DisplayName("DELETE returns 204 with no body")
        void shouldDeleteScreenshot() throws Exception {
            mockMvc.perform(delete("/api/v1/screenshots/9"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(screenshotService).deleteScreenshot(9L);
        }

        @Test
        @DisplayName("GET /count returns 200 with the total count")
        void shouldReturnCount() throws Exception {
            when(screenshotService.countScreenshots()).thenReturn(4L);

            mockMvc.perform(get("/api/v1/screenshots/count"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("4"));
        }
    }
}

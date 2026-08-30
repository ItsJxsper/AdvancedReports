package de.itsjxsper.advancedreports.backend.screenshot.controller;

import de.itsjxsper.advancedreports.backend.ratelimit.annotation.RateLimited;
import de.itsjxsper.advancedreports.backend.screenshot.exceptions.ScreenshotNotFoundException;
import de.itsjxsper.advancedreports.backend.screenshot.service.ScreenshotService;
import de.itsjxsper.advancedreports.common.model.screenshot.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/api/v1/screenshots")
@RequiredArgsConstructor
@Tag(name = "Screenshot API", description = "Screenshot API")
public class ScreenshotController {

    private final ScreenshotService screenshotService;

    @GetMapping
    @RateLimited(serverUuid = false, discordUserId = true)
    @Operation(summary = "Get all screenshots", description = "Retrieve a paginated list of screenshots")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Page<ScreenshotDto>> getAllScreenshots(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "100")
            @RequestParam(defaultValue = "100") int size
    ) {
        log.debug("Getting all screenshots with page={} and size={}", page, size);
        return ResponseEntity.ok(this.screenshotService.getScreenshots(PageRequest.of(page, size)));
    }

    @PostMapping
    @RateLimited(serverUuid = false, discordUserId = true)
    @Operation(summary = "Create screenshot", description = "Create a new screenshot")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Screenshot created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ScreenshotDto> createScreenshot(@Valid @RequestBody ScreenshotUpdateDto screenshotUpdateDto) {
        log.debug("Creating screenshot with status={}", screenshotUpdateDto.uploadStatus());
        ScreenshotDto screenshotDto = this.screenshotService.createScreenshot(screenshotUpdateDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(screenshotDto);
    }

    @PostMapping("/upload-url")
    @RateLimited(serverUuid = false, discordUserId = true)
    @Operation(summary = "Request screenshot upload url",
            description = "Reserve screenshot metadata and return a presigned URL the file is uploaded to directly")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Upload url created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "503", description = "Screenshot storage is unavailable"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ScreenshotUploadUrlDto> requestUploadUrl(
            @Valid @RequestBody ScreenshotUploadRequestDto uploadRequestDto
    ) {
        log.debug("Requesting screenshot upload url for file name={}", uploadRequestDto.originalFilename());
        ScreenshotUploadUrlDto uploadUrlDto = this.screenshotService.requestUpload(uploadRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(uploadUrlDto);
    }

    @PostMapping("/{screenshotId}/complete")
    @RateLimited(serverUuid = false, discordUserId = true)
    @Operation(summary = "Complete screenshot upload",
            description = "Verify the uploaded object in S3 and mark the screenshot as uploaded")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Screenshot upload completed successfully"),
            @ApiResponse(responseCode = "404", description = "Screenshot not found"),
            @ApiResponse(responseCode = "409", description = "Screenshot was not uploaded"),
            @ApiResponse(responseCode = "503", description = "Screenshot storage is unavailable"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ScreenshotDto> completeUpload(@PathVariable Long screenshotId) {
        log.debug("Completing screenshot upload with id={}", screenshotId);
        return ResponseEntity.ok(this.screenshotService.completeUpload(screenshotId));
    }

    @GetMapping("/{screenshotId}")
    @RateLimited(serverUuid = false, discordUserId = true)
    @Operation(summary = "Get screenshot by id", description = "Retrieve a screenshot by id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Screenshot found successfully"),
            @ApiResponse(responseCode = "404", description = "Screenshot not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ScreenshotDto> getScreenshot(@PathVariable Long screenshotId) {
        log.debug("Getting screenshot with id={}", screenshotId);
        ScreenshotDto screenshotDto = this.screenshotService.getScreenshot(screenshotId);
        if (screenshotDto == null) {
            throw new ScreenshotNotFoundException(screenshotId);
        }
        return ResponseEntity.ok(screenshotDto);
    }

    @GetMapping("/{screenshotId}/download-url")
    @RateLimited(serverUuid = false, discordUserId = true)
    @Operation(summary = "Get screenshot download url",
            description = "Return a presigned URL the screenshot is downloaded from directly")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Download url created successfully"),
            @ApiResponse(responseCode = "404", description = "Screenshot not found"),
            @ApiResponse(responseCode = "409", description = "Screenshot was not uploaded"),
            @ApiResponse(responseCode = "503", description = "Screenshot storage is unavailable"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ScreenshotDownloadUrlDto> getDownloadUrl(@PathVariable Long screenshotId) {
        log.debug("Getting screenshot download url with id={}", screenshotId);
        return ResponseEntity.ok(this.screenshotService.getDownloadUrl(screenshotId));
    }

    @GetMapping("/{screenshotId}/download")
    @RateLimited(serverUuid = false, discordUserId = true)
    @Operation(summary = "Download screenshot",
            description = "Redirect to a presigned URL the screenshot is downloaded from directly")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "302", description = "Redirect to the presigned download url"),
            @ApiResponse(responseCode = "404", description = "Screenshot not found"),
            @ApiResponse(responseCode = "409", description = "Screenshot was not uploaded"),
            @ApiResponse(responseCode = "503", description = "Screenshot storage is unavailable"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> downloadScreenshot(@PathVariable Long screenshotId) {
        log.debug("Downloading screenshot with id={}", screenshotId);
        ScreenshotDownloadUrlDto downloadUrlDto = this.screenshotService.getDownloadUrl(screenshotId);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(downloadUrlDto.downloadUrl()))
                .build();
    }

    @PatchMapping("/{screenshotId}")
    @RateLimited(serverUuid = false, discordUserId = true)
    @Operation(summary = "Update screenshot", description = "Update an existing screenshot by id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Screenshot updated successfully"),
            @ApiResponse(responseCode = "404", description = "Screenshot not found"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ScreenshotDto> updateScreenshot(
            @PathVariable Long screenshotId,
            @Valid @RequestBody ScreenshotUpdateDto screenshotUpdateDto
    ) {
        log.debug("Updating screenshot with id={}", screenshotId);
        ScreenshotDto screenshotDto = this.screenshotService.updateScreenshot(screenshotId, screenshotUpdateDto);
        if (screenshotDto == null) {
            throw new ScreenshotNotFoundException(screenshotId);
        }
        return ResponseEntity.ok(screenshotDto);
    }

    @DeleteMapping("/{screenshotId}")
    @RateLimited(serverUuid = false, discordUserId = true)
    @Operation(summary = "Delete screenshot", description = "Delete a screenshot by id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Screenshot deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Screenshot not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> deleteScreenshot(@PathVariable Long screenshotId) {
        log.debug("Deleting screenshot with id={}", screenshotId);
        this.screenshotService.deleteScreenshot(screenshotId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/count")
    @RateLimited(serverUuid = false, discordUserId = true)
    @Operation(summary = "Get screenshot count", description = "Retrieve the total number of screenshots")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Screenshot count retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Long> getScreenshotCount() {
        log.debug("Getting screenshot count");
        return ResponseEntity.ok(this.screenshotService.countScreenshots());
    }
}

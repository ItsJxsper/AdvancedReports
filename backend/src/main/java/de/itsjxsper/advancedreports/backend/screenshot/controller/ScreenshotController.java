package de.itsjxsper.advancedreports.backend.screenshot.controller;

import de.itsjxsper.advancedreports.backend.ratelimit.annotation.RateLimited;
import de.itsjxsper.advancedreports.backend.screenshot.exceptions.ScreenshotNotFoundException;
import de.itsjxsper.advancedreports.backend.screenshot.service.ScreenshotService;
import de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotDto;
import de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotUpdateDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

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

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RateLimited(serverUuid = false, discordUserId = true)
    @Operation(summary = "Upload screenshot file", description = "Upload a screenshot file to S3 and persist its metadata")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Screenshot uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ScreenshotDto> uploadScreenshot(
            @Parameter(description = "Screenshot file", required = true)
            @RequestPart("file") MultipartFile file
    ) {
        log.debug("Uploading screenshot file name={}", file != null ? file.getOriginalFilename() : null);
        ScreenshotDto screenshotDto = this.screenshotService.uploadScreenshot(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(screenshotDto);
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

    @GetMapping("/{screenshotId}/download")
    @RateLimited(serverUuid = false, discordUserId = true)
    @Operation(summary = "Download screenshot", description = "Download the screenshot stored in S3 by id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Screenshot downloaded successfully"),
            @ApiResponse(responseCode = "404", description = "Screenshot not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Resource> downloadScreenshot(@PathVariable Long screenshotId) {
        log.debug("Downloading screenshot with id={}", screenshotId);

        ScreenshotDto screenshotDto = this.screenshotService.getScreenshot(screenshotId);
        if (screenshotDto == null) {
            throw new ScreenshotNotFoundException(screenshotId);
        }
        byte[] content = this.screenshotService.downloadScreenshot(screenshotId);
        if (content == null) {
            throw new ScreenshotNotFoundException(screenshotId);
        }
        Resource resource = new ByteArrayResource(content);

        String contentType = screenshotDto.contentType() != null && !screenshotDto.contentType().isBlank()
                ? screenshotDto.contentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(screenshotDto.originalFilename() != null ? screenshotDto.originalFilename() : "screenshot", StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(resource);
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


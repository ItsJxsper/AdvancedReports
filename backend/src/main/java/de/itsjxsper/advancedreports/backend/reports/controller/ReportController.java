package de.itsjxsper.advancedreports.backend.reports.controller;

import de.itsjxsper.advancedreports.backend.ratelimit.annotation.RateLimited;
import de.itsjxsper.advancedreports.backend.reports.model.ReportDto;
import de.itsjxsper.advancedreports.backend.reports.model.ReportUpdateDto;
import de.itsjxsper.advancedreports.backend.reports.service.ReportService;
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

@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Report API", description = "Report API")
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    @RateLimited(playerUuid = true)
    @Operation(summary = "Get all reports", description = "Retrieve a paginated list of reports")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Page<ReportDto>> getAllReports(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "100")
            @RequestParam(defaultValue = "100") int size
    ) {
        log.debug("Getting all reports with page={} and size={}", page, size);
        return ResponseEntity.ok(this.reportService.getReports(PageRequest.of(page, size)));
    }

    @PostMapping
    @RateLimited(playerUuid = true)
    @Operation(summary = "Create report", description = "Create a new report")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Report created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "404", description = "Referenced object not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ReportDto> createReport(@Valid @RequestBody ReportUpdateDto reportUpdateDto) {
        log.debug("Creating report");
        ReportDto reportDto = this.reportService.createReport(reportUpdateDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(reportDto);
    }

    @GetMapping("/{reportId}")
    @RateLimited(playerUuid = true)
    @Operation(summary = "Get report by id", description = "Retrieve a report by id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Report found successfully"),
            @ApiResponse(responseCode = "404", description = "Report not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ReportDto> getReport(@PathVariable Long reportId) {
        log.debug("Getting report with id={}", reportId);
        return ResponseEntity.ok(this.reportService.getReport(reportId));
    }

    @PatchMapping("/{reportId}")
    @RateLimited(playerUuid = true)
    @Operation(summary = "Update report", description = "Update an existing report")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Report updated successfully"),
            @ApiResponse(responseCode = "404", description = "Report not found"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ReportDto> updateReport(
            @PathVariable Long reportId,
            @Valid @RequestBody ReportUpdateDto reportUpdateDto
    ) {
        log.debug("Updating report with id={}", reportId);
        return ResponseEntity.ok(this.reportService.updateReport(reportId, reportUpdateDto));
    }

    @DeleteMapping("/{reportId}")
    @RateLimited(playerUuid = true)
    @Operation(summary = "Delete report", description = "Delete a report by id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Report deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Report not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> deleteReport(@PathVariable Long reportId) {
        log.debug("Deleting report with id={}", reportId);
        this.reportService.deleteReport(reportId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/count")
    @RateLimited(playerUuid = true)
    @Operation(summary = "Get report count", description = "Retrieve the total number of reports")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Report count retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Long> getReportCount() {
        log.debug("Getting report count");
        return ResponseEntity.ok(this.reportService.countReports());
    }
}


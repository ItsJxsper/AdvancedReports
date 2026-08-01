package de.itsjxsper.advancedreports.api.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.itsjxsper.advancedreports.api.model.PageResponse;
import de.itsjxsper.advancedreports.common.model.report.ReportDto;
import de.itsjxsper.advancedreports.common.model.report.ReportUpdateDto;
import okhttp3.OkHttpClient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * REST client for {@code /api/v1/reports} (see {@code ReportController}).
 * All methods require the {@code X-Player-UUID} header, as the
 * corresponding endpoints in the backend are annotated with {@code @RateLimited(playerUuid = true)}
 * .
 */
public class ReportsApiClient extends AbstractApiClient {

    private static final String BASE_PATH = "/api/v1/reports";

    public ReportsApiClient(OkHttpClient httpClient, String baseUrl, ObjectMapper objectMapper, Executor executor) {
        super(httpClient, baseUrl, objectMapper, executor);
    }

    /**
     * Corresponds to {@code GET /api/v1/reports}.
     */
    public CompletableFuture<PageResponse<ReportDto>> getReports(int page, int size, UUID requesterPlayerUuid) {
        String path = BASE_PATH + "?page=" + page + "&size=" + size;
        return getAsync(path, new TypeReference<PageResponse<ReportDto>>() {
        }, playerHeader(requesterPlayerUuid));
    }

    public CompletableFuture<ReportDto> createReport(ReportUpdateDto dto, UUID reporterUuid) {
        return postAsync(BASE_PATH, dto, ReportDto.class, playerHeader(reporterUuid));
    }

    public CompletableFuture<ReportDto> getReport(long reportId, UUID requesterPlayerUuid) {
        return getAsync(BASE_PATH + "/" + reportId, ReportDto.class, playerHeader(requesterPlayerUuid));
    }

    public CompletableFuture<ReportDto> updateReport(long reportId, ReportUpdateDto dto, UUID requesterPlayerUuid) {
        return patchAsync(BASE_PATH + "/" + reportId, dto, ReportDto.class, playerHeader(requesterPlayerUuid));
    }

    public CompletableFuture<Void> deleteReport(long reportId, UUID requesterPlayerUuid) {
        return deleteAsync(BASE_PATH + "/" + reportId, playerHeader(requesterPlayerUuid));
    }

    public CompletableFuture<Long> countReports(UUID requesterPlayerUuid) {
        return getAsync(BASE_PATH + "/count", Long.class, playerHeader(requesterPlayerUuid));
    }

    private Map<String, String> playerHeader(UUID playerUuid) {
        return Map.of("X-Player-UUID", playerUuid.toString());
    }
}

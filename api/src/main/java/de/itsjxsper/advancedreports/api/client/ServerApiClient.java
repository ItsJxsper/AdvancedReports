package de.itsjxsper.advancedreports.api.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.itsjxsper.advancedreports.common.model.server.ServerDto;
import okhttp3.OkHttpClient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * REST client for {@code /api/v1/servers} (see {@code ServerController}).
 * <p>
 * Note: {@code createServer} and {@code updateServer} in the backend controller
 * currently accept {@code ServerDto} as a simple method parameter
 * (no {@code @RequestBody}) – presumably a bug in the controller. This client
 * assumes that this will be corrected in the backend and sends the
 * body as normal in JSON; if the backend still does not expect a {@code @RequestBody},
 * this will need to be adjusted here.
 */
public class ServerApiClient extends AbstractApiClient {

    private static final String BASE_PATH = "/api/v1/servers";

    public ServerApiClient(OkHttpClient httpClient, String baseUrl, ObjectMapper objectMapper, Executor executor) {
        super(httpClient, baseUrl, objectMapper, executor);
    }

    public CompletableFuture<ServerDto> createServer(ServerDto dto) {
        return postAsync(BASE_PATH, dto, ServerDto.class);
    }

    public CompletableFuture<ServerDto> getServer(UUID serverUuid) {
        return getAsync(BASE_PATH + "/" + serverUuid, ServerDto.class);
    }

    /**
     * Corresponds to {@code GET /api/v1/servers}. Note: The controller returns
     * {@code Iterable<ServerDto>}, not {@code Page<ServerDto>} – hence
     * it is modeled here as a simple {@link List} rather than {@code PageResponse}.
     */
    public CompletableFuture<List<ServerDto>> getAllServers(int page, int size) {
        String path = BASE_PATH + "?page=" + page + "&size=" + size;
        return getAsync(path, new TypeReference<List<ServerDto>>() {
        });
    }

    public CompletableFuture<ServerDto> updateServer(ServerDto dto) {
        return patchAsync(BASE_PATH, dto, ServerDto.class);
    }

    public CompletableFuture<Void> deleteServer(UUID serverUuid) {
        return deleteAsync(BASE_PATH + "/" + serverUuid);
    }

    public CompletableFuture<Long> countServers() {
        return getAsync(BASE_PATH + "/count", Long.class);
    }

    public CompletableFuture<Long> countReportsForServer(UUID serverUuid) {
        return getAsync(BASE_PATH + "/" + serverUuid + "/reports/count", Long.class);
    }
}

package de.itsjxsper.advancedreports.api.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.itsjxsper.advancedreports.api.model.PageResponse;
import de.itsjxsper.advancedreports.common.model.player.PlayerDTO;
import de.itsjxsper.advancedreports.common.model.player.PlayerUpdateDTO;
import okhttp3.OkHttpClient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * REST client for {@code /api/v1/player} (see {@code PlayerController}).
 * All endpoints only use {@code @RateLimited} with default values
 * (only {@code serverUuid = true}), so the header set globally by
 * {@code ServerHeaderInterceptor} is sufficient – no further
 * header parameters are required.
 */
public class PlayerApiClient extends AbstractApiClient {

    private static final String BASE_PATH = "/api/v1/player";

    public PlayerApiClient(OkHttpClient httpClient, String baseUrl, ObjectMapper objectMapper, Executor executor) {
        super(httpClient, baseUrl, objectMapper, executor);
    }

    public CompletableFuture<PageResponse<PlayerDTO>> getPlayers(int page, int size) {
        String path = BASE_PATH + "?page=" + page + "&size=" + size;
        return getAsync(path, new TypeReference<PageResponse<PlayerDTO>>() {
        });
    }

    public CompletableFuture<PlayerDTO> createPlayer(PlayerUpdateDTO dto) {
        return postAsync(BASE_PATH, dto, PlayerDTO.class);
    }

    public CompletableFuture<PlayerDTO> updatePlayer(PlayerUpdateDTO dto) {
        return patchAsync(BASE_PATH, dto, PlayerDTO.class);
    }

    public CompletableFuture<Void> deletePlayer(UUID playerUuid) {
        return deleteAsync(BASE_PATH + "/" + playerUuid);
    }

    public CompletableFuture<PlayerDTO> getPlayer(UUID playerUuid) {
        return getAsync(BASE_PATH + "/" + playerUuid, PlayerDTO.class);
    }

    public CompletableFuture<Long> countPlayers() {
        return getAsync(BASE_PATH + "/count", Long.class);
    }
}

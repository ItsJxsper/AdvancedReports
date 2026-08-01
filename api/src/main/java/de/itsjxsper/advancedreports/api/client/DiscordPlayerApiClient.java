package de.itsjxsper.advancedreports.api.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.itsjxsper.advancedreports.common.model.discord.DiscordPlayerDto;
import okhttp3.OkHttpClient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * REST client for {@code /api/v1/discord-players} (see {@code DiscordPlayerController}).
 * All endpoints use {@code @RateLimited(serverUuid = false, discordUserId = true)},
 * so an {@code X-Discord-ID} header is passed with each call.
 */
public class DiscordPlayerApiClient extends AbstractApiClient {

    private static final String BASE_PATH = "/api/v1/discord-players";

    public DiscordPlayerApiClient(OkHttpClient httpClient, String baseUrl, ObjectMapper objectMapper, Executor executor) {
        super(httpClient, baseUrl, objectMapper, executor);
    }

    public CompletableFuture<DiscordPlayerDto> createDiscordPlayer(DiscordPlayerDto dto, long discordUserId) {
        return postAsync(BASE_PATH, dto, DiscordPlayerDto.class, discordHeader(discordUserId));
    }

    public CompletableFuture<DiscordPlayerDto> getDiscordPlayerById(long discordPlayerId, long discordUserId) {
        return getAsync(BASE_PATH + "/" + discordPlayerId, DiscordPlayerDto.class, discordHeader(discordUserId));
    }

    public CompletableFuture<DiscordPlayerDto> getDiscordPlayerByPlayerUuid(UUID playerUuid, long discordUserId) {
        return getAsync(BASE_PATH + "/player/" + playerUuid, DiscordPlayerDto.class, discordHeader(discordUserId));
    }

    /**
     * Equivalent to {@code PUT /api/v1/discord-players/{id}} – the controller deliberately uses
     * PUT instead of PATCH here (full replacement, not a partial update).
     */
    public CompletableFuture<DiscordPlayerDto> updateDiscordPlayer(long discordPlayerId, DiscordPlayerDto dto, long discordUserId) {
        return putAsync(BASE_PATH + "/" + discordPlayerId, dto, DiscordPlayerDto.class, discordHeader(discordUserId));
    }

    public CompletableFuture<Void> deleteDiscordPlayerById(long discordPlayerId, long discordUserId) {
        return deleteAsync(BASE_PATH + "/" + discordPlayerId, discordHeader(discordUserId));
    }

    public CompletableFuture<Void> deleteDiscordPlayerByPlayerUuid(UUID playerUuid, long discordUserId) {
        return deleteAsync(BASE_PATH + "/player/" + playerUuid, discordHeader(discordUserId));
    }

    private Map<String, String> discordHeader(long discordUserId) {
        return Map.of("X-Discord-ID", String.valueOf(discordUserId));
    }
}

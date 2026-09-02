package de.itsjxsper.advancedreports.backend.e2e;

import de.itsjxsper.advancedreports.backend.support.AbstractE2ETest;
import de.itsjxsper.advancedreports.backend.support.ApiFixtures;
import de.itsjxsper.advancedreports.common.enums.exceptions.api.ApiErrorCode;
import de.itsjxsper.advancedreports.common.model.discord.DiscordPlayerDto;
import de.itsjxsper.advancedreports.common.model.exceptions.ApiErrorResponse;
import de.itsjxsper.advancedreports.common.model.player.PlayerDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end coverage for linking a Minecraft player to a Discord account.
 * <p>
 * Both defects that used to block this class are fixed: the {@code discord_player_entity} table can
 * be created again, and the writing service methods no longer inherit the class-level read-only
 * transaction that silently swallowed every insert.
 */
@DisplayName("E2E: Discord-Verknüpfung")
class DiscordPlayerE2ETest extends AbstractE2ETest {

    private DiscordPlayerDto linkPayload(UUID playerUuid, Long discordUserId) {
        return new DiscordPlayerDto(null, playerUuid, discordUserId);
    }

    @Nested
    @DisplayName("Verknüpfen")
    class Linking {

        @Test
        @DisplayName("verknüpft einen Spieler mit einem Discord-Account")
        void shouldLinkPlayer() {
            PlayerDTO player = ApiFixtures.createPlayer(client(), "Notch");

            ResponseEntity<DiscordPlayerDto> response = client().post()
                    .uri("/api/v1/discord-players")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(linkPayload(player.playerUUID(), 217476470391308288L))
                    .retrieve()
                    .toEntity(DiscordPlayerDto.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().id()).isNotNull();
            assertThat(response.getBody().playerEntityPlayerUUID()).isEqualTo(player.playerUUID());
            assertThat(response.getBody().discordUserId()).isEqualTo(217476470391308288L);
        }

        @Test
        @DisplayName("löst die Verknüpfung über die Spieler-UUID auf")
        void shouldResolveByPlayerUuid() {
            PlayerDTO player = ApiFixtures.createPlayer(client(), "Notch");

            client().post()
                    .uri("/api/v1/discord-players")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(linkPayload(player.playerUUID(), 17L))
                    .retrieve()
                    .toBodilessEntity();

            ResponseEntity<DiscordPlayerDto> response = client().get()
                    .uri("/api/v1/discord-players/player/{uuid}", player.playerUUID())
                    .retrieve()
                    .toEntity(DiscordPlayerDto.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().discordUserId()).isEqualTo(17L);
        }

        @Test
        @DisplayName("löscht die Verknüpfung, ohne den Spieler zu entfernen")
        void shouldDeleteLinkOnly() {
            PlayerDTO player = ApiFixtures.createPlayer(client(), "Notch");

            ResponseEntity<DiscordPlayerDto> created = client().post()
                    .uri("/api/v1/discord-players")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(linkPayload(player.playerUUID(), 17L))
                    .retrieve()
                    .toEntity(DiscordPlayerDto.class);

            assertThat(client().delete()
                    .uri("/api/v1/discord-players/{id}", created.getBody().id())
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            // Der Spieler muss die Löschung überleben - siehe DiscordPlayerRepositoryIT zum
            // cascade = ALL / orphanRemoval auf playerEntity.
            assertThat(client().get()
                    .uri("/api/v1/player/{uuid}", player.playerUUID())
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()).isEqualTo(HttpStatus.OK);
        }

    }

    @Nested
    @DisplayName("Fehlerfälle ohne Tabellenzugriff")
    class ErrorCases {

        @Test
        @DisplayName("antwortet mit 404 PLAYER_NOT_FOUND für eine unbekannte Spieler-UUID")
        void shouldReturnNotFoundForUnknownPlayer() {
            ResponseEntity<ApiErrorResponse> response = client().post()
                    .uri("/api/v1/discord-players")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(linkPayload(UUID.randomUUID(), 17L))
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            // Der Spieler wird vor dem Tabellenzugriff nachgeschlagen, deshalb greift hier noch die
            // saubere 404-Antwort. Gemeldet wird PLAYER_NOT_FOUND, nicht DISCORD_USER_NOT_FOUND:
            // fehlt der Minecraft-Spieler, ist das kein Fehler der Discord-Verknüpfung -
            // DiscordPlayerService wirft dafür bewusst PlayerNotFoundException.
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.PLAYER_NOT_FOUND);
        }
    }
}

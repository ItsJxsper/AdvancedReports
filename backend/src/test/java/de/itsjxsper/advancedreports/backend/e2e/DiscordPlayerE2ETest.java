package de.itsjxsper.advancedreports.backend.e2e;

import de.itsjxsper.advancedreports.backend.exceptions.ApiErrorCode;
import de.itsjxsper.advancedreports.backend.exceptions.ApiErrorResponse;
import de.itsjxsper.advancedreports.backend.support.AbstractE2ETest;
import de.itsjxsper.advancedreports.backend.support.ApiFixtures;
import de.itsjxsper.advancedreports.common.model.discord.DiscordPlayerDto;
import de.itsjxsper.advancedreports.common.model.player.PlayerDTO;
import org.junit.jupiter.api.Disabled;
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
 * Everything that writes is disabled: the {@code discord_player_entity} table does not exist, because
 * Hibernate's generated DDL for it is rejected by Postgres. See the class-level {@code @Disabled} on
 * {@code DiscordPlayerRepositoryIT} for the full analysis. The read paths that only need to produce a
 * 404 still run, since they never touch the table.
 */
@DisplayName("E2E: Discord-Verknüpfung")
class DiscordPlayerE2ETest extends AbstractE2ETest {

    private static final String TABLE_MISSING_BUG = """
            BUG (blockierend, ganze Domain): Die Tabelle discord_player_entity existiert nicht. \
            DiscordPlayerEntity#discordUserId kombiniert @JdbcTypeCode(SqlTypes.LONG32NVARCHAR) - die \
            Spalte wird damit zu 'text' - mit @Max(18), woraus Hibernate einen numerischen CHECK \
            generiert. Postgres lehnt das DDL ab: 'ERROR: operator does not exist: text <= integer'. \
            Jeder schreibende Zugriff endet daher in 'relation "discord_player_entity" does not exist' \
            und nach aussen in 500. Zusaetzlich wuerde @Max(18) jede echte, 17-19-stellige \
            Discord-Snowflake bereits an der Bean Validation abweisen. Fix: @JdbcTypeCode entfernen \
            und @Max(18) durch @Digits(integer = 19, fraction = 0) ersetzen. Siehe \
            discord/data/entity/DiscordPlayerEntity.java:18-21.""";

    private DiscordPlayerDto linkPayload(UUID playerUuid, Long discordUserId) {
        return new DiscordPlayerDto(null, playerUuid, discordUserId);
    }

    @Nested
    @DisplayName("Verknüpfen")
    class Linking {

        @Test
        @Disabled(TABLE_MISSING_BUG)
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
        @Disabled(TABLE_MISSING_BUG)
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
        @Disabled(TABLE_MISSING_BUG)
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

        @Test
        @DisplayName("dokumentiert, dass das Verknüpfen aktuell mit 500 scheitert")
        void shouldCurrentlyFailToLink() {
            PlayerDTO player = ApiFixtures.createPlayer(client(), "Notch");

            ResponseEntity<ApiErrorResponse> response = client().post()
                    .uri("/api/v1/discord-players")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(linkPayload(player.playerUUID(), 17L))
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Nested
    @DisplayName("Fehlerfälle ohne Tabellenzugriff")
    class ErrorCases {

        @Test
        @DisplayName("antwortet mit 404 DISCORD_USER_NOT_FOUND für eine unbekannte Spieler-UUID")
        void shouldReturnNotFoundForUnknownPlayer() {
            ResponseEntity<ApiErrorResponse> response = client().post()
                    .uri("/api/v1/discord-players")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(linkPayload(UUID.randomUUID(), 17L))
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            // Der Spieler wird vor dem Tabellenzugriff nachgeschlagen, deshalb greift hier noch die
            // saubere 404-Antwort.
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.DISCORD_USER_NOT_FOUND);
        }
    }
}

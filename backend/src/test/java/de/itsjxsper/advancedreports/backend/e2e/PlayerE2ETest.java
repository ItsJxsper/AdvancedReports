package de.itsjxsper.advancedreports.backend.e2e;

import de.itsjxsper.advancedreports.backend.support.AbstractE2ETest;
import de.itsjxsper.advancedreports.backend.support.ApiFixtures;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.enums.exceptions.api.ApiErrorCode;
import de.itsjxsper.advancedreports.common.model.exceptions.ApiErrorResponse;
import de.itsjxsper.advancedreports.common.model.player.PlayerDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("E2E: Players")
class PlayerE2ETest extends AbstractE2ETest {

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("creates a player, reads, renames and deletes them again")
        void shouldRunFullCrudCycle() {
            UUID playerUuid = UUID.randomUUID();

            PlayerDTO created = ApiFixtures.createPlayer(client(), playerUuid, "Notch");
            assertThat(created.playerUUID()).isEqualTo(playerUuid);
            assertThat(created.playerName()).isEqualTo("Notch");

            ResponseEntity<PlayerDTO> fetched = client().get()
                    .uri("/api/v1/player/{uuid}", playerUuid)
                    .retrieve()
                    .toEntity(PlayerDTO.class);

            assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(fetched.getBody().playerName()).isEqualTo("Notch");

            ResponseEntity<PlayerDTO> renamed = client().patch()
                    .uri("/api/v1/player")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(TestDataFactory.playerUpdateDto(playerUuid, "Jeb_"))
                    .retrieve()
                    .toEntity(PlayerDTO.class);

            assertThat(renamed.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(renamed.getBody().playerName()).isEqualTo("Jeb_");

            assertThat(client().delete()
                    .uri("/api/v1/player/{uuid}", playerUuid)
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            assertThat(client().get()
                    .uri("/api/v1/player/{uuid}", playerUuid)
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("counts and lists players")
        void shouldCountAndListPlayers() {
            ApiFixtures.createPlayer(client(), "Notch");
            ApiFixtures.createPlayer(client(), "Jeb");
            ApiFixtures.createPlayer(client(), "Dinnerbone");

            ResponseEntity<Long> count = client().get()
                    .uri("/api/v1/player/count")
                    .retrieve()
                    .toEntity(Long.class);

            assertThat(count.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(count.getBody()).isEqualTo(3L);

            ResponseEntity<String> list = client().get()
                    .uri("/api/v1/player?page=0&size=2")
                    .retrieve()
                    .toEntity(String.class);

            assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(list.getBody()).contains("\"totalElements\":3");
        }
    }

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        @DisplayName("answers 409 PLAYER_ALREADY_EXISTS for a duplicate UUID")
        void shouldRejectDuplicateUuid() {
            UUID playerUuid = UUID.randomUUID();
            ApiFixtures.createPlayer(client(), playerUuid, "Notch");

            ResponseEntity<ApiErrorResponse> response = client().post()
                    .uri("/api/v1/player")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(TestDataFactory.playerUpdateDto(playerUuid, "Notch"))
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.PLAYER_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("answers 404 PLAYER_NOT_FOUND for an unknown UUID")
        void shouldReturnNotFound() {
            ResponseEntity<ApiErrorResponse> response = client().get()
                    .uri("/api/v1/player/{uuid}", UUID.randomUUID())
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.PLAYER_NOT_FOUND);
        }

        @Test
        @DisplayName("answers 404 when updating an unknown player")
        void shouldReturnNotFoundOnUpdate() {
            ResponseEntity<ApiErrorResponse> response = client().patch()
                    .uri("/api/v1/player")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(TestDataFactory.playerUpdateDto(UUID.randomUUID(), "Notch"))
                    .retrieve()
                    .toEntity(ApiErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo(ApiErrorCode.PLAYER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Partial update without a name")
    class PartialUpdate {

        @Test
        @DisplayName("keeps the existing name when no name is sent")
        void shouldKeepNameWhenAbsent() {
            UUID playerUuid = UUID.randomUUID();
            ApiFixtures.createPlayer(client(), playerUuid, "Notch");

            ResponseEntity<PlayerDTO> response = client().patch()
                    .uri("/api/v1/player")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new de.itsjxsper.advancedreports.common.model.player.PlayerUpdateDTO(
                            playerUuid, Optional.empty()))
                    .retrieve()
                    .toEntity(PlayerDTO.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().playerName()).isEqualTo("Notch");
        }

    }
}

package de.itsjxsper.advancedreports.backend.discord.controller;

import de.itsjxsper.advancedreports.backend.discord.exceptions.DiscordUserNotFoundException;
import de.itsjxsper.advancedreports.backend.discord.service.DiscordPlayerService;
import de.itsjxsper.advancedreports.common.model.discord.DiscordPlayerDto;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DiscordPlayerController.class)
@ActiveProfiles("test")
@DisplayName("DiscordPlayerController")
class DiscordPlayerControllerTest {

    private static final UUID PLAYER_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Long DISCORD_PLAYER_ID = 5L;

    /**
     * A realistic Discord snowflake — 18 digits, far beyond what {@code @Max(18)} permits.
     */
    private static final Long REAL_SNOWFLAKE = 217476470391308288L;
    private final DiscordPlayerDto discordPlayerDto =
            new DiscordPlayerDto(DISCORD_PLAYER_ID, PLAYER_UUID, 17L);
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private DiscordPlayerService discordPlayerService;

    @Nested
    @DisplayName("POST /api/v1/discord-players")
    class CreateDiscordPlayer {

        @Test
        @DisplayName("liefert 201 mit der angelegten Verknüpfung")
        void shouldCreateDiscordPlayer() throws Exception {
            when(discordPlayerService.createDiscordPlayer(any())).thenReturn(discordPlayerDto);

            mockMvc.perform(post("/api/v1/discord-players")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(discordPlayerDto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.playerEntityPlayerUUID").value(PLAYER_UUID.toString()))
                    .andExpect(jsonPath("$.discordUserId").value(17));
        }

        @Test
        @DisplayName("liefert 404 DISCORD_USER_NOT_FOUND, wenn der Minecraft-Spieler fehlt")
        void shouldReturnNotFound() throws Exception {
            when(discordPlayerService.createDiscordPlayer(any()))
                    .thenThrow(new DiscordUserNotFoundException(PLAYER_UUID));

            mockMvc.perform(post("/api/v1/discord-players")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(discordPlayerDto)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("DISCORD_USER_NOT_FOUND"));
        }

        @Test
        @Disabled("BUG: @Max(18) auf discordUserId begrenzt den *Wert* auf 18, nicht die Stellenzahl "
                + "(common DiscordPlayerDto und discord/data/entity/DiscordPlayerEntity.java:18). "
                + "Discord-IDs sind 17-19-stellige Snowflakes, also wird jede echte Discord-ID von "
                + "der Bean Validation abgelehnt - die Domain ist mit realen Daten unbenutzbar. "
                + "Gemeint war vermutlich @Digits(integer = 19, fraction = 0).")
        @DisplayName("akzeptiert eine echte, 18-stellige Discord-Snowflake")
        void shouldAcceptRealSnowflake() throws Exception {
            DiscordPlayerDto dto = new DiscordPlayerDto(DISCORD_PLAYER_ID, PLAYER_UUID, REAL_SNOWFLAKE);
            when(discordPlayerService.createDiscordPlayer(any())).thenReturn(dto);

            mockMvc.perform(post("/api/v1/discord-players")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.discordUserId").value(REAL_SNOWFLAKE));
        }

        @Test
        @DisplayName("dokumentiert, dass eine echte Discord-Snowflake aktuell mit 400 abgelehnt wird")
        void shouldCurrentlyRejectRealSnowflake() throws Exception {
            DiscordPlayerDto dto = new DiscordPlayerDto(DISCORD_PLAYER_ID, PLAYER_UUID, REAL_SNOWFLAKE);

            mockMvc.perform(post("/api/v1/discord-players")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/discord-players/{discordPlayerId}")
    class GetById {

        @Test
        @DisplayName("liefert 200 mit der Verknüpfung")
        void shouldReturnDiscordPlayer() throws Exception {
            when(discordPlayerService.getDiscordPlayerById(DISCORD_PLAYER_ID))
                    .thenReturn(discordPlayerDto);

            mockMvc.perform(get("/api/v1/discord-players/{id}", DISCORD_PLAYER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(5));
        }

        @Test
        @DisplayName("liefert 404 DISCORD_USER_NOT_FOUND für eine unbekannte id")
        void shouldReturnNotFound() throws Exception {
            when(discordPlayerService.getDiscordPlayerById(99L))
                    .thenThrow(new DiscordUserNotFoundException(99L));

            mockMvc.perform(get("/api/v1/discord-players/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Discord player with ID 99 not found"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/discord-players/player/{playerUUID}")
    class GetByPlayerUuid {

        @Test
        @DisplayName("liefert 200 mit der Verknüpfung zur Spieler-UUID")
        void shouldReturnDiscordPlayer() throws Exception {
            when(discordPlayerService.getDiscordPlayerByPlayerUUID(PLAYER_UUID))
                    .thenReturn(discordPlayerDto);

            mockMvc.perform(get("/api/v1/discord-players/player/{uuid}", PLAYER_UUID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.playerEntityPlayerUUID").value(PLAYER_UUID.toString()));
        }

        @Test
        @DisplayName("liefert 404 DISCORD_USER_NOT_FOUND für eine unbekannte Spieler-UUID")
        void shouldReturnNotFound() throws Exception {
            when(discordPlayerService.getDiscordPlayerByPlayerUUID(PLAYER_UUID))
                    .thenThrow(new DiscordUserNotFoundException(PLAYER_UUID));

            mockMvc.perform(get("/api/v1/discord-players/player/{uuid}", PLAYER_UUID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("DISCORD_USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/discord-players/{discordPlayerId}")
    class UpdateDiscordPlayer {

        @Test
        @DisplayName("liefert 200 mit der aktualisierten Verknüpfung")
        void shouldUpdateDiscordPlayer() throws Exception {
            when(discordPlayerService.updateDiscordPlayer(any())).thenReturn(discordPlayerDto);

            mockMvc.perform(put("/api/v1/discord-players/{id}", DISCORD_PLAYER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(discordPlayerDto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(5));

            verify(discordPlayerService).updateDiscordPlayer(discordPlayerDto);
        }

        @Test
        @DisplayName("liefert 404 DISCORD_USER_NOT_FOUND für eine unbekannte Verknüpfung")
        void shouldReturnNotFound() throws Exception {
            when(discordPlayerService.updateDiscordPlayer(any()))
                    .thenThrow(new DiscordUserNotFoundException(DISCORD_PLAYER_ID));

            mockMvc.perform(put("/api/v1/discord-players/{id}", DISCORD_PLAYER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(discordPlayerDto)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("DISCORD_USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("DELETE-Endpunkte")
    class DeleteEndpoints {

        @Test
        @DisplayName("DELETE /{discordPlayerId} liefert 204 ohne Body")
        void shouldDeleteById() throws Exception {
            mockMvc.perform(delete("/api/v1/discord-players/{id}", DISCORD_PLAYER_ID))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(discordPlayerService).deleteDiscordPlayerByDiscordId(DISCORD_PLAYER_ID);
        }

        @Test
        @DisplayName("DELETE /player/{playerUUID} liefert 204 ohne Body")
        void shouldDeleteByPlayerUuid() throws Exception {
            mockMvc.perform(delete("/api/v1/discord-players/player/{uuid}", PLAYER_UUID))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(discordPlayerService).deleteDiscordPlayerByPlayerUUID(PLAYER_UUID);
        }

        @Test
        @DisplayName("liefert 404 DISCORD_USER_NOT_FOUND, wenn der Service wirft")
        void shouldReturnNotFound() throws Exception {
            doThrow(new DiscordUserNotFoundException(99L))
                    .when(discordPlayerService).deleteDiscordPlayerByDiscordId(99L);

            mockMvc.perform(delete("/api/v1/discord-players/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("DISCORD_USER_NOT_FOUND"));
        }
    }
}

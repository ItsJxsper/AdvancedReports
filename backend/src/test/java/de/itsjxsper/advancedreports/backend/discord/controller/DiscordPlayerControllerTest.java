package de.itsjxsper.advancedreports.backend.discord.controller;

import de.itsjxsper.advancedreports.backend.discord.exceptions.DiscordUserNotFoundException;
import de.itsjxsper.advancedreports.backend.discord.service.DiscordPlayerService;
import de.itsjxsper.advancedreports.common.model.discord.DiscordPlayerDto;
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
        @DisplayName("returns 201 with the created link")
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
        @DisplayName("returns 404 DISCORD_USER_NOT_FOUND when the Minecraft player is missing")
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
        @DisplayName("accepts a real 18-digit Discord snowflake")
        void shouldAcceptRealSnowflake() throws Exception {
            DiscordPlayerDto dto = new DiscordPlayerDto(DISCORD_PLAYER_ID, PLAYER_UUID, REAL_SNOWFLAKE);
            when(discordPlayerService.createDiscordPlayer(any())).thenReturn(dto);

            mockMvc.perform(post("/api/v1/discord-players")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.discordUserId").value(REAL_SNOWFLAKE));
        }

    }

    @Nested
    @DisplayName("GET /api/v1/discord-players/{discordPlayerId}")
    class GetById {

        @Test
        @DisplayName("returns 200 with the link")
        void shouldReturnDiscordPlayer() throws Exception {
            when(discordPlayerService.getDiscordPlayerById(DISCORD_PLAYER_ID))
                    .thenReturn(discordPlayerDto);

            mockMvc.perform(get("/api/v1/discord-players/{id}", DISCORD_PLAYER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(5));
        }

        @Test
        @DisplayName("returns 404 DISCORD_USER_NOT_FOUND for an unknown id")
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
        @DisplayName("returns 200 with the link for the player UUID")
        void shouldReturnDiscordPlayer() throws Exception {
            when(discordPlayerService.getDiscordPlayerByPlayerUUID(PLAYER_UUID))
                    .thenReturn(discordPlayerDto);

            mockMvc.perform(get("/api/v1/discord-players/player/{uuid}", PLAYER_UUID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.playerEntityPlayerUUID").value(PLAYER_UUID.toString()));
        }

        @Test
        @DisplayName("returns 404 DISCORD_USER_NOT_FOUND for an unknown player UUID")
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
        @DisplayName("returns 200 with the updated link")
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
        @DisplayName("returns 404 DISCORD_USER_NOT_FOUND for an unknown link")
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
    @DisplayName("DELETE endpoints")
    class DeleteEndpoints {

        @Test
        @DisplayName("DELETE /{discordPlayerId} returns 204 with no body")
        void shouldDeleteById() throws Exception {
            mockMvc.perform(delete("/api/v1/discord-players/{id}", DISCORD_PLAYER_ID))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(discordPlayerService).deleteDiscordPlayerByDiscordId(DISCORD_PLAYER_ID);
        }

        @Test
        @DisplayName("DELETE /player/{playerUUID} returns 204 with no body")
        void shouldDeleteByPlayerUuid() throws Exception {
            mockMvc.perform(delete("/api/v1/discord-players/player/{uuid}", PLAYER_UUID))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(discordPlayerService).deleteDiscordPlayerByPlayerUUID(PLAYER_UUID);
        }

        @Test
        @DisplayName("returns 404 DISCORD_USER_NOT_FOUND when the service throws")
        void shouldReturnNotFound() throws Exception {
            doThrow(new DiscordUserNotFoundException(99L))
                    .when(discordPlayerService).deleteDiscordPlayerByDiscordId(99L);

            mockMvc.perform(delete("/api/v1/discord-players/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("DISCORD_USER_NOT_FOUND"));
        }
    }
}

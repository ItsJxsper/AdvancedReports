package de.itsjxsper.advancedreports.backend.player.controller;

import de.itsjxsper.advancedreports.backend.player.exception.PlayerAlreadyExistException;
import de.itsjxsper.advancedreports.backend.player.exception.PlayerNotFoundException;
import de.itsjxsper.advancedreports.backend.player.service.PlayerService;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.model.player.PlayerDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PlayerController.class)
@ActiveProfiles("test")
@DisplayName("PlayerController")
class PlayerControllerTest {

    private static final UUID PLAYER_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final PlayerDTO playerDto = new PlayerDTO(PLAYER_UUID, "Notch");
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private PlayerService playerService;

    @Nested
    @DisplayName("GET /api/v1/player")
    class GetAllPlayers {

        @Test
        @DisplayName("returns 200 with a paginated list")
        void shouldReturnPagedPlayers() throws Exception {
            when(playerService.getPlayers(any())).thenReturn(new PageImpl<>(List.of(playerDto)));

            mockMvc.perform(get("/api/v1/player"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].playerUUID").value(PLAYER_UUID.toString()))
                    .andExpect(jsonPath("$.content[0].playerName").value("Notch"));
        }

        @Test
        @DisplayName("defaults to page=0 and size=100")
        void shouldUseDefaultPaging() throws Exception {
            when(playerService.getPlayers(any())).thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/v1/player")).andExpect(status().isOk());

            verify(playerService).getPlayers(PageRequest.of(0, 100));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/player")
    class CreatePlayer {

        @Test
        @DisplayName("returns 201 with the created player")
        void shouldCreatePlayer() throws Exception {
            when(playerService.createPlayer(any())).thenReturn(playerDto);

            mockMvc.perform(post("/api/v1/player")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    TestDataFactory.playerUpdateDto(PLAYER_UUID, "Notch"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.playerName").value("Notch"));
        }

        @Test
        @DisplayName("reads the name from the Optional field playerName")
        void shouldBindOptionalPlayerName() throws Exception {
            when(playerService.createPlayer(any())).thenReturn(playerDto);

            mockMvc.perform(post("/api/v1/player")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"playerUuid\":\"" + PLAYER_UUID + "\",\"playerName\":\"Notch\"}"))
                    .andExpect(status().isCreated());

            verify(playerService).createPlayer(TestDataFactory.playerUpdateDto(PLAYER_UUID, "Notch"));
        }

        @Test
        @DisplayName("returns 409 PLAYER_ALREADY_EXISTS when the UUID is already taken")
        void shouldReturnConflict() throws Exception {
            when(playerService.createPlayer(any()))
                    .thenThrow(new PlayerAlreadyExistException(PLAYER_UUID));

            mockMvc.perform(post("/api/v1/player")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    TestDataFactory.playerUpdateDto(PLAYER_UUID, "Notch"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PLAYER_ALREADY_EXISTS"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/player")
    class UpdatePlayer {

        @Test
        @DisplayName("returns 200 with the updated player")
        void shouldUpdatePlayer() throws Exception {
            when(playerService.updatePlayer(any())).thenReturn(playerDto);

            mockMvc.perform(patch("/api/v1/player")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    TestDataFactory.playerUpdateDto(PLAYER_UUID, "Notch"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.playerName").value("Notch"));
        }

        @Test
        @DisplayName("returns 404 PLAYER_NOT_FOUND for an unknown player")
        void shouldReturnNotFound() throws Exception {
            when(playerService.updatePlayer(any())).thenThrow(new PlayerNotFoundException(PLAYER_UUID));

            mockMvc.perform(patch("/api/v1/player")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    TestDataFactory.playerUpdateDto(PLAYER_UUID, "Notch"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/player/{playerUuid}")
    class GetPlayer {

        @Test
        @DisplayName("returns 200 with the player")
        void shouldReturnPlayer() throws Exception {
            when(playerService.getPlayer(PLAYER_UUID)).thenReturn(playerDto);

            mockMvc.perform(get("/api/v1/player/{uuid}", PLAYER_UUID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.playerUUID").value(PLAYER_UUID.toString()));
        }

        @Test
        @DisplayName("returns 404 PLAYER_NOT_FOUND for an unknown player")
        void shouldReturnNotFound() throws Exception {
            when(playerService.getPlayer(PLAYER_UUID)).thenThrow(new PlayerNotFoundException(PLAYER_UUID));

            mockMvc.perform(get("/api/v1/player/{uuid}", PLAYER_UUID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
        }

        @Test
        @DisplayName("returns 400 METHOD_ARGUMENT_TYPE_MISMATCH for an unparseable UUID")
        void shouldRejectInvalidUuid() throws Exception {
            mockMvc.perform(get("/api/v1/player/not-a-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("METHOD_ARGUMENT_TYPE_MISMATCH"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/player/{playerUuid}")
    class DeletePlayer {

        @Test
        @DisplayName("returns 204 with no body")
        void shouldDeletePlayer() throws Exception {
            mockMvc.perform(delete("/api/v1/player/{uuid}", PLAYER_UUID))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(playerService).deletePlayer(PLAYER_UUID);
        }

        @Test
        @DisplayName("returns 404 PLAYER_NOT_FOUND for an unknown player")
        void shouldReturnNotFound() throws Exception {
            doThrow(new PlayerNotFoundException(PLAYER_UUID)).when(playerService).deletePlayer(PLAYER_UUID);

            mockMvc.perform(delete("/api/v1/player/{uuid}", PLAYER_UUID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/player/count")
    class CountPlayers {

        @Test
        @DisplayName("returns 200 with the total count")
        void shouldReturnCount() throws Exception {
            when(playerService.countPlayers()).thenReturn(5L);

            mockMvc.perform(get("/api/v1/player/count"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("5"));
        }
    }
}

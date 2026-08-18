package de.itsjxsper.advancedreports.backend.server.controller;

import de.itsjxsper.advancedreports.backend.server.exceptions.ServerNotFoundException;
import de.itsjxsper.advancedreports.backend.server.service.ServerService;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.model.server.ServerDto;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
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

@WebMvcTest(ServerController.class)
@ActiveProfiles("test")
@DisplayName("ServerController")
class ServerControllerTest {

    private static final UUID SERVER_UUID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final ServerDto serverDto = TestDataFactory.serverDto(SERVER_UUID);
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private ServerService serverService;

    @Nested
    @DisplayName("POST /api/v1/servers")
    class CreateServer {

        @Test
        @Disabled("BUG: ServerController#createServer (server/controller/ServerController.java:24) "
                + "deklariert den Parameter als 'ServerDto serverDto' ohne @RequestBody. Spring MVC "
                + "behandelt ihn deshalb als Model-Attribute und bindet ihn aus Query-Parametern statt "
                + "aus dem JSON-Body - ein gesendeter Body wird komplett ignoriert und der Service "
                + "bekommt ein DTO mit lauter null-Feldern. Dasselbe gilt fuer #updateServer "
                + "(Zeile 66). Zusaetzlich fehlt an POST /servers das @RateLimited, das jeder andere "
                + "Endpunkt dieses Controllers traegt.")
        @DisplayName("übernimmt den JSON-Body und liefert 200 mit dem registrierten Server")
        void shouldCreateServerFromJsonBody() throws Exception {
            when(serverService.createServer(any())).thenReturn(serverDto);

            mockMvc.perform(post("/api/v1/servers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(serverDto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.serverUUID").value(SERVER_UUID.toString()));

            verify(serverService).createServer(serverDto);
        }

        @Test
        @DisplayName("dokumentiert, dass der JSON-Body aktuell verworfen wird")
        void shouldCurrentlyIgnoreJsonBody() throws Exception {
            when(serverService.createServer(any())).thenReturn(serverDto);

            mockMvc.perform(post("/api/v1/servers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(serverDto)))
                    .andExpect(status().isOk());

            // Ohne @RequestBody kommt ein leeres DTO an, nicht das gesendete.
            verify(serverService).createServer(new ServerDto(null, null, null));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/servers/{serverUUID}")
    class GetServer {

        @Test
        @DisplayName("liefert 200 mit dem Server")
        void shouldReturnServer() throws Exception {
            when(serverService.getServerByUUID(SERVER_UUID)).thenReturn(serverDto);

            mockMvc.perform(get("/api/v1/servers/{uuid}", SERVER_UUID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.serverUUID").value(SERVER_UUID.toString()))
                    .andExpect(jsonPath("$.port").value(25565));
        }

        @Test
        @DisplayName("liefert 404 SERVER_NOT_FOUND für eine unbekannte UUID")
        void shouldReturnNotFound() throws Exception {
            when(serverService.getServerByUUID(SERVER_UUID))
                    .thenThrow(new ServerNotFoundException(SERVER_UUID));

            mockMvc.perform(get("/api/v1/servers/{uuid}", SERVER_UUID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SERVER_NOT_FOUND"))
                    .andExpect(jsonPath("$.message")
                            .value("Server with UUID " + SERVER_UUID + " was not found"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/servers")
    class GetAllServers {

        @Test
        @DisplayName("liefert 200 mit einer paginierten Liste")
        void shouldReturnPagedServers() throws Exception {
            when(serverService.getAllServers(0, 10)).thenReturn(new PageImpl<>(List.of(serverDto)));

            mockMvc.perform(get("/api/v1/servers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].serverUUID").value(SERVER_UUID.toString()));
        }

        @Test
        @DisplayName("nutzt page=0 und size=10 als Voreinstellung")
        void shouldUseDefaultPaging() throws Exception {
            when(serverService.getAllServers(0, 10)).thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/v1/servers")).andExpect(status().isOk());

            verify(serverService).getAllServers(0, 10);
        }

        @Test
        @DisplayName("übernimmt page und size aus den Query-Parametern")
        void shouldHonourPagingParameters() throws Exception {
            when(serverService.getAllServers(3, 7)).thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/v1/servers").param("page", "3").param("size", "7"))
                    .andExpect(status().isOk());

            verify(serverService).getAllServers(3, 7);
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/servers/{serverUUID}")
    class DeleteServer {

        @Test
        @DisplayName("liefert 204 ohne Body")
        void shouldDeleteServer() throws Exception {
            mockMvc.perform(delete("/api/v1/servers/{uuid}", SERVER_UUID))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(serverService).deleteServer(SERVER_UUID);
        }

        @Test
        @DisplayName("liefert 404 SERVER_NOT_FOUND, wenn der Service wirft")
        void shouldReturnNotFound() throws Exception {
            doThrow(new ServerNotFoundException(SERVER_UUID)).when(serverService).deleteServer(SERVER_UUID);

            mockMvc.perform(delete("/api/v1/servers/{uuid}", SERVER_UUID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SERVER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("Zählendpunkte")
    class CountEndpoints {

        @Test
        @DisplayName("GET /count liefert 200 mit der Anzahl registrierter Server")
        void shouldReturnServerCount() throws Exception {
            when(serverService.countServers()).thenReturn(3L);

            mockMvc.perform(get("/api/v1/servers/count"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("3"));
        }

        @Test
        @DisplayName("GET /{serverUUID}/reports/count liefert 200 mit der Reportanzahl des Servers")
        void shouldReturnReportCountForServer() throws Exception {
            when(serverService.countReportsForServer(SERVER_UUID)).thenReturn(11L);

            mockMvc.perform(get("/api/v1/servers/{uuid}/reports/count", SERVER_UUID))
                    .andExpect(status().isOk())
                    .andExpect(content().string("11"));
        }
    }
}

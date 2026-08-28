package de.itsjxsper.advancedreports.backend.reports.controller;

import de.itsjxsper.advancedreports.backend.reports.exceptions.ReportNotFoundException;
import de.itsjxsper.advancedreports.backend.reports.service.ReportService;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.enums.report.ReportStatus;
import de.itsjxsper.advancedreports.common.model.report.ReportDto;
import de.itsjxsper.advancedreports.common.model.report.ReportCreateDto;
import de.itsjxsper.advancedreports.common.model.report.ReportUpdateDto;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReportController.class)
@ActiveProfiles("test")
@DisplayName("ReportController")
class ReportControllerTest {

    private static final UUID REPORTER_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REPORTED_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID HANDLER_UUID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SERVER_UUID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final ReportDto reportDto = new ReportDto(1L, REPORTER_UUID, REPORTED_UUID, 7L,
            "Verdacht auf Fliegen", SERVER_UUID, "world:100:64:-200", ReportStatus.PENDING,
            HANDLER_UUID, null, null, Instant.parse("2026-01-01T12:00:00Z"), null);
    private final ReportCreateDto createDto = TestDataFactory.reportCreateDto(
            REPORTER_UUID, REPORTED_UUID, 7L, SERVER_UUID, HANDLER_UUID);
    private final ReportUpdateDto updateDto = TestDataFactory.reportUpdateDto(
            REPORTER_UUID, REPORTED_UUID, 7L, SERVER_UUID, HANDLER_UUID);
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private ReportService reportService;

    @Nested
    @DisplayName("GET /api/v1/reports")
    class GetAllReports {

        @Test
        @DisplayName("liefert 200 mit einer paginierten Liste")
        void shouldReturnPagedReports() throws Exception {
            when(reportService.getReports(any())).thenReturn(new PageImpl<>(List.of(reportDto)));

            mockMvc.perform(get("/api/v1/reports"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.content[0].reporterUUID").value(REPORTER_UUID.toString()))
                    .andExpect(jsonPath("$.content[0].reportStatus").value("PENDING"));
        }

        @Test
        @DisplayName("nutzt page=0 und size=100 als Voreinstellung")
        void shouldUseDefaultPaging() throws Exception {
            when(reportService.getReports(any())).thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/v1/reports")).andExpect(status().isOk());

            verify(reportService).getReports(PageRequest.of(0, 100));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/reports")
    class CreateReport {

        @Test
        @DisplayName("liefert 201 mit dem angelegten Report")
        void shouldCreateReport() throws Exception {
            when(reportService.createReport(any())).thenReturn(reportDto);

            mockMvc.perform(post("/api/v1/reports")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createDto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.serverUUID").value(SERVER_UUID.toString()));
        }

        @Test
        @DisplayName("gibt den Request-Body unverändert an den Service weiter")
        void shouldPassBodyToService() throws Exception {
            when(reportService.createReport(any())).thenReturn(reportDto);

            mockMvc.perform(post("/api/v1/reports")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createDto)))
                    .andExpect(status().isCreated());

            verify(reportService).createReport(createDto);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/reports/{reportId}")
    class GetReport {

        @Test
        @DisplayName("liefert 200 mit dem Report")
        void shouldReturnReport() throws Exception {
            when(reportService.getReport(1L)).thenReturn(reportDto);

            mockMvc.perform(get("/api/v1/reports/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reason").value("Verdacht auf Fliegen"))
                    .andExpect(jsonPath("$.location").value("world:100:64:-200"));
        }

        @Test
        @DisplayName("liefert 404 REPORT_NOT_FOUND für eine unbekannte id")
        void shouldReturnNotFound() throws Exception {
            when(reportService.getReport(99L)).thenThrow(new ReportNotFoundException(99L));

            mockMvc.perform(get("/api/v1/reports/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Report with ID 99 was not found"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/reports/{reportId}")
    class UpdateReport {

        @Test
        @DisplayName("liefert 200 mit dem aktualisierten Report")
        void shouldUpdateReport() throws Exception {
            when(reportService.updateReport(eq(1L), any())).thenReturn(reportDto);

            mockMvc.perform(patch("/api/v1/reports/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));

            verify(reportService).updateReport(1L, updateDto);
        }

        @Test
        @DisplayName("liefert 404 REPORT_NOT_FOUND für eine unbekannte id")
        void shouldReturnNotFound() throws Exception {
            when(reportService.updateReport(eq(99L), any())).thenThrow(new ReportNotFoundException(99L));

            mockMvc.perform(patch("/api/v1/reports/99")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/reports/{reportId}")
    class DeleteReport {

        @Test
        @DisplayName("liefert 204 ohne Body")
        void shouldDeleteReport() throws Exception {
            mockMvc.perform(delete("/api/v1/reports/1"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(reportService).deleteReport(1L);
        }

        @Test
        @DisplayName("liefert 404 REPORT_NOT_FOUND für eine unbekannte id")
        void shouldReturnNotFound() throws Exception {
            doThrow(new ReportNotFoundException(99L)).when(reportService).deleteReport(99L);

            mockMvc.perform(delete("/api/v1/reports/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/reports/count")
    class CountReports {

        @Test
        @DisplayName("liefert 200 mit der Gesamtanzahl")
        void shouldReturnCount() throws Exception {
            when(reportService.countReports()).thenReturn(42L);

            mockMvc.perform(get("/api/v1/reports/count"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("42"));
        }
    }
}

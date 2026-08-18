package de.itsjxsper.advancedreports.backend.reports.service;

import de.itsjxsper.advancedreports.backend.category.data.entity.CategoryEntity;
import de.itsjxsper.advancedreports.backend.category.data.repository.CategoryRepository;
import de.itsjxsper.advancedreports.backend.config.RabbitMQConfiguration;
import de.itsjxsper.advancedreports.backend.messaging.events.ReportCreatedEvent;
import de.itsjxsper.advancedreports.backend.messaging.events.ReportUpdatedEvent;
import de.itsjxsper.advancedreports.backend.player.data.entity.PlayerEntity;
import de.itsjxsper.advancedreports.backend.player.data.repository.PlayerRepository;
import de.itsjxsper.advancedreports.backend.player.exception.PlayerNotFoundException;
import de.itsjxsper.advancedreports.backend.reports.data.entity.ReportsEntity;
import de.itsjxsper.advancedreports.backend.reports.data.repository.ReportRepository;
import de.itsjxsper.advancedreports.backend.reports.exceptions.ReportNotFoundException;
import de.itsjxsper.advancedreports.backend.reports.mapper.ReportMapper;
import de.itsjxsper.advancedreports.backend.screenshot.data.entity.ScreenshotEntity;
import de.itsjxsper.advancedreports.backend.screenshot.data.repository.ScreenshotRepository;
import de.itsjxsper.advancedreports.backend.screenshot.exceptions.ScreenshotNotFoundException;
import de.itsjxsper.advancedreports.backend.server.data.entity.ServerEntity;
import de.itsjxsper.advancedreports.backend.server.data.repository.ServerRepository;
import de.itsjxsper.advancedreports.backend.server.exceptions.ServerNotFoundException;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.enums.report.ReportStatus;
import de.itsjxsper.advancedreports.common.model.report.ReportDto;
import de.itsjxsper.advancedreports.common.model.report.ReportUpdateDto;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService")
class ReportServiceTest {

    private static final UUID REPORTER_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REPORTED_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID HANDLER_UUID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SERVER_UUID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Long CATEGORY_ID = 7L;
    private static final Long SCREENSHOT_ID = 9L;
    private static final Long REPORT_ID = 1L;

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ServerRepository serverRepository;
    @Mock
    private ScreenshotRepository screenshotRepository;
    @Mock
    private ReportMapper reportMapper;
    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private ReportService reportService;

    private ServerEntity serverEntity;
    private ScreenshotEntity screenshotEntity;
    private ReportsEntity reportEntity;
    private ReportDto reportDto;

    @BeforeEach
    void setUp() {
        PlayerEntity reporter = TestDataFactory.player(REPORTER_UUID, "Reporter");
        PlayerEntity reported = TestDataFactory.player(REPORTED_UUID, "Reported");
        PlayerEntity handler = TestDataFactory.player(HANDLER_UUID, "Handler");

        CategoryEntity category = TestDataFactory.category("cheating");
        category.setId(CATEGORY_ID);

        serverEntity = TestDataFactory.server();
        serverEntity.setServerUuid(SERVER_UUID);

        screenshotEntity = TestDataFactory.screenshot("screenshots/2026-01-01/abc-screenshot.png");
        screenshotEntity.setId(SCREENSHOT_ID);

        reportEntity = TestDataFactory.report(reporter, reported, handler, category, serverEntity);
        reportEntity.setId(REPORT_ID);

        reportDto = new ReportDto(REPORT_ID, REPORTER_UUID, REPORTED_UUID, CATEGORY_ID,
                "Verdacht auf Fliegen", SERVER_UUID, "world:100:64:-200", ReportStatus.PENDING,
                HANDLER_UUID, null, null, Instant.now(), null);
    }

    private ReportUpdateDto updateDto(UUID serverUuid, Long screenshotId) {
        return new ReportUpdateDto(REPORTER_UUID, REPORTED_UUID, CATEGORY_ID, "Verdacht auf Fliegen",
                serverUuid, "world:100:64:-200", ReportStatus.PENDING, HANDLER_UUID, null, screenshotId);
    }

    @Nested
    @DisplayName("createReport")
    class CreateReport {

        @Test
        @DisplayName("legt einen Report an und veröffentlicht ein ReportCreatedEvent")
        void shouldCreateReportAndPublishEvent() {
            ReportUpdateDto dto = updateDto(SERVER_UUID, SCREENSHOT_ID);

            when(reportMapper.toEntity(dto)).thenReturn(reportEntity);
            when(serverRepository.findById(SERVER_UUID)).thenReturn(Optional.of(serverEntity));
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.of(screenshotEntity));
            when(reportRepository.save(reportEntity)).thenReturn(reportEntity);
            when(reportMapper.toDto(reportEntity)).thenReturn(reportDto);

            ReportDto result = reportService.createReport(dto);

            assertThat(result).isEqualTo(reportDto);
            assertThat(reportEntity.getServer()).isSameAs(serverEntity);
            assertThat(reportEntity.getScreenshotEntity()).isSameAs(screenshotEntity);

            ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
            verify(rabbitTemplate).convertAndSend(
                    org.mockito.ArgumentMatchers.eq(RabbitMQConfiguration.EXCHANGE),
                    org.mockito.ArgumentMatchers.eq(""),
                    payload.capture());

            assertThat(payload.getValue()).isInstanceOf(ReportCreatedEvent.class);
            ReportCreatedEvent event = (ReportCreatedEvent) payload.getValue();
            assertThat(event.getEvent()).isEqualTo("report.created");
            assertThat(event.getReportId()).isEqualTo(REPORT_ID);
            assertThat(event.getServerUuid()).isEqualTo(SERVER_UUID);
            assertThat(event.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("wirft ServerNotFoundException, wenn der referenzierte Server fehlt")
        void shouldThrowWhenServerNotFound() {
            ReportUpdateDto dto = updateDto(SERVER_UUID, null);

            when(reportMapper.toEntity(dto)).thenReturn(reportEntity);
            when(serverRepository.findById(SERVER_UUID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.createReport(dto))
                    .isInstanceOf(ServerNotFoundException.class)
                    .hasMessageContaining(SERVER_UUID.toString());

            verify(reportRepository, never()).save(any());
            verifyNoInteractions(rabbitTemplate);
        }

        @Test
        @DisplayName("wirft ScreenshotNotFoundException, wenn der referenzierte Screenshot fehlt")
        void shouldThrowWhenScreenshotNotFound() {
            ReportUpdateDto dto = updateDto(SERVER_UUID, SCREENSHOT_ID);

            when(reportMapper.toEntity(dto)).thenReturn(reportEntity);
            when(serverRepository.findById(SERVER_UUID)).thenReturn(Optional.of(serverEntity));
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.createReport(dto))
                    .isInstanceOf(ScreenshotNotFoundException.class)
                    .hasMessageContaining(String.valueOf(SCREENSHOT_ID));

            verify(reportRepository, never()).save(any());
            verifyNoInteractions(rabbitTemplate);
        }

        @Test
        @DisplayName("schlägt keinen Screenshot nach, wenn keine screenshotId übergeben wurde")
        void shouldNotLookUpScreenshotWhenIdAbsent() {
            ReportUpdateDto dto = updateDto(SERVER_UUID, null);

            when(reportMapper.toEntity(dto)).thenReturn(reportEntity);
            when(serverRepository.findById(SERVER_UUID)).thenReturn(Optional.of(serverEntity));
            when(reportRepository.save(reportEntity)).thenReturn(reportEntity);
            when(reportMapper.toDto(reportEntity)).thenReturn(reportDto);

            reportService.createReport(dto);

            verifyNoInteractions(screenshotRepository);
        }

        @Test
        @Disabled("BUG: ReportService#createReport (reports/service/ReportService.java:69) liest "
                + "savedEntity.getServer().getServerUuid() bedingungslos, obwohl die "
                + "Server-Zuordnung optional ist (ReportsEntity#server ist nullable und das "
                + "Nachschlagen haengt an einem null-Check). Ein Report ohne Server endet daher in "
                + "einer NullPointerException statt in einem Event mit serverUuid = null. In "
                + "Produktion wird die NPE derzeit davon verdeckt, dass ReportMapperImpl#toEntity "
                + "immer eine leere ServerEntity erzeugt - die schlaegt dafuer beim Speichern als "
                + "transiente Referenz fehl (siehe ReportMapperTest#shouldSkipOptionalAssociations).")
        @DisplayName("legt einen Report ohne Server an und veröffentlicht ein Event ohne serverUuid")
        void shouldCreateReportWithoutServer() {
            ReportUpdateDto dto = updateDto(null, null);
            reportEntity.setServer(null);

            when(reportMapper.toEntity(dto)).thenReturn(reportEntity);
            when(reportRepository.save(reportEntity)).thenReturn(reportEntity);
            when(reportMapper.toDto(reportEntity)).thenReturn(reportDto);

            reportService.createReport(dto);

            verifyNoInteractions(serverRepository);

            ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
            verify(rabbitTemplate).convertAndSend(anyString(), anyString(), payload.capture());
            assertThat(((ReportCreatedEvent) payload.getValue()).getServerUuid()).isNull();
        }

        @Test
        @Disabled("BUG: ReportService laesst sich playerRepository und categoryRepository injizieren, "
                + "benutzt sie aber nie. Reporter, Reported, HandledBy und Kategorie werden daher "
                + "nicht validiert: der ReportMapper baut aus den reinen UUIDs/Ids transiente "
                + "Entities, sodass eine unbekannte Referenz erst als "
                + "Fremdschluesselverletzung der Datenbank auffaellt statt als "
                + "PlayerNotFoundException / CategoryNotFoundException.")
        @DisplayName("wirft PlayerNotFoundException, wenn der Reporter nicht existiert")
        void shouldValidateReporterExists() {
            ReportUpdateDto dto = updateDto(SERVER_UUID, null);

            when(reportMapper.toEntity(dto)).thenReturn(reportEntity);
            when(playerRepository.findByPlayerUuid(REPORTER_UUID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.createReport(dto))
                    .isInstanceOf(PlayerNotFoundException.class);

            verify(reportRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateReport")
    class UpdateReport {

        @Test
        @DisplayName("aktualisiert einen Report und veröffentlicht ein ReportUpdatedEvent")
        void shouldUpdateReportAndPublishEvent() {
            ReportUpdateDto dto = updateDto(SERVER_UUID, null);
            reportEntity.setReportStatus(ReportStatus.APPROVED);

            when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(reportEntity));
            when(reportMapper.partialUpdate(dto, reportEntity)).thenReturn(reportEntity);
            when(reportRepository.save(reportEntity)).thenReturn(reportEntity);
            when(reportMapper.toDto(reportEntity)).thenReturn(reportDto);

            ReportDto result = reportService.updateReport(REPORT_ID, dto);

            assertThat(result).isEqualTo(reportDto);

            ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
            verify(rabbitTemplate).convertAndSend(
                    org.mockito.ArgumentMatchers.eq(RabbitMQConfiguration.EXCHANGE),
                    org.mockito.ArgumentMatchers.eq(""),
                    payload.capture());

            assertThat(payload.getValue()).isInstanceOf(ReportUpdatedEvent.class);
            ReportUpdatedEvent event = (ReportUpdatedEvent) payload.getValue();
            assertThat(event.getEvent()).isEqualTo("report.updated");
            assertThat(event.getReportId()).isEqualTo(REPORT_ID);
            assertThat(event.getNewStatus()).isEqualTo(ReportStatus.APPROVED.name());
            assertThat(event.getHandledBy()).isEqualTo(HANDLER_UUID);
        }

        @Test
        @DisplayName("wirft ReportNotFoundException, wenn der Report nicht existiert")
        void shouldThrowWhenReportNotFound() {
            ReportUpdateDto dto = updateDto(SERVER_UUID, null);
            when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.updateReport(REPORT_ID, dto))
                    .isInstanceOf(ReportNotFoundException.class)
                    .hasMessageContaining(String.valueOf(REPORT_ID));

            verify(reportRepository, never()).save(any());
            verifyNoInteractions(rabbitTemplate);
        }
    }

    @Nested
    @DisplayName("deleteReport")
    class DeleteReport {

        @Test
        @DisplayName("löscht einen bestehenden Report ohne ein Event zu veröffentlichen")
        void shouldDeleteReport() {
            when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(reportEntity));

            reportService.deleteReport(REPORT_ID);

            verify(reportRepository).delete(reportEntity);
            verifyNoInteractions(rabbitTemplate);
        }

        @Test
        @DisplayName("wirft ReportNotFoundException, wenn der Report nicht existiert")
        void shouldThrowWhenReportNotFound() {
            when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.deleteReport(REPORT_ID))
                    .isInstanceOf(ReportNotFoundException.class);

            verify(reportRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("getReport, getReports und countReports")
    class ReadOperations {

        @Test
        @DisplayName("liefert einen Report zur id zurück")
        void shouldReturnReport() {
            when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(reportEntity));
            when(reportMapper.toDto(reportEntity)).thenReturn(reportDto);

            assertThat(reportService.getReport(REPORT_ID)).isEqualTo(reportDto);
        }

        @Test
        @DisplayName("wirft ReportNotFoundException, wenn der Report nicht existiert")
        void shouldThrowWhenReportNotFound() {
            when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.getReport(REPORT_ID))
                    .isInstanceOf(ReportNotFoundException.class);
        }

        @Test
        @DisplayName("liefert Reports absteigend nach Erstellungszeitpunkt sortiert zurück")
        void shouldReturnReportsSortedByCreatedAtDesc() {
            Pageable pageable = PageRequest.of(0, 10);
            when(reportRepository.findAllByOrderByCreatedAtDesc(pageable))
                    .thenReturn(new PageImpl<>(List.of(reportEntity)));
            when(reportMapper.toDto(reportEntity)).thenReturn(reportDto);

            Page<ReportDto> result = reportService.getReports(pageable);

            assertThat(result.getContent()).containsExactly(reportDto);
            verify(reportRepository).findAllByOrderByCreatedAtDesc(pageable);
        }

        @Test
        @DisplayName("gibt die Gesamtanzahl der Reports zurück")
        void shouldCountReports() {
            when(reportRepository.count()).thenReturn(13L);

            assertThat(reportService.countReports()).isEqualTo(13L);
        }
    }
}

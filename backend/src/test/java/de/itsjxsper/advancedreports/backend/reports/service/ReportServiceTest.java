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
import de.itsjxsper.advancedreports.common.model.report.ReportCreateDto;
import de.itsjxsper.advancedreports.common.model.report.ReportDto;
import de.itsjxsper.advancedreports.common.model.report.ReportUpdateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

    private PlayerEntity reporterEntity;
    private PlayerEntity reportedEntity;
    private PlayerEntity handlerEntity;
    private CategoryEntity categoryEntity;
    private ServerEntity serverEntity;
    private ScreenshotEntity screenshotEntity;
    private ReportsEntity reportEntity;
    private ReportDto reportDto;

    @BeforeEach
    void setUp() {
        reporterEntity = TestDataFactory.player(REPORTER_UUID, "Reporter");
        reportedEntity = TestDataFactory.player(REPORTED_UUID, "Reported");
        handlerEntity = TestDataFactory.player(HANDLER_UUID, "Handler");

        categoryEntity = TestDataFactory.category("cheating");
        categoryEntity.setId(CATEGORY_ID);

        serverEntity = TestDataFactory.server();
        serverEntity.setServerUuid(SERVER_UUID);

        screenshotEntity = TestDataFactory.screenshot("screenshots/2026-01-01/abc-screenshot.png");
        screenshotEntity.setId(SCREENSHOT_ID);

        reportEntity = TestDataFactory.report(reporterEntity, reportedEntity, handlerEntity, categoryEntity, serverEntity);
        reportEntity.setId(REPORT_ID);

        reportDto = new ReportDto(REPORT_ID, REPORTER_UUID, REPORTED_UUID, CATEGORY_ID,
                "Suspected of flying", SERVER_UUID, "world:100:64:-200", ReportStatus.PENDING,
                HANDLER_UUID, null, null, Instant.now(), null);
    }

    /**
     * The service now loads reporter, reported, categoryEntity and handledBy from the repositories
     * itself - previously those stayed mapper stubs carrying nothing but an id.
     */
    private void stubCoreAssociations() {
        when(playerRepository.findById(REPORTER_UUID)).thenReturn(Optional.of(reporterEntity));
        when(playerRepository.findById(REPORTED_UUID)).thenReturn(Optional.of(reportedEntity));
        when(playerRepository.findById(HANDLER_UUID)).thenReturn(Optional.of(handlerEntity));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(categoryEntity));
    }

    private ReportCreateDto createDto(UUID serverUuid, Long screenshotId) {
        return new ReportCreateDto(REPORTER_UUID, REPORTED_UUID, CATEGORY_ID, "Suspected of flying",
                serverUuid, "world:100:64:-200", ReportStatus.PENDING, HANDLER_UUID, null, screenshotId);
    }

    private ReportUpdateDto updateDto(UUID serverUuid, Long screenshotId) {
        return new ReportUpdateDto(REPORTER_UUID, REPORTED_UUID, CATEGORY_ID, "Suspected of flying",
                serverUuid, "world:100:64:-200", ReportStatus.PENDING, HANDLER_UUID, null, screenshotId);
    }

    @Nested
    @DisplayName("createReport")
    class CreateReport {

        @Test
        @DisplayName("creates a report and publishes a ReportCreatedEvent")
        void shouldCreateReportAndPublishEvent() {
            ReportCreateDto dto = createDto(SERVER_UUID, SCREENSHOT_ID);

            when(reportMapper.toEntity(dto)).thenReturn(reportEntity);
            stubCoreAssociations();
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
        @DisplayName("throws ServerNotFoundException when the referenced server is missing")
        void shouldThrowWhenServerNotFound() {
            ReportCreateDto dto = createDto(SERVER_UUID, null);

            when(reportMapper.toEntity(dto)).thenReturn(reportEntity);
            stubCoreAssociations();
            when(serverRepository.findById(SERVER_UUID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.createReport(dto))
                    .isInstanceOf(ServerNotFoundException.class)
                    .hasMessageContaining(SERVER_UUID.toString());

            verify(reportRepository, never()).save(any());
            verifyNoInteractions(rabbitTemplate);
        }

        @Test
        @DisplayName("throws ScreenshotNotFoundException when the referenced screenshot is missing")
        void shouldThrowWhenScreenshotNotFound() {
            ReportCreateDto dto = createDto(SERVER_UUID, SCREENSHOT_ID);

            when(reportMapper.toEntity(dto)).thenReturn(reportEntity);
            stubCoreAssociations();
            when(serverRepository.findById(SERVER_UUID)).thenReturn(Optional.of(serverEntity));
            when(screenshotRepository.findById(SCREENSHOT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.createReport(dto))
                    .isInstanceOf(ScreenshotNotFoundException.class)
                    .hasMessageContaining(String.valueOf(SCREENSHOT_ID));

            verify(reportRepository, never()).save(any());
            verifyNoInteractions(rabbitTemplate);
        }

        @Test
        @DisplayName("does not look up a screenshot when no screenshotId was passed")
        void shouldNotLookUpScreenshotWhenIdAbsent() {
            ReportCreateDto dto = createDto(SERVER_UUID, null);

            when(reportMapper.toEntity(dto)).thenReturn(reportEntity);
            stubCoreAssociations();
            when(serverRepository.findById(SERVER_UUID)).thenReturn(Optional.of(serverEntity));
            when(reportRepository.save(reportEntity)).thenReturn(reportEntity);
            when(reportMapper.toDto(reportEntity)).thenReturn(reportDto);

            reportService.createReport(dto);

            verifyNoInteractions(screenshotRepository);
        }

        @Test
        @DisplayName("creates a report without a server and publishes an event without serverUuid")
        void shouldCreateReportWithoutServer() {
            ReportCreateDto dto = createDto(null, null);
            reportEntity.setServer(null);

            when(reportMapper.toEntity(dto)).thenReturn(reportEntity);
            stubCoreAssociations();
            when(reportRepository.save(reportEntity)).thenReturn(reportEntity);
            when(reportMapper.toDto(reportEntity)).thenReturn(reportDto);

            reportService.createReport(dto);

            verifyNoInteractions(serverRepository);

            ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
            verify(rabbitTemplate).convertAndSend(anyString(), anyString(), payload.capture());
            assertThat(((ReportCreatedEvent) payload.getValue()).getServerUuid()).isNull();
        }

        @Test
        @DisplayName("throws PlayerNotFoundException when the reporter does not exist")
        void shouldValidateReporterExists() {
            ReportCreateDto dto = createDto(SERVER_UUID, null);

            when(reportMapper.toEntity(dto)).thenReturn(reportEntity);
            when(playerRepository.findById(REPORTER_UUID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.createReport(dto))
                    .isInstanceOf(PlayerNotFoundException.class);

            verify(reportRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateReport")
    class UpdateReport {

        @Test
        @DisplayName("updates a report and publishes a ReportUpdatedEvent")
        void shouldUpdateReportAndPublishEvent() {
            ReportUpdateDto dto = updateDto(SERVER_UUID, null);
            reportEntity.setReportStatus(ReportStatus.APPROVED);

            when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(reportEntity));
            when(reportMapper.partialUpdate(dto, reportEntity)).thenReturn(reportEntity);
            // On PATCH too, associations that were sent are loaded fresh instead of overwriting the
            // managed entities.
            stubCoreAssociations();
            when(serverRepository.findById(SERVER_UUID)).thenReturn(Optional.of(serverEntity));
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
        @DisplayName("throws ReportNotFoundException when the report does not exist")
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
        @DisplayName("deletes an existing report without publishing an event")
        void shouldDeleteReport() {
            when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(reportEntity));

            reportService.deleteReport(REPORT_ID);

            verify(reportRepository).delete(reportEntity);
            verifyNoInteractions(rabbitTemplate);
        }

        @Test
        @DisplayName("throws ReportNotFoundException when the report does not exist")
        void shouldThrowWhenReportNotFound() {
            when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.deleteReport(REPORT_ID))
                    .isInstanceOf(ReportNotFoundException.class);

            verify(reportRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("getReport, getReports and countReports")
    class ReadOperations {

        @Test
        @DisplayName("returns the report for the id")
        void shouldReturnReport() {
            when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(reportEntity));
            when(reportMapper.toDto(reportEntity)).thenReturn(reportDto);

            assertThat(reportService.getReport(REPORT_ID)).isEqualTo(reportDto);
        }

        @Test
        @DisplayName("throws ReportNotFoundException when the report does not exist")
        void shouldThrowWhenReportNotFound() {
            when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.getReport(REPORT_ID))
                    .isInstanceOf(ReportNotFoundException.class);
        }

        @Test
        @DisplayName("returns reports sorted descending by creation time")
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
        @DisplayName("returns the total number of reports")
        void shouldCountReports() {
            when(reportRepository.count()).thenReturn(13L);

            assertThat(reportService.countReports()).isEqualTo(13L);
        }
    }
}

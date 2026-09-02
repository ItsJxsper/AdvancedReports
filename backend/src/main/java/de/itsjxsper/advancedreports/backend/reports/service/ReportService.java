package de.itsjxsper.advancedreports.backend.reports.service;

import de.itsjxsper.advancedreports.backend.category.data.entity.CategoryEntity;
import de.itsjxsper.advancedreports.backend.category.data.repository.CategoryRepository;
import de.itsjxsper.advancedreports.backend.category.exceptions.CategoryNotFoundException;
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
import de.itsjxsper.advancedreports.common.enums.report.ReportStatus;
import de.itsjxsper.advancedreports.common.model.report.ReportCreateDto;
import de.itsjxsper.advancedreports.common.model.report.ReportDto;
import de.itsjxsper.advancedreports.common.model.report.ReportUpdateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository reportRepository;
    private final PlayerRepository playerRepository;
    private final CategoryRepository categoryRepository;
    private final ServerRepository serverRepository;
    private final ScreenshotRepository screenshotRepository;
    private final ReportMapper reportMapper;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public ReportDto createReport(ReportCreateDto reportCreateDto) {
        log.debug("Creating report for reporter={} reported={}", reportCreateDto.reporterUUID(), reportCreateDto.reportedUUID());

        var reportEntity = this.reportMapper.toEntity(reportCreateDto);

        // A freshly filed report is PENDING - the client does not have to send that.
        if (reportEntity.getReportStatus() == null) {
            reportEntity.setReportStatus(ReportStatus.PENDING);
        }

        // Only server and screenshot used to be resolved; reporter, reported, categoryEntity and
        // handledBy stayed mapper stubs carrying nothing but an id. An unknown player or category
        // id therefore surfaced as an FK violation instead of a clean 404.
        reportEntity.setReporter(requirePlayer(reportCreateDto.reporterUUID()));
        reportEntity.setReported(requirePlayer(reportCreateDto.reportedUUID()));
        reportEntity.setCategoryEntity(requireCategory(reportCreateDto.categoryId()));
        reportEntity.setHandledBy(findPlayer(reportCreateDto.handledByUUID()));
        reportEntity.setServer(findServer(reportCreateDto.serverUUID()));
        reportEntity.setScreenshotEntity(findScreenshot(reportCreateDto.screenshotId()));

        ReportsEntity savedEntity = this.reportRepository.save(reportEntity);
        log.debug("Created report with id={}", savedEntity.getId());

        // getServer() is optional - accessing it directly threw an NPE for every report without a
        // server, after the row had already been written.
        this.rabbitTemplate.convertAndSend(
                RabbitMQConfiguration.EXCHANGE,
                "",
                new ReportCreatedEvent(
                        savedEntity.getId(),
                        savedEntity.getServer() != null ? savedEntity.getServer().getServerUuid() : null,
                        Instant.now())
        );

        return this.reportMapper.toDto(savedEntity);
    }

    @Transactional
    public ReportDto updateReport(Long reportId, ReportUpdateDto reportUpdateDto) {
        log.debug("Updating report with id={}", reportId);

        ReportsEntity reportEntity = this.reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportNotFoundException(reportId));

        reportEntity = this.reportMapper.partialUpdate(reportUpdateDto, reportEntity);

        // Only touch associations that were actually sent - the mapper deliberately leaves them
        // alone instead of overwriting the loaded entities.
        if (reportUpdateDto.reporterUUID() != null) {
            reportEntity.setReporter(requirePlayer(reportUpdateDto.reporterUUID()));
        }
        if (reportUpdateDto.reportedUUID() != null) {
            reportEntity.setReported(requirePlayer(reportUpdateDto.reportedUUID()));
        }
        if (reportUpdateDto.categoryId() != null) {
            reportEntity.setCategoryEntity(requireCategory(reportUpdateDto.categoryId()));
        }
        if (reportUpdateDto.handledByUUID() != null) {
            reportEntity.setHandledBy(requirePlayer(reportUpdateDto.handledByUUID()));
        }
        if (reportUpdateDto.serverUUID() != null) {
            reportEntity.setServer(findServer(reportUpdateDto.serverUUID()));
        }
        if (reportUpdateDto.screenshotId() != null) {
            reportEntity.setScreenshotEntity(findScreenshot(reportUpdateDto.screenshotId()));
        }

        ReportsEntity savedEntity = this.reportRepository.save(reportEntity);
        log.debug("Updated report with id={}", savedEntity.getId());

        this.rabbitTemplate.convertAndSend(
                RabbitMQConfiguration.EXCHANGE,
                "",
                new ReportUpdatedEvent(
                        savedEntity.getId(),
                        savedEntity.getReportStatus() != null ? savedEntity.getReportStatus().name() : null,
                        savedEntity.getHandledBy() != null ? savedEntity.getHandledBy().getPlayerUuid() : null,
                        Instant.now())
        );

        return this.reportMapper.toDto(savedEntity);
    }

    @Transactional
    public void deleteReport(Long reportId) {
        log.debug("Deleting report with id={}", reportId);

        ReportsEntity reportEntity = this.reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportNotFoundException(reportId));

        this.reportRepository.delete(reportEntity);
        log.debug("Deleted report with id={}", reportId);
    }

    public ReportDto getReport(Long reportId) {
        log.debug("Fetching report with id={}", reportId);

        return this.reportRepository.findById(reportId)
                .map(reportMapper::toDto)
                .orElseThrow(() -> new ReportNotFoundException(reportId));
    }

    public Page<ReportDto> getReports(Pageable pageable) {
        log.debug("Fetching reports with pageable={}", pageable);

        return this.reportRepository.findAllByOrderByCreatedAtDesc(pageable).map(reportMapper::toDto);
    }

    public long countReports() {
        long count = this.reportRepository.count();
        log.debug("Counted reports={}", count);
        return count;
    }

    private PlayerEntity requirePlayer(UUID playerUuid) {
        if (playerUuid == null) {
            throw new IllegalArgumentException("Player UUID is required");
        }
        return this.playerRepository.findById(playerUuid)
                .orElseThrow(() -> new PlayerNotFoundException(playerUuid));
    }

    private PlayerEntity findPlayer(UUID playerUuid) {
        return playerUuid == null ? null : requirePlayer(playerUuid);
    }

    private CategoryEntity requireCategory(Long categoryId) {
        if (categoryId == null) {
            throw new IllegalArgumentException("Category id is required");
        }
        return this.categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    private ServerEntity findServer(UUID serverUuid) {
        return serverUuid == null ? null : this.serverRepository.findById(serverUuid)
                .orElseThrow(() -> new ServerNotFoundException(serverUuid));
    }

    private ScreenshotEntity findScreenshot(Long screenshotId) {
        return screenshotId == null ? null : this.screenshotRepository.findById(screenshotId)
                .orElseThrow(() -> new ScreenshotNotFoundException(screenshotId));
    }
}

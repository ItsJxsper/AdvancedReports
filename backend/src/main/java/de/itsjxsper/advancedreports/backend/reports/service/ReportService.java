package de.itsjxsper.advancedreports.backend.reports.service;

import de.itsjxsper.advancedreports.backend.categories.data.repository.CategoryRepository;
import de.itsjxsper.advancedreports.backend.config.RabbitMQConfiguration;
import de.itsjxsper.advancedreports.backend.messaging.events.ReportCreatedEvent;
import de.itsjxsper.advancedreports.backend.messaging.events.ReportUpdatedEvent;
import de.itsjxsper.advancedreports.backend.player.data.repository.PlayerRepository;
import de.itsjxsper.advancedreports.backend.reports.data.entity.ReportsEntity;
import de.itsjxsper.advancedreports.backend.reports.data.repository.ReportRepository;
import de.itsjxsper.advancedreports.backend.reports.exceptions.ReportNotFoundException;
import de.itsjxsper.advancedreports.backend.reports.mapper.ReportMapper;
import de.itsjxsper.advancedreports.backend.reports.model.ReportDto;
import de.itsjxsper.advancedreports.backend.reports.model.ReportUpdateDto;
import de.itsjxsper.advancedreports.backend.screenshot.data.repository.ScreenshotRepository;
import de.itsjxsper.advancedreports.backend.screenshot.exceptions.ScreenshotNotFoundException;
import de.itsjxsper.advancedreports.backend.server.data.repository.ServerRepository;
import de.itsjxsper.advancedreports.backend.server.exceptions.ServerNotFoundException;
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
    public ReportDto createReport(ReportUpdateDto reportUpdateDto) {
        log.debug("Creating report for reporter={} reported={}", reportUpdateDto.reporterUUID(), reportUpdateDto.reportedUUID());

        var reportEntity = this.reportMapper.toEntity(reportUpdateDto);

        if (reportUpdateDto.serverUUID().isPresent()) {
            UUID serverUUID = reportUpdateDto.serverUUID().orElseThrow();
            reportEntity.setServer(this.serverRepository.findById(serverUUID)
                    .orElseThrow(() -> new ServerNotFoundException(serverUUID)));
        }

        if (reportUpdateDto.screenshotId().isPresent()) {
            Long screenshotId = reportUpdateDto.screenshotId().orElseThrow();
            reportEntity.setScreenshotEntity(this.screenshotRepository.findById(screenshotId)
                    .orElseThrow(() -> new ScreenshotNotFoundException(screenshotId)));
        }

        ReportsEntity savedEntity = this.reportRepository.save(reportEntity);
        log.debug("Created report with id={}", savedEntity.getId());

        this.rabbitTemplate.convertAndSend(
                RabbitMQConfiguration.EXCHANGE,
                "",
                new ReportCreatedEvent(savedEntity.getId(), savedEntity.getServer().getServerUuid(), Instant.now())
        );

        return this.reportMapper.toDto(savedEntity);
    }

    @Transactional
    public ReportDto updateReport(Long reportId, ReportUpdateDto reportUpdateDto) {
        log.debug("Updating report with id={}", reportId);

        ReportsEntity reportEntity = this.reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportNotFoundException(reportId));

        reportEntity = this.reportMapper.partialUpdate(reportUpdateDto, reportEntity);

        ReportsEntity savedEntity = this.reportRepository.save(reportEntity);
        log.debug("Updated report with id={}", savedEntity.getId());

        this.rabbitTemplate.convertAndSend(
                RabbitMQConfiguration.EXCHANGE,
                "",
                new ReportUpdatedEvent(savedEntity.getId(), savedEntity.getStatus().name(), savedEntity.getHandledBy().getPlayerUuid(), Instant.now())
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

}


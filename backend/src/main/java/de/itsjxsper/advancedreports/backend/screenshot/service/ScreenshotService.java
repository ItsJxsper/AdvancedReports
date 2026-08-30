package de.itsjxsper.advancedreports.backend.screenshot.service;

import de.itsjxsper.advancedreports.backend.config.RabbitMQConfiguration;
import de.itsjxsper.advancedreports.backend.messaging.events.ScreenshotReadyEvent;
import de.itsjxsper.advancedreports.backend.reports.data.entity.ReportsEntity;
import de.itsjxsper.advancedreports.backend.screenshot.data.entity.ScreenshotEntity;
import de.itsjxsper.advancedreports.backend.screenshot.data.repository.ScreenshotRepository;
import de.itsjxsper.advancedreports.backend.screenshot.exceptions.ScreenshotNotFoundException;
import de.itsjxsper.advancedreports.backend.screenshot.exceptions.ScreenshotUploadIncompleteException;
import de.itsjxsper.advancedreports.backend.screenshot.mapper.ScreenshotMapper;
import de.itsjxsper.advancedreports.common.enums.screenshot.UploadStatus;
import de.itsjxsper.advancedreports.common.model.screenshot.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScreenshotService {

    private final ScreenshotRepository screenshotRepository;
    private final S3ScreenshotStorageService screenshotStorageService;
    private final RabbitTemplate rabbitTemplate;

    private final ScreenshotMapper screenshotMapper;

    @Value("${aws.s3.max-upload-size-bytes:10485760}")
    private long maxUploadSizeBytes;

    @Transactional
    public ScreenshotDto createScreenshot(ScreenshotUpdateDto screenshotUpdateDto) {
        log.debug("Creating screenshot with url={} and status={}", screenshotUpdateDto.s3Url(), screenshotUpdateDto.uploadStatus());

        ScreenshotEntity screenshotEntity = new ScreenshotEntity();
        screenshotEntity = this.screenshotMapper.partialUpdateScreenshotEntity(screenshotUpdateDto, screenshotEntity);

        if (screenshotEntity.getUploadStatus() == null) {
            screenshotEntity.setUploadStatus(UploadStatus.SUCCESS);
        }

        ScreenshotEntity savedEntity = this.screenshotRepository.save(screenshotEntity);
        log.debug("Created screenshot with id={}", savedEntity.getId());

        return this.screenshotMapper.toScreenshotDto(savedEntity);
    }

    /**
     * Reserves an object key, persists the metadata as {@link UploadStatus#PENDING} and hands the caller
     * a presigned URL so it can upload the file straight to S3 without the bytes passing through here.
     */
    @Transactional
    public ScreenshotUploadUrlDto requestUpload(ScreenshotUploadRequestDto uploadRequestDto) {
        log.debug("Requesting upload url for file name={} size={}",
                uploadRequestDto.originalFilename(), uploadRequestDto.fileSizeBytes());

        long fileSizeBytes = uploadRequestDto.fileSizeBytes();
        if (fileSizeBytes <= 0) {
            throw new IllegalArgumentException("Screenshot file size must be greater than zero");
        }
        if (fileSizeBytes > this.maxUploadSizeBytes) {
            throw new IllegalArgumentException("Screenshot file size " + fileSizeBytes
                    + " exceeds the maximum of " + this.maxUploadSizeBytes + " bytes");
        }

        var presignedUpload = this.screenshotStorageService.presignUpload(
                uploadRequestDto.originalFilename(), uploadRequestDto.contentType(), fileSizeBytes);

        ScreenshotEntity screenshotEntity = new ScreenshotEntity();
        screenshotEntity.setS3ObjectKey(presignedUpload.objectKey());
        screenshotEntity.setS3Url(presignedUpload.storageUri());
        screenshotEntity.setOriginalFilename(presignedUpload.originalFilename());
        screenshotEntity.setContentType(presignedUpload.contentType());
        screenshotEntity.setFileSizeBytes(fileSizeBytes);
        screenshotEntity.setUploadStatus(UploadStatus.PENDING);

        ScreenshotEntity savedEntity = this.screenshotRepository.save(screenshotEntity);
        log.debug("Reserved screenshot id={} objectKey={}", savedEntity.getId(), savedEntity.getS3ObjectKey());

        return new ScreenshotUploadUrlDto(
                savedEntity.getId(),
                presignedUpload.objectKey(),
                presignedUpload.uploadUrl(),
                "PUT",
                presignedUpload.requiredHeaders(),
                presignedUpload.expiresAt(),
                savedEntity.getUploadStatus()
        );
    }

    /**
     * Confirms a client-side upload. The object is verified against S3 rather than trusted, and the real
     * size and content type replace the values the client declared up front.
     * <p>
     * A missing object marks the screenshot as {@link UploadStatus#FAILED} and reports a conflict. That
     * write has to survive the exception, hence the {@code noRollbackFor}.
     */
    @Transactional(noRollbackFor = ScreenshotUploadIncompleteException.class)
    public ScreenshotDto completeUpload(Long screenshotId) {
        log.debug("Completing screenshot upload with id={}", screenshotId);

        ScreenshotEntity screenshotEntity = findScreenshotEntity(screenshotId);
        if (screenshotEntity == null) {
            throw new ScreenshotNotFoundException(screenshotId);
        }

        // Ein erneuter Aufruf nach einem Client-Retry darf nicht erneut ein Event ausloesen.
        if (screenshotEntity.getUploadStatus() == UploadStatus.SUCCESS) {
            log.debug("Screenshot id={} was already completed", screenshotId);
            return this.screenshotMapper.toScreenshotDto(screenshotEntity);
        }

        String objectKey = screenshotEntity.getS3ObjectKey();
        if (objectKey == null || objectKey.isBlank()) {
            throw new ScreenshotUploadIncompleteException(screenshotId);
        }

        var objectMetadata = this.screenshotStorageService.headObject(objectKey);
        if (objectMetadata.isEmpty()) {
            screenshotEntity.setUploadStatus(UploadStatus.FAILED);
            this.screenshotRepository.save(screenshotEntity);
            log.debug("Screenshot id={} objectKey={} is missing in S3, marked as FAILED", screenshotId, objectKey);
            throw new ScreenshotUploadIncompleteException(screenshotId);
        }

        screenshotEntity.setFileSizeBytes(objectMetadata.get().contentLength());
        if (objectMetadata.get().contentType() != null && !objectMetadata.get().contentType().isBlank()) {
            screenshotEntity.setContentType(objectMetadata.get().contentType());
        }
        screenshotEntity.setUploadStatus(UploadStatus.SUCCESS);

        ScreenshotEntity savedEntity = this.screenshotRepository.save(screenshotEntity);
        log.debug("Completed screenshot id={} objectKey={} size={}",
                savedEntity.getId(), savedEntity.getS3ObjectKey(), savedEntity.getFileSizeBytes());

        publishScreenshotReady(savedEntity);

        return this.screenshotMapper.toScreenshotDto(savedEntity);
    }

    /**
     * Hands the caller a presigned URL so it can fetch the file straight from S3.
     */
    public ScreenshotDownloadUrlDto getDownloadUrl(Long screenshotId) {
        log.debug("Building download url for screenshot with id={}", screenshotId);

        ScreenshotEntity screenshotEntity = findScreenshotEntity(screenshotId);
        if (screenshotEntity == null) {
            throw new ScreenshotNotFoundException(screenshotId);
        }

        String objectKey = screenshotEntity.getS3ObjectKey();
        if (objectKey == null || objectKey.isBlank() || screenshotEntity.getUploadStatus() != UploadStatus.SUCCESS) {
            throw new ScreenshotUploadIncompleteException(screenshotId);
        }

        var presignedDownload = this.screenshotStorageService.presignDownload(
                objectKey, screenshotEntity.getOriginalFilename(), screenshotEntity.getContentType());

        return new ScreenshotDownloadUrlDto(
                screenshotEntity.getId(),
                presignedDownload.downloadUrl(),
                presignedDownload.originalFilename(),
                presignedDownload.contentType(),
                presignedDownload.expiresAt()
        );
    }

    @Transactional
    public ScreenshotDto updateScreenshot(Long screenshotId, ScreenshotUpdateDto screenshotUpdateDto) {
        log.debug("Updating screenshot with id={}", screenshotId);

        ScreenshotEntity screenshotEntity = findScreenshotEntity(screenshotId);
        if (screenshotEntity == null) {
            return null;
        }

        screenshotEntity = this.screenshotMapper.partialUpdateScreenshotEntity(screenshotUpdateDto, screenshotEntity);

        ScreenshotEntity savedEntity = this.screenshotRepository.save(screenshotEntity);
        log.debug("Updated screenshot with id={}", savedEntity.getId());

        return this.screenshotMapper.toScreenshotDto(savedEntity);
    }

    @Transactional
    public void deleteScreenshot(Long screenshotId) {
        log.debug("Deleting screenshot with id={}", screenshotId);

        ScreenshotEntity screenshotEntity = findScreenshotEntity(screenshotId);
        if (screenshotEntity == null) {
            return;
        }

        if (screenshotEntity.getS3ObjectKey() != null && !screenshotEntity.getS3ObjectKey().isBlank()) {
            this.screenshotStorageService.delete(screenshotEntity.getS3ObjectKey());
        }

        this.screenshotRepository.delete(screenshotEntity);
        log.debug("Deleted screenshot with id={}", screenshotId);
    }

    public ScreenshotDto getScreenshot(Long screenshotId) {
        log.debug("Fetching screenshot with id={}", screenshotId);

        ScreenshotEntity screenshotEntity = findScreenshotEntity(screenshotId);
        if (screenshotEntity == null) {
            return null;
        }

        return this.screenshotMapper.toScreenshotDto(screenshotEntity);
    }

    public Page<ScreenshotDto> getScreenshots(Pageable pageable) {
        log.debug("Fetching screenshots with pageable={}", pageable);

        return this.screenshotRepository.findAll(pageable).map(this.screenshotMapper::toScreenshotDto);
    }

    public long countScreenshots() {
        long count = this.screenshotRepository.count();
        log.debug("Counted screenshots={}", count);
        return count;
    }

    private void publishScreenshotReady(ScreenshotEntity screenshotEntity) {
        Long reportId = screenshotEntity.getReportsEntities().stream()
                .findFirst()
                .map(ReportsEntity::getId)
                .orElse(null);

        this.rabbitTemplate.convertAndSend(
                RabbitMQConfiguration.EXCHANGE,
                "",
                new ScreenshotReadyEvent(
                        "screenshot.ready",
                        reportId,
                        screenshotEntity.getId(),
                        screenshotEntity.getS3Url(),
                        Instant.now()
                )
        );
    }

    private ScreenshotEntity findScreenshotEntity(Long screenshotId) {
        return this.screenshotRepository.findById(screenshotId).orElse(null);
    }
}

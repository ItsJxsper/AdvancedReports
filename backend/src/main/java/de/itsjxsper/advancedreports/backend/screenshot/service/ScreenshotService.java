package de.itsjxsper.advancedreports.backend.screenshot.service;

import de.itsjxsper.advancedreports.backend.screenshot.data.entity.ScreenshotEntity;
import de.itsjxsper.advancedreports.backend.screenshot.data.repository.ScreenshotRepository;
import de.itsjxsper.advancedreports.backend.screenshot.enums.UploadStatus;
import de.itsjxsper.advancedreports.backend.screenshot.model.ScreenshotDto;
import de.itsjxsper.advancedreports.backend.screenshot.model.ScreenshotUpdateDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScreenshotService {

    private final ScreenshotRepository screenshotRepository;
    private final S3ScreenshotStorageService screenshotStorageService;
    private final RabbitTemplate rabbitTemplate;

    public ScreenshotDto createScreenshot(ScreenshotUpdateDto screenshotUpdateDto) {
        log.debug("Creating screenshot with url={} and status={}", screenshotUpdateDto.s3Url(), screenshotUpdateDto.uploadStatus());

        ScreenshotEntity screenshotEntity = new ScreenshotEntity();
        applyUpdateDto(screenshotEntity, screenshotUpdateDto);

        if (screenshotEntity.getUploadStatus() == null) {
            screenshotEntity.setUploadStatus(UploadStatus.SUCCESS);
        }

        ScreenshotEntity savedEntity = this.screenshotRepository.save(screenshotEntity);
        log.debug("Created screenshot with id={}", savedEntity.getId());

        return toDto(savedEntity);
    }

    public ScreenshotDto uploadScreenshot(MultipartFile file) {
        log.debug("Uploading screenshot file name={} size={}", file != null ? file.getOriginalFilename() : null, file != null ? file.getSize() : null);

        var storedScreenshot = this.screenshotStorageService.upload(file);

        ScreenshotEntity screenshotEntity = new ScreenshotEntity();
        applyStoredScreenshot(screenshotEntity, storedScreenshot);
        screenshotEntity.setUploadStatus(UploadStatus.SUCCESS);

        ScreenshotEntity savedEntity = this.screenshotRepository.save(screenshotEntity);
        log.debug("Uploaded screenshot id={} objectKey={}", savedEntity.getId(), savedEntity.getS3ObjectKey());

        return toDto(savedEntity);
    }

    public ScreenshotDto updateScreenshot(Long screenshotId, ScreenshotUpdateDto screenshotUpdateDto) {
        log.debug("Updating screenshot with id={}", screenshotId);

        ScreenshotEntity screenshotEntity = findScreenshotEntity(screenshotId);
        if (screenshotEntity == null) {
            return null;
        }

        screenshotUpdateDto.s3ObjectKey().ifPresent(screenshotEntity::setS3ObjectKey);
        screenshotUpdateDto.originalFilename().ifPresent(screenshotEntity::setOriginalFilename);
        screenshotUpdateDto.contentType().ifPresent(screenshotEntity::setContentType);
        screenshotUpdateDto.fileSizeBytes().ifPresent(screenshotEntity::setFileSizeBytes);
        screenshotUpdateDto.uploadStatus().ifPresent(screenshotEntity::setUploadStatus);

        ScreenshotEntity savedEntity = this.screenshotRepository.save(screenshotEntity);
        log.debug("Updated screenshot with id={}", savedEntity.getId());

        return toDto(savedEntity);
    }

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

        return toDto(screenshotEntity);
    }

    public byte[] downloadScreenshot(Long screenshotId) {
        log.debug("Downloading screenshot with id={}", screenshotId);

        ScreenshotEntity screenshotEntity = findScreenshotEntity(screenshotId);
        if (screenshotEntity == null) {
            return null;
        }

        if (screenshotEntity.getS3ObjectKey() == null || screenshotEntity.getS3ObjectKey().isBlank()) {
            throw new IllegalStateException("Screenshot with ID " + screenshotId + " has no S3 object key");
        }

        var downloadedScreenshot = this.screenshotStorageService.download(screenshotEntity.getS3ObjectKey());
        return downloadedScreenshot.content();
    }

    public Page<ScreenshotDto> getScreenshots(Pageable pageable) {
        log.debug("Fetching screenshots with pageable={}", pageable);

        return this.screenshotRepository.findAll(pageable)
                .map(this::toDto);
    }

    public long countScreenshots() {
        long count = this.screenshotRepository.count();
        log.debug("Counted screenshots={}", count);
        return count;
    }

    private ScreenshotDto toDto(ScreenshotEntity screenshotEntity) {
        return new ScreenshotDto(
                screenshotEntity.getId(),
                screenshotEntity.getS3Url(),
                screenshotEntity.getS3ObjectKey(),
                screenshotEntity.getOriginalFilename(),
                screenshotEntity.getContentType(),
                screenshotEntity.getFileSizeBytes(),
                screenshotEntity.getUploadStatus()
        );
    }

    private void applyUpdateDto(ScreenshotEntity screenshotEntity, ScreenshotUpdateDto screenshotUpdateDto) {
        screenshotUpdateDto.s3ObjectKey().ifPresent(screenshotEntity::setS3ObjectKey);
        screenshotUpdateDto.originalFilename().ifPresent(screenshotEntity::setOriginalFilename);
        screenshotUpdateDto.contentType().ifPresent(screenshotEntity::setContentType);
        screenshotUpdateDto.fileSizeBytes().ifPresent(screenshotEntity::setFileSizeBytes);
        screenshotUpdateDto.uploadStatus().ifPresent(screenshotEntity::setUploadStatus);
    }

    private void applyStoredScreenshot(ScreenshotEntity screenshotEntity, S3ScreenshotStorageService.StoredScreenshot storedScreenshot) {
        screenshotEntity.setS3ObjectKey(storedScreenshot.objectKey());
        screenshotEntity.setS3Url(storedScreenshot.storageUri());
        screenshotEntity.setOriginalFilename(storedScreenshot.originalFilename());
        screenshotEntity.setContentType(storedScreenshot.contentType());
        screenshotEntity.setFileSizeBytes(storedScreenshot.fileSizeBytes());
    }

    private ScreenshotEntity findScreenshotEntity(Long screenshotId) {
        return this.screenshotRepository.findById(screenshotId).orElse(null);
    }
}

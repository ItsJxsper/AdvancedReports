package de.itsjxsper.advancedreports.backend.screenshot.service;

import de.itsjxsper.advancedreports.backend.screenshot.data.entity.ScreenshotEntity;
import de.itsjxsper.advancedreports.backend.screenshot.data.repository.ScreenshotRepository;
import de.itsjxsper.advancedreports.backend.screenshot.mapper.ScreenshotMapper;
import de.itsjxsper.advancedreports.common.enums.screenshot.UploadStatus;
import de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotDto;
import de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotUpdateDto;
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

    private final ScreenshotMapper screenshotMapper;

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

    public ScreenshotDto uploadScreenshot(MultipartFile file) {
        log.debug("Uploading screenshot file name={} size={}", file != null ? file.getOriginalFilename() : null, file != null ? file.getSize() : null);

        var storedScreenshot = this.screenshotStorageService.upload(file);

        ScreenshotEntity screenshotEntity = new ScreenshotEntity();
        applyStoredScreenshot(screenshotEntity, storedScreenshot);
        screenshotEntity.setUploadStatus(UploadStatus.SUCCESS);

        ScreenshotEntity savedEntity = this.screenshotRepository.save(screenshotEntity);
        log.debug("Uploaded screenshot id={} objectKey={}", savedEntity.getId(), savedEntity.getS3ObjectKey());

        return this.screenshotMapper.toScreenshotDto(savedEntity);
    }

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

        return this.screenshotRepository.findAll(pageable).map(this.screenshotMapper::toScreenshotDto);
    }

    public long countScreenshots() {
        long count = this.screenshotRepository.count();
        log.debug("Counted screenshots={}", count);
        return count;
    }

    private void applyStoredScreenshot(ScreenshotEntity screenshotEntity, S3ScreenshotStorageService.StoredScreenshot storedScreenshot) {
        screenshotEntity.setS3ObjectKey(storedScreenshot.objectKey());
        screenshotEntity.setOriginalFilename(storedScreenshot.originalFilename());
        screenshotEntity.setContentType(storedScreenshot.contentType());
        screenshotEntity.setFileSizeBytes(storedScreenshot.fileSizeBytes());
    }

    private ScreenshotEntity findScreenshotEntity(Long screenshotId) {
        return this.screenshotRepository.findById(screenshotId).orElse(null);
    }
}

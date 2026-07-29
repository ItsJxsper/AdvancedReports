package de.itsjxsper.advancedreports.backend.screenshot.mapper;

import de.itsjxsper.advancedreports.backend.screenshot.data.entity.ScreenshotEntity;
import de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotDto;
import de.itsjxsper.advancedreports.common.model.screenshot.ScreenshotUpdateDto;
import org.mapstruct.*;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface ScreenshotMapper {
    ScreenshotEntity toScreenshotEntity(ScreenshotDto screenshotDto);

    ScreenshotDto toScreenshotDto(ScreenshotEntity screenshotEntity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    ScreenshotEntity partialUpdateScreenshotEntity(ScreenshotDto screenshotDto, @MappingTarget ScreenshotEntity screenshotEntity);

    ScreenshotEntity toScreenshotEntity(ScreenshotUpdateDto screenshotUpdateDto);

    @Mapping(target = "s3Url", source = "screenshotEntity.s3Url")
    @Mapping(target = "s3ObjectKey", source = "screenshotEntity.s3ObjectKey")
    @Mapping(target = "originalFilename", source = "screenshotEntity.originalFilename")
    @Mapping(target = "contentType", source = "screenshotEntity.contentType")
    @Mapping(target = "fileSizeBytes", source = "screenshotEntity.fileSizeBytes")
    @Mapping(target = "uploadStatus", source = "screenshotEntity.uploadStatus")
    ScreenshotDto toScreenshotDto(ScreenshotEntity screenshotEntity, ScreenshotUpdateDto screenshotUpdateDto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    ScreenshotEntity partialUpdateScreenshotEntity(ScreenshotUpdateDto screenshotUpdateDto, @MappingTarget ScreenshotEntity screenshotEntity);
}
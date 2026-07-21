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

    ScreenshotDto toScreenshotDto(ScreenshotEntity screenshotEntity, ScreenshotUpdateDto screenshotUpdateDto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    ScreenshotEntity partialUpdateScreenshotEntity(ScreenshotUpdateDto screenshotUpdateDto, @MappingTarget ScreenshotEntity screenshotEntity);
}
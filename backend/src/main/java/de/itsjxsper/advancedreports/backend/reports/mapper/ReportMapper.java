package de.itsjxsper.advancedreports.backend.reports.mapper;

import de.itsjxsper.advancedreports.backend.reports.data.entity.ReportsEntity;
import de.itsjxsper.advancedreports.common.model.report.ReportDto;
import de.itsjxsper.advancedreports.common.model.report.ReportUpdateDto;
import org.mapstruct.*;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface ReportMapper {

    @Mappings({
            @Mapping(source = "reporter.playerUuid", target = "reporterUUID"),
            @Mapping(source = "reported.playerUuid", target = "reportedUUID"),
            @Mapping(source = "categoryEntity.id", target = "categoryId"),
            @Mapping(source = "server.serverUuid", target = "serverUUID"),
            @Mapping(source = "handledBy.playerUuid", target = "handledByUUID"),
            @Mapping(source = "screenshotEntity.id", target = "screenshotId")
    })
    ReportDto toDto(ReportsEntity entity);

    @Mappings({
            @Mapping(source = "reporterUUID", target = "reporter.playerUuid"),
            @Mapping(source = "reportedUUID", target = "reported.playerUuid"),
            @Mapping(source = "categoryId", target = "categoryEntity.id"),
            @Mapping(source = "reason", target = "reason"),
            @Mapping(source = "serverUUID", target = "server.serverUuid"),
            @Mapping(source = "location", target = "location"),
            @Mapping(source = "reportStatus", target = "reportStatus"),
            @Mapping(source = "handledByUUID", target = "handledBy.playerUuid"),
            @Mapping(source = "handlerNote", target = "handlerNote"),
            @Mapping(source = "screenshotId", target = "screenshotEntity.id")
    })
    ReportsEntity toEntity(ReportUpdateDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mappings({
            @Mapping(source = "reporterUUID", target = "reporter.playerUuid"),
            @Mapping(source = "reportedUUID", target = "reported.playerUuid"),
            @Mapping(source = "categoryId", target = "categoryEntity.id"),
            @Mapping(source = "reason", target = "reason"),
            @Mapping(source = "serverUUID", target = "server.serverUuid"),
            @Mapping(source = "location", target = "location"),
            @Mapping(source = "reportStatus", target = "reportStatus"),
            @Mapping(source = "handledByUUID", target = "handledBy.playerUuid"),
            @Mapping(source = "handlerNote", target = "handlerNote"),
            @Mapping(source = "screenshotId", target = "screenshotEntity.id")
    })
    ReportsEntity partialUpdate(ReportUpdateDto dto, @MappingTarget ReportsEntity entity);
}
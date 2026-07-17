package de.itsjxsper.advancedreports.backend.reports.mapper;

import de.itsjxsper.advancedreports.backend.reports.data.entity.ReportsEntity;
import de.itsjxsper.advancedreports.common.model.report.ReportDto;
import de.itsjxsper.advancedreports.common.model.report.ReportUpdateDto;
import org.mapstruct.*;

import java.util.Optional;

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
            @Mapping(source = "reporterUUID", target = "reporter.playerUuid", qualifiedByName = "unwrapOptional"),
            @Mapping(source = "reportedUUID", target = "reported.playerUuid", qualifiedByName = "unwrapOptional"),
            @Mapping(source = "categoryId", target = "categoryEntity.id", qualifiedByName = "unwrapOptional"),
            @Mapping(source = "reason", target = "reason", qualifiedByName = "unwrapOptional"),
            @Mapping(source = "serverUUID", target = "server.serverUuid", qualifiedByName = "unwrapOptional"),
            @Mapping(source = "location", target = "location", qualifiedByName = "unwrapOptional"),
            @Mapping(source = "status", target = "reportStatus", qualifiedByName = "unwrapOptional"),
            @Mapping(source = "handledByUUID", target = "handledBy.playerUuid", qualifiedByName = "unwrapOptional"),
            @Mapping(source = "handlerNote", target = "handlerNote", qualifiedByName = "unwrapOptional"),
            @Mapping(source = "screenshotId", target = "screenshotEntity.id", qualifiedByName = "unwrapOptional")
    })
    ReportsEntity toEntity(ReportUpdateDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mappings({
            @Mapping(source = "reporterUUID", target = "reporter.playerUuid", qualifiedByName = "unwrapOptional"),
            @Mapping(source = "reportedUUID", target = "reported.playerUuid", qualifiedByName = "unwrapOptional"),
            @Mapping(source = "categoryId", target = "categoryEntity.id", qualifiedByName = "unwrapOptional"),
            @Mapping(source = "reason", target = "reason", qualifiedByName = "unwrapOptional"),
            @Mapping(source = "serverUUID", target = "server.serverUuid", qualifiedByName = "unwrapOptional"),
            @Mapping(source = "location", target = "location", qualifiedByName = "unwrapOptional"),
            @Mapping(source = "status", target = "reportStatus", qualifiedByName = "unwrapOptional"),
            @Mapping(source = "handledByUUID", target = "handledBy.playerUuid", qualifiedByName = "unwrapOptional"),
            @Mapping(source = "handlerNote", target = "handlerNote", qualifiedByName = "unwrapOptional"),
            @Mapping(source = "screenshotId", target = "screenshotEntity.id", qualifiedByName = "unwrapOptional")
    })
    ReportsEntity partialUpdate(ReportUpdateDto dto, @MappingTarget ReportsEntity entity);

    @Named("unwrapOptional")
    default <T> T unwrap(Optional<T> optional) {
        return optional.orElse(null);
    }
}
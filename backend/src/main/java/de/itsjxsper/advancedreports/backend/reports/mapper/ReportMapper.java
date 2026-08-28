package de.itsjxsper.advancedreports.backend.reports.mapper;

import de.itsjxsper.advancedreports.backend.reports.data.entity.ReportsEntity;
import de.itsjxsper.advancedreports.common.model.report.ReportDto;
import de.itsjxsper.advancedreports.common.model.report.ReportUpdateDto;
import org.mapstruct.*;

/**
 * Only scalar fields are mapped here. The associations are resolved in {@code ReportService} from
 * their repositories.
 * <p>
 * Mapping them through nested paths ({@code reporterUUID -> reporter.playerUuid}) made MapStruct
 * build throw-away entities that carried nothing but an id, which Hibernate then rejected as
 * unsaved transient instances - a report without a screenshot or server could not be created at
 * all. Worse, the generated {@code partialUpdate} wrote those paths into the <em>managed</em>
 * entities, so a PATCH carrying a different {@code reporterUUID} tried to change the primary key of
 * an existing player row.
 */
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
            @Mapping(target = "reporter", ignore = true),
            @Mapping(target = "reported", ignore = true),
            @Mapping(target = "categoryEntity", ignore = true),
            @Mapping(target = "server", ignore = true),
            @Mapping(target = "handledBy", ignore = true),
            @Mapping(target = "screenshotEntity", ignore = true),
            @Mapping(source = "reason", target = "reason"),
            @Mapping(source = "location", target = "location"),
            @Mapping(source = "reportStatus", target = "reportStatus"),
            @Mapping(source = "handlerNote", target = "handlerNote")
    })
    ReportsEntity toEntity(ReportUpdateDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mappings({
            @Mapping(target = "reporter", ignore = true),
            @Mapping(target = "reported", ignore = true),
            @Mapping(target = "categoryEntity", ignore = true),
            @Mapping(target = "server", ignore = true),
            @Mapping(target = "handledBy", ignore = true),
            @Mapping(target = "screenshotEntity", ignore = true),
            @Mapping(source = "reason", target = "reason"),
            @Mapping(source = "location", target = "location"),
            @Mapping(source = "reportStatus", target = "reportStatus"),
            @Mapping(source = "handlerNote", target = "handlerNote")
    })
    ReportsEntity partialUpdate(ReportUpdateDto dto, @MappingTarget ReportsEntity entity);
}

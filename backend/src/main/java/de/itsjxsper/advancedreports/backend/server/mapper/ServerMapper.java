package de.itsjxsper.advancedreports.backend.server.mapper;

import de.itsjxsper.advancedreports.backend.server.data.entity.ServerEntity;
import de.itsjxsper.advancedreports.common.model.server.ServerDto;
import org.mapstruct.*;

/**
 * MapStruct matches property names case-sensitively, so the DTO component {@code serverUUID} never
 * lined up with the entity property {@code serverUuid}. With
 * {@code unmappedTargetPolicy = ReportingPolicy.IGNORE} that mismatch was silent: the generated
 * mapper simply wrote {@code UUID serverUUID = null}, so every server the API returned carried a
 * null UUID and {@code PATCH /servers} could never resolve its target. Both directions are mapped
 * explicitly below.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface ServerMapper {

    @Mapping(source = "serverUUID", target = "serverUuid")
    ServerEntity toEntity(ServerDto serverDto);

    @Mapping(source = "serverUuid", target = "serverUUID")
    ServerDto toDto(ServerEntity serverEntity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(source = "serverUUID", target = "serverUuid")
    ServerEntity partialUpdate(ServerDto serverDto, @MappingTarget ServerEntity serverEntity);
}

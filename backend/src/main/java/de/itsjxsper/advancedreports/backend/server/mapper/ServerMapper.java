package de.itsjxsper.advancedreports.backend.server.mapper;

import de.itsjxsper.advancedreports.backend.server.data.entity.ServerEntity;
import de.itsjxsper.advancedreports.backend.server.model.ServerDto;
import org.mapstruct.*;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface ServerMapper {
    ServerEntity toEntity(ServerDto serverDto);

    ServerDto toDto(ServerEntity serverEntity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    ServerEntity partialUpdate(ServerDto serverDto, @MappingTarget ServerEntity serverEntity);
}
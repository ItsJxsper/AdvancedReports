package de.itsjxsper.advancedreports.backend.discord.mapper;

import de.itsjxsper.advancedreports.backend.discord.data.entity.DiscordPlayerEntity;
import de.itsjxsper.advancedreports.backend.discord.model.DiscordPlayerDto;
import org.mapstruct.*;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = MappingConstants.ComponentModel.SPRING)
public interface DiscordPlayerMapper {
    @Mapping(source = "playerEntityPlayerUUID", target = "playerEntity.playerUuid")
    DiscordPlayerEntity toEntity(DiscordPlayerDto discordPlayerDto);

    @Mapping(source = "playerEntity.playerUuid", target = "playerEntityPlayerUUID")
    DiscordPlayerDto toDto(DiscordPlayerEntity discordPlayerEntity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(source = "playerEntityPlayerUUID", target = "playerEntity.playerUuid")
    DiscordPlayerEntity partialUpdate(DiscordPlayerDto discordPlayerDto, @MappingTarget DiscordPlayerEntity discordPlayerEntity);
}
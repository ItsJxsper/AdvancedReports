package de.itsjxsper.advancedreports.backend.discord.service;

import de.itsjxsper.advancedreports.backend.discord.data.repository.DiscordPlayerRepository;
import de.itsjxsper.advancedreports.backend.discord.exceptions.DiscordUserNotFoundException;
import de.itsjxsper.advancedreports.backend.discord.mapper.DiscordPlayerMapper;
import de.itsjxsper.advancedreports.backend.player.data.entity.PlayerEntity;
import de.itsjxsper.advancedreports.backend.player.data.repository.PlayerRepository;
import de.itsjxsper.advancedreports.backend.player.exception.PlayerNotFoundException;
import de.itsjxsper.advancedreports.common.model.discord.DiscordPlayerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiscordPlayerService {

    private final DiscordPlayerRepository discordPlayerRepository;
    private final PlayerRepository playerRepository;
    private final DiscordPlayerMapper discordPlayerMapper;


    public DiscordPlayerDto createDiscordPlayer(DiscordPlayerDto discordPlayerDto) {
        log.debug("Creating DiscordPlayer with data: {}", discordPlayerDto);

        PlayerEntity playerEntity = this.playerRepository.findByPlayerUuid(discordPlayerDto.playerEntityPlayerUUID())
                // Fehlt der Minecraft-Spieler, ist das kein Discord-Fehler - PlayerNotFoundException
                // existiert genau dafuer und bildet auf PLAYER_NOT_FOUND ab.
                .orElseThrow(() -> new PlayerNotFoundException(discordPlayerDto.playerEntityPlayerUUID()));

        var discordPlayerEntity = this.discordPlayerMapper.toEntity(discordPlayerDto);
        discordPlayerEntity.setPlayerEntity(playerEntity);

        var savedEntity = this.discordPlayerRepository.save(discordPlayerEntity);
        log.debug("Created DiscordPlayer with id={}", savedEntity.getId());

        return this.discordPlayerMapper.toDto(savedEntity);
    }

    public DiscordPlayerDto getDiscordPlayerByPlayerUUID(UUID playerUUID) {
        log.debug("Retrieving DiscordPlayer for playerUUID={}", playerUUID);

        var discordPlayerEntity = this.discordPlayerRepository.findByPlayerEntity_PlayerUuid(playerUUID)
                .orElseThrow(() -> new DiscordUserNotFoundException(playerUUID));

        log.debug("Found DiscordPlayer with id={} for playerUUID={}", discordPlayerEntity.getId(), playerUUID);
        return this.discordPlayerMapper.toDto(discordPlayerEntity);
    }

    public DiscordPlayerDto getDiscordPlayerById(Long discordPlayerId) {
        log.debug("Retrieving DiscordPlayer with id={}", discordPlayerId);

        var discordPlayerEntity = this.discordPlayerRepository.findById(discordPlayerId)
                .orElseThrow(() -> new DiscordUserNotFoundException(discordPlayerId));

        log.debug("Found DiscordPlayer with id={}", discordPlayerId);
        return this.discordPlayerMapper.toDto(discordPlayerEntity);
    }

    public DiscordPlayerDto updateDiscordPlayer(DiscordPlayerDto discordPlayerDto) {
        log.debug("Updating DiscordPlayer with id={}", discordPlayerDto.id());

        var discordPlayerEntity = this.discordPlayerRepository.findById(discordPlayerDto.id())
                .orElseThrow(() -> new DiscordUserNotFoundException(discordPlayerDto.id()));

        if (discordPlayerDto.discordUserId() != null) {
            discordPlayerEntity.setDiscordUserId(discordPlayerDto.discordUserId());
        }

        var savedEntity = this.discordPlayerRepository.save(discordPlayerEntity);
        log.debug("Updated DiscordPlayer with id={}", savedEntity.getId());

        return this.discordPlayerMapper.toDto(savedEntity);
    }

    public void deleteDiscordPlayerByDiscordId(Long discordPlayerId) {
        log.debug("Deleting DiscordPlayer with id={}", discordPlayerId);

        var discordPlayerEntity = this.discordPlayerRepository.findById(discordPlayerId)
                .orElseThrow(() -> new DiscordUserNotFoundException(discordPlayerId));

        this.discordPlayerRepository.delete(discordPlayerEntity);
        log.debug("Deleted DiscordPlayer with id={}", discordPlayerId);
    }

    public void deleteDiscordPlayerByPlayerUUID(UUID playerUUID) {
        log.debug("Deleting DiscordPlayer for playerUUID={}", playerUUID);

        var discordPlayerEntity = this.discordPlayerRepository.findByPlayerEntity_PlayerUuid(playerUUID)
                .orElseThrow(() -> new DiscordUserNotFoundException(playerUUID));

        this.discordPlayerRepository.delete(discordPlayerEntity);
        log.debug("Deleted DiscordPlayer for playerUUID={}", playerUUID);
    }
}

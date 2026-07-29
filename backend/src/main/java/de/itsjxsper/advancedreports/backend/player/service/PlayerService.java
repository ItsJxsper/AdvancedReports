package de.itsjxsper.advancedreports.backend.player.service;

import de.itsjxsper.advancedreports.backend.player.data.entity.PlayerEntity;
import de.itsjxsper.advancedreports.backend.player.data.repository.PlayerRepository;
import de.itsjxsper.advancedreports.backend.player.exception.PlayerAlreadyExistException;
import de.itsjxsper.advancedreports.backend.player.exception.PlayerNotFoundException;
import de.itsjxsper.advancedreports.common.model.player.PlayerDTO;
import de.itsjxsper.advancedreports.common.model.player.PlayerUpdateDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository playerRepository;

    public PlayerDTO createPlayer(PlayerUpdateDTO playerUpdateDTO) {
        log.debug("Creating player with uuid={}", playerUpdateDTO.playerUuid());
        this.playerRepository.findByPlayerUuid(playerUpdateDTO.playerUuid())
                .ifPresent(player -> {
                            throw new PlayerAlreadyExistException(playerUpdateDTO.playerUuid());
                        }
                );

        PlayerEntity playerEntity = new PlayerEntity();
        playerEntity.setPlayerUuid(playerUpdateDTO.playerUuid());
        playerEntity.setPlayerName(playerUpdateDTO.playerName().orElse(null));

        var savedEntity = this.playerRepository.save(playerEntity);
        log.debug("Created player with uuid={} and name={}", savedEntity.getPlayerUuid(), savedEntity.getPlayerName());

        return new PlayerDTO(savedEntity.getPlayerUuid(), savedEntity.getPlayerName());
    }

    public PlayerDTO updatePlayer(PlayerUpdateDTO playerUpdateDTO) {
        log.debug("Updating player with uuid={}", playerUpdateDTO.playerUuid());
        var playerEntity = this.playerRepository.findByPlayerUuid(playerUpdateDTO.playerUuid())
                .orElseThrow(() -> new PlayerNotFoundException(playerUpdateDTO.playerUuid()));

        playerEntity.setPlayerName(playerUpdateDTO.playerName().orElse(null));

        var savedEntity = this.playerRepository.save(playerEntity);
        log.debug("Updated player with uuid={} and name={}", savedEntity.getPlayerUuid(), savedEntity.getPlayerName());

        return new PlayerDTO(savedEntity.getPlayerUuid(), savedEntity.getPlayerName());
    }

    public void deletePlayer(UUID playerUuid) {
        log.debug("Deleting player with uuid={}", playerUuid);
        var playerEntity = this.playerRepository.findByPlayerUuid(playerUuid)
                .orElseThrow(() -> new PlayerNotFoundException(playerUuid));
        this.playerRepository.delete(playerEntity);
        log.debug("Deleted player with uuid={}", playerUuid);
    }

    public PlayerDTO getPlayer(UUID playerUuid) {
        log.debug("Fetching player with uuid={}", playerUuid);
        return this.playerRepository.findByPlayerUuid(playerUuid)
                .map(playerEntity -> new PlayerDTO(playerEntity.getPlayerUuid(), playerEntity.getPlayerName()))
                .orElseThrow(() -> new PlayerNotFoundException(playerUuid));
    }

    public long countPlayers() {
        var count = this.playerRepository.count();
        log.debug("Counted players={}", count);
        return count;
    }

    public Page<PlayerDTO> getPlayers(Pageable pageable) {
        log.debug("Fetching players page with pageable={}", pageable);
        return this.playerRepository.findAll(pageable)
                .map(playerEntity -> new PlayerDTO(playerEntity.getPlayerUuid(), playerEntity.getPlayerName()));
    }
}

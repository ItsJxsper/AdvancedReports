package de.itsjxsper.advancedreports.player.service;

import de.itsjxsper.advancedreports.player.data.entity.PlayerEntity;
import de.itsjxsper.advancedreports.player.data.repository.PlayerRepository;
import de.itsjxsper.advancedreports.player.exception.PlayerAlreadyExistException;
import de.itsjxsper.advancedreports.player.exception.PlayerNotFoundException;
import de.itsjxsper.advancedreports.player.model.PlayerDTO;
import de.itsjxsper.advancedreports.player.model.PlayerUpdateDTO;
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
        log.debug("Creating player with uuid={}", playerUpdateDTO.playerUUID());
        this.playerRepository.findByPlayerUUID(playerUpdateDTO.playerUUID())
                .ifPresent(player -> {
                            throw new PlayerAlreadyExistException(playerUpdateDTO.playerUUID());
                        }
                );

        PlayerEntity playerEntity = new PlayerEntity();
        playerEntity.setPlayerUUID(playerUpdateDTO.playerUUID());
        playerEntity.setPlayerName(playerUpdateDTO.playerName().orElse(null));

        var savedEntity = this.playerRepository.save(playerEntity);
        log.debug("Created player with uuid={} and name={}", savedEntity.getPlayerUUID(), savedEntity.getPlayerName());

        return new PlayerDTO(savedEntity.getPlayerUUID(), savedEntity.getPlayerName());
    }

    public PlayerDTO updatePlayer(PlayerUpdateDTO playerUpdateDTO) {
        log.debug("Updating player with uuid={}", playerUpdateDTO.playerUUID());
        var playerEntity = this.playerRepository.findByPlayerUUID(playerUpdateDTO.playerUUID())
                .orElseThrow(() -> new PlayerNotFoundException(playerUpdateDTO.playerUUID()));

        playerEntity.setPlayerName(playerUpdateDTO.playerName().orElse(null));

        var savedEntity = this.playerRepository.save(playerEntity);
        log.debug("Updated player with uuid={} and name={}", savedEntity.getPlayerUUID(), savedEntity.getPlayerName());

        return new PlayerDTO(savedEntity.getPlayerUUID(), savedEntity.getPlayerName());
    }

    public void deletePlayer(UUID playerUUID) {
        log.debug("Deleting player with uuid={}", playerUUID);
        var playerEntity = this.playerRepository.findByPlayerUUID(playerUUID)
                .orElseThrow(() -> new PlayerNotFoundException(playerUUID));
        this.playerRepository.delete(playerEntity);
        log.debug("Deleted player with uuid={}", playerUUID);
    }

    public PlayerDTO getPlayer(UUID playerUUID) {
        log.debug("Fetching player with uuid={}", playerUUID);
        return this.playerRepository.findByPlayerUUID(playerUUID)
                .map(playerEntity -> new PlayerDTO(playerEntity.getPlayerUUID(), playerEntity.getPlayerName()))
                .orElseThrow(() -> new PlayerNotFoundException(playerUUID));
    }

    public long countPlayers() {
        var count = this.playerRepository.count();
        log.debug("Counted players={}", count);
        return count;
    }

    public Page<PlayerDTO> getPlayers(Pageable pageable) {
        log.debug("Fetching players page with pageable={}", pageable);
        return this.playerRepository.findAll(pageable)
                .map(playerEntity -> new PlayerDTO(playerEntity.getPlayerUUID(), playerEntity.getPlayerName()));
    }
}

package de.itsjxsper.advancedreports.player.service;

import de.itsjxsper.advancedreports.player.data.entity.PlayerEntity;
import de.itsjxsper.advancedreports.player.data.repository.PlayerRepository;
import de.itsjxsper.advancedreports.player.exception.PlayerAlreadyExistException;
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
        this.playerRepository.findByPlayerUUID(playerUpdateDTO.playerUUID())
                .ifPresent(player -> {
                            throw new PlayerAlreadyExistException(playerUpdateDTO.playerUUID());
                        }
                );

        PlayerEntity playerEntity = new PlayerEntity();
        playerEntity.setPlayerUUID(playerUpdateDTO.playerUUID());
        playerEntity.setPlayerName(playerUpdateDTO.playerName().orElse(null));

        var savedEntity = this.playerRepository.save(playerEntity);

        return new PlayerDTO(savedEntity.getPlayerUUID(), savedEntity.getPlayerName());
    }

    public PlayerDTO updatePlayer(PlayerUpdateDTO playerUpdateDTO) {
        var playerEntity = this.playerRepository.findByPlayerUUID(playerUpdateDTO.playerUUID())
                .orElseThrow(() -> new PlayerAlreadyExistException(playerUpdateDTO.playerUUID()));

        playerEntity.setPlayerName(playerUpdateDTO.playerName().orElse(null));

        var savedEntity = this.playerRepository.save(playerEntity);

        return new PlayerDTO(savedEntity.getPlayerUUID(), savedEntity.getPlayerName());
    }

    public void deletePlayer(UUID playerUUID) {
        this.playerRepository.deleteByPlayerUUID(playerUUID);
    }

    public PlayerDTO getPlayer(UUID playerUUID) {
        return this.playerRepository.findByPlayerUUID(playerUUID)
                .map(playerEntity -> new PlayerDTO(playerEntity.getPlayerUUID(), playerEntity.getPlayerName()))
                .orElseThrow(() -> new PlayerAlreadyExistException(playerUUID));
    }

    public long countPlayers() {
        return this.playerRepository.count();
    }

    public Page<PlayerDTO> getPlayers(Pageable pageable) {
        return this.playerRepository.findAll(pageable)
                .map(playerEntity -> new PlayerDTO(playerEntity.getPlayerUUID(), playerEntity.getPlayerName()));
    }
}

package de.itsjxsper.advancedreports.player.data.repository;

import de.itsjxsper.advancedreports.player.data.entity.PlayerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<PlayerEntity, UUID> {

    Optional<PlayerEntity> findByPlayerUUID(UUID playerUUID);

    void deleteByPlayerUUID(UUID playerUUID);
}
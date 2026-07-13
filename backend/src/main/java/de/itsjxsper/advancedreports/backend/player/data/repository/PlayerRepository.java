package de.itsjxsper.advancedreports.backend.player.data.repository;

import de.itsjxsper.advancedreports.backend.player.data.entity.PlayerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<PlayerEntity, UUID> {

    Optional<PlayerEntity> findByPlayerUuid(UUID playerUuid);

    void deleteByPlayerUuid(UUID playerUuid);
}
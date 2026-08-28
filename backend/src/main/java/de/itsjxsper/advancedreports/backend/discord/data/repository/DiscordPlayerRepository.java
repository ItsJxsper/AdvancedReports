package de.itsjxsper.advancedreports.backend.discord.data.repository;

import de.itsjxsper.advancedreports.backend.discord.data.entity.DiscordPlayerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DiscordPlayerRepository extends JpaRepository<DiscordPlayerEntity, Long> {
    Optional<DiscordPlayerEntity> findByPlayerEntity_PlayerUuid(UUID playerUuid);

    boolean existsByPlayerEntity_PlayerUuid(UUID playerUuid);

    boolean existsByDiscordUserId(Long discordUserId);
}
package de.itsjxsper.advancedreports.backend.discord.model;

import de.itsjxsper.advancedreports.backend.discord.data.entity.DiscordPlayerEntity;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Max;

import java.io.Serializable;
import java.util.UUID;

/**
 * DTO for {@link DiscordPlayerEntity}
 */
public record DiscordPlayerDto(@Nullable Long id, UUID playerEntityPlayerUUID,
                               @Max(18) Long discordUserId) implements Serializable {
}
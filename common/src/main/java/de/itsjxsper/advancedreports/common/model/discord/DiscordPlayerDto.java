package de.itsjxsper.advancedreports.common.model.discord;

import jakarta.validation.constraints.Max;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.UUID;

/**
 * Data Transfer Object for Discord player linking.
 *
 * @param id                     the unique identifier of the link
 * @param playerEntityPlayerUUID the UUID of the player entity
 * @param discordUserId          the Discord user ID
 */
public record DiscordPlayerDto(@Nullable Long id, UUID playerEntityPlayerUUID,
                               @Max(18) Long discordUserId) implements Serializable {
}
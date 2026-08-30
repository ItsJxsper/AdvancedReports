package de.itsjxsper.advancedreports.common.model.discord;

import jakarta.validation.constraints.Digits;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.UUID;

/**
 * Data Transfer Object for Discord player linking.
 *
 * @param id                     the unique identifier of the link
 * @param playerEntityPlayerUUID the UUID of the player entity
 * @param discordUserId          the Discord user ID (a 17-19 digit snowflake)
 */
public record DiscordPlayerDto(@Nullable Long id, UUID playerEntityPlayerUUID,
                               @Digits(integer = 19, fraction = 0) Long discordUserId) implements Serializable {
}
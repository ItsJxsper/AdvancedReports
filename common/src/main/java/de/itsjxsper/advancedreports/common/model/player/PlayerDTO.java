package de.itsjxsper.advancedreports.common.model.player;

import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.UUID;

/**
 * Data Transfer Object for player information.
 *
 * @param playerUUID the unique identifier (UUID) of the player
 * @param playerName the name of the player
 */
public record PlayerDTO(
        UUID playerUUID,
        @Size(message = "Player name must be between 3 and 16 characters", min = 3, max = 16)
        String playerName
) implements Serializable {
}
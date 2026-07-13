package de.itsjxsper.advancedreports.backend.player.model;

import de.itsjxsper.advancedreports.backend.player.data.entity.PlayerEntity;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.UUID;

/**
 * DTO for {@link PlayerEntity}
 */
public record PlayerDTO(
        UUID playerUUID,
        @Size(message = "Player name must be between 3 and 16 characters", min = 3, max = 16)
        String playerName
) implements Serializable {
}
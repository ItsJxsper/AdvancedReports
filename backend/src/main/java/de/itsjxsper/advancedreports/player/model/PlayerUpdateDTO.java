package de.itsjxsper.advancedreports.player.model;

import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.Optional;
import java.util.UUID;

/**
 * DTO for {@link de.itsjxsper.advancedreports.player.data.entity.PlayerEntity}
 */
public record PlayerUpdateDTO(
        UUID playerUUID,
        @Size(message = "Player name must be between 3 and 16 characters", min = 3, max = 16)
        Optional<String> playerName
) implements Serializable {
}
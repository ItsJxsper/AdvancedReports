package de.itsjxsper.advancedreports.common.model.player;

import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.UUID;


public record PlayerDTO(
        UUID playerUUID,
        @Size(message = "Player name must be between 3 and 16 characters", min = 3, max = 16)
        String playerName
) implements Serializable {
}
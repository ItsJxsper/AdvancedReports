package de.itsjxsper.advancedreports.common.model.player;

import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.Optional;
import java.util.UUID;

public record PlayerUpdateDTO(
        UUID playerUuid,
        @Size(message = "Player name must be between 3 and 16 characters", min = 3, max = 16)
        Optional<String> playerName
) implements Serializable {
}
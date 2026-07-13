
package de.itsjxsper.advancedreports.common.model.discord;

import jakarta.validation.constraints.Max;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.UUID;

public record DiscordPlayerDto(@Nullable Long id, UUID playerEntityPlayerUUID,
                               @Max(18) Long discordUserId) implements Serializable {
}
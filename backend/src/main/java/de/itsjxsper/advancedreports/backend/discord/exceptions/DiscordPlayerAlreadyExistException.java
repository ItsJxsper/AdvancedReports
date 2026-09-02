package de.itsjxsper.advancedreports.backend.discord.exceptions;

import java.util.UUID;

/**
 * Thrown when a Discord link already exists for the given player or Discord account.
 * <p>
 * {@code createDiscordPlayer} had no existence check at all, unlike every other create in the
 * project. The join column is unique, so a second link for the same player surfaced as a raw
 * constraint violation; {@code discord_user_id} has no unique constraint, so the same Discord
 * account could be linked to unlimited players without any error whatsoever.
 */
public class DiscordPlayerAlreadyExistException extends RuntimeException {

    public DiscordPlayerAlreadyExistException(UUID playerUuid) {
        super("Player with UUID " + playerUuid + " is already linked to a Discord account");
    }

    public DiscordPlayerAlreadyExistException(Long discordUserId) {
        super("Discord account " + discordUserId + " is already linked to a player");
    }
}

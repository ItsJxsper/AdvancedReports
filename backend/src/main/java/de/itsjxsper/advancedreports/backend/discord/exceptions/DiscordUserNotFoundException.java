package de.itsjxsper.advancedreports.backend.discord.exceptions;

import java.util.UUID;

public class DiscordUserNotFoundException extends RuntimeException {
    public DiscordUserNotFoundException(Long discordId) {
        super("Discord player with ID " + discordId + " not found");
    }

    public DiscordUserNotFoundException(UUID playerUUID) {
        super("Discord player with Minecraft UUID " + playerUUID + " not found");
    }
}

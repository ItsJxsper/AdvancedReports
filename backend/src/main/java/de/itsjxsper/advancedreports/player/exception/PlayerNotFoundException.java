package de.itsjxsper.advancedreports.player.exception;

import java.util.UUID;

public class PlayerNotFoundException extends RuntimeException {

    public PlayerNotFoundException(UUID playerUUID) {
        super("Player with UUID " + playerUUID + " was not found");
    }
}


package de.itsjxsper.advancedreports.player.exception;

import java.util.UUID;

public class PlayerAlreadyExistException extends RuntimeException {

    public PlayerAlreadyExistException(UUID playerUUID) {
        super("Player with UUID " + playerUUID + " already exists");
    }
}

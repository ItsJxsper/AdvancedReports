package de.itsjxsper.advancedreports.backend.server.exceptions;

import java.util.UUID;

public class ServerNotFoundException extends RuntimeException {
    public ServerNotFoundException(UUID serverUUID) {
        super("Server with UUID " + serverUUID + " was not found");
    }
}

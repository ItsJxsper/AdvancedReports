package de.itsjxsper.advancedreports.common.model.server;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.UUID;

public record ServerUpdateDto(
        @NotNull
        UUID serverUUID,
        InetAddress ip_address,
        Integer port
) implements Serializable {
}
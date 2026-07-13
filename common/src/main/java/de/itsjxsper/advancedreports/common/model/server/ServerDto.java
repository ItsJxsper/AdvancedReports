package de.itsjxsper.advancedreports.common.model.server;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.UUID;

public record ServerDto(
        @NotNull
        UUID serverUUID,
        @NotNull
        InetAddress ipAddress,
        @Positive
        Integer port
) implements Serializable {
}
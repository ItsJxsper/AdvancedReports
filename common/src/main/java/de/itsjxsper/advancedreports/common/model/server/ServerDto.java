package de.itsjxsper.advancedreports.common.model.server;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.UUID;

/**
 * Data Transfer Object for server information.
 *
 * @param serverUUID the unique identifier (UUID) of the server
 * @param ipAddress  the IP address of the server
 * @param port       the port number of the server
 */
public record ServerDto(
        @NotNull
        UUID serverUUID,
        @NotNull
        InetAddress ipAddress,
        @Positive
        Integer port
) implements Serializable {
}
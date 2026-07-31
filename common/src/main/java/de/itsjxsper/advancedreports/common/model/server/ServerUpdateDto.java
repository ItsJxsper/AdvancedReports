package de.itsjxsper.advancedreports.common.model.server;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.UUID;

/**
 * Data Transfer Object for updating server information.
 *
 * @param serverUUID the unique identifier (UUID) of the server
 * @param ip_address the new IP address of the server
 * @param port       the new port number of the server
 */
public record ServerUpdateDto(
        @NotNull
        UUID serverUUID,
        InetAddress ip_address,
        Integer port
) implements Serializable {
}
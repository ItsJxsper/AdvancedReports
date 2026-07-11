package de.itsjxsper.advancedreports.backend.server.model;

import de.itsjxsper.advancedreports.backend.server.data.entity.ServerEntity;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.UUID;

/**
 * DTO for {@link ServerEntity}
 */
public record ServerUpdateDto(
        @NotNull
        UUID serverUUID,
        @NotNull
        InetAddress ip_address,
        @Positive
        Integer port
) implements Serializable {
}
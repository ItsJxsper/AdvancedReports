package de.itsjxsper.advancedreports.common.model.player;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.Optional;
import java.util.UUID;

/**
 * Data Transfer Object for updating player information.
 * <p>
 * {@code playerName} is wrapped in an {@link Optional} to distinguish "not sent" from "set to this
 * value": on a PATCH an empty Optional leaves the existing name untouched. The size constraint sits
 * on the container <em>element</em> rather than on the Optional itself - Hibernate Validator has no
 * validator for {@code @Size} on {@code Optional<String>} and throws
 * {@code UnexpectedTypeException} at validation time, so the constraint never actually ran.
 *
 * @param playerUuid the unique identifier (UUID) of the player
 * @param playerName the new name of the player, wrapped in an Optional
 */
public record PlayerUpdateDTO(
        @NotNull UUID playerUuid,
        Optional<@Size(message = "Player name must be between 3 and 16 characters", min = 3, max = 16) String> playerName
) implements Serializable {
}

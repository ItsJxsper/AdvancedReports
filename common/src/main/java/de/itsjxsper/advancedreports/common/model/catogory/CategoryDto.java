package de.itsjxsper.advancedreports.common.model.catogory;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * Data Transfer Object for categories.
 *
 * @param id          the unique identifier of the category
 * @param name        the internal name of the category
 * @param displayName the display name of the category
 * @param description a description of the category
 * @param cooldownSec the cooldown in seconds for this category
 * @param active      whether the category is active
 */
public record CategoryDto(
        @PositiveOrZero Long id,
        @Size(message = "Category name must be between 3 and 64 characters", min = 3, max = 64)
        String name,
        @Size(message = "Category display name must be between 3 and 64 characters", min = 3, max = 64)
        String displayName,
        String description,
        @PositiveOrZero Long cooldownSec,
        Boolean active
) implements Serializable {
}
package de.itsjxsper.advancedreports.backend.categories.model;

import de.itsjxsper.advancedreports.backend.categories.data.entity.CategoryEntity;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * DTO for {@link CategoryEntity}
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
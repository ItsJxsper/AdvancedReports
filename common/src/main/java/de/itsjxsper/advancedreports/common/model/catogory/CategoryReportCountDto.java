package de.itsjxsper.advancedreports.common.model.catogory;

import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * Data Transfer Object for category report counts.
 *
 * @param id          the unique identifier of the category
 * @param name        the name of the category
 * @param reportCount the number of reports in this category
 */
public record CategoryReportCountDto(
        Long id,
        @Size(message = "Category name must be between 3 and 64 characters", min = 3, max = 64)
        String name,
        Long reportCount
) implements Serializable {
}

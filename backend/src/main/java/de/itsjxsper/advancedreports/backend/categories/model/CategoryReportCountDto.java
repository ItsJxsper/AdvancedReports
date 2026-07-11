package de.itsjxsper.advancedreports.backend.categories.model;

import jakarta.validation.constraints.Size;

import java.io.Serializable;

public record CategoryReportCountDto(
        Long id,
        @Size(message = "Category name must be between 3 and 64 characters", min = 3, max = 64)
        String name,
        Long reportCount
) implements Serializable {
}

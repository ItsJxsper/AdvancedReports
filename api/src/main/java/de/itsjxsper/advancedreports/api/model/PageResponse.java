package de.itsjxsper.advancedreports.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Simplified representation of a Spring {@code Page<T>} JSON response.
 * Covers only the fields that clients actually need; unknown
 * fields (e.g. {@code pageable}, {@code sort}, {@code first}, {@code last})
 * are ignored rather than throwing a deserialization error.
 *
 * @param content       the elements on the current page
 * @param totalElements total number of elements across all pages
 * @param totalPages    total number of pages
 * @param number        index of the current page (0-based)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PageResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int number
) {
}

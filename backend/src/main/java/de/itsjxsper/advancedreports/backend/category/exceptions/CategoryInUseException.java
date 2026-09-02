package de.itsjxsper.advancedreports.backend.category.exceptions;

/**
 * Thrown when a category still has reports attached to it and therefore cannot be deleted.
 * <p>
 * Unlike servers and screenshots, a report cannot be detached from its category:
 * {@code reports_entity.category_entity_id} is NOT NULL. Deleting the category anyway would mean
 * deleting its reports, which is what the previous {@code orphanRemoval = true} mapping did silently.
 */
public class CategoryInUseException extends RuntimeException {

    public CategoryInUseException(Long categoryId, long reportCount) {
        super("Category with ID " + categoryId + " still has " + reportCount
                + " report(s) and cannot be deleted");
    }
}

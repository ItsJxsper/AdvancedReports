package de.itsjxsper.advancedreports.backend.categories.exceptions;

public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(Long categoryId) {
        super("Category with ID " + categoryId + " was not found");
    }
}


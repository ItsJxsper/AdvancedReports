package de.itsjxsper.advancedreports.backend.category.exceptions;

public class CategoryAlreadyExistException extends RuntimeException {

    public CategoryAlreadyExistException(String name) {
        super("Category with name '" + name + "' already exists");
    }
}


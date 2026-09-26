package com.example.ticketing.ticket;

/**
 * Custom exception cho Category operations.
 */
public class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException(Long id) {
        super("Category not found with id: " + id);
    }
    
    public CategoryNotFoundException(String message) {
        super(message);
    }
}

package com.poc.product.domain.model;

/**
 * Enumeration of product categories.
 */
public enum Category {

    ELECTRONICS,
    CLOTHING,
    BOOKS,
    HOME,
    SPORTS;

    /**
     * Performs a case-insensitive lookup of a category by name.
     *
     * @param value the category name (case-insensitive)
     * @return the matching Category
     * @throws IllegalArgumentException if no match is found
     */
    public static Category fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Category value must not be null");
        }
        try {
            return Category.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown category: " + value);
        }
    }
}

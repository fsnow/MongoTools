package com.fsnow.indexanalyzer.model;

import java.util.Objects;

/**
 * Represents a field in a sort specification.
 */
public class SortField {
    private final String field;
    private final SortDirection direction;
    
    public SortField(String field, SortDirection direction) {
        this.field = Objects.requireNonNull(field, "Field name cannot be null");
        this.direction = Objects.requireNonNull(direction, "Sort direction cannot be null");
    }
    
    public String getField() {
        return field;
    }
    
    public SortDirection getDirection() {
        return direction;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SortField sortField = (SortField) o;
        return Objects.equals(field, sortField.field) && direction == sortField.direction;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(field, direction);
    }
    
    @Override
    public String toString() {
        return String.format("SortField{field='%s', direction=%s}", field, direction);
    }
}
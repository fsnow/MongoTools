package com.fsnow.indexanalyzer.model;

import java.util.Objects;

/**
 * Represents a field in a MongoDB index.
 */
public class IndexField {
    private final String field;
    private final SortDirection direction;
    
    public IndexField(String field, SortDirection direction) {
        this.field = Objects.requireNonNull(field, "Field name cannot be null");
        this.direction = Objects.requireNonNull(direction, "Sort direction cannot be null");
    }
    
    public IndexField(String field, int direction) {
        this(field, SortDirection.fromValue(direction));
    }
    
    public String getField() {
        return field;
    }
    
    public SortDirection getDirection() {
        return direction;
    }
    
    /**
     * Converts to a SortField representation.
     */
    public SortField toSortField() {
        return new SortField(field, direction);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IndexField that = (IndexField) o;
        return Objects.equals(field, that.field) && direction == that.direction;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(field, direction);
    }
    
    @Override
    public String toString() {
        return String.format("IndexField{field='%s', direction=%s}", field, direction);
    }
}
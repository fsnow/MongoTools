package com.fsnow.indexanalyzer.exception;

/**
 * Exception thrown when an invalid namespace format is provided.
 */
public class InvalidNamespaceException extends IndexAnalyzerException {
    
    public InvalidNamespaceException(String namespace) {
        super(String.format("Invalid namespace format: '%s'. Expected: database.collection", namespace));
    }
}
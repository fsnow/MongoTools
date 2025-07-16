package com.fsnow.indexanalyzer.exception;

/**
 * Exception thrown when Criteria parsing fails.
 */
public class CriteriaParsingException extends IndexAnalyzerException {
    
    public CriteriaParsingException(String message) {
        super(message);
    }
    
    public CriteriaParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
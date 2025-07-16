package com.fsnow.indexanalyzer.exception;

/**
 * Base exception for all MongoDB Index Analyzer errors.
 */
public class IndexAnalyzerException extends RuntimeException {
    
    public IndexAnalyzerException(String message) {
        super(message);
    }
    
    public IndexAnalyzerException(String message, Throwable cause) {
        super(message, cause);
    }
}
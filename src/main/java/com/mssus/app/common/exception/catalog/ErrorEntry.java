package com.mssus.app.common.exception.catalog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an error entry from the error catalog.
 * Contains all metadata needed to create consistent error responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorEntry {
    
    /**
     * Unique error identifier in format: domain.category.name
     * Example: "auth.validation.missing-credential"
     */
    private String id;
    
    /**
     * HTTP status code for this error (400, 401, 404, 500, etc.)
     */
    private Integer httpStatus;
    
    /**
     * Error severity level: INFO, WARN, ERROR
     */
    private String severity;
    
    /**
     * Whether this error condition is retryable
     */
    private Boolean isRetryable;
    
    /**
     * Template for the error message shown to users
     */
    private String messageTemplate;
    
    /**
     * Domain this error belongs to (auth, user, wallet, etc.)
     */
    private String domain;
    
    /**
     * Error category (validation, not-found, conflict, etc.)
     */
    private String category;
    
    /**
     * Team or service responsible for this error type
     */
    private String owner;
    
    /**
     * Optional remediation advice for users
     */
    private String remediation;
    
    /**
     * Get the formatted error message, replacing placeholders if needed
     * 
     * @param args Optional arguments to replace placeholders in messageTemplate
     * @return Formatted error message
     */
    public String getFormattedMessage(Object... args) {
        if (args.length > 0) {
            return String.format(messageTemplate, args);
        }
        return messageTemplate;
    }
    
    /**
     * Check if this is a client error (4xx status code)
     * 
     * @return true if HTTP status is in 400-499 range
     */
    public boolean isClientError() {
        return httpStatus != null && httpStatus >= 400 && httpStatus < 500;
    }
    
    /**
     * Check if this is a server error (5xx status code)
     * 
     * @return true if HTTP status is in 500-599 range
     */
    public boolean isServerError() {
        return httpStatus != null && httpStatus >= 500 && httpStatus < 600;
    }
}

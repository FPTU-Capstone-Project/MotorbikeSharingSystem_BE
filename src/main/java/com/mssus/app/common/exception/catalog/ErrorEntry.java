package com.mssus.app.common.exception.catalog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

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
     * Formats the message template using standard `String.format` placeholders (e.g., %s, %d).
     *
     * @param args Arguments to be formatted into the message template.
     * @return Formatted error message
     */
    public String getFormattedMessage(Object... args) {
        if (messageTemplate == null || args == null || args.length == 0) {
            return messageTemplate;
        }
        return String.format(messageTemplate, args);
    }

    /**
     * Formats the message template using a map of named placeholders.
     * Replaces occurrences of "{key}" with the corresponding value from the map.
     *
     * @param context A map of placeholder names to their replacement values.
     * @return The formatted message string.
     */
    public String getFormattedMessage(Map<String, Object> context) {
        if (context == null || context.isEmpty() || this.messageTemplate == null) {
            return this.messageTemplate;
        }

        String formattedMessage = this.messageTemplate;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            // Ensure value is not null to prevent `replace` from throwing an error
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            formattedMessage = formattedMessage.replace(placeholder, value);
        }
        return formattedMessage;
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

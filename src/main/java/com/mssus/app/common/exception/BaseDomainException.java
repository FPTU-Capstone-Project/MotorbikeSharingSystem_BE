package com.mssus.app.common.exception;

import com.mssus.app.common.exception.catalog.ErrorCatalogService;
import com.mssus.app.common.exception.catalog.ErrorEntry;
import lombok.Getter;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Base domain exception class that integrates with the error catalog system.
 * All domain-specific exceptions should extend this class or use its static factory methods.
 */
@Getter
public class BaseDomainException extends RuntimeException {
    
    private final String errorId;
    private final Map<String, Object> context;
    private final ErrorEntry errorEntry;
    
    /**
     * Private constructor - use static factory methods instead
     */
    private BaseDomainException(String errorId, String message, Throwable cause, Map<String, Object> context, ErrorEntry errorEntry) {
        super(message, cause);
        this.errorId = errorId;
        this.context = context != null ? Collections.unmodifiableMap(new HashMap<>(context)) : Collections.emptyMap();
        this.errorEntry = errorEntry;
    }

    /**
     * Overrides the default getMessage() to ensure the message is always formatted with context.
     * This makes the exception robust, as it can format the message just-in-time.
     *
     * @return The formatted error message.
     */
    @Override
    public String getMessage() {
        String rawMessage = super.getMessage();
        if (errorEntry != null && context != null && !context.isEmpty()) {
            return errorEntry.getFormattedMessage(context);
        }
        return rawMessage;
    }
    
    /**
     * Create exception using error ID from catalog
     * 
     * @param errorId Error ID from the catalog
     * @return BaseDomainException instance
     */
    public static BaseDomainException of(String errorId) {
        return of(errorId, null, null, null);
    }
    
    /**
     * Create exception using error ID from catalog with additional context
     * 
     * @param errorId Error ID from the catalog
     * @param context Additional context information
     * @return BaseDomainException instance
     */
    public static BaseDomainException of(String errorId, Map<String, Object> context) {
        return of(errorId, null, null, context);
    }
    
    /**
     * Create exception using error ID from catalog with cause
     * 
     * @param errorId Error ID from the catalog
     * @param cause Root cause exception
     * @return BaseDomainException instance
     */
    public static BaseDomainException of(String errorId, Throwable cause) {
        return of(errorId, null, cause, null);
    }
    
    /**
     * Create exception using error ID from catalog with custom message
     * 
     * @param errorId Error ID from the catalog
     * @param customMessage Custom message to override catalog message
     * @return BaseDomainException instance
     */
    public static BaseDomainException of(String errorId, String customMessage) {
        return of(errorId, customMessage, null, null);
    }
    
    /**
     * Create exception with all parameters
     * 
     * @param errorId Error ID from the catalog
     * @param customMessage Custom message to override catalog message (null to use catalog message)
     * @param cause Root cause exception
     * @param context Additional context information
     * @return BaseDomainException instance
     */
    public static BaseDomainException of(String errorId, String customMessage, Throwable cause, Map<String, Object> context) {
        ErrorCatalogService catalogService = ApplicationContextHolder.getErrorCatalogService();
        ErrorEntry errorEntry;
        String message;
        
        if (catalogService != null) {
            if (customMessage != null) {
                errorEntry = catalogService.getErrorEntry(errorId, customMessage);
                message = customMessage;
            } else {
                errorEntry = catalogService.getErrorEntry(errorId);
                message = errorEntry.getMessageTemplate();
            }
        } else {
            // Fallback when Spring context is not available
            errorEntry = createFallbackErrorEntry(errorId, customMessage);
            message = customMessage != null ? customMessage : "An error occurred";
        }
        
        return new BaseDomainException(errorId, message, cause, context, errorEntry);
    }
    
    /**
     * Create formatted exception with message arguments
     * 
     * @param errorId Error ID from the catalog
     * @param messageArgs Arguments to format into the message template
     * @return BaseDomainException instance
     */
    public static BaseDomainException formatted(String errorId, Object... messageArgs) {
        ErrorCatalogService catalogService = ApplicationContextHolder.getErrorCatalogService();
        ErrorEntry errorEntry;
        String message;
        
        if (catalogService != null) {
            errorEntry = catalogService.getErrorEntry(errorId);
            message = errorEntry.getFormattedMessage(messageArgs);
        } else {
            // Fallback when Spring context is not available
            errorEntry = createFallbackErrorEntry(errorId, null);
            message = "An error occurred";
        }
        
        return new BaseDomainException(errorId, message, null, null, errorEntry);
    }
    
    /**
     * Create exception using error ID and a map of named placeholders for message formatting.
     * The context map is also stored in the exception.
     * <p>
     * Example:
     * <pre>
     * // errors.yaml: ride.validation.invalid-scheduled-time: "Chuyến xe tiếp theo phải sau {minTime}"
     * throw BaseDomainException.withContext(
     *     "ride.validation.invalid-scheduled-time",
     *     Map.of("minTime", "23:20 22/10/2025")
     * );
     * </pre>
     *
     * @param errorId Error ID from the catalog.
     * @param context A map where keys are placeholder names in the message template and values are the replacement values.
     * @return BaseDomainException instance with a formatted message.
     */
    public static BaseDomainException withContext(String errorId, Map<String, Object> context) {
        ErrorCatalogService catalogService = ApplicationContextHolder.getErrorCatalogService();
        ErrorEntry errorEntry;
        String message;

        if (catalogService != null) {
            errorEntry = catalogService.getErrorEntry(errorId);
            message = errorEntry.getFormattedMessage(context);
        } else {
            errorEntry = createFallbackErrorEntry(errorId, "An error occurred with context.");
            message = errorEntry.getMessageTemplate();
        }

        return new BaseDomainException(errorId, message, null, context, errorEntry);
    }

    // Static factory methods for common error types
    
    public static BaseDomainException notFound(String resource) {
        return formatted("user.not-found.by-id", resource);
    }
    
    public static BaseDomainException conflict(String message) {
        return of("user.conflict.email-exists", message);
    }
    
    public static BaseDomainException unauthorized() {
        return of("auth.unauthorized.access-denied");
    }
    
    public static BaseDomainException unauthorized(String message) {
        return of("auth.unauthorized.access-denied", message);
    }
    
    public static BaseDomainException validation(String message) {
        return of("validation.request.invalid-body", message);
    }
    
    public static BaseDomainException validation(String message, Map<String, Object> context) {
        return of("validation.request.invalid-body", message, null, context);
    }
    
    /**
     * Get HTTP status code from the error entry
     * 
     * @return HTTP status code
     */
    public int getHttpStatus() {
        return errorEntry != null ? errorEntry.getHttpStatus() : 500;
    }
    
    /**
     * Get error domain from the error entry
     * 
     * @return Error domain
     */
    public String getDomain() {
        return errorEntry != null ? errorEntry.getDomain() : "system";
    }
    
    /**
     * Get error category from the error entry
     * 
     * @return Error category
     */
    public String getCategory() {
        return errorEntry != null ? errorEntry.getCategory() : "internal";
    }
    
    /**
     * Check if this error is retryable
     * 
     * @return true if the error is retryable
     */
    public boolean isRetryable() {
        return errorEntry != null && Boolean.TRUE.equals(errorEntry.getIsRetryable());
    }
    
    /**
     * Get error severity level
     * 
     * @return Error severity (INFO, WARN, ERROR)
     */
    public String getSeverity() {
        return errorEntry != null ? errorEntry.getSeverity() : "ERROR";
    }
    
    /**
     * Create fallback error entry when catalog is not available
     */
    private static ErrorEntry createFallbackErrorEntry(String errorId, String customMessage) {
        return ErrorEntry.builder()
                .id(errorId != null ? errorId : "system.internal.unexpected")
                .httpStatus(500)
                .severity("ERROR")
                .isRetryable(false)
                .messageTemplate(customMessage != null ? customMessage : "An unexpected error occurred")
                .domain("system")
                .category("internal")
                .owner("platform-team")
                .build();
    }
    
    /**
     * Helper component to access Spring beans from static context
     */
    @Component
    public static class ApplicationContextHolder implements ApplicationContextAware {
        
        private static ApplicationContext applicationContext;
        
        @Override
        public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
            ApplicationContextHolder.applicationContext = applicationContext;
        }
        
        public static ErrorCatalogService getErrorCatalogService() {
            if (applicationContext != null) {
                try {
                    return applicationContext.getBean(ErrorCatalogService.class);
                } catch (BeansException e) {
                    // Bean not available, return null to use fallback
                    return null;
                }
            }
            return null;
        }
    }
}

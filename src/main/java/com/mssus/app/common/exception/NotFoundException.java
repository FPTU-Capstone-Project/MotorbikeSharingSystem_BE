package com.mssus.app.common.exception;

/**
 * Exception for resource not found scenarios.
 * Supports both legacy constructor-based usage and new catalog-based factory methods.
 * 
 * @deprecated Use BaseDomainException.of("user.not-found.by-id") or specific factory methods instead
 */
@Deprecated
public class NotFoundException extends DomainException {
    
    // Legacy constructors for backward compatibility
    public NotFoundException(String message) {
        super("NOT_FOUND", message);
    }

    public NotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }

    // New catalog-based factory methods
    public static BaseDomainException userNotFound(Integer userId) {
        return BaseDomainException.formatted("user.not-found.by-id", "User with ID " + userId + " not found");
    }

    public static BaseDomainException userNotFound(String identifier) {
        return BaseDomainException.formatted("user.not-found.by-id", "User not found: " + identifier);
    }

    public static BaseDomainException verificationNotFound(Integer verificationId) {
        return BaseDomainException.formatted("user.not-found.by-id", "Verification with ID " + verificationId + " not found");
    }
    
    // Generic not found factory method
    public static BaseDomainException resourceNotFound(String resourceType, String identifier) {
        return BaseDomainException.formatted("user.not-found.by-id", resourceType + " with " + identifier + " not found");
    }
    
    // Catalog-based factory method for general use
    public static BaseDomainException of(String message) {
        return BaseDomainException.of("user.not-found.by-id", message);
    }
}

package com.mssus.app.common.exception;

/**
 * Exception for resource conflict scenarios.
 * Supports both legacy constructor-based usage and new catalog-based factory methods.
 * 
 * @deprecated Use BaseDomainException.of("user.conflict.email-exists") or specific factory methods instead
 */
@Deprecated
public class ConflictException extends DomainException {
    
    // Legacy constructors for backward compatibility
    public ConflictException(String message) {
        super("CONFLICT", message);
    }

    public ConflictException(String errorCode, String message) {
        super(errorCode, message);
    }

    // New catalog-based factory methods
    public static BaseDomainException emailAlreadyExists(String email) {
        return BaseDomainException.formatted("user.conflict.email-exists", "Email already exists: " + email);
    }

    public static BaseDomainException phoneAlreadyExists(String phone) {
        return BaseDomainException.formatted("user.conflict.username-exists", "Phone already exists: " + phone);
    }

    public static BaseDomainException licenseNumberAlreadyExists(String licenseNumber) {
        return BaseDomainException.formatted("user.conflict.username-exists", "License number already exists: " + licenseNumber);
    }

    public static BaseDomainException profileAlreadyExists(String profileType) {
        return BaseDomainException.formatted("user.conflict.username-exists", profileType + " profile already exists for this user");
    }
    
    // Catalog-based factory method for general use
    public static BaseDomainException of(String message) {
        return BaseDomainException.of("user.conflict.email-exists", message);
    }
}

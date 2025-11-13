package com.mssus.app.common.exception;

import java.util.Map;

/**
 * Exception for validation failures.
 * Supports both legacy constructor-based usage and new catalog-based factory methods.
 * 
 * @deprecated Use BaseDomainException.of("validation.*") or specific factory methods instead
 */
@Deprecated
public class ValidationException extends DomainException {
    private final Map<String, String> fieldErrors;

    // Legacy constructors for backward compatibility
    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
        this.fieldErrors = null;
    }

    public ValidationException(String message, Map<String, String> fieldErrors) {
        super("VALIDATION_ERROR", message);
        this.fieldErrors = fieldErrors;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    // New catalog-based factory methods
    public static BaseDomainException invalidOtp() {
        return BaseDomainException.formatted("validation.request.invalid-body", "Invalid or expired OTP code");
    }

    public static BaseDomainException passwordMismatch() {
        return BaseDomainException.formatted("user.validation.old-password-incorrect", "Old password is incorrect");
    }

    public static BaseDomainException invalidFileType(String expectedType) {
        return BaseDomainException.formatted("validation.request.invalid-body", "Invalid file type. Expected: " + expectedType);
    }

    public static BaseDomainException fileTooLarge(long maxSize) {
        return BaseDomainException.formatted("validation.file.size-exceeded", "File size exceeds maximum allowed size of " + maxSize + " bytes");
    }
    
    // Catalog-based factory method with field errors support
    public static BaseDomainException withFieldErrors(String message, Map<String, String> fieldErrors) {
        Map<String, Object> context = Map.of("fieldErrors", fieldErrors);
        return BaseDomainException.of("validation.request.invalid-body", message, null, context);
    }
    
    // Catalog-based factory method for general use
    public static BaseDomainException of(String message) {
        return BaseDomainException.of("validation.request.invalid-body", message);
    }
}

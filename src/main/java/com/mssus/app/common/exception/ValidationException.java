package com.mssus.app.common.exception;

import java.util.Map;

public class ValidationException extends DomainException {
    private final Map<String, String> fieldErrors;

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

    public static ValidationException invalidOtp() {
        return new ValidationException("Invalid or expired OTP code");
    }

    public static ValidationException passwordMismatch() {
        return new ValidationException("Old password is incorrect");
    }

    public static ValidationException invalidFileType(String expectedType) {
        return new ValidationException("Invalid file type. Expected: " + expectedType);
    }

    public static ValidationException fileTooLarge(long maxSize) {
        return new ValidationException("File size exceeds maximum allowed size of " + maxSize + " bytes");
    }
}

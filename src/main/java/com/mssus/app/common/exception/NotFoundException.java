package com.mssus.app.common.exception;

public class NotFoundException extends DomainException {
    public NotFoundException(String message) {
        super("NOT_FOUND", message);
    }

    public NotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }

    public static NotFoundException userNotFound(Integer userId) {
        return new NotFoundException("USER_NOT_FOUND", "User with ID " + userId + " not found");
    }

    public static NotFoundException userNotFound(String identifier) {
        return new NotFoundException("USER_NOT_FOUND", "User not found: " + identifier);
    }

    public static NotFoundException verificationNotFound(Integer verificationId) {
        return new NotFoundException("VERIFICATION_NOT_FOUND", "Verification with ID " + verificationId + " not found");
    }
}

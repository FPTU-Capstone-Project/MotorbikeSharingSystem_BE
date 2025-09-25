package com.mssus.app.common.exception;

public class ConflictException extends DomainException {
    public ConflictException(String message) {
        super("CONFLICT", message);
    }

    public ConflictException(String errorCode, String message) {
        super(errorCode, message);
    }

    public static ConflictException emailAlreadyExists(String email) {
        return new ConflictException("EMAIL_ALREADY_EXISTS", "Email already exists: " + email);
    }

    public static ConflictException phoneAlreadyExists(String phone) {
        return new ConflictException("PHONE_ALREADY_EXISTS", "Phone already exists: " + phone);
    }

    public static ConflictException licenseNumberAlreadyExists(String licenseNumber) {
        return new ConflictException("LICENSE_ALREADY_EXISTS", "License number already exists: " + licenseNumber);
    }

    public static ConflictException profileAlreadyExists(String profileType) {
        return new ConflictException("PROFILE_ALREADY_EXISTS", profileType + " profile already exists for this user");
    }
}

package com.mssus.app.exception;

public class UnauthorizedException extends DomainException {
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message);
    }

    public UnauthorizedException(String errorCode, String message) {
        super(errorCode, message);
    }

    public static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email/phone or password");
    }

    public static UnauthorizedException tokenExpired() {
        return new UnauthorizedException("TOKEN_EXPIRED", "Authentication token has expired");
    }

    public static UnauthorizedException accessDenied() {
        return new UnauthorizedException("ACCESS_DENIED", "You don't have permission to access this resource");
    }

    public static UnauthorizedException accountDisabled() {
        return new UnauthorizedException("ACCOUNT_DISABLED", "Your account has been disabled");
    }

    public static UnauthorizedException profileNotActive(String profileType) {
        return new UnauthorizedException("PROFILE_NOT_ACTIVE", profileType + " profile is not active");
    }
}

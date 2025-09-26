package com.mssus.app.common.exception;

/**
 * Exception for authorization and authentication failures.
 * Supports both legacy constructor-based usage and new catalog-based factory methods.
 * 
 * @deprecated Use BaseDomainException.of("auth.unauthorized.*") or specific factory methods instead
 */
@Deprecated
public class UnauthorizedException extends DomainException {
    
    // Legacy constructors for backward compatibility
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message);
    }

    public UnauthorizedException(String errorCode, String message) {
        super(errorCode, message);
    }

    // New catalog-based factory methods
    public static BaseDomainException invalidCredentials() {
        return BaseDomainException.of("auth.validation.invalid-credentials");
    }

    public static BaseDomainException tokenExpired() {
        return BaseDomainException.of("auth.unauthorized.token-expired");
    }

    public static BaseDomainException tokenInvalid() {
        return BaseDomainException.of("auth.unauthorized.token-invalid");
    }

    public static BaseDomainException accessDenied() {
        return BaseDomainException.of("auth.unauthorized.access-denied");
    }

    public static BaseDomainException accountDisabled() {
        return BaseDomainException.formatted("auth.unauthorized.access-denied", "Your account has been disabled");
    }

    public static BaseDomainException profileNotActive(String profileType) {
        return BaseDomainException.formatted("auth.unauthorized.access-denied", profileType + " profile is not active");
    }

    public static BaseDomainException accountPending() {
        return BaseDomainException.formatted("auth.unauthorized.access-denied", "Your account is pending approval");
    }
    
    // Catalog-based factory method for general use
    public static BaseDomainException of(String message) {
        return BaseDomainException.of("auth.unauthorized.access-denied", message);
    }
}

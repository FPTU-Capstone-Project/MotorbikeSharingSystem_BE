package com.mssus.app.common.exception;

/**
 * Exception for forbidden access - user is authenticated but not authorized to perform the action.
 */
public class ForbiddenException extends DomainException {
    
    public ForbiddenException(String message) {
        super("FORBIDDEN", message);
    }
    
    public ForbiddenException(String message, Throwable cause) {
        super("FORBIDDEN", message, cause);
    }
}


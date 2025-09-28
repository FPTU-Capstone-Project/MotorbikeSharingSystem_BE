package com.mssus.app.common.exception;

public class SmsException extends RuntimeException {
    public SmsException(String message) {
        super(message);
    }

    public SmsException(String message, Throwable cause) {
        super(message, cause);
    }
}

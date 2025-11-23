package com.mssus.app.common.exception;

public class PayosClientException extends RuntimeException {
    public PayosClientException(String message) {
        super(message);
    }

    public PayosClientException(String message, Throwable cause) {
        super(message, cause);
    }
}


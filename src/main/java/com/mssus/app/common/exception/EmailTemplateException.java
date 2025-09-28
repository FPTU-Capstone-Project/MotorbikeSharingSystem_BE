package com.mssus.app.common.exception;

public class EmailTemplateException extends RuntimeException {
    public EmailTemplateException(String message) {
        super(message);
    }

    public EmailTemplateException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.mssus.app.exception.wallet;

import com.mssus.app.exception.DomainException;

public class WalletException extends DomainException {

    public WalletException(String errorCode, String message) {
        super(errorCode, message);
    }

    public WalletException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
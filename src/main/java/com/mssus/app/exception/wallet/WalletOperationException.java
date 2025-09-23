package com.mssus.app.exception.wallet;

public class WalletOperationException extends WalletException {

    public WalletOperationException(String operation, String reason) {
        super("WALLET_OPERATION_FAILED", "Wallet operation '" + operation + "' failed: " + reason);
    }

    public WalletOperationException(String operation, String reason, Throwable cause) {
        super("WALLET_OPERATION_FAILED", "Wallet operation '" + operation + "' failed: " + reason, cause);
    }

    public WalletOperationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public WalletOperationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
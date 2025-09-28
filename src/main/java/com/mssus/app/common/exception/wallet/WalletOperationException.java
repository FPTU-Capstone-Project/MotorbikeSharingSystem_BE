package com.mssus.app.common.exception.wallet;

public class WalletOperationException extends WalletException {

    public WalletOperationException(String operation, String reason) {
        super("WALLET_OPERATION_FAILED", "Wallet operation '" + operation + "' failed: " + reason);
    }


    public WalletOperationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
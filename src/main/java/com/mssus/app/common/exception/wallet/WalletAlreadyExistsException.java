package com.mssus.app.common.exception.wallet;

public class WalletAlreadyExistsException extends WalletException {

    public WalletAlreadyExistsException(Integer userId) {
        super("WALLET_ALREADY_EXISTS", "Wallet already exists for user ID: " + userId);
    }

    public WalletAlreadyExistsException(String pspAccountId) {
        super("WALLET_ALREADY_EXISTS", "Wallet already exists for PSP account ID: " + pspAccountId);
    }
}
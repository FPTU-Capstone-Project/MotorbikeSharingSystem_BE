package com.mssus.app.exception.wallet;

public class WalletNotFoundException extends WalletException {

    public WalletNotFoundException(Integer userId) {
        super("WALLET_NOT_FOUND", "Wallet not found for user ID: " + userId);
    }

    public WalletNotFoundException(Integer walletId, String type) {
        super("WALLET_NOT_FOUND", "Wallet not found with " + type + ": " + walletId);
    }

    public WalletNotFoundException(String message) {
        super("WALLET_NOT_FOUND", message);
    }
}
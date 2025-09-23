package com.mssus.app.exception.wallet;

public class WalletInactiveException extends WalletException {

    public WalletInactiveException(Integer walletId) {
        super("WALLET_INACTIVE", "Wallet is inactive and cannot be used. Wallet ID: " + walletId);
    }

    public WalletInactiveException(Integer userId, String context) {
        super("WALLET_INACTIVE", "Wallet is inactive for user " + userId + " in context: " + context);
    }
}
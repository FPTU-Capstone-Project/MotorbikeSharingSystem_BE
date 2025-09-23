package com.mssus.app.service;

import com.mssus.app.dto.response.wallet.WalletResponse;

import java.math.BigDecimal;
import java.util.List;

public interface WalletService {
    WalletResponse initiateWallet(Integer userId);
    WalletResponse getWalletByUserId(Integer userId);
    WalletResponse getWalletById(Integer walletId);
    void activateWallet(Integer walletId);
    void deactivateWallet(Integer walletId);
    BigDecimal getAvailableBalance(Integer userId);
    BigDecimal getPendingBalance(Integer userId);
    void syncWalletWithPSP(Integer walletId);
    boolean isWalletOwner(Integer walletId, String username);
    List<WalletResponse> getActiveWallets();
}

package com.mssus.app.service;

import com.mssus.app.dto.response.wallet.WalletResponse;

import java.math.BigDecimal;
import java.util.List;

public interface WalletService {
    void updateWalletBalanceOnTopUp(Integer userId, BigDecimal amount);
    void increasePendingBalance(Integer userId, BigDecimal amount);
    void decreasePendingBalance(Integer userId, BigDecimal amount);
    void transferPendingToAvailable(Integer userId, BigDecimal amount);
}

package com.mssus.app.service;

import com.mssus.app.dto.request.wallet.PayoutInitRequest;
import com.mssus.app.dto.request.wallet.TopUpInitRequest;
import com.mssus.app.dto.response.wallet.DriverEarningsResponse;
import com.mssus.app.dto.response.wallet.PayoutInitResponse;
import com.mssus.app.dto.response.wallet.TopUpInitResponse;
import com.mssus.app.dto.response.wallet.WalletResponse;
import com.mssus.app.entity.Wallet;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;

public interface WalletService {

    // Core wallet balance operations
    void updateWalletBalanceOnTopUp(Integer userId, BigDecimal amount);
    void increasePendingBalance(Integer userId, BigDecimal amount);
    void decreasePendingBalance(Integer userId, BigDecimal amount);
    void transferPendingToAvailable(Integer userId, BigDecimal amount);

    // Wallet retrieval
    WalletResponse getBalance(Authentication authentication);
    Wallet getWalletByUserId(Integer userId);

    // Top-up operations
    TopUpInitResponse initiateTopUp(TopUpInitRequest request, Authentication authentication);

    // Payout operations
    PayoutInitResponse initiatePayout(PayoutInitRequest request, Authentication authentication);

    // Driver earnings
    DriverEarningsResponse getDriverEarnings(Authentication authentication);

    // Wallet creation
    Wallet createWalletForUser(Integer userId);

    // Balance checks
    boolean hasSufficientBalance(Integer userId, BigDecimal amount);
}

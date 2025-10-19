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
    void updateWalletBalanceOnTopUp(Integer userId, BigDecimal amount);

    void increasePendingBalance(Integer userId, BigDecimal amount);

    void decreasePendingBalance(Integer userId, BigDecimal amount);

    void increaseShadowBalance(Integer userId, BigDecimal amount);

    void decreaseShadowBalance(Integer userId, BigDecimal amount);

    void transferPendingToAvailable(Integer userId, BigDecimal amount);

    WalletResponse getBalance(Authentication authentication);

    Wallet getWalletByUserId(Integer userId);

    TopUpInitResponse initiateTopUp(TopUpInitRequest request, Authentication authentication);

    PayoutInitResponse initiatePayout(PayoutInitRequest request, Authentication authentication);

    DriverEarningsResponse getDriverEarnings(Authentication authentication);

    Wallet createWalletForUser(Integer userId);

    boolean hasSufficientBalance(Integer userId, BigDecimal amount);

    void reconcileWalletBalance(Integer userId);
}

package com.mssus.app.service;

import com.mssus.app.dto.request.wallet.TopUpInitRequest;
import com.mssus.app.dto.response.wallet.TopUpInitResponse;
import com.mssus.app.dto.response.wallet.TopUpWebhookConfirmResponse;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;

public interface TopUpService {
    /**
     * Initiate top-up: Tạo PayOS payment link và PENDING transaction
     */
    TopUpInitResponse initiateTopUp(TopUpInitRequest request, Authentication authentication);

    /**
     * Handle PayOS webhook callback
     */
    void handleTopUpWebhook(String orderCode, String status, BigDecimal amount);

    /**
     * Confirm transaction status after webhook
     */
    TopUpWebhookConfirmResponse confirmTopUpWebhook(String orderCode, BigDecimal amount);
}


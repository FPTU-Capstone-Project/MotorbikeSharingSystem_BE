package com.mssus.app.service;

import jakarta.annotation.Nonnull;
import vn.payos.type.CheckoutResponseData;

import java.math.BigDecimal;

public interface PayOSService {
    /**
     * ✅ Chỉ tạo PayOS payment link, KHÔNG tạo transaction
     */
    CheckoutResponseData createTopUpPaymentLink(
        Integer userId,
        BigDecimal amount,
        String email,
        @Nonnull String description,
        String returnUrl,
        String cancelUrl
    ) throws Exception;
    
    /**
     * ✅ Parse webhook payload, KHÔNG xử lý transaction
     * @return WebhookPayload với orderCode, status, amount
     */
    WebhookPayload parseWebhook(String payload);
    
    /**
     * DTO cho parsed webhook data
     */
    record WebhookPayload(String orderCode, String status, BigDecimal amount) {}
}

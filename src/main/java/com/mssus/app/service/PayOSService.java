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
     * ✅ Hủy PayOS payment link
     * API: POST /v2/payment-requests/{id}/cancel
     */
    void cancelPaymentLink(Long orderCode, String cancellationReason) throws Exception;

    /**
     * ✅ Tạo lại PayOS payment link với orderCode mới (dùng khi duplicate request)
     * PayOS không cho phép tạo trùng orderCode, nên phải:
     * 1. Hủy link cũ
     * 2. Tạo orderCode mới
     * 3. Tạo payment link mới với orderCode mới
     *
     * @param oldOrderCode OrderCode cũ cần hủy
     * @return CheckoutResponseData với orderCode mới
     */
    CheckoutResponseData recreateTopUpPaymentLink(
            Long oldOrderCode,
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

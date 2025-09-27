package com.mssus.app.service;

import com.mssus.app.entity.Transactions;
import jakarta.annotation.Nonnull;
import vn.payos.type.CheckoutResponseData;

import java.math.BigDecimal;

public interface PayOSService {
    CheckoutResponseData createPaymentLink(Long orderCode, BigDecimal amount, @Nonnull String description) throws Exception;
    CheckoutResponseData createTopUpPaymentLink(Integer userId, BigDecimal amount, @Nonnull String description) throws Exception;
    void handleWebhook(String payload);
}

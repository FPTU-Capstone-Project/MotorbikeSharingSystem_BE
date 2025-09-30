package com.mssus.app.service;

import com.mssus.app.entity.Transaction;
import jakarta.annotation.Nonnull;
import vn.payos.type.CheckoutResponseData;

import java.math.BigDecimal;

public interface PayOSService {
    CheckoutResponseData createTopUpPaymentLink(Integer userId, BigDecimal amount, @Nonnull String description) throws Exception;
    void handleWebhook(String payload);
}

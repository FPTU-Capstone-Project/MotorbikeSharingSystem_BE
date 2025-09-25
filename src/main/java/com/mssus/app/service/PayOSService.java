package com.mssus.app.service;

import jakarta.annotation.Nonnull;
import vn.payos.type.CheckoutResponseData;

import java.math.BigDecimal;

public interface PayOSService {
    CheckoutResponseData createPaymentLink(Long orderCode, BigDecimal amount, @Nonnull String description) throws Exception;
}

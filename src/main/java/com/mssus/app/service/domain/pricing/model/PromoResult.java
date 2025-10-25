package com.mssus.app.service.domain.pricing.model;

public record PromoResult(
    boolean applied,
    String promoCode,
    MoneyVnd discount,
    String reason // "expired", "limit_reached", "ok"
) {}


package com.mssus.app.common.exception.wallet;

import java.math.BigDecimal;

public class InsufficientBalanceException extends WalletException {

    private final BigDecimal availableBalance;
    private final BigDecimal requiredAmount;
    private final BigDecimal shortfall;

    public InsufficientBalanceException(BigDecimal availableBalance, BigDecimal requiredAmount) {
        super("INSUFFICIENT_BALANCE",
              String.format("Insufficient wallet balance. Available: %s, Required: %s, Shortfall: %s",
                           availableBalance, requiredAmount, requiredAmount.subtract(availableBalance)));
        this.availableBalance = availableBalance;
        this.requiredAmount = requiredAmount;
        this.shortfall = requiredAmount.subtract(availableBalance);
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public BigDecimal getRequiredAmount() {
        return requiredAmount;
    }

    public BigDecimal getShortfall() {
        return shortfall;
    }
}
package com.mssus.app.service;

import java.math.BigDecimal;

/**
 * Service để tính toán số dư từ ledger (transactions table)
 * Đây là SINGLE SOURCE OF TRUTH cho balance calculation
 */
public interface BalanceCalculationService {
    
    /**
     * Tính số dư khả dụng từ ledger (transactions table)
     * SSOT - Single Source of Truth
     */
    BigDecimal calculateAvailableBalance(Integer walletId);
    
    /**
     * Tính số dư đang hold từ ledger
     */
    BigDecimal calculatePendingBalance(Integer walletId);
    
    /**
     * Tính tổng số dư (available + pending)
     */
    BigDecimal calculateTotalBalance(Integer walletId);
}


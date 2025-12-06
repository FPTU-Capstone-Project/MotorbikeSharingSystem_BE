package com.mssus.app.service.impl;

import com.mssus.app.repository.TransactionRepository;
import com.mssus.app.service.BalanceCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// Cache annotations removed - balance is calculated directly from database
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Service để tính balance từ ledger (không dùng cache)
 * 
 * Strategy:
 * 1. Tính balance trực tiếp từ transactions table mỗi lần query
 * 2. Snapshot strategy: Lưu balance snapshot mỗi ngày
 * 3. Index optimization: Đảm bảo indexes cho wallet_id + status queries
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BalanceCalculationServiceImpl implements BalanceCalculationService {
    
    private final TransactionRepository transactionRepository;
    
    /**
     * Tính số dư khả dụng từ ledger (transactions table)
     * Đây là SINGLE SOURCE OF TRUTH cho balance
     * Tính trực tiếp từ database mỗi lần query (không dùng cache)
     */
    @Override
    @Transactional(readOnly = true)
    public BigDecimal calculateAvailableBalance(Integer walletId) {
        BigDecimal balance = transactionRepository.calculateAvailableBalance(walletId);
        log.debug("Calculated available balance for wallet {}: {}", walletId, balance);
        return balance != null ? balance : BigDecimal.ZERO;
    }
    
    /**
     * Tính số dư đang hold từ ledger
     * Tính trực tiếp từ database mỗi lần query (không dùng cache)
     */
    @Override
    @Transactional(readOnly = true)
    public BigDecimal calculatePendingBalance(Integer walletId) {
        BigDecimal balance = transactionRepository.calculatePendingBalance(walletId);
        log.debug("Calculated pending balance for wallet {}: {}", walletId, balance);
        return balance != null ? balance : BigDecimal.ZERO;
    }
    
    /**
     * Tính tổng số dư (available + pending)
     * Tính trực tiếp từ database mỗi lần query (không dùng cache)
     */
    @Override
    @Transactional(readOnly = true)
    public BigDecimal calculateTotalBalance(Integer walletId) {
        BigDecimal available = calculateAvailableBalance(walletId);
        BigDecimal pending = calculatePendingBalance(walletId);
        BigDecimal total = available.add(pending);
        log.debug("Calculated total balance for wallet {}: available={}, pending={}, total={}", 
            walletId, available, pending, total);
        return total;
    }
    
    /**
     * Invalidate cache khi có transaction mới (no-op vì không dùng cache)
     * Giữ lại method này để tương thích với code hiện tại
     * Balance sẽ được tính lại trực tiếp từ database mỗi lần query
     */
    public void invalidateBalanceCache(Integer walletId) {
        log.debug("Balance cache invalidation called for wallet: {} (cache disabled)", walletId);
        // No-op: Balance is calculated directly from database, no cache to invalidate
    }
    
    /**
     * Invalidate cache cho tất cả wallets (no-op vì không dùng cache)
     * Giữ lại method này để tương thích với code hiện tại
     */
    public void invalidateAllBalanceCache() {
        log.debug("All balance cache invalidation called (cache disabled)");
        // No-op: Balance is calculated directly from database, no cache to invalidate
    }
}

package com.mssus.app.service.impl;

import com.mssus.app.repository.TransactionRepository;
import com.mssus.app.service.BalanceCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * ✅ FIX P0-BALANCE_CACHE: Service để tính balance từ ledger với caching
 * 
 * Strategy:
 * 1. Cache balance trong Redis (TTL: 5 phút)
 * 2. Invalidate cache khi có transaction mới
 * 3. Snapshot strategy: Lưu balance snapshot mỗi ngày
 * 4. Index optimization: Đảm bảo indexes cho wallet_id + status queries
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BalanceCalculationServiceImpl implements BalanceCalculationService {
    
    private final TransactionRepository transactionRepository;
    
    /**
     * Tính số dư khả dụng từ ledger (transactions table)
     * Đây là SINGLE SOURCE OF TRUTH cho balance
     * 
     * ✅ FIX P0-BALANCE_CACHE: Cache trong Redis với TTL 5 phút
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "walletBalance", key = "'available:' + #walletId", unless = "#result == null")
    public BigDecimal calculateAvailableBalance(Integer walletId) {
        BigDecimal balance = transactionRepository.calculateAvailableBalance(walletId);
        log.debug("Calculated available balance for wallet {}: {}", walletId, balance);
        return balance != null ? balance : BigDecimal.ZERO;
    }
    
    /**
     * Tính số dư đang hold từ ledger
     * 
     * ✅ FIX P0-BALANCE_CACHE: Cache trong Redis với TTL 5 phút
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "walletBalance", key = "'pending:' + #walletId", unless = "#result == null")
    public BigDecimal calculatePendingBalance(Integer walletId) {
        BigDecimal balance = transactionRepository.calculatePendingBalance(walletId);
        log.debug("Calculated pending balance for wallet {}: {}", walletId, balance);
        return balance != null ? balance : BigDecimal.ZERO;
    }
    
    /**
     * Tính tổng số dư (available + pending)
     * 
     * ✅ FIX P0-BALANCE_CACHE: Cache trong Redis với TTL 5 phút
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "walletBalance", key = "'total:' + #walletId", unless = "#result == null")
    public BigDecimal calculateTotalBalance(Integer walletId) {
        BigDecimal available = calculateAvailableBalance(walletId);
        BigDecimal pending = calculatePendingBalance(walletId);
        BigDecimal total = available.add(pending);
        log.debug("Calculated total balance for wallet {}: available={}, pending={}, total={}", 
            walletId, available, pending, total);
        return total;
    }
    
    /**
     * ✅ FIX P0-BALANCE_CACHE: Invalidate cache khi có transaction mới
     * Gọi method này sau khi tạo/update transaction
     */
    @Caching(evict = {
        @CacheEvict(value = "walletBalance", key = "'available:' + #walletId"),
        @CacheEvict(value = "walletBalance", key = "'pending:' + #walletId"),
        @CacheEvict(value = "walletBalance", key = "'total:' + #walletId")
    })
    public void invalidateBalanceCache(Integer walletId) {
        log.debug("Invalidated balance cache for wallet: {}", walletId);
    }
    
    /**
     * ✅ FIX P0-BALANCE_CACHE: Invalidate cache cho tất cả wallets
     * Dùng khi cần force refresh toàn bộ
     */
    @CacheEvict(value = "walletBalance", allEntries = true)
    public void invalidateAllBalanceCache() {
        log.info("Invalidated all balance cache");
    }
}

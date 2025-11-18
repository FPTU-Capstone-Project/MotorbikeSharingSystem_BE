package com.mssus.app.service.impl;

import com.mssus.app.repository.TransactionRepository;
import com.mssus.app.service.BalanceCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class BalanceCalculationServiceImpl implements BalanceCalculationService {
    
    private final TransactionRepository transactionRepository;
    
    /**
     * Tính số dư khả dụng từ ledger (transactions table)
     * Đây là SINGLE SOURCE OF TRUTH cho balance
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
}

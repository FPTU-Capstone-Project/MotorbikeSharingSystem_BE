package com.mssus.app.service.impl;

import com.mssus.app.common.exception.ValidationException;
import com.mssus.app.repository.TransactionRepository;
import com.mssus.app.service.LedgerValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * ✅ FIX P0-LEDGER: Service để validate Ledger Total Invariants
 * 
 * Đảm bảo: SUM(all debit) = SUM(all credit)
 * Nếu không balanced → hệ thống đã tự sinh hoặc mất tiền (CRITICAL!)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerValidationServiceImpl implements LedgerValidationService {
    
    private final TransactionRepository transactionRepository;
    
    @Override
    @Transactional(readOnly = true)
    public BigDecimal calculateTotalDebit() {
        BigDecimal totalDebit = transactionRepository.calculateTotalDebit();
        log.debug("Total debit (OUT direction): {}", totalDebit);
        return totalDebit != null ? totalDebit : BigDecimal.ZERO;
    }
    
    @Override
    @Transactional(readOnly = true)
    public BigDecimal calculateTotalCredit() {
        BigDecimal totalCredit = transactionRepository.calculateTotalCredit();
        log.debug("Total credit (IN direction): {}", totalCredit);
        return totalCredit != null ? totalCredit : BigDecimal.ZERO;
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean validateLedgerInvariants() {
        BigDecimal debit = calculateTotalDebit();
        BigDecimal credit = calculateTotalCredit();
        BigDecimal difference = debit.subtract(credit);
        
        boolean isBalanced = difference.abs().compareTo(BigDecimal.valueOf(0.01)) < 0; // Allow 0.01 VND tolerance for rounding
        
        if (!isBalanced) {
            log.error("❌ LEDGER INVARIANT VIOLATION: Debit ({}) != Credit ({}) | Difference: {}",
                debit, credit, difference);
        } else {
            log.debug("✅ Ledger invariants validated: Debit = Credit = {}", debit);
        }
        
        return isBalanced;
    }
    
    @Override
    @Transactional(readOnly = true)
    public void validateLedgerInvariantsOrThrow() {
        BigDecimal debit = calculateTotalDebit();
        BigDecimal credit = calculateTotalCredit();
        BigDecimal difference = debit.subtract(credit);
        
        // Allow 0.01 VND tolerance for rounding errors
        if (difference.abs().compareTo(BigDecimal.valueOf(0.01)) >= 0) {
            String errorMsg = String.format(
                "Ledger invariant violation: Total Debit (%s) != Total Credit (%s) | Difference: %s VND. " +
                "This indicates money was created or lost in the system!",
                debit, credit, difference);
            log.error(errorMsg);
            throw new ValidationException(errorMsg);
        }
        
        log.debug("✅ Ledger invariants validated: Debit = Credit = {}", debit);
    }
    
    @Override
    @Transactional(readOnly = true)
    public BigDecimal calculateLedgerDifference() {
        BigDecimal debit = calculateTotalDebit();
        BigDecimal credit = calculateTotalCredit();
        BigDecimal difference = debit.subtract(credit);
        log.debug("Ledger difference (Debit - Credit): {}", difference);
        return difference;
    }
    
    @Override
    @Transactional(readOnly = true)
    public BigDecimal calculateInternalDebit() {
        BigDecimal totalDebit = transactionRepository.calculateInternalDebit();
        log.debug("Internal debit (OUT, excluding TOPUP/PAYOUT system): {}", totalDebit);
        return totalDebit != null ? totalDebit : BigDecimal.ZERO;
    }
    
    @Override
    @Transactional(readOnly = true)
    public BigDecimal calculateInternalCredit() {
        BigDecimal totalCredit = transactionRepository.calculateInternalCredit();
        log.debug("Internal credit (IN, excluding TOPUP/PAYOUT system): {}", totalCredit);
        return totalCredit != null ? totalCredit : BigDecimal.ZERO;
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean validateInternalLedgerInvariants() {
        BigDecimal debit = calculateInternalDebit();
        BigDecimal credit = calculateInternalCredit();
        BigDecimal difference = debit.subtract(credit);
        
        // Allow 0.01 VND tolerance for rounding
        boolean isBalanced = difference.abs().compareTo(BigDecimal.valueOf(0.01)) < 0;
        
        if (!isBalanced) {
            log.error("❌ INTERNAL LEDGER INVARIANT VIOLATION: Internal Debit ({}) != Internal Credit ({}) | Difference: {}",
                debit, credit, difference);
        } else {
            log.debug("✅ Internal ledger invariants validated: Internal Debit = Internal Credit = {}", debit);
        }
        
        return isBalanced;
    }
    
    @Override
    @Transactional(readOnly = true)
    public void validateInternalLedgerInvariantsOrThrow() {
        BigDecimal debit = calculateInternalDebit();
        BigDecimal credit = calculateInternalCredit();
        BigDecimal difference = debit.subtract(credit);
        
        // Allow 0.01 VND tolerance for rounding errors
        if (difference.abs().compareTo(BigDecimal.valueOf(0.01)) >= 0) {
            String errorMsg = String.format(
                "Internal ledger invariant violation: Internal Debit (%s) != Internal Credit (%s) | Difference: %s VND. " +
                "This indicates money was created or lost in internal transactions (excluding TOPUP/PAYOUT external flows)!",
                debit, credit, difference);
            log.error(errorMsg);
            throw new ValidationException(errorMsg);
        }
        
        log.debug("✅ Internal ledger invariants validated: Internal Debit = Internal Credit = {}", debit);
    }
}


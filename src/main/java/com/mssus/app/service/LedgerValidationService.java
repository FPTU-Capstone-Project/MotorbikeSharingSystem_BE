package com.mssus.app.service;

import java.math.BigDecimal;

/**
 * Service để validate Ledger Total Invariants
 * 
 * Nguyên tắc: SUM(all debit entries) = SUM(all credit entries)
 * 
 * Trong hệ thống:
 * - Debit = OUT direction (tiền ra khỏi hệ thống)
 * - Credit = IN direction (tiền vào hệ thống)
 * - INTERNAL không ảnh hưởng tổng (chỉ chuyển đổi trong cùng wallet)
 * 
 * Nếu không balance → hệ thống đã tự sinh hoặc mất tiền (CRITICAL BUG!)
 */
public interface LedgerValidationService {
    
    /**
     * Tính tổng debit (OUT direction) của tất cả transactions
     * Chỉ tính SUCCESS transactions
     */
    BigDecimal calculateTotalDebit();
    
    /**
     * Tính tổng credit (IN direction) của tất cả transactions
     * Chỉ tính SUCCESS transactions
     */
    BigDecimal calculateTotalCredit();
    
    /**
     * Validate ledger invariants
     * @return true nếu balanced, false nếu không balanced
     */
    boolean validateLedgerInvariants();
    
    /**
     * Validate và throw exception nếu không balanced
     * @throws ValidationException nếu ledger không balanced
     */
    void validateLedgerInvariantsOrThrow();
    
    /**
     * Tính difference giữa debit và credit
     * @return difference (debit - credit). Nếu = 0 thì balanced
     */
    BigDecimal calculateLedgerDifference();
    
    /**
     * Tính tổng debit cho internal transactions only
     * Loại trừ TOPUP và PAYOUT system transactions
     */
    BigDecimal calculateInternalDebit();
    
    /**
     * Tính tổng credit cho internal transactions only
     * Loại trừ TOPUP và PAYOUT system transactions
     */
    BigDecimal calculateInternalCredit();
    
    /**
     * Validate internal ledger invariants (loại trừ external transactions)
     * @return true nếu balanced, false nếu không balanced
     */
    boolean validateInternalLedgerInvariants();
    
    /**
     * Validate internal ledger và throw exception nếu không balanced
     */
    void validateInternalLedgerInvariantsOrThrow();
}


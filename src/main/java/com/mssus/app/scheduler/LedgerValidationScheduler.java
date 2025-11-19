package com.mssus.app.scheduler;

import com.mssus.app.service.LedgerValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * ✅ FIX P0-LEDGER: Scheduler để validate Ledger Total Invariants định kỳ
 * 
 * Chạy mỗi giờ để phát hiện sớm nếu có money leak hoặc money creation
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LedgerValidationScheduler {
    private final LedgerValidationService ledgerValidationService;
    
    /**
     * Validate ledger invariants every hour
     * Runs at minute 0 of every hour
     * 
     * Note: Full ledger (including TOPUP/PAYOUT) may not balance due to external flows.
     * Focus on internal ledger validation.
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour at minute 0
    public void validateLedgerInvariants() {
        log.info("Starting ledger invariant validation");
        
        try {
            // 1. Validate full ledger (for reference, may not balance due to external flows)
            BigDecimal totalDebit = ledgerValidationService.calculateTotalDebit();
            BigDecimal totalCredit = ledgerValidationService.calculateTotalCredit();
            BigDecimal totalDifference = ledgerValidationService.calculateLedgerDifference();
            
            log.info("Full ledger: Debit = {}, Credit = {}, Difference = {}", 
                totalDebit, totalCredit, totalDifference);
            
            // 2. Validate internal ledger (should always balance)
            BigDecimal internalDebit = ledgerValidationService.calculateInternalDebit();
            BigDecimal internalCredit = ledgerValidationService.calculateInternalCredit();
            
            boolean isInternalBalanced = ledgerValidationService.validateInternalLedgerInvariants();
            
            if (isInternalBalanced) {
                log.info("✅ Internal ledger invariants validated successfully: Internal Debit = Internal Credit = {}", 
                    internalDebit);
            } else {
                BigDecimal internalDifference = internalDebit.subtract(internalCredit);
                log.error("❌ CRITICAL: Internal ledger invariant violation detected!");
                log.error("   Internal Debit: {}", internalDebit);
                log.error("   Internal Credit: {}", internalCredit);
                log.error("   Difference: {} VND", internalDifference);
                log.error("   This indicates money was created or lost in internal transactions!");
                // TODO: Send alert to monitoring system (e.g., email, Slack, PagerDuty)
            }
        } catch (Exception e) {
            log.error("Failed to validate ledger invariants: {}", e.getMessage(), e);
        }
    }
}


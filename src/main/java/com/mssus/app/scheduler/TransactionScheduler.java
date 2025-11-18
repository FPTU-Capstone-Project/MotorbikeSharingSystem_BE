package com.mssus.app.scheduler;

import com.mssus.app.common.enums.TransactionStatus;
import com.mssus.app.common.enums.TransactionType;
import com.mssus.app.entity.Transaction;
import com.mssus.app.repository.TransactionRepository;
import com.mssus.app.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ✅ FIX P2-9: Scheduler để timeout PENDING transactions sau 24 giờ
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionScheduler {
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    
    /**
     * Timeout PENDING transactions older than 24 hours
     * Runs every hour
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    public void timeoutPendingTransactions() {
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusHours(24);
        
        log.info("Starting timeout job for PENDING transactions older than {}", timeoutThreshold);
        
        // Find PENDING TOPUP transactions older than 24 hours
        List<Transaction> pendingTopups = transactionRepository
            .findByStatusAndTypeAndCreatedAtBefore(
                TransactionStatus.PENDING, 
                TransactionType.TOPUP, 
                timeoutThreshold);
        
        int topupTimeoutCount = 0;
        for (Transaction txn : pendingTopups) {
            try {
                transactionService.handleTopupFailed(
                    txn.getPspRef(), 
                    "Transaction timeout after 24 hours");
                topupTimeoutCount++;
                log.info("Timeout topup transaction: pspRef={}, txnId={}", 
                    txn.getPspRef(), txn.getTxnId());
            } catch (Exception e) {
                log.error("Failed to timeout topup transaction: pspRef={}, error={}", 
                    txn.getPspRef(), e.getMessage(), e);
            }
        }
        
        // Find PENDING PAYOUT transactions older than 24 hours
        List<Transaction> pendingPayouts = transactionRepository
            .findByStatusAndTypeAndCreatedAtBefore(
                TransactionStatus.PENDING, 
                TransactionType.PAYOUT, 
                timeoutThreshold);
        
        int payoutTimeoutCount = 0;
        for (Transaction txn : pendingPayouts) {
            try {
                transactionService.handlePayoutFailed(
                    txn.getPspRef(), 
                    "Transaction timeout after 24 hours");
                payoutTimeoutCount++;
                log.info("Timeout payout transaction: pspRef={}, txnId={}", 
                    txn.getPspRef(), txn.getTxnId());
            } catch (Exception e) {
                log.error("Failed to timeout payout transaction: pspRef={}, error={}", 
                    txn.getPspRef(), e.getMessage(), e);
            }
        }
        
        log.info("Timeout job completed: topup={}, payout={}", topupTimeoutCount, payoutTimeoutCount);
    }
}


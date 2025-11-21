package com.mssus.app.scheduler;

import com.mssus.app.common.enums.TransactionStatus;
import com.mssus.app.common.enums.TransactionType;
import com.mssus.app.entity.Transaction;
import com.mssus.app.repository.TransactionRepository;
import com.mssus.app.service.PayoutPollingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduler to poll PayOS API for payout status when webhook is not received.
 * Runs periodically to check PENDING/PROCESSING payouts that are older than threshold.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PayoutPollingScheduler {

    private final TransactionRepository transactionRepository;
    private final PayoutPollingService payoutPollingService;

    /**
     * Poll PayOS API for payout status.
     * Runs every 15 minutes to check payouts that might have missed webhook.
     */
    @Scheduled(cron = "${app.payout.polling.cron:0 */15 * * * *}") // Every 15 minutes
    public void pollPayoutStatus() {
        log.info("Starting payout polling job");

        try {
            // Find PENDING and PROCESSING payout transactions
            List<Transaction> pendingPayouts = transactionRepository
                    .findByTypeAndStatusAndActorKindUser(TransactionType.PAYOUT, TransactionStatus.PENDING);
            
            List<Transaction> processingPayouts = transactionRepository
                    .findByTypeAndStatusAndActorKindUser(TransactionType.PAYOUT, TransactionStatus.PROCESSING);

            int polledCount = 0;
            int updatedCount = 0;
            int errorCount = 0;

            // Poll PENDING payouts
            for (Transaction txn : pendingPayouts) {
                if (payoutPollingService.shouldPoll(txn)) {
                    try {
                        polledCount++;
                        boolean updated = payoutPollingService.pollPayoutStatus(txn);
                        if (updated) {
                            updatedCount++;
                        }
                    } catch (Exception e) {
                        errorCount++;
                        log.error("Error polling payout status for txnId={}, pspRef={}: {}", 
                                txn.getTxnId(), txn.getPspRef(), e.getMessage(), e);
                    }
                }
            }

            // Poll PROCESSING payouts
            for (Transaction txn : processingPayouts) {
                if (payoutPollingService.shouldPoll(txn)) {
                    try {
                        polledCount++;
                        boolean updated = payoutPollingService.pollPayoutStatus(txn);
                        if (updated) {
                            updatedCount++;
                        }
                    } catch (Exception e) {
                        errorCount++;
                        log.error("Error polling payout status for txnId={}, pspRef={}: {}", 
                                txn.getTxnId(), txn.getPspRef(), e.getMessage(), e);
                    }
                }
            }

            log.info("Payout polling job completed: polled={}, updated={}, errors={}", 
                    polledCount, updatedCount, errorCount);

        } catch (Exception e) {
            log.error("Fatal error in payout polling job", e);
        }
    }

    /**
     * Alert admin if there are payouts stuck in PENDING/PROCESSING for too long.
     * Runs every hour.
     */
    @Scheduled(cron = "${app.payout.alert.cron:0 0 * * * *}") // Every hour
    public void alertStuckPayouts() {
        log.debug("Checking for stuck payouts");

        try {
            LocalDateTime alertThreshold = LocalDateTime.now().minusHours(2); // Alert if stuck > 2 hours

            List<Transaction> stuckPayouts = transactionRepository
                    .findByTypeAndStatusAndActorKindUser(TransactionType.PAYOUT, TransactionStatus.PENDING)
                    .stream()
                    .filter(txn -> txn.getCreatedAt() != null && txn.getCreatedAt().isBefore(alertThreshold))
                    .toList();

            if (!stuckPayouts.isEmpty()) {
                log.warn("Found {} payouts stuck in PENDING status for more than 2 hours", stuckPayouts.size());
                for (Transaction txn : stuckPayouts) {
                    log.warn("Stuck payout: txnId={}, pspRef={}, createdAt={}, age={} hours",
                            txn.getTxnId(),
                            txn.getPspRef(),
                            txn.getCreatedAt(),
                            java.time.Duration.between(txn.getCreatedAt(), LocalDateTime.now()).toHours());
                }
                // TODO: Send alert to admin (email/Slack/etc)
            }

        } catch (Exception e) {
            log.error("Error in stuck payout alert job", e);
        }
    }
}


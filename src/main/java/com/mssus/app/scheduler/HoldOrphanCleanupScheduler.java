package com.mssus.app.scheduler;

import com.mssus.app.common.enums.TransactionStatus;
import com.mssus.app.common.enums.TransactionType;
import com.mssus.app.entity.Transaction;
import com.mssus.app.repository.TransactionRepository;
import com.mssus.app.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ✅ FIX P0-HOLD_ORPHAN: Scheduler để detect và cleanup orphaned holds
 * 
 * Orphaned hold = HOLD_CREATE transaction không có corresponding HOLD_RELEASE
 * sau một khoảng thời gian (e.g., 7 ngày)
 * 
 * Nguyên nhân:
 * - Ride/request bị cancel nhưng release hold failed (exception)
 * - System crash trước khi release
 * - Manual intervention needed
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HoldOrphanCleanupScheduler {
    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    
    /**
     * Detect và cleanup orphaned holds
     * Runs daily at 3 AM
     */
    @Scheduled(cron = "0 0 3 * * *") // Daily at 3 AM
    public void cleanupOrphanedHolds() {
        log.info("Starting orphaned hold cleanup job");
        
        LocalDateTime threshold = LocalDateTime.now().minusDays(7); // Holds older than 7 days
        
        // Find all HOLD_CREATE transactions older than threshold
        List<Transaction> holdCreates = transactionRepository
            .findByStatusAndTypeAndCreatedAtBefore(
                TransactionStatus.SUCCESS,
                TransactionType.HOLD_CREATE,
                threshold);
        
        int orphanedCount = 0;
        int releasedCount = 0;
        int errorCount = 0;
        
        for (Transaction holdCreate : holdCreates) {
            UUID groupId = holdCreate.getGroupId();
            if (groupId == null) {
                log.warn("HOLD_CREATE transaction {} has no groupId, skipping", holdCreate.getTxnId());
                continue;
            }
            
            // Check if already released
            Optional<Transaction> releaseTxn = transactionRepository
                .findByGroupIdAndType(groupId, TransactionType.HOLD_RELEASE);
            boolean isReleased = releaseTxn.isPresent();
            
            if (!isReleased) {
                orphanedCount++;
                log.warn("Found orphaned hold: txnId={}, groupId={}, amount={}, createdAt={}",
                    holdCreate.getTxnId(), groupId, holdCreate.getAmount(), holdCreate.getCreatedAt());
                
                try {
                    // Auto-release orphaned hold
                    walletService.releaseHold(
                        groupId,
                        "Auto-release: Orphaned hold detected after 7 days (cleanup job)");
                    
                    releasedCount++;
                    log.info("Auto-released orphaned hold: groupId={}, amount={}", 
                        groupId, holdCreate.getAmount());
                    
                } catch (Exception e) {
                    errorCount++;
                    log.error("Failed to auto-release orphaned hold: groupId={}, error={}", 
                        groupId, e.getMessage(), e);
                    // TODO: Send alert to monitoring system
                }
            }
        }
        
        log.info("Orphaned hold cleanup completed: total={}, orphaned={}, released={}, errors={}", 
            holdCreates.size(), orphanedCount, releasedCount, errorCount);
        
        if (orphanedCount > 0) {
            log.warn("⚠️ Found {} orphaned holds! This indicates potential issues with hold release logic.", orphanedCount);
        }
    }
}


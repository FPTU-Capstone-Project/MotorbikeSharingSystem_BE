package com.mssus.app.scheduler;

import com.mssus.app.entity.Wallet;
import com.mssus.app.repository.WalletRepository;
import com.mssus.app.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ✅ FIX P2-10: Scheduler để chạy daily reconciliation cho tất cả wallets
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WalletScheduler {
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    
    /**
     * Daily reconciliation for all wallets
     * Runs daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    public void dailyReconciliation() {
        log.info("Starting daily reconciliation for all wallets");
        
        List<Wallet> wallets = walletRepository.findAll();
        int successCount = 0;
        int errorCount = 0;
        
        for (Wallet wallet : wallets) {
            try {
                if (wallet.getUser() != null && wallet.getUser().getUserId() != null) {
                    walletService.reconcileWalletBalance(wallet.getUser().getUserId());
                    successCount++;
                } else {
                    log.warn("Wallet {} has no user associated, skipping reconciliation", wallet.getWalletId());
                }
            } catch (Exception e) {
                log.error("Reconciliation failed for wallet {}: {}", 
                    wallet.getWalletId(), e.getMessage(), e);
                errorCount++;
            }
        }
        
        log.info("Daily reconciliation completed: total={}, success={}, errors={}", 
            wallets.size(), successCount, errorCount);
    }
}


package com.mssus.app.scheduler;

import com.mssus.app.service.BankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler to update bank list from VietQR API every 7 days.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BankUpdateScheduler {

    private final BankService bankService;

    /**
     * Update banks from VietQR API.
     * Runs every 7 days at 2 AM.
     */
    @Scheduled(cron = "${app.banks.update-cron:0 0 2 */7 * *}") // Every 7 days at 2 AM
    public void updateBanks() {
        log.info("Starting scheduled bank update from VietQR API");

        try {
            bankService.fetchAndSaveBanks();
            log.info("Scheduled bank update completed successfully");
        } catch (Exception e) {
            log.error("Failed to update banks in scheduled job", e);
            // Don't throw - allow next run to retry
        }
    }
}


package com.mssus.app.service.domain.recon;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationScheduler {

    private final ReconciliationService reconciliationService;

    @Scheduled(cron = "0 0 1 * * *")
    public void runDaily() {
        log.info("Starting daily reconciliation run");
        reconciliationService.runReconciliation();
    }
}

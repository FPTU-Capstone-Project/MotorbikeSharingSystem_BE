package com.mssus.app.worker;

import com.mssus.app.service.UserReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReportEscalationJob {

    private final UserReportService userReportService;

    /**
     * Auto-escalate stale reports every 6 hours
     * Runs at: 00:00, 06:00, 12:00, 18:00 daily
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void escalateStaleReports() {
        log.info("Starting auto-escalation job for stale reports");
        
        try {
            userReportService.escalateStaleReports();
            log.info("Auto-escalation job completed successfully");
        } catch (Exception e) {
            log.error("Error during auto-escalation job", e);
        }
    }
}


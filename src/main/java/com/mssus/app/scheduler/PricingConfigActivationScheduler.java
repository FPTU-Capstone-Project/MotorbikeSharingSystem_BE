package com.mssus.app.scheduler;

import com.mssus.app.common.enums.PricingConfigStatus;
import com.mssus.app.entity.PricingConfig;
import com.mssus.app.repository.PricingConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class PricingConfigActivationScheduler {

    private final PricingConfigRepository pricingConfigRepository;

    /**
     * Promote scheduled pricing configs to ACTIVE once their go-live time arrives.
     * Runs every 5 minutes to keep pricing deterministic across services.
     */
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void promoteScheduledConfig() {
        Instant now = Instant.now();
        pricingConfigRepository.findScheduled()
            .filter(cfg -> cfg.getValidFrom() != null && !cfg.getValidFrom().isAfter(now))
            .ifPresent(this::activateConfig);
    }

    private void activateConfig(PricingConfig scheduled) {
        log.info("Activating scheduled pricing config {} effective {}", scheduled.getPricingConfigId(), scheduled.getValidFrom());

        pricingConfigRepository.findFirstByStatus(PricingConfigStatus.ACTIVE)
            .filter(active -> !active.getPricingConfigId().equals(scheduled.getPricingConfigId()))
            .ifPresent(active -> {
                if (active.getValidUntil() == null || active.getValidUntil().isAfter(scheduled.getValidFrom())) {
                    active.setValidUntil(scheduled.getValidFrom());
                }
                active.setStatus(PricingConfigStatus.ARCHIVED);
                pricingConfigRepository.save(active);
            });

        scheduled.setStatus(PricingConfigStatus.ACTIVE);
        pricingConfigRepository.save(scheduled);
    }
}

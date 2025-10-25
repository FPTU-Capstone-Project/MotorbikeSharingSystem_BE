package com.mssus.app.worker;

import com.mssus.app.common.enums.SosAlertEventType;
import com.mssus.app.common.enums.SosAlertStatus;
import com.mssus.app.config.properties.SosConfigurationProperties;
import com.mssus.app.entity.SosAlert;
import com.mssus.app.repository.SosAlertRepository;
import com.mssus.app.service.SosAlertEventService;
import com.mssus.app.service.event.SosAlertEscalatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class SosEscalationWorker {

    private static final List<SosAlertStatus> ACTIVE_STATUSES = List.of(
        SosAlertStatus.ACTIVE,
        SosAlertStatus.ESCALATED
    );

    private final SosAlertRepository sosAlertRepository;
    private final SosAlertEventService sosAlertEventService;
    private final ApplicationEventPublisher eventPublisher;
    private final SosConfigurationProperties sosConfig;
    private final TransactionTemplate transactionTemplate;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SosEscalationWorker(
        SosAlertRepository sosAlertRepository,
        SosAlertEventService sosAlertEventService,
        ApplicationEventPublisher eventPublisher,
        SosConfigurationProperties sosConfig,
        PlatformTransactionManager transactionManager
    ) {
        this.sosAlertRepository = sosAlertRepository;
        this.sosAlertEventService = sosAlertEventService;
        this.eventPublisher = eventPublisher;
        this.sosConfig = sosConfig;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.sos.escalation-scan-ms:15000}")
    public void evaluateEscalations() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            LocalDateTime now = LocalDateTime.now();
            List<SosAlert> candidates = sosAlertRepository.findEscalationCandidates(ACTIVE_STATUSES, now);
            if (candidates.isEmpty()) {
                return;
            }

            for (SosAlert candidate : candidates) {
                transactionTemplate.execute(status -> {
                    escalateIfNeeded(candidate.getSosId(), now);
                    return null;
                });
            }
        } catch (Exception ex) {
            log.error("SOS escalation worker encountered an error", ex);
        } finally {
            running.set(false);
        }
    }

    private void escalateIfNeeded(Integer sosId, LocalDateTime now) {
        sosAlertRepository.findByIdForUpdate(sosId).ifPresent(alert -> {
            if (alert.getAcknowledgedAt() != null || alert.getResolvedAt() != null) {
                return;
            }

            if (alert.getNextEscalationAt() == null || alert.getNextEscalationAt().isAfter(now)) {
                return;
            }

            alert.setStatus(SosAlertStatus.ESCALATED);
            alert.setLastEscalatedAt(now);
            int escalationCount = alert.getEscalationCount() == null ? 0 : alert.getEscalationCount();
            escalationCount += 1;
            alert.setEscalationCount(escalationCount);
            alert.setNextEscalationAt(now.plus(sosConfig.getEscalationInterval()));

            boolean campusSecurityNotice = false;
            if (!Boolean.TRUE.equals(alert.getCampusSecurityNotified())
                && sosConfig.getCampusSecurityPhones() != null
                && !sosConfig.getCampusSecurityPhones().isEmpty()) {
                alert.setCampusSecurityNotified(true);
                campusSecurityNotice = true;
            }

            sosAlertRepository.save(alert);

            sosAlertEventService.record(alert, SosAlertEventType.ESCALATED,
                String.format("Escalation wave %d triggered", escalationCount), null);

            eventPublisher.publishEvent(new SosAlertEscalatedEvent(this, alert, campusSecurityNotice));
            log.info("SOS alert {} escalated (count={})", sosId, alert.getEscalationCount());
        });
    }
}

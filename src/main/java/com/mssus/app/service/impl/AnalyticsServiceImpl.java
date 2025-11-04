package com.mssus.app.service.impl;

import com.mssus.app.entity.Verification;
import com.mssus.app.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsServiceImpl implements AnalyticsService {

    @Override
    public void trackVerificationDecision(Verification verification, Map<String, Object> metadata) {
        log.info("Analytics event: verification_decision verificationId={} metadata={}",
                verification.getVerificationId(), metadata);
    }

    @Override
    public void trackVerificationEscalation(Verification verification, Map<String, Object> metadata) {
        log.info("Analytics event: verification_escalation verificationId={} metadata={}",
                verification.getVerificationId(), metadata);
    }
}

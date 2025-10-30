package com.mssus.app.service;

import com.mssus.app.entity.Verification;

import java.util.Map;

public interface AnalyticsService {

    void trackVerificationDecision(Verification verification, Map<String, Object> metadata);

    void trackVerificationEscalation(Verification verification, Map<String, Object> metadata);
}

package com.mssus.app.dto.domain.sos;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class SafetyDashboardStatsResponse {
    // SOS Alerts Statistics
    Integer activeAlertsCount;
    Integer resolvedTodayCount;
    BigDecimal averageResponseTimeMinutes;
    Integer totalAlertsCount;
    
    // Driver Verification Statistics
    Long approvedDriversCount;
    Long pendingDriversCount;
    Long rejectedDriversCount;
    BigDecimal driverVerificationPercentage;
    
    // Additional metrics
    Integer escalatedAlertsCount;
    Integer falseAlarmCount;
    Integer acknowledgedAlertsCount;
}


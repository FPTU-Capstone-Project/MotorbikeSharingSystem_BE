package com.mssus.app.service;

import com.mssus.app.dto.domain.sos.SafetyAlertsListRequest;
import com.mssus.app.dto.domain.sos.SafetyAlertsListResponse;
import com.mssus.app.dto.domain.sos.SafetyDashboardStatsResponse;
import com.mssus.app.entity.User;

public interface SafetyService {
    
    SafetyDashboardStatsResponse getDashboardStats();
    
    SafetyAlertsListResponse getAlertsList(SafetyAlertsListRequest request);
    
    void markAsResolved(User adminUser, Integer alertId, String notes);
    
    void markAsFalseAlarm(User adminUser, Integer alertId, String notes);
}


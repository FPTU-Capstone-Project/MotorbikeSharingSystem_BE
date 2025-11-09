package com.mssus.app.dto.domain.sos;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class SafetyAlertsListResponse {
    List<SosAlertResponse> alerts;
    Integer totalPages;
    Long totalRecords;
    Integer currentPage;
    Integer pageSize;
}


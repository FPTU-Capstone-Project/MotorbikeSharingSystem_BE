package com.mssus.app.dto.domain.sos;

import com.mssus.app.common.enums.SosAlertStatus;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class SafetyAlertsListRequest {
    List<SosAlertStatus> statuses;
    Integer page;
    Integer pageSize;
    String sortBy;
    String sortDir;
}


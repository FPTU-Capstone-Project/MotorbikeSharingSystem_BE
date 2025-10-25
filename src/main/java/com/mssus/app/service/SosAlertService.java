package com.mssus.app.service;

import com.mssus.app.common.enums.SosAlertStatus;
import com.mssus.app.dto.domain.sos.AcknowledgeSosRequest;
import com.mssus.app.dto.domain.sos.ResolveSosRequest;
import com.mssus.app.dto.domain.sos.SosAlertResponse;
import com.mssus.app.dto.domain.sos.TriggerSosRequest;
import com.mssus.app.entity.User;

import java.util.List;

public interface SosAlertService {

    SosAlertResponse triggerSos(User user, TriggerSosRequest request);

    SosAlertResponse acknowledgeAlert(User staffUser, Integer alertId, AcknowledgeSosRequest request);

    SosAlertResponse resolveAlert(User staffUser, Integer alertId, ResolveSosRequest request);

    List<SosAlertResponse> getAlertsForUser(User user, List<SosAlertStatus> statuses);

    List<SosAlertResponse> getAlertsForAdmin(List<SosAlertStatus> statuses);

    SosAlertResponse getAlert(Integer alertId);

    List<SosAlertResponse.SosAlertEventDto> getAlertTimeline(Integer alertId);
}

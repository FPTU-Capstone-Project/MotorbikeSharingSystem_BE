package com.mssus.app.dto.sos;

import com.mssus.app.common.enums.AlertType;
import com.mssus.app.common.enums.SosAlertStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class SosAlertResponse {
    Integer sosId;
    Integer sharedRideId;
    Integer triggeredBy;
    String triggeredByName;
    AlertType alertType;
    Double currentLat;
    Double currentLng;
    String contactInfo;
    String rideSnapshot;
    String description;
    SosAlertStatus status;
    Integer acknowledgedBy;
    String acknowledgedByName;
    LocalDateTime acknowledgedAt;
    Integer resolvedBy;
    String resolvedByName;
    LocalDateTime resolvedAt;
    String resolutionNotes;
    LocalDateTime lastEscalatedAt;
    LocalDateTime nextEscalationAt;
    Integer escalationCount;
    Boolean fallbackContactUsed;
    Boolean autoCallTriggered;
    Boolean campusSecurityNotified;
    LocalDateTime acknowledgementDeadline;
    LocalDateTime createdAt;
    List<SosAlertEventDto> timeline;

    @Value
    @Builder
    public static class SosAlertEventDto {
        Long eventId;
        String eventType;
        String description;
        String metadata;
        LocalDateTime createdAt;
    }
}

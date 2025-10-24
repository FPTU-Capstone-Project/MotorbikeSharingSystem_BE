package com.mssus.app.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mssus.app.common.enums.DeliveryMethod;
import com.mssus.app.common.enums.NotificationType;
import com.mssus.app.common.enums.Priority;
import com.mssus.app.common.enums.SosAlertEventType;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.config.properties.SosConfigurationProperties;
import com.mssus.app.entity.EmergencyContact;
import com.mssus.app.entity.SosAlert;
import com.mssus.app.entity.User;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.service.NotificationService;
import com.mssus.app.service.SosAlertEventService;
import com.mssus.app.service.event.SosAlertEscalatedEvent;
import com.mssus.app.service.event.SosAlertResolvedEvent;
import com.mssus.app.service.event.SosAlertTriggeredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class SosAlertNotificationListener {

    private static final String SOS_QUEUE = "/queue/sos";

    private final NotificationService notificationService;
    private final SosAlertEventService sosAlertEventService;
    private final UserRepository userRepository;
    private final SosConfigurationProperties sosConfig;
    private final ObjectMapper objectMapper;

    @EventListener
    public void handleSosAlertTriggered(SosAlertTriggeredEvent event) {
        SosAlert alert = event.getSosAlert();
        User triggeredBy = alert.getTriggeredBy();

        notifyOriginUser(alert, triggeredBy);
        notifyAdministrators(alert, "New SOS alert activated", NotificationType.SOS_ALERT);
        notifyEmergencyContacts(alert, event.getContacts());
    }

    @EventListener
    public void handleSosAlertEscalated(SosAlertEscalatedEvent event) {
        SosAlert alert = event.getSosAlert();
        notifyAdministrators(alert, "SOS alert escalation pending acknowledgement", NotificationType.SOS_ESCALATED);

        if (event.isCampusSecurityNotice()) {
            notifyCampusSecurity(alert);
        }
    }

    @EventListener
    public void handleSosAlertResolved(SosAlertResolvedEvent event) {
        SosAlert alert = event.getSosAlert();
        User triggeredBy = alert.getTriggeredBy();

        if (triggeredBy != null) {
            String payload = buildPayload(alert);
            notificationService.sendNotification(
                triggeredBy,
                NotificationType.SOS_RESOLVED,
                "SOS alert resolved",
                "An admin has resolved your SOS alert.",
                payload,
                Priority.MEDIUM,
                DeliveryMethod.PUSH,
                SOS_QUEUE
            );
        }

        notifyAdministrators(alert, "SOS alert resolved", NotificationType.SOS_RESOLVED);
    }

    private void notifyOriginUser(SosAlert alert, User triggeredBy) {
        if (triggeredBy == null) {
            return;
        }

        String payload = buildPayload(alert);
        notificationService.sendNotification(
            triggeredBy,
            NotificationType.SOS_ALERT,
            "SOS alert activated",
            "We're contacting your emergency network now.",
            payload,
            Priority.URGENT,
            DeliveryMethod.PUSH,
            SOS_QUEUE
        );
        sosAlertEventService.record(alert, SosAlertEventType.ORIGINATOR_NOTIFIED,
            "Rider/driver notified of SOS activation", triggeredBy.getUserId().toString());
    }

    private void notifyAdministrators(SosAlert alert, String message, NotificationType type) {
        List<Integer> adminIds = sosConfig.getAdminUserIds();
        if (adminIds == null || adminIds.isEmpty()) {
            return;
        }

        String payload = buildPayload(alert);

        adminIds.stream()
            .map(userRepository::findById)
            .flatMap(Optional::stream)
            .forEach(admin -> {
                notificationService.sendNotification(
                    admin,
                    type,
                    "SOS alert requires attention",
                    message,
                    payload,
                    Priority.URGENT,
                    DeliveryMethod.PUSH,
                    SOS_QUEUE
                );
                sosAlertEventService.record(alert, SosAlertEventType.ADMIN_NOTIFIED,
                    "Administrator notified: " + admin.getFullName(),
                    admin.getUserId().toString());
            });
    }

    private void notifyEmergencyContacts(SosAlert alert, List<EmergencyContact> contacts) {
        if (contacts == null || contacts.isEmpty()) {
            return;
        }

        for (EmergencyContact contact : contacts) {
            String description = String.format("Emergency contact notified: %s (%s)",
                contact.getName(), contact.getPhone());
            sosAlertEventService.record(alert, SosAlertEventType.CONTACT_NOTIFIED,
                description, contact.getPhone());
        }
    }

    private void notifyCampusSecurity(SosAlert alert) {
        List<String> campusPhones = sosConfig.getCampusSecurityPhones();
        if (campusPhones == null || campusPhones.isEmpty()) {
            return;
        }

        campusPhones.forEach(phone ->
            sosAlertEventService.record(alert, SosAlertEventType.CAMPUS_SECURITY_NOTIFIED,
                "Campus security notified", phone)
        );
    }

    private String buildPayload(SosAlert alert) {
        Map<String, Object> payload = Map.of(
            "sosId", alert.getSosId(),
            "triggeredBy", alert.getTriggeredBy() != null ? alert.getTriggeredBy().getFullName() : null,
            "status", alert.getStatus().name(),
            "lat", alert.getCurrentLat(),
            "lng", alert.getCurrentLng()
        );
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw BaseDomainException.of("sos.alert.notification-payload-error");
        }
    }
}

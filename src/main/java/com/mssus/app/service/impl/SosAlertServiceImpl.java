package com.mssus.app.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mssus.app.common.enums.AlertType;
import com.mssus.app.common.enums.SosAlertEventType;
import com.mssus.app.common.enums.SosAlertStatus;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.infrastructure.config.properties.SosConfigurationProperties;
import com.mssus.app.dto.domain.sos.AcknowledgeSosRequest;
import com.mssus.app.dto.domain.sos.ResolveSosRequest;
import com.mssus.app.dto.domain.sos.SosAlertResponse;
import com.mssus.app.dto.domain.sos.TriggerSosRequest;
import com.mssus.app.entity.EmergencyContact;
import com.mssus.app.entity.SharedRide;
import com.mssus.app.entity.SosAlert;
import com.mssus.app.entity.User;
import com.mssus.app.repository.EmergencyContactRepository;
import com.mssus.app.repository.SharedRideRepository;
import com.mssus.app.repository.SosAlertEventRepository;
import com.mssus.app.repository.SosAlertRepository;
import com.mssus.app.service.SosAlertService;
import com.mssus.app.service.SosAlertEventService;
import com.mssus.app.service.domain.event.SosAlertResolvedEvent;
import com.mssus.app.service.domain.event.SosAlertTriggeredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SosAlertServiceImpl implements SosAlertService {

    private final SosAlertRepository sosAlertRepository;
    private final SosAlertEventRepository sosAlertEventRepository;
    private final EmergencyContactRepository emergencyContactRepository;
    private final SharedRideRepository sharedRideRepository;
    private final SosAlertEventService sosAlertEventService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final SosConfigurationProperties sosConfig;

    @Override
    @Transactional
    public SosAlertResponse triggerSos(User user, TriggerSosRequest request) {
        validateTriggerRequest(user, request);

        SharedRide sharedRide = resolveSharedRide(request.getSharedRideId());
        List<EmergencyContact> contacts = emergencyContactRepository.findByUser_UserIdOrderByIsPrimaryDescCreatedAtAsc(user.getUserId());

        boolean forceFallback = Boolean.TRUE.equals(request.getForceFallbackCall());
        boolean hasContacts = !contacts.isEmpty();

        String contactInfo = serializeContactInfo(contacts, forceFallback || !hasContacts);
        String rideSnapshot = resolveRideSnapshot(sharedRide, request.getRideSnapshot());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime ackDeadline = now.plus(sosConfig.getAcknowledgementTimeout());

        SosAlert alert = SosAlert.builder()
            .sharedRide(sharedRide)
            .triggeredBy(user)
            .alertType(Optional.ofNullable(request.getAlertType()).orElse(AlertType.EMERGENCY))
            .currentLat(request.getCurrentLat())
            .currentLng(request.getCurrentLng())
            .contactInfo(contactInfo)
            .rideSnapshot(rideSnapshot)
            .description(StringUtils.trimToNull(request.getDescription()))
            .status(SosAlertStatus.ACTIVE)
            .acknowledgementDeadline(ackDeadline)
            .nextEscalationAt(ackDeadline)
            .fallbackContactUsed(!hasContacts || forceFallback)
            .autoCallTriggered(false)
            .build();

        SosAlert saved = sosAlertRepository.save(alert);
        log.info("SOS alert {} created by user {} (rideId={})", saved.getSosId(), user.getUserId(),
            sharedRide != null ? sharedRide.getSharedRideId() : null);

        sosAlertEventService.record(saved, SosAlertEventType.CREATED, "SOS alert triggered", null);
        if (!hasContacts || forceFallback) {
            sosAlertEventService.record(saved, SosAlertEventType.FALLBACK_CONTACT_USED,
                "Fallback emergency number scheduled for contact", sosConfig.getFallbackEmergencyNumber());
        }
        sosAlertEventService.record(saved, SosAlertEventType.DISPATCH_REQUESTED,
            "Dispatch workflow started", null);

        eventPublisher.publishEvent(new SosAlertTriggeredEvent(this, saved, contacts));

        return toResponse(saved, true);
    }

    @Override
    @Transactional
    public SosAlertResponse acknowledgeAlert(User staffUser, Integer alertId, AcknowledgeSosRequest request) {
        SosAlert alert = sosAlertRepository.findByIdForUpdate(alertId)
            .orElseThrow(() -> BaseDomainException.of("sos.alert.not-found"));

        if (alert.getStatus() == SosAlertStatus.RESOLVED || alert.getStatus() == SosAlertStatus.FALSE_ALARM) {
            throw BaseDomainException.of("sos.alert.already-resolved");
        }

        LocalDateTime now = LocalDateTime.now();

        alert.setAcknowledgedBy(staffUser);
        alert.setAcknowledgedAt(now);
        alert.setNextEscalationAt(null);
        alert.setStatus(SosAlertStatus.ACKNOWLEDGED);

        if (request != null && StringUtils.isNotBlank(request.getNote())) {
            String note = request.getNote().trim();
            alert.setResolutionNotes(mergeNotes(alert.getResolutionNotes(), "Acknowledgement note: " + note));
            sosAlertEventService.record(alert, SosAlertEventType.NOTE_ADDED, "Acknowledgement note added", note);
        }

        SosAlert saved = sosAlertRepository.save(alert);
        sosAlertEventService.record(saved, SosAlertEventType.ACKNOWLEDGED,
            String.format("Alert acknowledged by %s", staffUser.getFullName()), null);

        log.info("SOS alert {} acknowledged by staff user {}", alertId, staffUser.getUserId());
        return toResponse(saved, true);
    }

    @Override
    @Transactional
    public SosAlertResponse resolveAlert(User staffUser, Integer alertId, ResolveSosRequest request) {
        SosAlert alert = sosAlertRepository.findByIdForUpdate(alertId)
            .orElseThrow(() -> BaseDomainException.of("sos.alert.not-found"));

        if (alert.getStatus() == SosAlertStatus.RESOLVED || alert.getStatus() == SosAlertStatus.FALSE_ALARM) {
            throw BaseDomainException.of("sos.alert.already-resolved");
        }

        LocalDateTime now = LocalDateTime.now();
        Boolean falseAlarm = request != null ? request.getFalseAlarm() : null;
        String resolutionNotes = request != null ? StringUtils.trimToNull(request.getResolutionNotes()) : null;

        alert.setResolvedBy(staffUser);
        alert.setResolvedAt(now);
        alert.setNextEscalationAt(null);
        alert.setResolutionNotes(mergeNotes(alert.getResolutionNotes(), resolutionNotes));
        alert.setStatus(Boolean.TRUE.equals(falseAlarm) ? SosAlertStatus.FALSE_ALARM : SosAlertStatus.RESOLVED);

        SosAlert saved = sosAlertRepository.save(alert);

        sosAlertEventService.record(saved, SosAlertEventType.RESOLVED,
            String.format("Alert resolved by %s", staffUser.getFullName()),
            Boolean.TRUE.equals(falseAlarm) ? "Marked as false alarm" : null);

        eventPublisher.publishEvent(new SosAlertResolvedEvent(this, saved));

        log.info("SOS alert {} resolved by user {} (falseAlarm={})", alertId, staffUser.getUserId(), falseAlarm);
        return toResponse(saved, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SosAlertResponse> getAlertsForUser(User user, List<SosAlertStatus> statuses) {
        List<SosAlertStatus> effectiveStatuses = statuses == null || statuses.isEmpty()
            ? List.of(SosAlertStatus.ACTIVE, SosAlertStatus.ESCALATED, SosAlertStatus.ACKNOWLEDGED)
            : statuses;

        return sosAlertRepository
            .findByTriggeredBy_UserIdAndStatusInOrderByCreatedAtDesc(user.getUserId(), effectiveStatuses)
            .stream()
            .map(alert -> toResponse(alert, false))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SosAlertResponse> getAlertsForAdmin(List<SosAlertStatus> statuses) {
        List<SosAlertStatus> effectiveStatuses = statuses == null || statuses.isEmpty()
            ? List.of(SosAlertStatus.ACTIVE, SosAlertStatus.ESCALATED, SosAlertStatus.ACKNOWLEDGED)
            : statuses;

        return sosAlertRepository.findByStatusInOrderByCreatedAtDesc(effectiveStatuses)
            .stream()
            .map(alert -> toResponse(alert, false))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SosAlertResponse getAlert(Integer alertId) {
        SosAlert alert = sosAlertRepository.findById(alertId)
            .orElseThrow(() -> BaseDomainException.of("sos.alert.not-found"));
        return toResponse(alert, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SosAlertResponse.SosAlertEventDto> getAlertTimeline(Integer alertId) {
        SosAlert alert = sosAlertRepository.findById(alertId)
            .orElseThrow(() -> BaseDomainException.of("sos.alert.not-found"));
        return toTimelineDtos(alert);
    }

    private void validateTriggerRequest(User user, TriggerSosRequest request) {
        if (user == null || user.getUserId() == null) {
            throw BaseDomainException.of("sos.alert.invalid-user");
        }
        if (request == null) {
            throw BaseDomainException.of("sos.alert.invalid-request");
        }
    }

    private SharedRide resolveSharedRide(Integer sharedRideId) {
        if (sharedRideId == null) {
            return null;
        }
        return sharedRideRepository.findById(sharedRideId)
            .orElseThrow(() -> BaseDomainException.of("sos.alert.ride-not-found"));
    }

    private String serializeContactInfo(List<EmergencyContact> contacts, boolean includeFallback) {
        List<Map<String, Object>> payload = new ArrayList<>();

        for (EmergencyContact contact : contacts) {
            payload.add(Map.of(
                "contactId", contact.getContactId(),
                "name", contact.getName(),
                "phone", contact.getPhone(),
                "relationship", contact.getRelationship(),
                "primary", Boolean.TRUE.equals(contact.getIsPrimary())
            ));
        }

        if (includeFallback) {
            payload.add(Map.of(
                "fallback", true,
                "phone", sosConfig.getFallbackEmergencyNumber()
            ));
        }

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize contact info for SOS alert", e);
            throw BaseDomainException.of("sos.alert.contact-serialization-error");
        }
    }

    private String resolveRideSnapshot(SharedRide sharedRide, String rideSnapshotOverride) {
        if (StringUtils.isNotBlank(rideSnapshotOverride)) {
            return rideSnapshotOverride;
        }
        if (sharedRide == null) {
            return null;
        }
        Map<String, Object> snapshot = Map.of(
            "sharedRideId", sharedRide.getSharedRideId(),
            "status", sharedRide.getStatus() != null ? sharedRide.getStatus().name() : null,
            "driverId", sharedRide.getDriver() != null ? sharedRide.getDriver().getDriverId() : null,
            "vehicleId", sharedRide.getVehicle() != null ? sharedRide.getVehicle().getVehicleId() : null,
            "scheduledTime", sharedRide.getScheduledTime(),
            "startLocationId", sharedRide.getStartLocation() != null ? sharedRide.getStartLocation().getLocationId() : null,
            "endLocationId", sharedRide.getEndLocation() != null ? sharedRide.getEndLocation().getLocationId() : null
        );

        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize ride snapshot for shared ride {}", sharedRide.getSharedRideId(), e);
            return null;
        }
    }

    private SosAlertResponse toResponse(SosAlert alert, boolean includeTimeline) {
        List<SosAlertResponse.SosAlertEventDto> timeline = includeTimeline ? toTimelineDtos(alert) : List.of();

        return SosAlertResponse.builder()
            .sosId(alert.getSosId())
            .sharedRideId(alert.getSharedRide() != null ? alert.getSharedRide().getSharedRideId() : null)
            .triggeredBy(alert.getTriggeredBy() != null ? alert.getTriggeredBy().getUserId() : null)
            .triggeredByName(alert.getTriggeredBy() != null ? alert.getTriggeredBy().getFullName() : null)
            .alertType(alert.getAlertType())
            .currentLat(alert.getCurrentLat())
            .currentLng(alert.getCurrentLng())
            .contactInfo(alert.getContactInfo())
            .rideSnapshot(alert.getRideSnapshot())
            .description(alert.getDescription())
            .status(alert.getStatus())
            .acknowledgedBy(alert.getAcknowledgedBy() != null ? alert.getAcknowledgedBy().getUserId() : null)
            .acknowledgedByName(alert.getAcknowledgedBy() != null ? alert.getAcknowledgedBy().getFullName() : null)
            .acknowledgedAt(alert.getAcknowledgedAt())
            .resolvedBy(alert.getResolvedBy() != null ? alert.getResolvedBy().getUserId() : null)
            .resolvedByName(alert.getResolvedBy() != null ? alert.getResolvedBy().getFullName() : null)
            .resolvedAt(alert.getResolvedAt())
            .resolutionNotes(alert.getResolutionNotes())
            .lastEscalatedAt(alert.getLastEscalatedAt())
            .nextEscalationAt(alert.getNextEscalationAt())
            .escalationCount(alert.getEscalationCount())
            .fallbackContactUsed(Boolean.TRUE.equals(alert.getFallbackContactUsed()))
            .autoCallTriggered(Boolean.TRUE.equals(alert.getAutoCallTriggered()))
            .campusSecurityNotified(Boolean.TRUE.equals(alert.getCampusSecurityNotified()))
            .acknowledgementDeadline(alert.getAcknowledgementDeadline())
            .createdAt(alert.getCreatedAt())
            .timeline(timeline)
            .build();
    }

    private List<SosAlertResponse.SosAlertEventDto> toTimelineDtos(SosAlert alert) {
        return sosAlertEventRepository.findBySosAlertOrderByCreatedAtAsc(alert)
            .stream()
            .map(event -> SosAlertResponse.SosAlertEventDto.builder()
                .eventId(event.getEventId())
                .eventType(event.getEventType().name())
                .description(event.getDescription())
                .metadata(event.getMetadata())
                .createdAt(event.getCreatedAt())
                .build())
            .toList();
    }

    private String mergeNotes(String existingNotes, String newNote) {
        if (StringUtils.isBlank(newNote)) {
            return existingNotes;
        }
        if (StringUtils.isBlank(existingNotes)) {
            return newNote;
        }
        return existingNotes + System.lineSeparator() + newNote;
    }
}

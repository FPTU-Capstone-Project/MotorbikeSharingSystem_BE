package com.mssus.app.controller;

import com.mssus.app.common.enums.SosAlertStatus;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.domain.sos.AcknowledgeSosRequest;
import com.mssus.app.dto.domain.sos.EmergencyContactRequest;
import com.mssus.app.dto.domain.sos.EmergencyContactResponse;
import com.mssus.app.dto.domain.sos.ResolveSosRequest;
import com.mssus.app.dto.domain.sos.SosAlertResponse;
import com.mssus.app.dto.domain.sos.TriggerSosRequest;
import com.mssus.app.entity.User;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.service.EmergencyContactService;
import com.mssus.app.service.SosAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sos")
@RequiredArgsConstructor
@Tag(name = "SOS", description = "SOS alerts and emergency contact management")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class SosController {

    private final SosAlertService sosAlertService;
    private final EmergencyContactService emergencyContactService;
    private final UserRepository userRepository;

    @PostMapping("/alerts")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Trigger SOS alert")
    public ResponseEntity<SosAlertResponse> triggerAlert(
        Authentication authentication,
        @Valid @RequestBody TriggerSosRequest request) {

        User user = resolveUser(authentication);
        SosAlertResponse response = sosAlertService.triggerSos(user, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/alerts/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get SOS alerts for current user")
    public ResponseEntity<List<SosAlertResponse>> getMyAlerts(
        Authentication authentication,
        @RequestParam(value = "status", required = false) List<SosAlertStatus> statuses) {

        User user = resolveUser(authentication);
        List<SosAlertResponse> response = sosAlertService.getAlertsForUser(user, statuses);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/alerts/{alertId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get SOS alert detail")
    public ResponseEntity<SosAlertResponse> getAlert(
        Authentication authentication,
        @PathVariable Integer alertId) {

        User user = resolveUser(authentication);
        SosAlertResponse alert = sosAlertService.getAlert(alertId);

        return ResponseEntity.ok(alert);
    }

    @GetMapping("/alerts/{alertId}/timeline")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get SOS alert timeline events")
    public ResponseEntity<List<SosAlertResponse.SosAlertEventDto>> getAlertTimeline(
        Authentication authentication,
        @PathVariable Integer alertId) {

        User user = resolveUser(authentication);
        SosAlertResponse alert = sosAlertService.getAlert(alertId);

        return ResponseEntity.ok(alert.getTimeline());
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List SOS alerts for administrators")
    public ResponseEntity<List<SosAlertResponse>> getAlertsForAdmin(
        @RequestParam(value = "status", required = false) List<SosAlertStatus> statuses) {
        List<SosAlertResponse> response = sosAlertService.getAlertsForAdmin(statuses);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/alerts/{alertId}/acknowledge")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Acknowledge SOS alert")
    public ResponseEntity<SosAlertResponse> acknowledgeAlert(
        Authentication authentication,
        @PathVariable Integer alertId,
        @Valid @RequestBody(required = false) AcknowledgeSosRequest request) {

        User user = resolveUser(authentication);
        SosAlertResponse response = sosAlertService.acknowledgeAlert(user, alertId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/alerts/{alertId}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @Operation(summary = "Resolve SOS alert")
    public ResponseEntity<SosAlertResponse> resolveAlert(
        Authentication authentication,
        @PathVariable Integer alertId,
        @Valid @RequestBody(required = false) ResolveSosRequest request) {

        User user = resolveUser(authentication);
        SosAlertResponse response = sosAlertService.resolveAlert(user, alertId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/contacts")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List emergency contacts")
    public ResponseEntity<List<EmergencyContactResponse>> getContacts(Authentication authentication) {
        User user = resolveUser(authentication);
        List<EmergencyContactResponse> response = emergencyContactService.getContacts(user);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/contacts")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create emergency contact")
    public ResponseEntity<EmergencyContactResponse> createContact(
        Authentication authentication,
        @Valid @RequestBody EmergencyContactRequest request) {

        User user = resolveUser(authentication);
        EmergencyContactResponse response = emergencyContactService.createContact(user, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/contacts/{contactId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update emergency contact")
    public ResponseEntity<EmergencyContactResponse> updateContact(
        Authentication authentication,
        @PathVariable Integer contactId,
        @Valid @RequestBody EmergencyContactRequest request) {

        User user = resolveUser(authentication);
        EmergencyContactResponse response = emergencyContactService.updateContact(user, contactId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/contacts/{contactId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete emergency contact")
    public ResponseEntity<Void> deleteContact(
        Authentication authentication,
        @PathVariable Integer contactId) {

        User user = resolveUser(authentication);
        emergencyContactService.deleteContact(user, contactId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/contacts/{contactId}/primary")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Set primary emergency contact")
    public ResponseEntity<EmergencyContactResponse> setPrimaryContact(
        Authentication authentication,
        @PathVariable Integer contactId) {

        User user = resolveUser(authentication);
        EmergencyContactResponse response = emergencyContactService.setPrimaryContact(user, contactId);
        return ResponseEntity.ok(response);
    }

    private User resolveUser(Authentication authentication) {
        String username = authentication.getName();
        return userRepository.findByEmail(username)
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));
    }

    private boolean isAdmin(User user) {
        return user.getUserType() != null && switch (user.getUserType()) {
            case ADMIN -> true;
            case USER -> false;
        };
    }
}

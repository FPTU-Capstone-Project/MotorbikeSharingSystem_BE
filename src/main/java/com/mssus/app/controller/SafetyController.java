package com.mssus.app.controller;

import com.mssus.app.common.enums.SosAlertStatus;
import com.mssus.app.dto.domain.sos.MarkFalseAlarmRequest;
import com.mssus.app.dto.domain.sos.SafetyAlertsListRequest;
import com.mssus.app.dto.domain.sos.SafetyAlertsListResponse;
import com.mssus.app.dto.domain.sos.SafetyDashboardStatsResponse;
import com.mssus.app.entity.User;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.service.SafetyService;
import com.mssus.app.common.exception.BaseDomainException;
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
@RequestMapping("/api/v1/safety")
@RequiredArgsConstructor
@Tag(name = "Safety Management", description = "Safety dashboard and SOS alerts management for administrators")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class SafetyController {

    private final SafetyService safetyService;
    private final UserRepository userRepository;

    @GetMapping("/dashboard/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get safety dashboard statistics", 
               description = "Returns statistics including active alerts, resolved alerts, average response time, and driver verification stats")
    public ResponseEntity<SafetyDashboardStatsResponse> getDashboardStats() {
        SafetyDashboardStatsResponse stats = safetyService.getDashboardStats();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get paginated list of SOS alerts", 
               description = "Returns paginated list of SOS alerts with optional status filtering")
    public ResponseEntity<SafetyAlertsListResponse> getAlertsList(
            @RequestParam(value = "status", required = false) List<SosAlertStatus> statuses,
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir) {
        
        SafetyAlertsListRequest request = SafetyAlertsListRequest.builder()
            .statuses(statuses)
            .page(page)
            .pageSize(pageSize)
            .sortBy(sortBy)
            .sortDir(sortDir)
            .build();
        
        SafetyAlertsListResponse response = safetyService.getAlertsList(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/alerts/{alertId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Mark SOS alert as resolved", 
               description = "Admin marks an SOS alert as resolved with optional notes")
    public ResponseEntity<Void> markAsResolved(
            Authentication authentication,
            @PathVariable Integer alertId,
            @RequestParam(value = "notes", required = false) String notes) {
        
        User adminUser = resolveUser(authentication);
        safetyService.markAsResolved(adminUser, alertId, notes);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/alerts/{alertId}/false-alarm")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Mark SOS alert as false alarm", 
               description = "Admin marks an SOS alert as false alarm with optional notes")
    public ResponseEntity<Void> markAsFalseAlarm(
            Authentication authentication,
            @PathVariable Integer alertId,
            @RequestBody(required = false) @Valid MarkFalseAlarmRequest request) {
        
        User adminUser = resolveUser(authentication);
        String notes = request != null ? request.getNotes() : null;
        safetyService.markAsFalseAlarm(adminUser, alertId, notes);
        return ResponseEntity.ok().build();
    }

    private User resolveUser(Authentication authentication) {
        String username = authentication.getName();
        return userRepository.findByEmail(username)
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));
    }
}


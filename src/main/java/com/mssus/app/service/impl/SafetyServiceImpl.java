package com.mssus.app.service.impl;

import com.mssus.app.common.enums.SosAlertStatus;
import com.mssus.app.common.enums.VerificationStatus;
import com.mssus.app.common.enums.VerificationType;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.domain.sos.ResolveSosRequest;
import com.mssus.app.dto.domain.sos.SafetyAlertsListRequest;
import com.mssus.app.dto.domain.sos.SafetyAlertsListResponse;
import com.mssus.app.dto.domain.sos.SafetyDashboardStatsResponse;
import com.mssus.app.dto.domain.sos.SosAlertResponse;
import com.mssus.app.entity.SosAlert;
import com.mssus.app.entity.User;
import com.mssus.app.repository.SosAlertRepository;
import com.mssus.app.repository.VerificationRepository;
import com.mssus.app.service.SafetyService;
import com.mssus.app.service.SosAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SafetyServiceImpl implements SafetyService {

    private final SosAlertRepository sosAlertRepository;
    private final SosAlertService sosAlertService;
    private final VerificationRepository verificationRepository;

    @Override
    @Transactional(readOnly = true)
    public SafetyDashboardStatsResponse getDashboardStats() {
        // SOS Alerts Statistics
        List<SosAlert> allAlerts = sosAlertRepository.findAll();
        long activeAlertsCount = allAlerts.stream()
            .filter(alert -> alert.getStatus() == SosAlertStatus.ACTIVE)
            .count();
        
        long escalatedAlertsCount = allAlerts.stream()
            .filter(alert -> alert.getStatus() == SosAlertStatus.ESCALATED)
            .count();
        
        long acknowledgedAlertsCount = allAlerts.stream()
            .filter(alert -> alert.getStatus() == SosAlertStatus.ACKNOWLEDGED)
            .count();
        
        // Resolved today
        LocalDate today = LocalDate.now();
        long resolvedTodayCount = allAlerts.stream()
            .filter(alert -> alert.getStatus() == SosAlertStatus.RESOLVED 
                && alert.getResolvedAt() != null
                && alert.getResolvedAt().toLocalDate().equals(today))
            .count();
        
        // False alarm count
        long falseAlarmCount = allAlerts.stream()
            .filter(alert -> alert.getStatus() == SosAlertStatus.FALSE_ALARM)
            .count();
        
        // Calculate average response time (in minutes)
        List<SosAlert> resolvedAlerts = allAlerts.stream()
            .filter(alert -> alert.getStatus() == SosAlertStatus.RESOLVED 
                && alert.getResolvedAt() != null
                && alert.getCreatedAt() != null)
            .collect(Collectors.toList());
        
        BigDecimal averageResponseTimeMinutes = BigDecimal.ZERO;
        if (!resolvedAlerts.isEmpty()) {
            long totalMinutes = resolvedAlerts.stream()
                .mapToLong(alert -> {
                    LocalDateTime createdAt = alert.getCreatedAt();
                    LocalDateTime resolvedAt = alert.getResolvedAt();
                    if (createdAt != null && resolvedAt != null) {
                        return ChronoUnit.MINUTES.between(createdAt, resolvedAt);
                    }
                    return 0;
                })
                .sum();
            
            averageResponseTimeMinutes = BigDecimal.valueOf(totalMinutes)
                .divide(BigDecimal.valueOf(resolvedAlerts.size()), 2, RoundingMode.HALF_UP);
        }
        
        // Driver Verification Statistics
        // Count drivers with APPROVED verification for DRIVER_LICENSE or DRIVER_DOCUMENTS
        long approvedDriversCount = verificationRepository.countByTypeAndStatus(
            VerificationType.DRIVER_LICENSE, VerificationStatus.APPROVED
        ) + verificationRepository.countByTypeAndStatus(
            VerificationType.DRIVER_DOCUMENTS, VerificationStatus.APPROVED
        );
        
        long pendingDriversCount = verificationRepository.countByTypeAndStatus(
            VerificationType.DRIVER_LICENSE, VerificationStatus.PENDING
        ) + verificationRepository.countByTypeAndStatus(
            VerificationType.DRIVER_DOCUMENTS, VerificationStatus.PENDING
        );
        
        long rejectedDriversCount = verificationRepository.countByTypeAndStatus(
            VerificationType.DRIVER_LICENSE, VerificationStatus.REJECTED
        ) + verificationRepository.countByTypeAndStatus(
            VerificationType.DRIVER_DOCUMENTS, VerificationStatus.REJECTED
        );
        
        // Calculate driver verification percentage
        long totalDriverVerifications = approvedDriversCount + pendingDriversCount + rejectedDriversCount;
        BigDecimal driverVerificationPercentage = BigDecimal.ZERO;
        if (totalDriverVerifications > 0) {
            driverVerificationPercentage = BigDecimal.valueOf(approvedDriversCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalDriverVerifications), 2, RoundingMode.HALF_UP);
        }
        
        return SafetyDashboardStatsResponse.builder()
            .activeAlertsCount((int) activeAlertsCount)
            .resolvedTodayCount((int) resolvedTodayCount)
            .averageResponseTimeMinutes(averageResponseTimeMinutes)
            .totalAlertsCount(allAlerts.size())
            .approvedDriversCount(approvedDriversCount)
            .pendingDriversCount(pendingDriversCount)
            .rejectedDriversCount(rejectedDriversCount)
            .driverVerificationPercentage(driverVerificationPercentage)
            .escalatedAlertsCount((int) escalatedAlertsCount)
            .falseAlarmCount((int) falseAlarmCount)
            .acknowledgedAlertsCount((int) acknowledgedAlertsCount)
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SafetyAlertsListResponse getAlertsList(SafetyAlertsListRequest request) {
        int page = request.getPage() != null && request.getPage() >= 0 ? request.getPage() : 0;
        int pageSize = request.getPageSize() != null && request.getPageSize() > 0 ? request.getPageSize() : 10;
        
        // Build sort
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        if (request.getSortBy() != null && !request.getSortBy().isEmpty()) {
            Sort.Direction direction = "asc".equalsIgnoreCase(request.getSortDir()) 
                ? Sort.Direction.ASC 
                : Sort.Direction.DESC;
            sort = Sort.by(direction, request.getSortBy());
        }
        
        Pageable pageable = PageRequest.of(page, pageSize, sort);
        
        // Get alerts
        List<SosAlertStatus> statuses = request.getStatuses() != null && !request.getStatuses().isEmpty()
            ? request.getStatuses()
            : List.of(SosAlertStatus.values());
        
        Page<SosAlert> alertPage;
        if (statuses.size() == SosAlertStatus.values().length) {
            // All statuses - get all
            alertPage = sosAlertRepository.findAll(pageable);
        } else {
            // Filter by statuses
            alertPage = sosAlertRepository.findByStatusIn(statuses, pageable);
        }
        
        List<SosAlert> alerts = alertPage.getContent();
        List<SosAlertResponse> alertResponses = alerts.stream()
            .map(alert -> sosAlertService.getAlert(alert.getSosId()))
            .collect(Collectors.toList());
        
        return SafetyAlertsListResponse.builder()
            .alerts(alertResponses)
            .totalPages(alertPage.getTotalPages())
            .totalRecords(alertPage.getTotalElements())
            .currentPage(page)
            .pageSize(pageSize)
            .build();
    }

    @Override
    @Transactional
    public void markAsResolved(User adminUser, Integer alertId, String notes) {
        ResolveSosRequest request = ResolveSosRequest.builder()
            .resolutionNotes(notes)
            .build();
        sosAlertService.resolveAlert(adminUser, alertId, request);
        log.info("Admin {} marked SOS alert {} as resolved", adminUser.getEmail(), alertId);
    }

    @Override
    @Transactional
    public void markAsFalseAlarm(User adminUser, Integer alertId, String notes) {
        SosAlert alert = sosAlertRepository.findById(alertId)
            .orElseThrow(() -> BaseDomainException.of("sos.alert.not-found"));
        
        alert.setStatus(SosAlertStatus.FALSE_ALARM);
        alert.setResolvedBy(adminUser);
        alert.setResolvedAt(LocalDateTime.now());
        if (notes != null && !notes.isEmpty()) {
            alert.setResolutionNotes(notes);
        }
        
        sosAlertRepository.save(alert);
        log.info("Admin {} marked SOS alert {} as false alarm", adminUser.getEmail(), alertId);
    }
}


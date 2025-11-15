package com.mssus.app.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mssus.app.common.enums.DeliveryMethod;
import com.mssus.app.common.enums.NotificationType;
import com.mssus.app.common.enums.Priority;
import com.mssus.app.common.enums.ReportPriority;
import com.mssus.app.common.enums.ReportStatus;
import com.mssus.app.common.enums.ReportType;
import com.mssus.app.common.enums.SharedRideStatus;
import com.mssus.app.common.enums.UserType;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.request.report.DriverReportResponseRequest;
import com.mssus.app.dto.request.report.RideReportCreateRequest;
import com.mssus.app.dto.request.report.UpdateRideReportRequest;
import com.mssus.app.dto.request.report.UserReportCreateRequest;
import com.mssus.app.dto.request.report.UserReportResolveRequest;
import com.mssus.app.dto.request.report.StartReportChatRequest;
import com.mssus.app.dto.request.chat.SendMessageRequest;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.chat.MessageResponse;
import com.mssus.app.dto.response.report.ReportAnalyticsResponse;
import com.mssus.app.dto.response.report.UserReportResponse;
import com.mssus.app.dto.response.report.UserReportSummaryResponse;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.SharedRide;
import com.mssus.app.entity.User;
import com.mssus.app.entity.UserReport;
import com.mssus.app.mapper.UserReportMapper;
import com.mssus.app.repository.DriverProfileRepository;
import com.mssus.app.repository.SharedRideRepository;
import com.mssus.app.repository.UserReportRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.service.NotificationService;
import com.mssus.app.service.UserReportService;
import com.mssus.app.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserReportServiceImpl implements UserReportService {

    private static final String DEFAULT_QUEUE = "/queue/notifications";
    private static final int REPORT_WINDOW_DAYS = 7;
    private static final int ESCALATION_THRESHOLD_HOURS = 48; // 48 hours

    private final UserReportRepository userReportRepository;
    private final UserRepository userRepository;
    private final SharedRideRepository sharedRideRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final NotificationService notificationService;
    private final MessageService messageService;
    private final UserReportMapper userReportMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public UserReportResponse submitReport(Authentication authentication, UserReportCreateRequest request) {
        User reporter = resolveUser(authentication);
        String description = request.getDescription().trim();
        ReportPriority priority = request.getPriority() != null ? request.getPriority() : ReportPriority.MEDIUM;

        UserReport report = UserReport.builder()
            .reporter(reporter)
            .reportType(request.getReportType())
            .status(ReportStatus.OPEN)
            .description(description)
            .priority(priority)
            .build();

        UserReport saved = userReportRepository.save(report);
        log.info("User {} submitted report {} of type {} with priority {}", 
            reporter.getUserId(), saved.getReportId(), saved.getReportType(), saved.getPriority());

        notifyAdminsOfNewReport(saved);

        return userReportMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserReportSummaryResponse> getReports(ReportStatus status, ReportType reportType, Pageable pageable) {
        Pageable effectivePageable = ensurePageable(pageable);

        Page<UserReport> page;
        if (status != null && reportType != null) {
            page = userReportRepository.findByStatusAndReportType(status, reportType, effectivePageable);
        } else if (status != null) {
            page = userReportRepository.findByStatus(status, effectivePageable);
        } else if (reportType != null) {
            page = userReportRepository.findByReportType(reportType, effectivePageable);
        } else {
            page = userReportRepository.findAll(effectivePageable);
        }

        List<UserReportSummaryResponse> content = page.getContent()
            .stream()
            .map(userReportMapper::toSummary)
            .toList();

        return buildPageResponse(page, content);
    }

    @Override
    @Transactional(readOnly = true)
    public UserReportResponse getReportDetails(Integer reportId) {
        UserReport report = userReportRepository.findById(reportId)
            .orElseThrow(() -> BaseDomainException.of("user-report.not-found", "Report not found"));
        return userReportMapper.toResponse(report);
    }

    @Override
    @Transactional
    public UserReportResponse resolveReport(Integer reportId, UserReportResolveRequest request, Authentication authentication) {
        User admin = resolveUser(authentication);
        if (!Objects.equals(admin.getUserType(), UserType.ADMIN)) {
            throw BaseDomainException.unauthorized("Only administrators can resolve reports");
        }

        UserReport report = userReportRepository.findById(reportId)
            .orElseThrow(() -> BaseDomainException.of("user-report.not-found", "Report not found"));

        if (ReportStatus.RESOLVED.equals(report.getStatus())) {
            throw BaseDomainException.validation("Report is already resolved");
        }

        String resolutionMessage = request.getResolutionMessage().trim();

        report.setResolver(admin);
        report.setResolutionMessage(resolutionMessage);
        report.setStatus(ReportStatus.RESOLVED);
        report.setResolvedAt(LocalDateTime.now());

        UserReport saved = userReportRepository.save(report);
        log.info("Admin {} resolved report {} with status {}", admin.getUserId(), saved.getReportId(), saved.getStatus());

        notifyReporterOfResolution(saved);

        return userReportMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UserReportResponse submitRideReport(Integer rideId, Authentication authentication, RideReportCreateRequest request) {
        User reporter = resolveUser(authentication);
        SharedRide ride = sharedRideRepository.findById(rideId)
            .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource", rideId));

        // Validate ride is completed
        if (ride.getStatus() != SharedRideStatus.COMPLETED) {
            throw BaseDomainException.validation("Ride must be completed before a report can be submitted");
        }

        // Validate time window (7 days after completion)
        if (ride.getCompletedAt() == null) {
            throw BaseDomainException.validation("Ride completion time is missing");
        }

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(REPORT_WINDOW_DAYS);
        if (ride.getCompletedAt().isBefore(cutoffDate)) {
            throw BaseDomainException.validation(
                String.format("Reporting window has expired. Reports must be submitted within %d days of ride completion.", REPORT_WINDOW_DAYS));
        }

        // Check for duplicate report
        userReportRepository.findBySharedRideSharedRideIdAndReporterUserId(rideId, reporter.getUserId())
            .ifPresent(existing -> {
                throw BaseDomainException.validation("A report already exists for this ride");
            });

        // Validate report type for ride reports
        if (request.getReportType() != ReportType.SAFETY &&
            request.getReportType() != ReportType.BEHAVIOR &&
            request.getReportType() != ReportType.PAYMENT &&
            request.getReportType() != ReportType.ROUTE &&
            request.getReportType() != ReportType.OTHER) {
            throw BaseDomainException.validation("Invalid report type for ride reports. Valid types: SAFETY, BEHAVIOR, PAYMENT, ROUTE, OTHER");
        }

        String description = request.getDescription().trim();
        DriverProfile driver = ride.getDriver();
        ReportPriority priority = request.getPriority() != null ? request.getPriority() : ReportPriority.MEDIUM;

        UserReport report = UserReport.builder()
            .reporter(reporter)
            .sharedRide(ride)
            .driver(driver)
            .reportType(request.getReportType())
            .status(ReportStatus.PENDING)
            .description(description)
            .priority(priority)
            .build();

        UserReport saved = userReportRepository.save(report);
        log.info("User {} submitted ride report {} for ride {} of type {} with priority {}", 
            reporter.getUserId(), saved.getReportId(), rideId, saved.getReportType(), saved.getPriority());

        notifyAdminsOfRideReport(saved);

        // Send confirmation notification to user
        notifyUserOfReportSubmission(saved);

        return userReportMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UserReportResponse updateRideReportStatus(Integer reportId, UpdateRideReportRequest request, Authentication authentication) {
        User admin = resolveUser(authentication);
        if (!Objects.equals(admin.getUserType(), UserType.ADMIN)) {
            throw BaseDomainException.unauthorized("Only administrators can update report status");
        }

        UserReport report = userReportRepository.findById(reportId)
            .orElseThrow(() -> BaseDomainException.of("user-report.not-found", "Report not found"));

        if (report.getSharedRide() == null) {
            throw BaseDomainException.validation("This endpoint is only for ride-specific reports");
        }

        // Validate status transition
        ReportStatus currentStatus = report.getStatus();
        ReportStatus newStatus = request.getStatus();

        if (currentStatus == ReportStatus.RESOLVED || currentStatus == ReportStatus.DISMISSED) {
            throw BaseDomainException.validation(String.format("Cannot change status from %s", currentStatus));
        }

        // Update status and admin notes
        report.setStatus(newStatus);
        if (request.getAdminNotes() != null && !request.getAdminNotes().trim().isEmpty()) {
            report.setAdminNotes(request.getAdminNotes().trim());
        }

        // Set resolved_at if status is RESOLVED or DISMISSED
        if (newStatus == ReportStatus.RESOLVED || newStatus == ReportStatus.DISMISSED) {
            report.setResolvedAt(LocalDateTime.now());
            report.setResolver(admin);
            if (request.getAdminNotes() != null && !request.getAdminNotes().trim().isEmpty()) {
                report.setResolutionMessage(request.getAdminNotes().trim());
            }
        }

        UserReport saved = userReportRepository.save(report);
        log.info("Admin {} updated ride report {} to status {}", admin.getUserId(), saved.getReportId(), saved.getStatus());

        // Notify user of status change
        notifyUserOfReportStatusChange(saved, newStatus);

        return userReportMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserReportSummaryResponse> getMyReports(Authentication authentication, Pageable pageable) {
        User user = resolveUser(authentication);
        Pageable effectivePageable = ensurePageable(pageable);

        Page<UserReport> page = userReportRepository.findByReporterUserId(user.getUserId(), effectivePageable);

        List<UserReportSummaryResponse> content = page.getContent()
            .stream()
            .map(userReportMapper::toSummary)
            .toList();

        return buildPageResponse(page, content);
    }

    @Override
    @Transactional
    public UserReportResponse submitDriverResponse(Integer reportId, DriverReportResponseRequest request, Authentication authentication) {
        User driver = resolveUser(authentication);
        
        // Verify user is a driver
        DriverProfile driverProfile = driverProfileRepository.findByUserUserId(driver.getUserId())
            .orElseThrow(() -> BaseDomainException.unauthorized("Only drivers can respond to reports"));

        UserReport report = userReportRepository.findById(reportId)
            .orElseThrow(() -> BaseDomainException.of("user-report.not-found", "Report not found"));

        // Verify report is about this driver
        if (report.getDriver() == null || !report.getDriver().getDriverId().equals(driverProfile.getDriverId())) {
            throw BaseDomainException.unauthorized("You can only respond to reports about you");
        }

        // Check if already responded
        if (report.getDriverResponse() != null) {
            throw BaseDomainException.validation("Driver has already responded to this report");
        }

        // Update with driver response
        report.setDriverResponse(request.getDriverResponse().trim());
        report.setDriverRespondedAt(LocalDateTime.now());

        UserReport saved = userReportRepository.save(report);
        log.info("Driver {} submitted response to report {}", driverProfile.getDriverId(), reportId);

        // Notify admins of driver response
        notifyAdminsOfDriverResponse(saved);

        return userReportMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public MessageResponse startReportChat(Integer reportId, StartReportChatRequest request, Authentication authentication) {
        User admin = resolveUser(authentication);
        if (!Objects.equals(admin.getUserType(), UserType.ADMIN)) {
            throw BaseDomainException.unauthorized("Only administrators can initiate report chats");
        }

        UserReport report = userReportRepository.findById(reportId)
            .orElseThrow(() -> BaseDomainException.of("user-report.not-found", "Report not found"));

        Integer reporterId = report.getReporter() != null ? report.getReporter().getUserId() : null;
        Integer reportedUserId = resolveReportedUserId(report);

        Integer targetUserId = request.getTargetUserId();
        boolean contactingReporter = reporterId != null && Objects.equals(reporterId, targetUserId);
        boolean contactingReported = reportedUserId != null && Objects.equals(reportedUserId, targetUserId);

        if (!contactingReporter && !contactingReported) {
            throw BaseDomainException.validation("Target user must be the reporter or the reported participant of this report");
        }

        String initialMessage = request.getInitialMessage();
        if (initialMessage == null || initialMessage.trim().isEmpty()) {
            initialMessage = "Xin chào, tôi là admin. Mình trao đổi về báo cáo này nhé.";
        }

        SendMessageRequest messageRequest = SendMessageRequest.builder()
            .receiverId(targetUserId)
            .reportId(reportId)
            .messageType(com.mssus.app.common.enums.MessageType.TEXT)
            .content(initialMessage.trim())
            .build();

        MessageResponse response = messageService.sendMessage(admin.getEmail(), messageRequest);

        LocalDateTime now = LocalDateTime.now();
        if (contactingReporter && report.getReporterChatStartedAt() == null) {
            report.setReporterChatStartedAt(now);
        }
        if (contactingReported && report.getReportedChatStartedAt() == null) {
            report.setReportedChatStartedAt(now);
        }

        if (!ReportStatus.RESOLVED.equals(report.getStatus()) && !ReportStatus.DISMISSED.equals(report.getStatus())) {
            report.setStatus(ReportStatus.IN_PROGRESS);
        }

        userReportRepository.save(report);

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ReportAnalyticsResponse getReportAnalytics() {
        List<UserReport> allReports = userReportRepository.findAll();

        // Count by status
        long pendingCount = allReports.stream().filter(r -> r.getStatus() == ReportStatus.PENDING).count();
        long openCount = allReports.stream().filter(r -> r.getStatus() == ReportStatus.OPEN).count();
        long inProgressCount = allReports.stream().filter(r -> r.getStatus() == ReportStatus.IN_PROGRESS).count();
        long resolvedCount = allReports.stream().filter(r -> r.getStatus() == ReportStatus.RESOLVED).count();
        long dismissedCount = allReports.stream().filter(r -> r.getStatus() == ReportStatus.DISMISSED).count();
        long escalatedCount = allReports.stream().filter(r -> r.getEscalatedAt() != null).count();

        // Count by type
        Map<String, Long> byType = allReports.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                r -> r.getReportType().name(),
                java.util.stream.Collectors.counting()
            ));

        // Count by priority
        Map<String, Long> byPriority = allReports.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                r -> r.getPriority().name(),
                java.util.stream.Collectors.counting()
            ));

        // Count by status
        Map<String, Long> byStatus = allReports.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                r -> r.getStatus().name(),
                java.util.stream.Collectors.counting()
            ));

        // Calculate average resolution time
        double avgResolutionTime = allReports.stream()
            .filter(r -> r.getResolvedAt() != null && r.getCreatedAt() != null)
            .mapToDouble(r -> java.time.Duration.between(r.getCreatedAt(), r.getResolvedAt()).toHours())
            .average()
            .orElse(0.0);

        // Reports by time period
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        LocalDateTime startOfWeek = now.minusDays(7);
        LocalDateTime startOfMonth = now.minusMonths(1);

        long reportsToday = allReports.stream().filter(r -> r.getCreatedAt().isAfter(startOfDay)).count();
        long reportsThisWeek = allReports.stream().filter(r -> r.getCreatedAt().isAfter(startOfWeek)).count();
        long reportsThisMonth = allReports.stream().filter(r -> r.getCreatedAt().isAfter(startOfMonth)).count();

        // Top reported drivers
        Map<Integer, Long> driverReportCounts = allReports.stream()
            .filter(r -> r.getDriver() != null)
            .collect(java.util.stream.Collectors.groupingBy(
                r -> r.getDriver().getDriverId(),
                java.util.stream.Collectors.counting()
            ));

        List<ReportAnalyticsResponse.DriverReportStats> topDrivers = driverReportCounts.entrySet().stream()
            .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
            .limit(10)
            .map(entry -> {
                DriverProfile driver = driverProfileRepository.findById(entry.getKey()).orElse(null);
                long criticalCount = allReports.stream()
                    .filter(r -> r.getDriver() != null && r.getDriver().getDriverId().equals(entry.getKey()))
                    .filter(r -> r.getPriority() == ReportPriority.CRITICAL)
                    .count();

                return ReportAnalyticsResponse.DriverReportStats.builder()
                    .driverId(entry.getKey())
                    .driverName(driver != null && driver.getUser() != null ? driver.getUser().getFullName() : "Unknown")
                    .reportCount(entry.getValue())
                    .criticalReports(criticalCount)
                    .build();
            })
            .toList();

        return ReportAnalyticsResponse.builder()
            .totalReports((long) allReports.size())
            .pendingReports(pendingCount)
            .openReports(openCount)
            .inProgressReports(inProgressCount)
            .resolvedReports(resolvedCount)
            .dismissedReports(dismissedCount)
            .escalatedReports(escalatedCount)
            .reportsByType(byType)
            .reportsByPriority(byPriority)
            .reportsByStatus(byStatus)
            .averageResolutionTimeHours(avgResolutionTime)
            .reportsToday(reportsToday)
            .reportsThisWeek(reportsThisWeek)
            .reportsThisMonth(reportsThisMonth)
            .topReportedDrivers(topDrivers)
            .build();
    }

    @Override
    @Transactional
    public void escalateStaleReports() {
        LocalDateTime thresholdTime = LocalDateTime.now().minusHours(ESCALATION_THRESHOLD_HOURS);

        List<UserReport> staleReports = userReportRepository.findAll().stream()
            .filter(r -> r.getStatus() == ReportStatus.PENDING || r.getStatus() == ReportStatus.OPEN || r.getStatus() == ReportStatus.IN_PROGRESS)
            .filter(r -> r.getEscalatedAt() == null) // Not already escalated
            .filter(r -> r.getCreatedAt().isBefore(thresholdTime))
            .toList();

        for (UserReport report : staleReports) {
            report.setEscalatedAt(LocalDateTime.now());
            report.setEscalationReason(String.format("Automatically escalated: No resolution within %d hours", ESCALATION_THRESHOLD_HOURS));
            
            // Increase priority if not already CRITICAL
            if (report.getPriority() != ReportPriority.CRITICAL) {
                ReportPriority oldPriority = report.getPriority();
                ReportPriority newPriority = switch (oldPriority) {
                    case LOW -> ReportPriority.MEDIUM;
                    case MEDIUM -> ReportPriority.HIGH;
                    case HIGH -> ReportPriority.CRITICAL;
                    default -> ReportPriority.CRITICAL;
                };
                report.setPriority(newPriority);
                log.info("Escalated report {} priority from {} to {}", report.getReportId(), oldPriority, newPriority);
            }

            userReportRepository.save(report);
            log.info("Escalated stale report {} (created {})", report.getReportId(), report.getCreatedAt());

            // Notify admins of escalation
            notifyAdminsOfEscalation(report);
        }

        log.info("Auto-escalation complete: {} reports escalated", staleReports.size());
    }

    private void notifyAdminsOfDriverResponse(UserReport report) {
        List<User> admins = userRepository.findByUserType(UserType.ADMIN);
        if (admins.isEmpty()) {
            log.warn("No admin users found to notify about driver response for report {}", report.getReportId());
            return;
        }

        String title = "Driver responded to report";
        String message = String.format("Driver has responded to report #%d", report.getReportId());
        String payload = toJsonSafe(Map.of(
            "reportId", report.getReportId(),
            "driverId", report.getDriver().getDriverId(),
            "status", report.getStatus()
        ));

        admins.forEach(admin -> notificationService.sendNotification(
            admin,
            NotificationType.SYSTEM,
            title,
            message,
            payload,
            Priority.MEDIUM,
            DeliveryMethod.IN_APP,
            DEFAULT_QUEUE
        ));
    }

    private void notifyAdminsOfEscalation(UserReport report) {
        List<User> admins = userRepository.findByUserType(UserType.ADMIN);
        if (admins.isEmpty()) {
            log.warn("No admin users found to notify about escalation of report {}", report.getReportId());
            return;
        }

        String title = "Report escalated - requires attention";
        String message = String.format("Report #%d has been automatically escalated (Priority: %s)", 
            report.getReportId(), report.getPriority());
        String payload = toJsonSafe(Map.of(
            "reportId", report.getReportId(),
            "priority", report.getPriority(),
            "escalatedAt", report.getEscalatedAt(),
            "escalationReason", report.getEscalationReason()
        ));

        admins.forEach(admin -> notificationService.sendNotification(
            admin,
            NotificationType.SYSTEM,
            title,
            message,
            payload,
            Priority.HIGH,
            DeliveryMethod.IN_APP,
            DEFAULT_QUEUE
        ));
    }

    private void notifyAdminsOfRideReport(UserReport report) {
        List<User> admins = userRepository.findByUserType(UserType.ADMIN);
        if (admins.isEmpty()) {
            log.warn("No admin users found to notify about ride report {}", report.getReportId());
            return;
        }

        String title = "New ride report submitted";
        String message = String.format("Ride report #%d (%s) for ride #%d requires review.", 
            report.getReportId(), report.getReportType(), report.getSharedRide().getSharedRideId());
        String payload = toJsonSafe(Map.of(
            "reportId", report.getReportId(),
            "rideId", report.getSharedRide().getSharedRideId(),
            "status", report.getStatus(),
            "reportType", report.getReportType()
        ));

        admins.forEach(admin -> notificationService.sendNotification(
            admin,
            NotificationType.RIDE_REPORT_SUBMITTED,
            title,
            message,
            payload,
            Priority.HIGH,
            DeliveryMethod.IN_APP,
            DEFAULT_QUEUE
        ));
    }

    private void notifyUserOfReportSubmission(UserReport report) {
        User reporter = report.getReporter();
        if (reporter == null) {
            return;
        }

        String title = "Ride report submitted";
        String message = String.format("Your report #%d has been submitted and is under review.", report.getReportId());
        String payload = toJsonSafe(Map.of(
            "reportId", report.getReportId(),
            "status", report.getStatus()
        ));

        notificationService.sendNotification(
            reporter,
            NotificationType.RIDE_REPORT_SUBMITTED,
            title,
            message,
            payload,
            Priority.MEDIUM,
            DeliveryMethod.IN_APP,
            DEFAULT_QUEUE
        );
    }

    private void notifyUserOfReportStatusChange(UserReport report, ReportStatus newStatus) {
        User reporter = report.getReporter();
        if (reporter == null) {
            return;
        }

        NotificationType notificationType;
        String title;
        String message;

        switch (newStatus) {
            case IN_PROGRESS:
                notificationType = NotificationType.RIDE_REPORT_IN_PROGRESS;
                title = "Your ride report is under review";
                message = "Your report is being reviewed by our team.";
                break;
            case RESOLVED:
                notificationType = NotificationType.RIDE_REPORT_RESOLVED;
                title = "Your ride report has been resolved";
                message = report.getResolutionMessage() != null ? report.getResolutionMessage() : 
                    "Your report has been resolved. Thank you for your feedback.";
                break;
            case DISMISSED:
                notificationType = NotificationType.RIDE_REPORT_DISMISSED;
                title = "Your ride report has been dismissed";
                message = report.getAdminNotes() != null ? report.getAdminNotes() : 
                    "Your report has been reviewed and dismissed.";
                break;
            default:
                return;
        }

        String payload = toJsonSafe(Map.of(
            "reportId", report.getReportId(),
            "status", newStatus
        ));

        notificationService.sendNotification(
            reporter,
            notificationType,
            title,
            message,
            payload,
            Priority.MEDIUM,
            DeliveryMethod.IN_APP,
            DEFAULT_QUEUE
        );
    }

    private void notifyAdminsOfNewReport(UserReport report) {
        List<User> admins = userRepository.findByUserType(UserType.ADMIN);
        if (admins.isEmpty()) {
            log.warn("No admin users found to notify about report {}", report.getReportId());
            return;
        }

        String title = "New user report submitted";
        String message = String.format("Report #%d (%s) requires review.", report.getReportId(), report.getReportType());
        String payload = toJsonSafe(Map.of(
            "reportId", report.getReportId(),
            "status", report.getStatus(),
            "reportType", report.getReportType()
        ));

        admins.forEach(admin -> notificationService.sendNotification(
            admin,
            NotificationType.USER_REPORT_SUBMITTED,
            title,
            message,
            payload,
            Priority.HIGH,
            DeliveryMethod.IN_APP,
            DEFAULT_QUEUE
        ));
    }

    private void notifyReporterOfResolution(UserReport report) {
        User reporter = report.getReporter();
        if (reporter == null) {
            log.warn("Report {} has no reporter associated when sending resolution notification", report.getReportId());
            return;
        }

        String title = "Your report has been resolved";
        String message = report.getResolutionMessage();
        String payload = toJsonSafe(Map.of(
            "reportId", report.getReportId(),
            "status", report.getStatus(),
            "resolvedAt", report.getResolvedAt()
        ));

        notificationService.sendNotification(
            reporter,
            NotificationType.USER_REPORT_RESOLVED,
            title,
            message,
            payload,
            Priority.MEDIUM,
            DeliveryMethod.IN_APP,
            DEFAULT_QUEUE
        );
    }

    private User resolveUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw BaseDomainException.unauthorized("Unauthenticated request");
        }
        return userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));
    }

    private Pageable ensurePageable(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return pageable;
    }

    private PageResponse<UserReportSummaryResponse> buildPageResponse(Page<UserReport> page, List<UserReportSummaryResponse> content) {
        return PageResponse.<UserReportSummaryResponse>builder()
            .data(content)
            .pagination(PageResponse.PaginationInfo.builder()
                .page(page.getNumber() + 1)
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalRecords(page.getTotalElements())
                .build())
            .build();
    }

    private String toJsonSafe(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification payload for report: {}", e.getMessage(), e);
            return null;
        }
    }

    private Integer resolveReportedUserId(UserReport report) {
        if (report == null) {
            return null;
        }

        // Prefer driver from ride context
        Integer reporterUserId = report.getReporter() != null ? report.getReporter().getUserId() : null;

        if (report.getDriver() != null && report.getDriver().getUser() != null) {
            Integer driverUserId = report.getDriver().getUser().getUserId();
            if (driverUserId != null && !Objects.equals(driverUserId, reporterUserId)) {
                return driverUserId;
            }
        }

        if (report.getSharedRide() != null
            && report.getSharedRide().getSharedRideRequest() != null
            && report.getSharedRide().getSharedRideRequest().getRider() != null
            && report.getSharedRide().getSharedRideRequest().getRider().getUser() != null) {
            Integer riderUserId = report.getSharedRide().getSharedRideRequest().getRider().getUser().getUserId();
            if (riderUserId != null && !Objects.equals(riderUserId, reporterUserId)) {
                return riderUserId;
            }
        }

        return null;
    }
}

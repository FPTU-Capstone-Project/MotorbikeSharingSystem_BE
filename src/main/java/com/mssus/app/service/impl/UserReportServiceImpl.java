package com.mssus.app.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mssus.app.common.enums.DeliveryMethod;
import com.mssus.app.common.enums.NotificationType;
import com.mssus.app.common.enums.Priority;
import com.mssus.app.common.enums.ReportStatus;
import com.mssus.app.common.enums.ReportType;
import com.mssus.app.common.enums.SharedRideStatus;
import com.mssus.app.common.enums.UserType;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.request.report.RideReportCreateRequest;
import com.mssus.app.dto.request.report.UpdateRideReportRequest;
import com.mssus.app.dto.request.report.UserReportCreateRequest;
import com.mssus.app.dto.request.report.UserReportResolveRequest;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.report.UserReportResponse;
import com.mssus.app.dto.response.report.UserReportSummaryResponse;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.SharedRide;
import com.mssus.app.entity.User;
import com.mssus.app.entity.UserReport;
import com.mssus.app.mapper.UserReportMapper;
import com.mssus.app.repository.SharedRideRepository;
import com.mssus.app.repository.UserReportRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.service.NotificationService;
import com.mssus.app.service.UserReportService;
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

    private final UserReportRepository userReportRepository;
    private final UserRepository userRepository;
    private final SharedRideRepository sharedRideRepository;
    private final NotificationService notificationService;
    private final UserReportMapper userReportMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public UserReportResponse submitReport(Authentication authentication, UserReportCreateRequest request) {
        User reporter = resolveUser(authentication);
        String description = request.getDescription().trim();

        UserReport report = UserReport.builder()
            .reporter(reporter)
            .reportType(request.getReportType())
            .status(ReportStatus.OPEN)
            .description(description)
            .build();

        UserReport saved = userReportRepository.save(report);
        log.info("User {} submitted report {} of type {}", reporter.getUserId(), saved.getReportId(), saved.getReportType());

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

        UserReport report = UserReport.builder()
            .reporter(reporter)
            .sharedRide(ride)
            .driver(driver)
            .reportType(request.getReportType())
            .status(ReportStatus.PENDING)
            .description(description)
            .build();

        UserReport saved = userReportRepository.save(report);
        log.info("User {} submitted ride report {} for ride {} of type {}", 
            reporter.getUserId(), saved.getReportId(), rideId, saved.getReportType());

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
}

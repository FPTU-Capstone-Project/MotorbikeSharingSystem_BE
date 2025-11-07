package com.mssus.app.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mssus.app.common.enums.DeliveryMethod;
import com.mssus.app.common.enums.NotificationType;
import com.mssus.app.common.enums.Priority;
import com.mssus.app.common.enums.ReportStatus;
import com.mssus.app.common.enums.ReportType;
import com.mssus.app.common.enums.UserType;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.request.report.UserReportCreateRequest;
import com.mssus.app.dto.request.report.UserReportResolveRequest;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.report.UserReportResponse;
import com.mssus.app.dto.response.report.UserReportSummaryResponse;
import com.mssus.app.entity.User;
import com.mssus.app.entity.UserReport;
import com.mssus.app.mapper.UserReportMapper;
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

    private final UserReportRepository userReportRepository;
    private final UserRepository userRepository;
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

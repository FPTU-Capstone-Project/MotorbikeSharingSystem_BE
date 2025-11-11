package com.mssus.app.worker;

import com.mssus.app.common.enums.ReportStatus;
import com.mssus.app.common.enums.UserStatus;
import com.mssus.app.entity.User;
import com.mssus.app.entity.UserReport;
import com.mssus.app.repository.UserReportRepository;
import com.mssus.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReportChatFollowUpJob {

    private static final String REPORTER_NO_RESPONSE = "REPORTER_NO_RESPONSE";
    private static final String REPORTED_NO_RESPONSE = "REPORTED_NO_RESPONSE";

    private final UserReportRepository userReportRepository;
    private final UserRepository userRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void enforceChatFollowUps() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(3);

        List<UserReport> reporterOverdue = userReportRepository.findReportsForReporterAutoDismiss(cutoff);
        for (UserReport report : reporterOverdue) {
            if (report.getStatus() == ReportStatus.DISMISSED || report.getStatus() == ReportStatus.RESOLVED) {
                continue;
            }
            report.setStatus(ReportStatus.DISMISSED);
            report.setResolvedAt(now);
            report.setAutoClosedAt(now);
            report.setAutoClosedReason(REPORTER_NO_RESPONSE);
            String existingNotes = report.getAdminNotes();
            String autoNote = "[auto] Report dismissed because reporter did not respond within 3 days.";
            report.setAdminNotes(existingNotes == null ? autoNote : existingNotes + "\n" + autoNote);
            userReportRepository.save(report);
            log.info("Report {} auto-dismissed due to no reporter response", report.getReportId());
        }

        List<UserReport> reportedOverdue = userReportRepository.findReportsForReportedAutoBan(cutoff);
        for (UserReport report : reportedOverdue) {
            if (report.getStatus() == ReportStatus.DISMISSED || report.getStatus() == ReportStatus.RESOLVED) {
                continue;
            }

            Integer reportedUserId = resolveReportedUserId(report);
            if (reportedUserId != null) {
                userRepository.findById(reportedUserId).ifPresent(user -> suspendUser(user, now));
            }

            report.setStatus(ReportStatus.RESOLVED);
            report.setResolvedAt(now);
            report.setAutoClosedAt(now);
            report.setAutoClosedReason(REPORTED_NO_RESPONSE);
            String existingNotes = report.getAdminNotes();
            String autoNote = "[auto] Report resolved and reported user suspended due to no response within 3 days.";
            report.setAdminNotes(existingNotes == null ? autoNote : existingNotes + "\n" + autoNote);
            report.setResolutionMessage("Auto-resolved: reported user failed to respond within 3 days.");

            userReportRepository.save(report);
            log.info("Report {} auto-resolved; reported user suspended for no response", report.getReportId());
        }
    }

    private void suspendUser(User user, LocalDateTime now) {
        if (UserStatus.SUSPENDED.equals(user.getStatus())) {
            return;
        }
        user.setStatus(UserStatus.SUSPENDED);
        user.setUpdatedAt(now);
        userRepository.save(user);
    }

    private Integer resolveReportedUserId(UserReport report) {
        if (report == null) {
            return null;
        }

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


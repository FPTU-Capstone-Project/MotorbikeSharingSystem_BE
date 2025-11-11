package com.mssus.app.repository;

import com.mssus.app.common.enums.ReportStatus;
import com.mssus.app.common.enums.ReportType;
import com.mssus.app.entity.UserReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserReportRepository extends JpaRepository<UserReport, Integer> {

    Page<UserReport> findByStatus(ReportStatus status, Pageable pageable);

    Page<UserReport> findByReportType(ReportType reportType, Pageable pageable);

    Page<UserReport> findByStatusAndReportType(ReportStatus status, ReportType reportType, Pageable pageable);

    Page<UserReport> findByReporterUserId(Integer userId, Pageable pageable);

    Optional<UserReport> findByReportIdAndReporterUserId(Integer reportId, Integer userId);

    Optional<UserReport> findBySharedRideSharedRideIdAndReporterUserId(Integer sharedRideId, Integer userId);

    Page<UserReport> findBySharedRideSharedRideId(Integer sharedRideId, Pageable pageable);

    Page<UserReport> findByDriverDriverId(Integer driverId, Pageable pageable);

    @Query("""
        SELECT r
        FROM UserReport r
        WHERE r.reporterChatStartedAt IS NOT NULL
          AND (r.reporterLastReplyAt IS NULL OR r.reporterLastReplyAt < r.reporterChatStartedAt)
          AND r.reporterChatStartedAt <= :cutoff
          AND r.status NOT IN (com.mssus.app.common.enums.ReportStatus.RESOLVED, com.mssus.app.common.enums.ReportStatus.DISMISSED)
    """)
    List<UserReport> findReportsForReporterAutoDismiss(@Param("cutoff") LocalDateTime cutoff);

    @Query("""
        SELECT r
        FROM UserReport r
        WHERE r.reportedChatStartedAt IS NOT NULL
          AND (r.reportedLastReplyAt IS NULL OR r.reportedLastReplyAt < r.reportedChatStartedAt)
          AND r.reportedChatStartedAt <= :cutoff
          AND r.status NOT IN (com.mssus.app.common.enums.ReportStatus.RESOLVED, com.mssus.app.common.enums.ReportStatus.DISMISSED)
    """)
    List<UserReport> findReportsForReportedAutoBan(@Param("cutoff") LocalDateTime cutoff);
}

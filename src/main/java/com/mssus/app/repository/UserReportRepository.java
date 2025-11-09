package com.mssus.app.repository;

import com.mssus.app.common.enums.ReportStatus;
import com.mssus.app.common.enums.ReportType;
import com.mssus.app.entity.UserReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}

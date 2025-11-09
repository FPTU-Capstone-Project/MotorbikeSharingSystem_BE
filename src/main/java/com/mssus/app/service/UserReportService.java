package com.mssus.app.service;

import com.mssus.app.common.enums.ReportStatus;
import com.mssus.app.common.enums.ReportType;
import com.mssus.app.dto.request.report.RideReportCreateRequest;
import com.mssus.app.dto.request.report.UpdateRideReportRequest;
import com.mssus.app.dto.request.report.UserReportCreateRequest;
import com.mssus.app.dto.request.report.UserReportResolveRequest;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.report.UserReportResponse;
import com.mssus.app.dto.response.report.UserReportSummaryResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

public interface UserReportService {

    UserReportResponse submitReport(Authentication authentication, UserReportCreateRequest request);

    UserReportResponse submitRideReport(Integer rideId, Authentication authentication, RideReportCreateRequest request);

    PageResponse<UserReportSummaryResponse> getReports(ReportStatus status, ReportType reportType, Pageable pageable);

    UserReportResponse getReportDetails(Integer reportId);

    UserReportResponse resolveReport(Integer reportId, UserReportResolveRequest request, Authentication authentication);

    UserReportResponse updateRideReportStatus(Integer reportId, UpdateRideReportRequest request, Authentication authentication);
}

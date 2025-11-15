package com.mssus.app.dto.response.report;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Report analytics and statistics")
public class ReportAnalyticsResponse {

    @Schema(description = "Total number of reports")
    private Long totalReports;

    @Schema(description = "Number of pending reports")
    private Long pendingReports;

    @Schema(description = "Number of open reports")
    private Long openReports;

    @Schema(description = "Number of in-progress reports")
    private Long inProgressReports;

    @Schema(description = "Number of resolved reports")
    private Long resolvedReports;

    @Schema(description = "Number of dismissed reports")
    private Long dismissedReports;

    @Schema(description = "Number of escalated reports")
    private Long escalatedReports;

    @Schema(description = "Reports by type count")
    private Map<String, Long> reportsByType;

    @Schema(description = "Reports by priority count")
    private Map<String, Long> reportsByPriority;

    @Schema(description = "Reports by status count")
    private Map<String, Long> reportsByStatus;

    @Schema(description = "Average resolution time in hours")
    private Double averageResolutionTimeHours;

    @Schema(description = "Reports created today")
    private Long reportsToday;

    @Schema(description = "Reports created this week")
    private Long reportsThisWeek;

    @Schema(description = "Reports created this month")
    private Long reportsThisMonth;

    @Schema(description = "Top reported drivers")
    private java.util.List<DriverReportStats> topReportedDrivers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Driver report statistics")
    public static class DriverReportStats {
        @Schema(description = "Driver ID")
        private Integer driverId;

        @Schema(description = "Driver name")
        private String driverName;

        @Schema(description = "Number of reports against driver")
        private Long reportCount;

        @Schema(description = "Number of critical reports")
        private Long criticalReports;
    }
}


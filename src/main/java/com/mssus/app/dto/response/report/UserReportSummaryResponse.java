package com.mssus.app.dto.response.report;

import com.mssus.app.common.enums.ReportPriority;
import com.mssus.app.common.enums.ReportStatus;
import com.mssus.app.common.enums.ReportType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Summary view of a user report")
public class UserReportSummaryResponse {

    @Schema(description = "Identifier of the report", example = "42")
    private Integer reportId;

    @Schema(description = "Type of the report", example = "SAFETY")
    private ReportType reportType;

    @Schema(description = "Current status of the report", example = "OPEN")
    private ReportStatus status;

    @Schema(description = "Priority level of the report", example = "MEDIUM")
    private ReportPriority priority;

    @Schema(description = "Brief description of the report", example = "Driver arrived late and was unprofessional.")
    private String description;

    @Schema(description = "Identifier of the reporting user", example = "15")
    private Integer reporterId;

    @Schema(description = "Full name of the reporting user", example = "Nguyen Van A")
    private String reporterName;

    @Schema(description = "Identifier of the shared ride (if this is a ride-specific report)", example = "123")
    private Integer sharedRideId;

    @Schema(description = "Identifier of the driver (if this is a ride-specific report)", example = "45")
    private Integer driverId;

    @Schema(description = "Timestamp when the report was created", example = "2024-05-18T10:15:30")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp when the report was last updated", example = "2024-05-19T08:02:11")
    private LocalDateTime updatedAt;

    @Schema(description = "Timestamp when admin first contacted the reporter", example = "2024-05-19T10:00:00")
    private LocalDateTime reporterChatStartedAt;

    @Schema(description = "Timestamp of the reporter's last reply", example = "2024-05-19T11:00:00")
    private LocalDateTime reporterLastReplyAt;

    @Schema(description = "Timestamp when admin first contacted the reported user", example = "2024-05-19T10:05:00")
    private LocalDateTime reportedChatStartedAt;

    @Schema(description = "Timestamp of the reported user's last reply", example = "2024-05-19T11:05:00")
    private LocalDateTime reportedLastReplyAt;

    @Schema(description = "Timestamp when the report was automatically closed (if applicable)")
    private LocalDateTime autoClosedAt;

    @Schema(description = "Reason for automatic closure", example = "REPORTER_NO_RESPONSE")
    private String autoClosedReason;
}

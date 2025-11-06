package com.mssus.app.dto.response.report;

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
@Schema(description = "Detailed view of a user report")
public class UserReportResponse {

    @Schema(description = "Identifier of the report", example = "42")
    private Integer reportId;

    @Schema(description = "Type of the report", example = "SAFETY")
    private ReportType reportType;

    @Schema(description = "Current status of the report", example = "RESOLVED")
    private ReportStatus status;

    @Schema(description = "Detailed description from the reporting user")
    private String description;

    @Schema(description = "Identifier of the reporting user", example = "15")
    private Integer reporterId;

    @Schema(description = "Full name of the reporting user", example = "Nguyen Van A")
    private String reporterName;

    @Schema(description = "Email of the reporting user", example = "student@example.edu")
    private String reporterEmail;

    @Schema(description = "Identifier of the admin who resolved the report", example = "1")
    private Integer resolverId;

    @Schema(description = "Full name of the resolving admin", example = "Admin User")
    private String resolverName;

    @Schema(description = "Resolution message provided by the admin")
    private String resolutionMessage;

    @Schema(description = "Timestamp when the report was created")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp when the report was last updated")
    private LocalDateTime updatedAt;

    @Schema(description = "Timestamp when the report was resolved")
    private LocalDateTime resolvedAt;
}

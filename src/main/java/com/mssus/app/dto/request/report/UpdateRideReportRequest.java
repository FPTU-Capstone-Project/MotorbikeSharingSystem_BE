package com.mssus.app.dto.request.report;

import com.mssus.app.common.enums.ReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for updating a ride report status")
public class UpdateRideReportRequest {

    @NotNull(message = "Status is required")
    @Schema(description = "New status for the report", example = "IN_PROGRESS", allowableValues = {"PENDING", "IN_PROGRESS", "RESOLVED", "DISMISSED"})
    private ReportStatus status;

    @Size(max = 2000, message = "Admin notes cannot exceed 2000 characters")
    @Schema(description = "Optional admin notes explaining the status change", example = "Investigating the issue with the driver.")
    private String adminNotes;
}


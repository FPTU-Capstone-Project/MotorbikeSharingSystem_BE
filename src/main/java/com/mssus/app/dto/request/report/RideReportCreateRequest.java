package com.mssus.app.dto.request.report;

import com.mssus.app.common.enums.ReportPriority;
import com.mssus.app.common.enums.ReportType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Request payload for submitting a ride report")
public class RideReportCreateRequest {

    @NotNull(message = "Report type is required")
    @Schema(description = "Type of the report", example = "SAFETY", allowableValues = {"SAFETY", "BEHAVIOR", "PAYMENT", "ROUTE", "OTHER"})
    private ReportType reportType;

    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    @Schema(description = "Detailed description of the issue encountered during the ride", example = "Driver arrived late and was unprofessional.")
    private String description;

    @Schema(description = "Priority level of the report", example = "MEDIUM", defaultValue = "MEDIUM")
    private ReportPriority priority;
}


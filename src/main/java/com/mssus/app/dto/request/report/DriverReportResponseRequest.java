package com.mssus.app.dto.request.report;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for driver response to a report")
public class DriverReportResponseRequest {

    @NotBlank(message = "Driver response is required")
    @Size(max = 2000, message = "Driver response cannot exceed 2000 characters")
    @Schema(description = "Driver's response to the report", example = "I apologize for any inconvenience. The traffic was unexpected that day.")
    private String driverResponse;
}


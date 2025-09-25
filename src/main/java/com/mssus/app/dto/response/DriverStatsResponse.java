package com.mssus.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Driver KYC statistics response")
public class DriverStatsResponse {

    @JsonProperty("total_drivers")
    @Schema(description = "Total number of drivers", example = "150")
    private Long totalDrivers;

    @JsonProperty("pending_verifications")
    @Schema(description = "Total pending verifications", example = "25")
    private Long pendingVerifications;

    @JsonProperty("pending_documents")
    @Schema(description = "Pending document verifications", example = "10")
    private Long pendingDocuments;

    @JsonProperty("pending_licenses")
    @Schema(description = "Pending license verifications", example = "8")
    private Long pendingLicenses;

    @JsonProperty("pending_vehicles")
    @Schema(description = "Pending vehicle verifications", example = "7")
    private Long pendingVehicles;

    @JsonProperty("pending_background_checks")
    @Schema(description = "Pending background checks", example = "5")
    private Long pendingBackgroundChecks;

    @JsonProperty("approved_drivers")
    @Schema(description = "Fully approved drivers", example = "100")
    private Long approvedDrivers;

    @JsonProperty("rejected_verifications")
    @Schema(description = "Total rejected verifications", example = "15")
    private Long rejectedVerifications;

    @JsonProperty("completion_rate")
    @Schema(description = "KYC completion rate percentage", example = "85.5")
    private Double completionRate;
}
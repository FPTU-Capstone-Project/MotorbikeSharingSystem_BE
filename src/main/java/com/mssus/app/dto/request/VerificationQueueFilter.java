package com.mssus.app.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(description = "Optional filters applied when retrieving the verification review queue.")
public class VerificationQueueFilter {

    @Schema(description = "Filter by verification status")
    String status;

    @Schema(description = "Filter by rider/driver profile type")
    String profileType;

    @Schema(description = "Only return high-risk submissions when true")
    Boolean highRiskOnly;

    @Schema(description = "Only return cases assigned to the current reviewer when true")
    Boolean onlyMine;

    @Schema(description = "Search by user email or name fragment")
    String search;
}

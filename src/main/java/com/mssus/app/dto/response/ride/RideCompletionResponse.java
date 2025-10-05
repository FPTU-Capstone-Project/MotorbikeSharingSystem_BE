package com.mssus.app.dto.response.ride;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for ride completion summary.
 * Used by: POST /api/v1/rides/{rideId}/complete (Driver)
 * 
 * @since 1.0.0 (Ride Module MVP)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Ride completion summary")
public class RideCompletionResponse {

    @JsonProperty("shared_ride_id")
    @Schema(description = "Ride ID", example = "300")
    private Integer sharedRideId;

    @Schema(description = "Ride status (should be COMPLETED)", example = "COMPLETED")
    private String status;

    @JsonProperty("actual_distance")
    @Schema(description = "Actual distance traveled (km)", example = "8.7")
    private Float actualDistance;

    @JsonProperty("actual_duration")
    @Schema(description = "Actual duration (minutes)", example = "27")
    private Integer actualDuration;

    @JsonProperty("total_fare_collected")
    @Schema(description = "Total fare from all riders", example = "75000.00")
    private BigDecimal totalFareCollected;

    @JsonProperty("driver_earnings")
    @Schema(description = "Driver's earnings after commission", example = "63750.00")
    private BigDecimal driverEarnings;

    @JsonProperty("platform_commission")
    @Schema(description = "Platform commission amount", example = "11250.00")
    private BigDecimal platformCommission;

    @JsonProperty("completed_requests_count")
    @Schema(description = "Number of completed requests", example = "3")
    private Integer completedRequestsCount;

    @JsonProperty("completed_at")
    @Schema(description = "Completion timestamp", example = "2025-10-05T08:32:00")
    private LocalDateTime completedAt;

    @JsonProperty("completed_requests")
    @Schema(description = "List of completed request IDs")
    private List<Integer> completedRequests;
}


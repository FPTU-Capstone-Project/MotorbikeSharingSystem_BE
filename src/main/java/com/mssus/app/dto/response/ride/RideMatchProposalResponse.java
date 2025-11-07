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

/**
 * Response DTO for AI matching proposals.
 * Used by: GET /api/v1/ride-requests/{requestId}/matches (Rider)
 * 
 * @since 1.0.0 (Ride Module MVP)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "AI matching proposal for a ride request")
public class RideMatchProposalResponse {

    @JsonProperty("shared_ride_id")
    @Schema(description = "Candidate ride ID", example = "300")
    private Integer sharedRideId;

    @JsonProperty("driver_id")
    @Schema(description = "Driver ID", example = "50")
    private Integer driverId;

    @JsonProperty("driver_name")
    @Schema(description = "Driver's full name", example = "John Doe")
    private String driverName;

    @JsonProperty("driver_rating")
    @Schema(description = "Driver's average rating", example = "4.8")
    private Float driverRating;

    @JsonProperty("vehicle_model")
    @Schema(description = "Vehicle model", example = "Honda Wave Alpha")
    private String vehicleModel;

    @JsonProperty("vehicle_plate")
    @Schema(description = "Vehicle plate number", example = "29A-12345")
    private String vehiclePlate;

    @JsonProperty("scheduled_time")
    @Schema(description = "Ride scheduled departure time", example = "2025-10-05T08:00:00")
    private LocalDateTime scheduledTime;

    @JsonProperty("estimated_fare")
    @Schema(description = "Total fare for this match", example = "25000.00")
    private BigDecimal totalFare;

    @JsonProperty("earned_amount")
    @Schema(description = "Driver's earned amount for this match", example = "20000.00")
    private BigDecimal earnedAmount;

    @JsonProperty("estimated_duration")
    @Schema(description = "Estimated ride duration in minutes", example = "25")
    private Integer estimatedDuration;

    @JsonProperty("estimated_distance")
    @Schema(description = "Estimated distance in km", example = "8.5")
    private Float estimatedDistance;

    @JsonProperty("detour_distance")
    @Schema(description = "Extra detour distance for pickup (km)", example = "0.8")
    private Float detourDistance;

    @JsonProperty("detour_duration")
    @Schema(description = "Extra detour time for pickup (minutes)", example = "3")
    private Integer detourDuration;

    @JsonProperty("match_score")
    @Schema(description = "Matching algorithm score (0-100)", example = "87.5")
    private Float matchScore;

    @JsonProperty("estimated_pickup_time")
    @Schema(description = "Estimated pickup time for rider", example = "2025-10-05T08:15:00")
    private LocalDateTime estimatedPickupTime;

    @JsonProperty("estimated_dropoff_time")
    @Schema(description = "Estimated dropoff time for rider", example = "2025-10-05T08:40:00")
    private LocalDateTime estimatedDropoffTime;
}


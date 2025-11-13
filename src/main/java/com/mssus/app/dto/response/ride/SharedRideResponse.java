package com.mssus.app.dto.response.ride;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mssus.app.dto.response.LocationResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for shared ride details.
 * 
 * @since 1.0.0 (Ride Module MVP)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Shared ride details")
public class SharedRideResponse {

    @JsonProperty("shared_ride_id")
    @Schema(description = "Ride ID", example = "300")
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

    @JsonProperty("vehicle_id")
    @Schema(description = "Vehicle ID", example = "10")
    private Integer vehicleId;

    @JsonProperty("vehicle_model")
    @Schema(description = "Vehicle model", example = "Honda Wave Alpha")
    private String vehicleModel;

    @JsonProperty("vehicle_plate")
    @Schema(description = "Vehicle plate number", example = "29A-12345")
    private String vehiclePlate;

    @JsonProperty("start_location")
    @Schema(description = "Start location details")
    private LocationResponse startLocation;

    @JsonProperty("end_location")
    @Schema(description = "End location details")
    private LocationResponse endLocation;

    @JsonProperty("route")
    @Schema(description = "Route summary information")
    private RouteSummaryResponse route;

    @JsonProperty("driver_approach_polyline")
    @Schema(description = "Polyline representing the driver's path to the pickup point")
    private String driverApproachPolyline;

    @JsonProperty("driver_approach_distance_meters")
    @Schema(description = "Distance in meters from driver's current position to pickup when the ride was accepted", example = "1500")
    private Integer driverApproachDistanceMeters;

    @JsonProperty("driver_approach_duration_seconds")
    @Schema(description = "Estimated duration in seconds for the driver to reach the pickup when the ride was accepted", example = "240")
    private Integer driverApproachDurationSeconds;

    @JsonProperty("driver_approach_eta")
    @Schema(description = "Estimated time the driver will arrive at pickup", example = "2025-10-05T08:10:00")
    private LocalDateTime driverApproachEta;

//    @JsonProperty("start_location_id")
//    @Schema(description = "Start location ID", example = "1")
//    private Integer startLocationId;
//
//    @JsonProperty("start_location_name")
//    @Schema(description = "Start location name", example = "FPT University")
//    private String startLocationName;
//
//    @JsonProperty("end_location_id")
//    @Schema(description = "End location ID", example = "2")
//    private Integer endLocationId;
//
//    @JsonProperty("end_location_name")
//    @Schema(description = "End location name", example = "Thu Duc Market")
//    private String endLocationName;

    @Schema(description = "Ride status (SCHEDULED, ONGOING, COMPLETED, CANCELLED)", example = "SCHEDULED")
    private String status;

//    @JsonProperty("max_passengers")
//    @Schema(description = "Maximum passenger capacity", example = "2")
//    private Integer maxPassengers;
//
//    @JsonProperty("current_passengers")
//    @Schema(description = "Current passenger count", example = "1")
//    private Integer currentPassengers;
//
//    @JsonProperty("available_seats")
//    @Schema(description = "Available seats", example = "1")
//    private Integer availableSeats;

    @JsonProperty("base_fare")
    @Schema(description = "Base fare amount", example = "15000.00")
    private BigDecimal baseFare;

    @JsonProperty("per_km_rate")
    @Schema(description = "Per kilometer rate", example = "3000.00")
    private BigDecimal perKmRate;

    @JsonProperty("estimated_duration")
    @Schema(description = "Estimated duration in minutes", example = "25")
    private Integer estimatedDuration;

    @JsonProperty("estimated_distance")
    @Schema(description = "Estimated distance in km", example = "8.5")
    private Float estimatedDistance;

    @JsonProperty("actual_duration")
    @Schema(description = "Actual duration in minutes (after completion)", example = "27")
    private Integer actualDuration;

    @JsonProperty("actual_distance")
    @Schema(description = "Actual distance in km (after completion)", example = "8.7")
    private Float actualDistance;

    @JsonProperty("scheduled_time")
    @Schema(description = "Scheduled departure time", example = "2025-10-05T08:00:00")
    private LocalDateTime scheduledTime;

    @JsonProperty("started_at")
    @Schema(description = "Actual start time", example = "2025-10-05T08:05:00")
    private LocalDateTime startedAt;

    @JsonProperty("completed_at")
    @Schema(description = "Completion time", example = "2025-10-05T08:32:00")
    private LocalDateTime completedAt;

    @JsonProperty("created_at")
    @Schema(description = "Creation timestamp", example = "2025-10-04T15:30:00")
    private LocalDateTime createdAt;
}


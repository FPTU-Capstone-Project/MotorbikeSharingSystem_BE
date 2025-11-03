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
 * Response DTO for shared ride request details.
 * 
 * @since 1.0.0 (Ride Module MVP)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Shared ride request details")
public class SharedRideRequestResponse {

    @JsonProperty("shared_ride_request_id")
    @Schema(description = "Request ID", example = "1001")
    private Integer sharedRideRequestId;

    @JsonProperty("request_kind")
    @Schema(description = "Request type (BOOKING or JOIN_RIDE)", example = "BOOKING")
    private String requestKind;

    @JsonProperty("shared_ride_id")
    @Schema(description = "Associated ride ID (null for pending BOOKING)", example = "300")
    private Integer sharedRideId;

    @JsonProperty("rider_id")
    @Schema(description = "Rider ID", example = "100")
    private Integer riderId;

    @JsonProperty("rider_name")
    @Schema(description = "Rider's full name", example = "Jane Smith")
    private String riderName;

    @JsonProperty("rider_rating")
    @Schema(description = "Rider's average rating", example = "4.9")
    private Float riderRating;

    @JsonProperty("pickup_location")
    @Schema(description = "Pickup location details in JSON format")
    private LocationResponse pickupLocation;

    @JsonProperty("dropoff_location")
    @Schema(description = "Dropoff location details in JSON format")
    private LocationResponse dropoffLocation;

    @Schema(description = "Request status (PENDING, CONFIRMED, ONGOING, COMPLETED, CANCELLED, EXPIRED)", example = "PENDING")
    private String status;

    @JsonProperty("fare_amount")
    @Schema(description = "Final fare amount", example = "25000.00")
    private BigDecimal fareAmount;

    @JsonProperty("original_fare")
    @Schema(description = "Original fare before discount", example = "30000.00")
    private BigDecimal originalFare;

    @JsonProperty("discount_amount")
    @Schema(description = "Discount applied", example = "5000.00")
    private BigDecimal discountAmount;

    @JsonProperty("pickup_time")
    @Schema(description = "Requested pickup time", example = "2025-10-05T08:30:00")
    private LocalDateTime pickupTime;

    @JsonProperty("special_requests")
    @Schema(description = "Special requests or notes", example = "Need helmet")
    private String specialRequests;

    @JsonProperty("estimated_pickup_time")
    @Schema(description = "Estimated pickup time (set by driver)", example = "2025-10-05T08:35:00")
    private LocalDateTime estimatedPickupTime;

    @JsonProperty("actual_pickup_time")
    @Schema(description = "Actual pickup time", example = "2025-10-05T08:37:00")
    private LocalDateTime actualPickupTime;

    @JsonProperty("estimated_dropoff_time")
    @Schema(description = "Estimated dropoff time", example = "2025-10-05T09:00:00")
    private LocalDateTime estimatedDropoffTime;

    @JsonProperty("actual_dropoff_time")
    @Schema(description = "Actual dropoff time", example = "2025-10-05T09:02:00")
    private LocalDateTime actualDropoffTime;

    @JsonProperty("initiated_by")
    @Schema(description = "Who initiated (rider or driver)", example = "rider")
    private String initiatedBy;

    @JsonProperty("polyline")
    @Schema(description = "Route polyline for the ride request from pickup to dropoff", example = "abcdEfghIjklMnopQrstuVwxyz")
    private String polyline;

    @JsonProperty("polyline_from_driver_to_pickup")
    @Schema(description = "Polyline from driver's current location to pickup point", example = "abcdEfghIjklMnopQrstuVwxyz")
    private String polylineFromDriverToPickup;

    @JsonProperty("route")
    @Schema(description = "Associated route summary when a ride has been confirmed")
    private RouteSummaryResponse route;

    @JsonProperty("created_at")
    @Schema(description = "Creation timestamp", example = "2025-10-04T16:00:00")
    private LocalDateTime createdAt;
}


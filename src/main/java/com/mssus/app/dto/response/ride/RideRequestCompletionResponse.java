//package com.mssus.app.dto.response.ride;
//
//import com.fasterxml.jackson.annotation.JsonInclude;
//import com.fasterxml.jackson.annotation.JsonProperty;
//import io.swagger.v3.oas.annotations.media.Schema;
//import lombok.AllArgsConstructor;
//import lombok.Builder;
//import lombok.Data;
//import lombok.NoArgsConstructor;
//
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//
//@Data
//@Builder
//@NoArgsConstructor
//@AllArgsConstructor
//@JsonInclude(JsonInclude.Include.NON_NULL)
//@Schema(description = "Ride request completion summary")
//public class RideRequestCompletionResponse {
//    @JsonProperty("shared_ride_id")
//    @Schema(description = "Ride ID", example = "300")
//    private Integer sharedRideId;
//
//    @JsonProperty("shared_ride_request_id")
//    @Schema(description = "Ride request ID", example = "1500")
//    private Integer sharedRideRequestId;
//
//    @JsonProperty("actual_distance")
//    @Schema(description = "Actual distance traveled (km)", example = "8.7")
//    private Float actualDistance;
//
//    @JsonProperty("actual_duration")
//    @Schema(description = "Actual duration (minutes)", example = "27")
//    private Integer actualDuration;
//
//    @JsonProperty("driver_earnings")
//    @Schema(description = "Driver's earnings after commission", example = "63750.00")
//    private BigDecimal driverEarnings;
//
//    @JsonProperty("completed_at")
//    @Schema(description = "Completion timestamp", example = "2025-10-05T08:32:00")
//    private LocalDateTime completedAt;
//}

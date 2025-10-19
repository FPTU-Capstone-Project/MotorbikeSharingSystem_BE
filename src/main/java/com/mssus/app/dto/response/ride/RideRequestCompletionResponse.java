package com.mssus.app.dto.response.ride;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Ride request completion summary")
public class RideRequestCompletionResponse {
    @JsonProperty("shared_ride_id")
    @Schema(description = "Ride ID", example = "300")
    private Integer sharedRideId;

    @JsonProperty("shared_ride_request_id")
    @Schema(description = "Ride request ID", example = "1500")
    private Integer sharedRideRequestId;

    @JsonProperty("request_total_fare")
    @Schema(description = "Total fare for the ride request", example = "75000.00")
    private String requestTotalFare;

    @JsonProperty("platform_commission")
    @Schema(description = "Platform commission amount", example = "11250.00")
    private BigDecimal platformCommission;

    @JsonProperty("driver_earnings_of_request")
    @Schema(description = "Driver's earnings after commission", example = "63750.00")
    private BigDecimal driverEarningsOfRequest;

    @JsonProperty("request_actual_distance")
    @Schema(description = "Actual distance traveled (km) of request", example = "8.7")
    private Float requestActualDistance;

    @JsonProperty("request_actual_duration")
    @Schema(description = "Actual duration (minutes) of request", example = "27")
    private Integer requestActualDuration;
}

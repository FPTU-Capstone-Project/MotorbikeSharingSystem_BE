package com.mssus.app.dto.response.rating;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Rating details for driver or rider history views")
public class RatingResponse {

    @JsonProperty("rating_id")
    @Schema(description = "Unique rating identifier", example = "42")
    private Integer ratingId;

    @JsonProperty("shared_ride_request_id")
    @Schema(description = "Associated shared ride request identifier", example = "128")
    private Integer sharedRideRequestId;

    @JsonProperty("shared_ride_id")
    @Schema(description = "Associated shared ride identifier", example = "56")
    private Integer sharedRideId;

    @JsonProperty("driver_id")
    @Schema(description = "Driver profile identifier", example = "12")
    private Integer driverId;

    @JsonProperty("driver_name")
    @Schema(description = "Driver full name", example = "Nguyen Van A")
    private String driverName;

    @JsonProperty("rider_id")
    @Schema(description = "Rider profile identifier", example = "34")
    private Integer riderId;

    @JsonProperty("rider_name")
    @Schema(description = "Rider full name", example = "Tran Thi B")
    private String riderName;

    @Schema(description = "Rating score (1-5)", example = "5", minimum = "1", maximum = "5")
    private Integer score;

    @Schema(description = "Optional rider comment", example = "Driver was punctual and polite")
    private String comment;

    @JsonProperty("created_at")
    @Schema(description = "Timestamp when the rating was created", example = "2025-10-26T10:15:30")
    private LocalDateTime createdAt;
}

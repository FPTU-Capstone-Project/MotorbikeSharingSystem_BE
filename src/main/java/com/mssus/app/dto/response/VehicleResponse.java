package com.mssus.app.dto.response;

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
@Schema(description = "Vehicle response")
public class VehicleResponse {

    @JsonProperty("vehicle_id")
    @Schema(description = "Vehicle ID", example = "1")
    private Integer vehicleId;

    @JsonProperty("driver_id")
    @Schema(description = "Driver ID", example = "1")
    private Integer driverId;

    @JsonProperty("driver_name")
    @Schema(description = "Driver full name", example = "Nguyen Van A")
    private String driverName;

    @JsonProperty("plate_number")
    @Schema(description = "Vehicle plate number", example = "29A-12345")
    private String plateNumber;

    @Schema(description = "Vehicle model", example = "Honda Wave Alpha")
    private String model;

    @Schema(description = "Vehicle color", example = "Red")
    private String color;

    @Schema(description = "Manufacturing year", example = "2020")
    private Integer year;

    @Schema(description = "Vehicle capacity", example = "2")
    private Integer capacity;

    @JsonProperty("insurance_expiry")
    @Schema(description = "Insurance expiry date")
    private LocalDateTime insuranceExpiry;

    @JsonProperty("last_maintenance")
    @Schema(description = "Last maintenance date")
    private LocalDateTime lastMaintenance;

    @JsonProperty("fuel_type")
    @Schema(description = "Fuel type", example = "Gasoline")
    private String fuelType;

    @Schema(description = "Vehicle status", example = "active")
    private String status;

    @JsonProperty("verified_at")
    @Schema(description = "Verification date")
    private LocalDateTime verifiedAt;

    @JsonProperty("created_at")
    @Schema(description = "Creation date")
    private LocalDateTime createdAt;
}
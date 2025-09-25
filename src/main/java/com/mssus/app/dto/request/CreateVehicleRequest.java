package com.mssus.app.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create vehicle request")
public class CreateVehicleRequest {

    @NotNull(message = "Driver ID is required")
    @Schema(description = "Driver ID who owns the vehicle", example = "1")
    private Integer driverId;

    @NotBlank(message = "Plate number is required")
    @Size(max = 20, message = "Plate number must not exceed 20 characters")
    @Schema(description = "Vehicle plate number", example = "29A-12345")
    private String plateNumber;

    @NotBlank(message = "Model is required")
    @Size(max = 100, message = "Model must not exceed 100 characters")
    @Schema(description = "Vehicle model", example = "Honda Wave Alpha")
    private String model;

    @Size(max = 50, message = "Color must not exceed 50 characters")
    @Schema(description = "Vehicle color", example = "Red")
    private String color;

    @Schema(description = "Manufacturing year", example = "2020")
    private Integer year;

    @Schema(description = "Vehicle capacity", example = "2")
    private Integer capacity;

    @Schema(description = "Insurance expiry date")
    private LocalDateTime insuranceExpiry;

    @Schema(description = "Last maintenance date")
    private LocalDateTime lastMaintenance;

    @Pattern(regexp = "^(gasoline|electric)$", message = "Fuel type must be either 'gasoline' or 'electric'")
    @Schema(description = "Fuel type", example = "gasoline")
    private String fuelType;

    @Pattern(regexp = "^(active|inactive|maintenance|pending)$",
             message = "Status must be one of: active, inactive, maintenance, pending")
    @Schema(description = "Vehicle status", example = "pending")
    private String status;
}
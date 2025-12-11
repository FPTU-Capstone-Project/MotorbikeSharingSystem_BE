package com.mssus.app.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Update vehicle request")
public class UpdateVehicleRequest {

    @Size(max = 20, message = "Plate number must not exceed 20 characters")
    @Pattern(regexp = "^[0-9]{2}[A-Za-z]-[0-9]{4,5}$",
             message = "Plate number must follow pattern NNX-12345 (e.g., 29A-12345)")
    @Schema(description = "Vehicle plate number", example = "29A-12345")
    private String plateNumber;

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
    @Schema(description = "Vehicle status", example = "active")
    private String status;
}
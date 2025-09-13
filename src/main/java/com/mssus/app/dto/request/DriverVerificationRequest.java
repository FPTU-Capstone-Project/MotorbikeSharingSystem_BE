package com.mssus.app.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Driver verification request")
public class DriverVerificationRequest {

    @Schema(description = "Driver license document", required = true)
    private MultipartFile driverLicense;

    @Schema(description = "Vehicle registration document", required = true)
    private MultipartFile vehicleRegistration;

    @NotBlank(message = "Vehicle model is required")
    @Schema(description = "Motorbike model", example = "Honda Wave")
    private String vehicleModel;

    @NotBlank(message = "Plate number is required")
    @Pattern(regexp = "^[0-9]{2}[A-Z]{1,2}[-\\s]?[0-9]{4,5}(\\.[0-9]{2})?$", 
             message = "Invalid Vietnamese plate number format")
    @Schema(description = "Vehicle plate number", example = "59A-12345")
    private String plateNumber;

    @Min(value = 2000, message = "Year must be 2000 or later")
    @Max(value = 2025, message = "Year cannot be in the future")
    @Schema(description = "Year of manufacture", example = "2020")
    private Integer year;

    @Schema(description = "Vehicle color", example = "Black")
    private String color;

    @NotBlank(message = "License number is required")
    @Schema(description = "Driver license number", example = "B12345678")
    private String licenseNumber;
}

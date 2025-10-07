package com.mssus.app.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mssus.app.common.enums.FuelType;
import lombok.Builder;
import lombok.Data;


import java.time.LocalDateTime;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class VehicleInfo {
    private String plateNumber;
    private String model;
    private String color;
    private Integer year;
    private Integer capacity;
    private FuelType fuelType;
    private LocalDateTime insuranceExpiry;
}

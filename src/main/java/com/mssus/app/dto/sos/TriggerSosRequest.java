package com.mssus.app.dto.sos;

import com.mssus.app.common.enums.AlertType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriggerSosRequest {

    private Integer sharedRideId;

    private Double currentLat;

    private Double currentLng;

    @Builder.Default
    private AlertType alertType = AlertType.EMERGENCY;

    @Size(max = 2000)
    private String description;

    @Size(max = 4000)
    private String rideSnapshot;

    private Boolean forceFallbackCall;
}

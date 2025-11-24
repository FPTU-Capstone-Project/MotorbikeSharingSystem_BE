package com.mssus.app.dto.domain.ride;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class RealTimeTrackingUpdateDto implements Serializable {
    private Integer rideId;
    private String polyline;
    private Double currentLat;
    private Double currentLng;
    private Double riderLat;
    private Double riderLng;
    private Double currentDistanceKm;
    private boolean detoured;
    private String source; // DRIVER or RIDER, indicates who produced this update
}

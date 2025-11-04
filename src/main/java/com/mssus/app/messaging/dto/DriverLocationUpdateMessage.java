package com.mssus.app.messaging.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Message representing a driver location update event.
 * Used for real-time tracking and ETA updates during active rides.
 */
@Value
@Builder
@Jacksonized
public class DriverLocationUpdateMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    Integer driverId;
    Double latitude;
    Double longitude;
    Double heading; // Optional: direction of travel in degrees
    Double speed; // Optional: speed in m/s
    Integer rideId; // Optional: current ride being served
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant timestamp;
    
    String correlationId;
}

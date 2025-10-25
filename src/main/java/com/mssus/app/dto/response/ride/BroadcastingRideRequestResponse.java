package com.mssus.app.dto.response.ride;

import com.mssus.app.entity.Location;

import java.math.BigDecimal;

public record BroadcastingRideRequestResponse(
    Integer rideRequestId,
    BigDecimal totalFare,
    Location pickupLocation,
    Location dropoffLocation,
//    String pickupLocation,
//    String dropoffLocation,
//    LatLng pickupCoordinates,
//    LatLng dropoffCoordinates,
    String desiredPickupTime
) {}

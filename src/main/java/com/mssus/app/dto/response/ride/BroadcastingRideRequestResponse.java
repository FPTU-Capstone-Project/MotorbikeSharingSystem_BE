package com.mssus.app.dto.response.ride;

import com.mssus.app.dto.ride.LatLng;

public record BroadcastingRideRequestResponse(
    Integer rideRequestId,
    Integer riderId,
    String pickupLocation,
    String dropoffLocation,
    LatLng pickupCoordinates,
    LatLng dropoffCoordinates,
    String desiredPickupTime
) {}

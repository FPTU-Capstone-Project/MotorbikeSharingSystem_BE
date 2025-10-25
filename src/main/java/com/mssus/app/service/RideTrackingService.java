package com.mssus.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mssus.app.dto.response.ride.TrackingResponse;
import com.mssus.app.dto.domain.ride.LatLng;
import com.mssus.app.dto.domain.ride.LocationPoint;

import java.util.List;
import java.util.Optional;

public interface RideTrackingService {
    TrackingResponse appendGpsPoints(Integer rideId, List<LocationPoint> points, String username);

    double computeDistanceFromPoints(JsonNode pointsNode);

    Optional<LatLng> getLatestPosition(Integer rideId, int maxStaleMinutes);

    void startTracking(Integer rideId);

    void stopTracking(Integer rideId);
}

package com.mssus.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mssus.app.dto.response.ride.TrackingResponse;
import com.mssus.app.dto.ride.LocationPoint;

import java.util.List;

public interface RideTrackingService {
    TrackingResponse appendGpsPoints(Integer rideId, List<LocationPoint> points, String username);

    double computeDistanceFromPoints(JsonNode pointsNode);
}

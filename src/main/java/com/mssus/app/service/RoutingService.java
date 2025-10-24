package com.mssus.app.service;

import com.mssus.app.dto.ride.LatLng;
import com.mssus.app.dto.response.RouteResponse;

import java.time.LocalDateTime;
import java.util.List;


public interface RoutingService {
    RouteResponse getRoute(double fromLat, double fromLon, double toLat, double toLon);

    RouteResponse getMultiStopRoute(List<LatLng> waypoints, LocalDateTime departureTime);

    String getAddressFromCoordinates(double lat, double lon);

    int getEstimatedTravelTimeMinutes(double fromLat, double fromLon, double toLat, double toLon);

}


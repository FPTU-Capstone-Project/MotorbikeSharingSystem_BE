package com.mssus.app.service;

import com.mssus.app.dto.LatLng;
import com.mssus.app.dto.response.RouteResponse;

import java.time.LocalDateTime;
import java.util.List;


public interface RoutingService {
    RouteResponse getRoute(double fromLat, double fromLon, double toLat, double toLon);

    // Add this method
    RouteResponse getMultiStopRoute(List<LatLng> waypoints, LocalDateTime departureTime);

}


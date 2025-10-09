package com.mssus.app.service;

import com.mssus.app.dto.response.RouteResponse;


public interface RoutingService {
    RouteResponse getRoute(double fromLat, double fromLon, double toLat, double toLon);

}


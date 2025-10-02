package com.mssus.app.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@Service
public class OsrmRoutingService {

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String OSRM_URL = "http://localhost:5000/route/v1/driving/";

    public RouteResponse getRoute(double fromLat, double fromLon, double toLat, double toLon) {
        String url = OSRM_URL + fromLon + "," + fromLat + ";" + toLon + "," + toLat
            + "?overview=full&geometries=polyline";

        ResponseEntity<OsrmResponse> resp = restTemplate.getForEntity(url, OsrmResponse.class);

        if (resp.getBody() == null || resp.getBody().routes == null || resp.getBody().routes.isEmpty()) {
            throw new RuntimeException("No route found from OSRM");
        }

        OsrmResponse.Route route = resp.getBody().routes.get(0);

        return new RouteResponse(
            route.distance / 1000.0,   // meters -> km
            route.duration / 60.0,    // seconds -> minutes
            route.geometry            // encoded polyline
        );
    }

    // Response DTOs
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class OsrmResponse {
        public List<Route> routes;

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Route {
            public double distance;
            public double duration;
            public String geometry;
        }
    }

    // What you’ll return to your controller
    public record RouteResponse(double distance_km, double time_min, String polyline) {}
}


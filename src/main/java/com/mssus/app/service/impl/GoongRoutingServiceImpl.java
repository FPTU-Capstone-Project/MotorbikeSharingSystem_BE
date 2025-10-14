package com.mssus.app.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mssus.app.dto.ride.LatLng;
import com.mssus.app.dto.response.RouteResponse;
import com.mssus.app.service.RoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Primary
@RequiredArgsConstructor
public class GoongRoutingServiceImpl implements RoutingService {
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${goong.base-url}")
    private String GOONG_URL;

    @Value("${goong.api-key}")
    private String API_KEY;

    @Override
    public RouteResponse getRoute(double fromLat, double fromLon, double toLat, double toLon) {
        String url = String.format("%s?origin=%f,%f&destination=%f,%f&vehicle=bike&api_key=%s",
            GOONG_URL, fromLat, fromLon, toLat, toLon, API_KEY);

        ResponseEntity<GoongResponse> resp = restTemplate.getForEntity(url, GoongResponse.class);

        if (resp.getBody() == null || resp.getBody().routes == null || resp.getBody().routes.isEmpty()) {
            throw new RuntimeException("No route found from Goong");
        }

        GoongResponse.Route route = resp.getBody().routes.get(0);

        // Calculate total distance and duration from legs
        long totalDistance = route.legs.stream().mapToLong(leg -> leg.distance.value).sum();
        long totalDuration = route.legs.stream().mapToLong(leg -> leg.duration.value).sum();

        return new RouteResponse(
            totalDistance,                    // meters
            totalDuration,                    // seconds
            route.overview_polyline.points    // encoded polyline
        );
    }

    @Override
    public RouteResponse getMultiStopRoute(List<LatLng> waypoints, LocalDateTime departure) {
        if (waypoints == null || waypoints.size() < 2) {
            throw new IllegalArgumentException("At least 2 waypoints required for multi-stop route");
        }

        // Build URL: origin=wp0 lat,lng & destination=wp1 lat,lng;wp2 lat,lng;... &vehicle=car&api_key=...
        String origin = waypoints.get(0).latitude() + "," + waypoints.get(0).longitude();
        String dest = waypoints.subList(1, waypoints.size()).stream()
            .map(wp -> wp.latitude() + "," + wp.longitude())
            .collect(Collectors.joining(";"));

        String url = String.format("%s?origin=%s&destination=%s&vehicle=bike&api_key=%s",
            GOONG_URL, origin, dest, API_KEY);

        // Note: Goong does not support departure_time for traffic-aware routing (static durations only)
        // If needed, consider fallback to another service or log warning

        ResponseEntity<GoongResponse> resp = restTemplate.getForEntity(url, GoongResponse.class);

        if (resp.getBody() == null || resp.getBody().routes == null || resp.getBody().routes.isEmpty()) {
            throw new RuntimeException("No multi-stop route found from Goong");
        }

        GoongResponse.Route route = resp.getBody().routes.get(0);

        // Calculate total distance and duration from legs
        long totalDistance = route.legs.stream().mapToLong(leg -> leg.distance.value).sum();
        long totalDuration = route.legs.stream().mapToLong(leg -> leg.duration.value).sum();

        return new RouteResponse(
            totalDistance,                    // meters
            totalDuration,                    // seconds
            route.overview_polyline.points    // encoded polyline for the whole route
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class GoongResponse {
        public List<Route> routes;

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Route {
            public List<Leg> legs;
            public OverviewPolyline overview_polyline;

            @JsonIgnoreProperties(ignoreUnknown = true)
            static class Leg {
                public DistanceValue distance;
                public DurationValue duration;
            }

            @JsonIgnoreProperties(ignoreUnknown = true)
            static class DistanceValue {
                public long value;  // meters
                public String text;
            }

            @JsonIgnoreProperties(ignoreUnknown = true)
            static class DurationValue {
                public long value;  // seconds
                public String text;
            }

            @JsonIgnoreProperties(ignoreUnknown = true)
            static class OverviewPolyline {
                public String points;
            }
        }
    }

}

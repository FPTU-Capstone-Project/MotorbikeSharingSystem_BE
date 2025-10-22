//package com.mssus.app.service.impl;
//
//import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
//import com.mssus.app.dto.ride.LatLng;
//import com.mssus.app.dto.response.RouteResponse;
//import com.mssus.app.service.RoutingService;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.ResponseEntity;
//import org.springframework.stereotype.Service;
//import org.springframework.web.client.RestTemplate;
//
//import java.time.LocalDateTime;
//import java.util.List;
//
//@Service
////@Primary
//public class OsrmRoutingServiceImpl implements RoutingService {
//    private final RestTemplate restTemplate = new RestTemplate();
//
//    @Value("${osrm.base-url}")
//    private String OSRM_URL;
//
//    public RouteResponse getRoute(double fromLat, double fromLon, double toLat, double toLon) {
//        String url = OSRM_URL + fromLon + "," + fromLat + ";" + toLon + "," + toLat
//            + "?overview=full&geometries=polyline";
//
//        ResponseEntity<OsrmResponse> resp = restTemplate.getForEntity(url, OsrmResponse.class);
//
//        if (resp.getBody() == null || resp.getBody().routes == null || resp.getBody().routes.isEmpty()) {
//            throw new RuntimeException("No route found from OSRM");
//        }
//
//        OsrmResponse.Route route = resp.getBody().routes.get(0);
//
//        return new RouteResponse(
//            route.distance,   // meters
//            route.duration,    // seconds
//            route.geometry            // encoded polyline
//        );
//    }
//
//    @Override
//    public RouteResponse getMultiStopRoute(List<LatLng> waypoints, LocalDateTime departureTime) {
//        throw new UnsupportedOperationException("Not implemented");
//    }
//
//    // Response DTOs
//    @JsonIgnoreProperties(ignoreUnknown = true)
//    static class OsrmResponse {
//        public List<OsrmResponse.Route> routes;
//
//        @JsonIgnoreProperties(ignoreUnknown = true)
//        static class Route {
//            public long distance;
//            public long duration;
//            public String geometry;
//        }
//    }
////
////    // What you’ll return to your controller
////    public record RouteResponse(double distance_km, double time_min, String polyline) {}
//}

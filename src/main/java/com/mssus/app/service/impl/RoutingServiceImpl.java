//package com.mssus.app.service.impl;
//
//import com.graphhopper.GHRequest;
//import com.graphhopper.GHResponse;
//import com.graphhopper.GraphHopper;
//import com.graphhopper.util.Parameters;
//import com.graphhopper.util.PointList;
//import com.mssus.app.service.RoutingService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.stereotype.Service;
//
//import java.util.HashMap;
//import java.util.Map;
//
//@RequiredArgsConstructor
//@Service
//public class RoutingServiceImpl implements RoutingService {
//    private final GraphHopper graphHopper;
//
//    @Override
////    public GHResponse calculateRoute(double fromLat, double fromLon, double toLat, double toLon) {
////        GHRequest request = new GHRequest(fromLat, fromLon, toLat, toLon)
////            .setProfile("scooter")
////            .setAlgorithm(Parameters.Algorithms.DIJKSTRA_BI);
////
////        return graphHopper.route(request);
////    }
//    public ResponseEntity<Map<String, Object>> calculateRoute(double fromLat, double fromLon, double toLat, double toLon) {
//        GHRequest request = new GHRequest(fromLat, fromLon, toLat, toLon)
//            .setProfile("scooter")
//            .setAlgorithm(Parameters.Algorithms.DIJKSTRA_BI);
//
//        GHResponse response = graphHopper.route(request);
//
//        Map<String, Object> result = new HashMap<>();
//        if (response.hasErrors()) {
//            System.err.println("Routing errors: " + response.getErrors());
//            result.put("error", "Routing failed");
//            result.put("messages", response.getErrors().stream().map(Throwable::getMessage).toList());
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
//        }
//
//        var best = response.getBest();
//        PointList points = best.getPoints();
//
//        // Log raw start/end from GraphHopper (indices 0 and points.size()-1)
//        System.out.println("Snapped Start: " + points.getLat(0) + ", " + points.getLon(0));  // Should match fromLat/fromLon closely
//        System.out.println("Snapped End: " + points.getLat(points.size() - 1) + ", " + points.getLon(points.size() - 1));  // Check if drifted
//        result.put("distance_km", Math.round(best.getDistance() / 1000 * 100) / 100.0);  // Rounded to 2 decimals
//        result.put("time_min", Math.round((float) best.getTime() / 1000 / 60 * 100) / 100.0);  // Rounded
//
//
//        result.put("polyline", encodePolyline(best.getPoints(), 5));  // Encoded polyline (compact string)
//        // Optional: Add bbox for map bounds: result.put("bbox", best.getPoints().toBBoxString());
//
//        return ResponseEntity.ok(result);
//    }
//
//    // Custom polyline encoder (Google format, precision=5 for ~1m accuracy)
////    private static String encodePolyline(PointList points, int precision) {
////        if (points.isEmpty()) return "";
////
////        StringBuilder encoded = new StringBuilder();
////        long lastLat = 0;
////        long lastLon = 0;
////        double factor = Math.pow(10, precision);
////
////        for (int i = 0; i < points.size(); i++) {
////            long lat = Math.round(points.getLat(i) * factor);
////            long lon = Math.round(points.getLon(i) * factor);
////
////            long dLat = lat - lastLat;
////            long dLon = lon - lastLon;
////
////            encodeValue(dLat, encoded);
////            encodeValue(dLon, encoded);
////
////            lastLat = lat;
////            lastLon = lon;
////        }
////        return encoded.toString();
////    }
////
////    private static void encodeValue(long v, StringBuilder sb) {
////        // Handle signed value: zigzag encoding
////        long encodedValue = (v << 1) ^ (v >> 63);  // Java long sign extend for negative
////        do {
////            int next5Bits = (int) (encodedValue & 0x1F);
////            encodedValue >>= 5;
////            if (encodedValue != 0) {
////                next5Bits |= 0x20;  // Continuation bit
////            }
////            sb.append((char) (next5Bits + 63));  // Offset to printable ASCII
////        } while (encodedValue != 0);
////    }
//
//    private static String encodePolyline(PointList points, int precision) {
//        if (points.isEmpty()) return "";
//        StringBuilder sb = new StringBuilder();
//        long prevLat = 0;
//        long prevLon = 0;
//        double factor = Math.pow(10, precision);
//        for (int i = 0; i < points.size(); i++) {
//            long lat = Math.round(points.getLat(i) * factor);
//            long lon = Math.round(points.getLon(i) * factor);
//            encodeCoordinate(lat - prevLat, sb);
//            encodeCoordinate(lon - prevLon, sb);
//            prevLat = lat;
//            prevLon = lon;
//        }
//        return sb.toString();
//    }
//
//    private static void encodeCoordinate(long delta, StringBuilder sb) {
//        long value = delta < 0 ? 2 * (-delta) - 1 : 2 * delta;
//        encodeValue(value, sb);
//    }
//
//    private static void encodeValue(long value, StringBuilder sb) {
//        while (value > 0) {
//            int chunk = (int) (value & 0x1F);
//            value >>= 5;
//            if (value > 0) {
//                chunk |= 0x20;
//            }
//            sb.append((char) (chunk + 63));
//        }
//    }
//}

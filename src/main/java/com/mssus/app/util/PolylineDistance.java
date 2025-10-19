package com.mssus.app.util;

import org.springframework.stereotype.Component;

import java.util.*;

public final class PolylineDistance {
    private static final double R_EARTH_M = 6371008.8;

    public static double metersFromEncodedPolyline(String encoded, int precision) {
        List<double[]> pts = decode(encoded, precision);
        return metersFromPoints(pts);
    }

    public static double metersFromPoints(List<double[]> latLngs) {
        if (latLngs == null || latLngs.size() < 2) return 0d;
        double total = 0d;
        double prevLat = latLngs.get(0)[0], prevLng = latLngs.get(0)[1];
        for (int i = 1; i < latLngs.size(); i++) {
            double lat = latLngs.get(i)[0], lng = latLngs.get(i)[1];
            total += haversineMeters(prevLat, prevLng, lat, lng);
            prevLat = lat;
            prevLng = lng;
        }
        return total;
    }

    // Google/OSRM Encoded Polyline decoder. precision=5 for polyline5, 6 for polyline6 (OSRM).
    public static List<double[]> decode(String encoded, int precision) {
        List<double[]> path = new ArrayList<>();
        int index = 0, lat = 0, lng = 0, shift, result, b;
        int factor = (int) Math.round(Math.pow(10, precision));
        while (index < encoded.length()) {
            // latitude
            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;
            // longitude
            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            path.add(new double[]{lat / (double) factor, lng / (double) factor});
        }
        return path;
    }

    public static long roundUpToNearestMeters(double meters, int step) {
        if (meters <= 0) return 0;
        long s = Math.max(1, step);
        long q = (long) Math.ceil(meters / s);
        return q * s;
    }

    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1), phi2 = Math.toRadians(lat2);
        double deltaPhi = phi2 - phi1, deltaLambda = Math.toRadians(lon2 - lon1);
        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) + Math.cos(phi1) * Math.cos(phi2) * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R_EARTH_M * c;
    }

}


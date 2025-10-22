package com.mssus.app.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mssus.app.common.enums.SharedRideStatus;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.response.RouteResponse;
import com.mssus.app.dto.response.ride.TrackingResponse;
import com.mssus.app.dto.ride.LatLng;
import com.mssus.app.dto.ride.LocationPoint;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.RideTrack;
import com.mssus.app.entity.SharedRide;
import com.mssus.app.entity.User;
import com.mssus.app.repository.DriverProfileRepository;
import com.mssus.app.repository.RideTrackRepository;
import com.mssus.app.repository.SharedRideRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.service.RealTimeNotificationService;
import com.mssus.app.service.RideTrackingService;
import com.mssus.app.service.RoutingService;
import com.mssus.app.util.PolylineDistance;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RideTrackingServiceImpl implements RideTrackingService {

    private final RideTrackRepository trackRepository;
    private final SharedRideRepository rideRepository;
    private final RoutingService routingService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final DriverProfileRepository driverRepository;
    private final RealTimeNotificationService notificationService;

    @Override
    @Transactional
    public TrackingResponse appendGpsPoints(Integer rideId, List<LocationPoint> points, String username) {
        // Fetch and validate ride
        SharedRide ride = rideRepository.findByIdForUpdate(rideId)
            .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource", rideId));

        User user = userRepository.findByEmail(username)
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));
        DriverProfile driver = driverRepository.findByUserUserId(user.getUserId())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.driver-profile"));

        if (!ride.getDriver().getDriverId().equals(driver.getDriverId())) {
            throw BaseDomainException.of("ride.unauthorized.not-owner");
        }

        if (ride.getStatus() != SharedRideStatus.ONGOING) {
            throw BaseDomainException.of("ride.validation.invalid-state", "Ride not ongoing");
        }

        // Validate points: Non-empty, sequential timestamps, no jumps >100km/h equiv
        if (points.isEmpty()) {
            throw BaseDomainException.of("tracking.invalid-points", "No points provided");
        }
        validatePoints(points);  // Custom method: Check speed, accuracy

        // Get or create track
        RideTrack track = trackRepository.findBySharedRideSharedRideId(rideId)
            .orElseGet(() -> {
                RideTrack newTrack = new RideTrack();
                newTrack.setSharedRide(ride);
                return newTrack;
            });

        // Append points to JSON (merge arrays)
        try {
            JsonNode existingPoints = track.getGpsPoints() != null ? track.getGpsPoints() : objectMapper.createArrayNode();
            ArrayNode newArray = objectMapper.createArrayNode();
            for (LocationPoint p : points) {
                ObjectNode pointNode = objectMapper.createObjectNode();
                pointNode.put("lat", p.lat());
                pointNode.put("lng", p.lng());
                pointNode.put("timestamp", p.timestamp().toString());
                newArray.add(pointNode);
            }
            // Merge: existing + new (avoid dups via timestamp check if needed)
            ((ArrayNode) existingPoints).addAll(newArray);
            track.setGpsPoints(existingPoints);
            track.setCreatedAt(LocalDateTime.now());  // Or lastUpdated
            trackRepository.save(track);

            log.debug("Appended {} points to track for ride {}", points.size(), rideId);

        } catch (Exception e) {
            log.error("Failed to append points for ride {}: {}", rideId, e.getMessage());
            throw BaseDomainException.of("tracking.append-failed", "Could not save GPS points");
        }

        // Compute partials for response (current dist from all points; ETA via routing)
        double currentDistanceKm = computeDistanceFromPoints(track.getGpsPoints());
//        int etaMinutes = computeEta(ride, track.getGpsPoints());  // Stub: Routing to end

        return new TrackingResponse(currentDistanceKm, "OK");
    }

    private void validatePoints(List<LocationPoint> points) {
        // Example: Check sequential, speed <200 km/h
        for (int i = 1; i < points.size(); i++) {
            double distKm = PolylineDistance.haversineMeters(
                points.get(i-1).lat(), points.get(i-1).lng(),
                points.get(i).lat(), points.get(i).lng());
            long timeDiffMin = java.time.Duration.between(points.get(i-1).timestamp(), points.get(i).timestamp()).toMinutes();
            if (timeDiffMin > 0 && (distKm / timeDiffMin) > 3.33) {  // >200 km/h
                throw BaseDomainException.of("tracking.invalid-speed", "Suspicious speed detected");
            }
        }

        // Ensure timestamps are recent (e.g., last <1min old)
        if (java.time.Duration.between(points.get(points.size()-1).timestamp(), LocalDateTime.now()).toMinutes() > 1) {
            log.warn("Potentially stale points: last point {} min old",
                java.time.Duration.between(points.get(points.size()-1).timestamp(), LocalDateTime.now()).toMinutes());
            // Not throwing error, just warning
        }
    }

    @Override
    public double computeDistanceFromPoints(JsonNode pointsNode) {
        if (pointsNode.size() < 2) return 0.0;
        double totalDist = 0.0;
        List<JsonNode> points = objectMapper.convertValue(pointsNode, new TypeReference<>() {
        });
        for (int i = 1; i < points.size(); i++) {
            double prevLat = points.get(i-1).get("lat").asDouble();
            double prevLng = points.get(i-1).get("lng").asDouble();
            double currLat = points.get(i).get("lat").asDouble();
            double currLng = points.get(i).get("lng").asDouble();
            totalDist += PolylineDistance.haversineMeters(prevLat, prevLng, currLat, currLng);
        }
        return totalDist;
    }

//    private int computeEta(SharedRide ride, JsonNode pointsNode) {
//        // Last point to end location, via routing (traffic-aware)
//        if (pointsNode.isEmpty()) return (int) (ride.getEstimatedDuration() * 0.8);  // Optimistic fallback
//        JsonNode lastPoint = pointsNode.get(pointsNode.size() - 1);
//        double lastLat = lastPoint.get("lat").asDouble();
//        double lastLng = lastPoint.get("lng").asDouble();
//        try {
//            RouteResponse etaRoute = routingService.getRoute(lastLat, lastLng, ride.getEndLat(), ride.getEndLng());
//            return (int) Math.ceil(etaRoute.time() / 60.0);
//        } catch (Exception e) {
//            log.warn("ETA routing failed for ride {}: {}", ride.getSharedRideId(), e.getMessage());
//            return ride.getEstimatedDuration();  // Fallback
//        }
//    }

    // Get latest position from track (with staleness check)
    @Override
    public Optional<LatLng> getLatestPosition(Integer rideId, int maxStaleMinutes) {
        RideTrack track = trackRepository.findBySharedRideSharedRideId(rideId)
            .orElse(null);
        if (track == null || track.getGpsPoints() == null || track.getGpsPoints().isEmpty()) {
            return Optional.empty();
        }

        // Get last point
        JsonNode lastPoint = track.getGpsPoints().get(track.getGpsPoints().size() - 1);
        double lat = lastPoint.get("lat").asDouble();
        double lng = lastPoint.get("lng").asDouble();
        LocalDateTime timestamp = LocalDateTime.parse(lastPoint.get("timestamp").asText());

        // Staleness check
        if (Duration.between(timestamp, LocalDateTime.now()).toMinutes() > maxStaleMinutes) {
            log.warn("Stale position for ride {}: {} min old", rideId,
                Duration.between(timestamp, LocalDateTime.now()).toMinutes());
            return Optional.empty();  // Caller falls back
        }

        log.debug("Latest pos for ride {}: ({}, {}) at {}", rideId, lat, lng, timestamp);
        return Optional.of(new LatLng(lat, lng));
    }

    @Override
    public void startTracking(Integer rideId) {
        SharedRide ride = rideRepository.findById(rideId)
            .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource", rideId));

        RideTrack track = trackRepository.findBySharedRideSharedRideId(rideId)
            .orElseGet(() -> {
                RideTrack newTrack = new RideTrack();
                newTrack.setSharedRide(ride);
                newTrack.setGpsPoints(objectMapper.createArrayNode());
                newTrack.setCreatedAt(LocalDateTime.now());
                return trackRepository.save(newTrack);
            });

        notificationService.notifyDriverTrackingStart(ride.getDriver(), rideId);

        log.info("Tracking initiated for ride {} - driver notified to start GPS updates", rideId);
    }

    @Override
    public void stopTracking(Integer rideId) {
        if (rideId == null) {
            throw BaseDomainException.of("ride.tracking.invalid-ride-id");
        }

        RideTrack track = trackRepository.findBySharedRideSharedRideId(rideId)
            .orElse(null);

        if (track == null) {
            log.debug("stopTracking called for ride {} but no tracking record exists", rideId);
            return;
        }

        if (Boolean.TRUE.equals(track.getIsTracking())) {
            log.debug("stopTracking called for ride {} but tracking already stopped", rideId);
            return;
        }

        track.setIsTracking(false);
        track.setStoppedAt(LocalDateTime.now());
        trackRepository.save(track);

        log.info("Stopped tracking for ride {}", rideId);
    }


}

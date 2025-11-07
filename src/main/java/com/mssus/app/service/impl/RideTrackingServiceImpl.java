package com.mssus.app.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mssus.app.common.enums.SharedRideStatus;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.response.ride.TrackingResponse;
import com.mssus.app.dto.domain.ride.LatLng;
import com.mssus.app.dto.domain.ride.LocationPoint;
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
import com.mssus.app.common.util.GeoUtil;
import com.mssus.app.dto.domain.ride.RealTimeTrackingUpdateDto;
import com.mssus.app.messaging.RideEventPublisher;
import com.mssus.app.messaging.dto.DriverLocationUpdateMessage;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RideTrackingServiceImpl implements RideTrackingService {

    private final RideTrackRepository trackRepository;
    private final SharedRideRepository rideRepository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final DriverProfileRepository driverRepository;
    private final RealTimeNotificationService notificationService;
    private final RideEventPublisher rideEventPublisher;

    private final SimpMessagingTemplate messagingTemplate;
    @Override
    @Transactional
    public TrackingResponse appendGpsPoints(Integer rideId, List<LocationPoint> points, String username) {
        // Fetch and validate ride
        SharedRide ride = rideRepository.findByIdForUpdate(rideId)
            .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource", rideId));
        User user = userRepository.findById(Integer.parseInt(username))
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
        publishDriverLocationUpdate(rideId, driver, points);

        // Get or create track
        RideTrack track = trackRepository.findBySharedRideSharedRideId(rideId)
            .orElseGet(() -> {
                RideTrack newTrack = new RideTrack();
                newTrack.setSharedRide(ride);
                newTrack.setIsTracking(true);
                return newTrack;
            });

        try {
            ArrayNode existingPoints = track.getGpsPoints() != null && track.getGpsPoints().isArray()
                                       ? (ArrayNode) track.getGpsPoints()
                                       : JsonNodeFactory.instance.arrayNode();

            ArrayNode newArray = objectMapper.createArrayNode();
            for (LocationPoint p : points) {
                ObjectNode pointNode = objectMapper.createObjectNode();
                pointNode.put("lat", p.lat());
                pointNode.put("lng", p.lng());
                pointNode.put("timestamp", p.timestamp().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)); // Use ISO_OFFSET_DATE_TIME for ZonedDateTime
                newArray.add(pointNode);
            }
            existingPoints.addAll(newArray);
            track.setGpsPoints(existingPoints);
            track.setCreatedAt(LocalDateTime.now());
            trackRepository.save(track);

            log.debug("Appended {} points to track for ride {}", points.size(), rideId);

        } catch (Exception e) {
            log.error("Failed to append points for ride {}: {}", rideId, e.getMessage());
            throw BaseDomainException.of("tracking.append-failed", "Could not save GPS points");
        }

        publishRealTimeTrackingUpdate(rideId);

        double currentDistanceKm = computeDistanceFromPoints(track.getGpsPoints());
        String polyline = generatePolylineFromPoints(rideId);

        return new TrackingResponse(currentDistanceKm, polyline, "OK");
    }

    private void validatePoints(List<LocationPoint> points) {
        // Example: Check sequential, speed <200 km/h
        for (int i = 1; i < points.size(); i++) {
            double distMeters = GeoUtil.haversineMeters(
                points.get(i-1).lat(), points.get(i-1).lng(),
                points.get(i).lat(), points.get(i).lng());
            double distKm = distMeters / 1000.0;
            long timeDiffSeconds = Duration.between(points.get(i-1).timestamp(), points.get(i).timestamp()).getSeconds();
            if (timeDiffSeconds > 0) {
                double speedKph = (distKm / timeDiffSeconds) * 3600; // km/h
                if (speedKph > 200) {
                    throw BaseDomainException.of("tracking.invalid-speed", "Suspicious speed detected: " + Math.round(speedKph) + " km/h");
                }
            }
        }

        // Ensure timestamps are recent (e.g., last <1min old)
        if (Duration.between(points.get(points.size() - 1).timestamp(), ZonedDateTime.now()).toMinutes() > 1) {
            log.warn("Potentially stale points: last point {} min old",
                Duration.between(points.get(points.size() - 1).timestamp().toInstant(), Instant.now()).toMinutes());
            // Not throwing error, just warning. Convert ZonedDateTime to Instant for Duration.between
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
            totalDist += GeoUtil.haversineMeters(prevLat, prevLng, currLat, currLng);
        }
        return totalDist / 1000.0;
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
        ZonedDateTime timestamp = ZonedDateTime.parse(lastPoint.get("timestamp").asText(), DateTimeFormatter.ISO_ZONED_DATE_TIME);

        // Staleness check
        if (Duration.between(timestamp, ZonedDateTime.now()).toMinutes() > maxStaleMinutes) {
            log.warn("Stale position for ride {}: {} min old", rideId, 
                Duration.between(timestamp, ZonedDateTime.now()).toMinutes());
            return Optional.empty();
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
                newTrack.setIsTracking(true);
                return trackRepository.save(newTrack);
            });

        notificationService.notifyDriverTrackingStart(ride.getDriver(), rideId);

        log.info("Tracking initiated for ride {} - driver notified to start GPS updates", rideId);
    }

    @Override
    public void stopTracking(Integer rideId) {
        if (rideId == null) {
            log.warn("stopTracking called with a null rideId.");
            return;
        }

        RideTrack track = trackRepository.findBySharedRideSharedRideId(rideId)
            .orElse(null);

        if (track == null) {
            log.warn("stopTracking called for ride {} but no tracking record exists. Ignoring.", rideId);
            return;
        }

        track.setIsTracking(false); 
        track.setStoppedAt(LocalDateTime.now());
        trackRepository.save(track);

        log.info("Stopped tracking for ride {}", rideId);
    }

    private String generatePolylineFromPoints(Integer rideId) {
        RideTrack track = trackRepository.findBySharedRideSharedRideId(rideId)
            .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource", rideId));

        if (track.getGpsPoints() == null || track.getGpsPoints().isEmpty()) {
            log.debug("No points for polyline on ride {}", rideId);
            return "";
        }

        List<double[]> latLngs = new ArrayList<>();
        for (JsonNode point : track.getGpsPoints()) {
            double lat = point.get("lat").asDouble();
            double lng = point.get("lng").asDouble();
            latLngs.add(new double[]{lat, lng});
        }

        if (latLngs.size() < 2) {
            log.debug("Insufficient points for polyline on ride {}: {}", rideId, latLngs.size());
            return "";
        }

        String polyline = GeoUtil.encodePolyline(latLngs, 5);
        log.debug("Generated polyline for ride {}: {} chars, {} points", rideId, polyline.length(), latLngs.size());

        return polyline;
    }

    private void publishRealTimeTrackingUpdate(Integer rideId) {
        try {
            RideTrack track = trackRepository.findBySharedRideSharedRideId(rideId)
                .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource", rideId));

            String polyline = generatePolylineFromPoints(rideId);
            Optional<LatLng> latestPos = getLatestPosition(rideId, 3); // Get latest position, max 1 min stale
            double currentDistanceKm = computeDistanceFromPoints(track.getGpsPoints());

            RealTimeTrackingUpdateDto updateDto = RealTimeTrackingUpdateDto.builder()
                .rideId(rideId)
                .polyline(polyline)
                .currentLat(latestPos.map(LatLng::latitude).orElse(null))
                .currentLng(latestPos.map(LatLng::longitude).orElse(null))
                .currentDistanceKm(currentDistanceKm)
                .build();

            messagingTemplate.convertAndSend("/topic/ride.tracking." + rideId, updateDto);
            log.debug("Published real-time tracking update for ride {}", rideId);
        } catch (Exception e) {
            log.error("Failed to publish real-time tracking update for ride {}: {}", rideId, e.getMessage(), e);
        }
    }

    private void publishDriverLocationUpdate(Integer rideId,
                                             DriverProfile driver,
                                             List<LocationPoint> points) {
        if (rideEventPublisher == null || driver == null || points == null || points.isEmpty()) {
            return;
        }
        try {
            // Use the latest location point from the list
            LocationPoint latest = points.get(points.size() - 1);
            DriverLocationUpdateMessage message = DriverLocationUpdateMessage.builder()
                .driverId(driver.getDriverId())
                .latitude(latest.lat())
                .longitude(latest.lng())
                .rideId(rideId)
                .timestamp(latest.timestamp() != null ? 
                    latest.timestamp().toInstant() : java.time.Instant.now())
                .correlationId(java.util.UUID.randomUUID().toString())
                .build();
            rideEventPublisher.publishDriverLocationUpdate(message);
        } catch (Exception ex) {
            log.warn("Failed to publish driver location update for ride {}: {}", rideId, ex.getMessage(), ex);
        }
    }

}

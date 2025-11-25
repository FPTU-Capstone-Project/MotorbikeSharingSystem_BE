package com.mssus.app.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mssus.app.common.enums.SharedRideRequestStatus;
import com.mssus.app.common.enums.SharedRideStatus;
import com.mssus.app.common.enums.UserType;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.response.RouteResponse;
import com.mssus.app.dto.response.ride.RideTrackingSnapshotResponse;
import com.mssus.app.dto.response.ride.TrackingResponse;
import com.mssus.app.dto.domain.ride.LatLng;
import com.mssus.app.dto.domain.ride.LocationPoint;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.Location;
import com.mssus.app.entity.RideTrack;
import com.mssus.app.entity.SharedRide;
import com.mssus.app.entity.SharedRideRequest;
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
import org.springframework.security.core.Authentication;
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
    private final RoutingService routingService;

    private final SimpMessagingTemplate messagingTemplate;
    private static final double ROUTE_DEVIATION_THRESHOLD_METERS = 120d;

    @Override
    @Transactional
    public TrackingResponse appendGpsPoints(Integer rideId, List<LocationPoint> points, String username,
            String activeProfile) {
        if (points == null || points.isEmpty()) {
            throw BaseDomainException.of("tracking.invalid-points", "No points provided");
        }

        // Fetch ride and user
        SharedRide ride = rideRepository.findByIdForUpdate(rideId)
                .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource", rideId));
        User user = userRepository.findById(Integer.parseInt(username))
                .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));

        boolean isDriverProfile = "DRIVER".equalsIgnoreCase(activeProfile);
        boolean isRiderProfile = "RIDER".equalsIgnoreCase(activeProfile);
        boolean isAdminProfile = UserType.ADMIN.equals(user.getUserType());

        // Fallback: infer role if active_profile missing
        if (!isDriverProfile && !isRiderProfile) {
            if (ride.getDriver() != null && ride.getDriver().getUser() != null
                    && ride.getDriver().getUser().getUserId().equals(user.getUserId())) {
                isDriverProfile = true;
            } else if (ride.getSharedRideRequest() != null
                    && ride.getSharedRideRequest().getRider() != null
                    && ride.getSharedRideRequest().getRider().getUser() != null
                    && ride.getSharedRideRequest().getRider().getUser().getUserId().equals(user.getUserId())) {
                isRiderProfile = true;
            }
        }

        // Admins are observers – they shouldn't append points but can request a live
        // snapshot
        if (isAdminProfile) {
            RideTrack track = trackRepository.findBySharedRideSharedRideId(rideId).orElse(null);
            LatLng driverPos = getLatestDriverPosition(rideId, 5).orElse(null);
            LatLng riderPos = getLatestRiderPosition(rideId, 5).orElse(null);
            TrackingPolylineResult polylineResult = determinePolyline(
                    ride,
                    ride.getSharedRideRequest(),
                    driverPos);
            double currentDistanceKm = track != null && track.getGpsPoints() != null
                    ? computeDistanceFromPoints(track.getGpsPoints())
                    : 0.0;

            return new TrackingResponse(
                    currentDistanceKm,
                    polylineResult.polyline(),
                    "OK",
                    driverPos != null ? driverPos.latitude() : null,
                    driverPos != null ? driverPos.longitude() : null,
                    polylineResult.detoured());
        }

        if (isDriverProfile) {
            return handleDriverPoints(ride, user, points);
        } else if (isRiderProfile) {
            return handleRiderPoint(ride, user, points.get(points.size() - 1));
        } else {
            throw BaseDomainException.of("ride.unauthorized.not-owner", "Unknown or missing active profile");
        }
    }

    private void validatePoints(List<LocationPoint> points) {
        // Example: Check sequential, speed <200 km/h
        for (int i = 1; i < points.size(); i++) {
            double distMeters = GeoUtil.haversineMeters(
                    points.get(i - 1).lat(), points.get(i - 1).lng(),
                    points.get(i).lat(), points.get(i).lng());
            double distKm = distMeters / 1000.0;
            long timeDiffSeconds = Duration.between(points.get(i - 1).timestamp(), points.get(i).timestamp())
                    .getSeconds();
            if (timeDiffSeconds > 0) {
                double speedKph = (distKm / timeDiffSeconds) * 3600; // km/h
                if (speedKph > 200) {
                    throw BaseDomainException.of("tracking.invalid-speed",
                            "Suspicious speed detected: " + Math.round(speedKph) + " km/h");
                }
            }
        }

        // Ensure timestamps are recent (e.g., last <1min old)
        if (Duration.between(points.get(points.size() - 1).timestamp(), ZonedDateTime.now()).toMinutes() > 1) {
            log.warn("Potentially stale points: last point {} min old",
                    Duration.between(points.get(points.size() - 1).timestamp().toInstant(), Instant.now()).toMinutes());
            // Not throwing error, just warning. Convert ZonedDateTime to Instant for
            // Duration.between
        }
    }

    @Override
    public double computeDistanceFromPoints(JsonNode pointsNode) {
        if (pointsNode.size() < 2)
            return 0.0;
        double totalDist = 0.0;
        List<JsonNode> points = objectMapper.convertValue(pointsNode, new TypeReference<>() {
        });
        for (int i = 1; i < points.size(); i++) {
            double prevLat = points.get(i - 1).get("lat").asDouble();
            double prevLng = points.get(i - 1).get("lng").asDouble();
            double currLat = points.get(i).get("lat").asDouble();
            double currLng = points.get(i).get("lng").asDouble();
            totalDist += GeoUtil.haversineMeters(prevLat, prevLng, currLat, currLng);
        }
        return totalDist / 1000.0;
    }

    // private int computeEta(SharedRide ride, JsonNode pointsNode) {
    // // Last point to end location, via routing (traffic-aware)
    // if (pointsNode.isEmpty()) return (int) (ride.getEstimatedDuration() * 0.8);
    // // Optimistic fallback
    // JsonNode lastPoint = pointsNode.get(pointsNode.size() - 1);
    // double lastLat = lastPoint.get("lat").asDouble();
    // double lastLng = lastPoint.get("lng").asDouble();
    // try {
    // RouteResponse etaRoute = routingService.getRoute(lastLat, lastLng,
    // ride.getEndLat(), ride.getEndLng());
    // return (int) Math.ceil(etaRoute.time() / 60.0);
    // } catch (Exception e) {
    // log.warn("ETA routing failed for ride {}: {}", ride.getSharedRideId(),
    // e.getMessage());
    // return ride.getEstimatedDuration(); // Fallback
    // }
    // }

    // Get latest position from track (with staleness check)
    @Override
    public Optional<LatLng> getLatestPosition(Integer rideId, int maxStaleMinutes) {
        return getLatestDriverPosition(rideId, maxStaleMinutes);
    }

    private Optional<LatLng> getLatestDriverPosition(Integer rideId, int maxStaleMinutes) {
        RideTrack track = trackRepository.findBySharedRideSharedRideId(rideId)
                .orElse(null);
        if (track == null || track.getGpsPoints() == null || track.getGpsPoints().isEmpty()) {
            return Optional.empty();
        }

        // Get last point
        JsonNode lastPoint = track.getGpsPoints().get(track.getGpsPoints().size() - 1);
        double lat = lastPoint.get("lat").asDouble();
        double lng = lastPoint.get("lng").asDouble();
        ZonedDateTime timestamp = ZonedDateTime.parse(lastPoint.get("timestamp").asText(),
                DateTimeFormatter.ISO_ZONED_DATE_TIME);

        // Staleness check
        if (Duration.between(timestamp, ZonedDateTime.now()).toMinutes() > maxStaleMinutes) {
            log.warn("Stale position for ride {}: {} min old", rideId,
                    Duration.between(timestamp, ZonedDateTime.now()).toMinutes());
            return Optional.empty();
        }

        log.debug("Latest pos for ride {}: ({}, {}) at {}", rideId, lat, lng, timestamp);
        return Optional.of(new LatLng(lat, lng));
    }

    private Optional<LatLng> getLatestRiderPosition(Integer rideId, int maxStaleMinutes) {
        RideTrack track = trackRepository.findBySharedRideSharedRideId(rideId)
                .orElse(null);
        if (track == null || track.getRiderLat() == null || track.getRiderLng() == null
                || track.getRiderTimestamp() == null) {
            return Optional.empty();
        }

        if (Duration.between(track.getRiderTimestamp(), LocalDateTime.now()).toMinutes() > maxStaleMinutes) {
            log.warn("Stale rider position for ride {}: {} min old", rideId,
                    Duration.between(track.getRiderTimestamp(), LocalDateTime.now()).toMinutes());
            return Optional.empty();
        }
        return Optional.of(new LatLng(track.getRiderLat(), track.getRiderLng()));
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

    @Override
    @Transactional
    public RideTrackingSnapshotResponse getTrackingSnapshot(Integer rideId, Authentication authentication) {
        if (authentication == null) {
            throw BaseDomainException.of("ride.unauthorized.not-owner");
        }

        SharedRide ride = rideRepository.findById(rideId)
                .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource", rideId));

        SharedRideRequest request = ride.getSharedRideRequest();

        User currentUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));

        boolean isDriver = ride.getDriver() != null
                && ride.getDriver().getUser() != null
                && ride.getDriver().getUser().getUserId().equals(currentUser.getUserId());
        boolean isRider = request != null
                && request.getRider() != null
                && request.getRider().getUser() != null
                && request.getRider().getUser().getUserId().equals(currentUser.getUserId());
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(auth -> "ROLE_ADMIN".equals(auth.getAuthority()));

        if (!isDriver && !isRider && !isAdmin) {
            throw BaseDomainException.of("ride.unauthorized.not-owner");
        }

        boolean canSeeDriver = isDriver || isAdmin;
        boolean canSeeRider = isRider || isAdmin;

        LatLng driverPos = canSeeDriver ? getLatestDriverPosition(rideId, 5).orElse(null) : null;
        LatLng riderPos = canSeeRider ? getLatestRiderPosition(rideId, 5).orElse(null) : null;
        TrackingPolylineResult polylineResult = determinePolyline(ride, request, driverPos);

        return RideTrackingSnapshotResponse.builder()
                .rideId(rideId)
                .driverLat(driverPos != null ? driverPos.latitude() : null)
                .driverLng(driverPos != null ? driverPos.longitude() : null)
                .riderLat(riderPos != null ? riderPos.latitude() : null)
                .riderLng(riderPos != null ? riderPos.longitude() : null)
                .requestStatus(request != null && request.getStatus() != null ? request.getStatus().name() : null)
                .rideStatus(ride.getStatus() != null ? ride.getStatus().name() : null)
                .polyline(polylineResult.polyline())
                .detoured(polylineResult.detoured())
                .estimatedArrival(resolveEta(request))
                .build();
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
            latLngs.add(new double[] { lat, lng });
        }

        if (latLngs.size() < 2) {
            log.debug("Insufficient points for polyline on ride {}: {}", rideId, latLngs.size());
            return "";
        }

        String polyline = GeoUtil.encodePolyline(latLngs, 5);
        log.debug("Generated polyline for ride {}: {} chars, {} points", rideId, polyline.length(), latLngs.size());

        return polyline;
    }

    private void publishRealTimeTrackingUpdate(Integer rideId,
            TrackingPolylineResult override,
            LatLng riderPos,
            String source) {
        try {
            RideTrack track = trackRepository.findBySharedRideSharedRideId(rideId)
                    .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource", rideId));

            Optional<LatLng> latestPos = getLatestDriverPosition(rideId, 3); // driver pos
            double currentDistanceKm = computeDistanceFromPoints(track.getGpsPoints());

            RealTimeTrackingUpdateDto updateDto = RealTimeTrackingUpdateDto.builder()
                    .rideId(rideId)
                    .polyline(override.polyline())
                    .currentLat(latestPos.map(LatLng::latitude).orElse(null))
                    .currentLng(latestPos.map(LatLng::longitude).orElse(null))
                    .riderLat(riderPos != null ? riderPos.latitude() : null)
                    .riderLng(riderPos != null ? riderPos.longitude() : null)
                    .currentDistanceKm(currentDistanceKm)
                    .detoured(override.detoured())
                    .source(source)
                    .build();

            messagingTemplate.convertAndSend("/topic/ride.tracking." + rideId, updateDto);
            log.debug("Published real-time tracking update for ride {}", rideId);
        } catch (Exception e) {
            log.error("Failed to publish real-time tracking update for ride {}: {}", rideId, e.getMessage(), e);
        }
    }

    private TrackingPolylineResult determinePolyline(SharedRide ride,
            SharedRideRequest request,
            LatLng latestPosition) {
        SharedRideRequestStatus status = request != null ? request.getStatus() : null;
        boolean approachingPickup = status == SharedRideRequestStatus.CONFIRMED;
        boolean inTransit = status == SharedRideRequestStatus.ONGOING;
        Location target = null;
        String baseline = null;

        if (approachingPickup && request != null) {
            baseline = ride.getDriverApproachPolyline();
            target = request.getPickupLocation();
        } else if (inTransit && request != null) {
            baseline = ride.getRoute() != null ? ride.getRoute().getPolyline() : null;
            target = request.getDropoffLocation();
        } else if (ride.getRoute() != null) {
            baseline = ride.getRoute().getPolyline();
            target = request != null ? request.getDropoffLocation() : ride.getEndLocation();
        } else if (ride.getRoute() == null && ride.getStartLocation() != null && ride.getEndLocation() != null) {
            // As a last resort, recompute a path between start and end if we have positions
            if (latestPosition != null) {
                RouteResponse recomputed = routingService.getRoute(
                        latestPosition.latitude(),
                        latestPosition.longitude(),
                        ride.getEndLocation().getLat(),
                        ride.getEndLocation().getLng());
                return new TrackingPolylineResult(recomputed.polyline(), false);
            }
        }

        if (baseline != null && latestPosition != null) {
            double deviation = distanceFromPointToPolylineMeters(latestPosition, baseline);
            if (deviation > ROUTE_DEVIATION_THRESHOLD_METERS && target != null) {
                RouteResponse recomputed = routingService.getRoute(
                        latestPosition.latitude(),
                        latestPosition.longitude(),
                        target.getLat(),
                        target.getLng());
                return new TrackingPolylineResult(recomputed.polyline(), true);
            }
        }

        if ((baseline == null || baseline.isBlank()) && latestPosition != null && target != null) {
            RouteResponse recomputed = routingService.getRoute(
                    latestPosition.latitude(),
                    latestPosition.longitude(),
                    target.getLat(),
                    target.getLng());
            return new TrackingPolylineResult(recomputed.polyline(), false);
        }

        return new TrackingPolylineResult(baseline, false);
    }

    private LocalDateTime resolveEta(SharedRideRequest request) {
        if (request == null || request.getStatus() == null) {
            return null;
        }
        if (request.getStatus() == SharedRideRequestStatus.CONFIRMED) {
            return request.getEstimatedPickupTime();
        }
        if (request.getStatus() == SharedRideRequestStatus.ONGOING) {
            return request.getEstimatedDropoffTime();
        }
        return null;
    }

    private double distanceFromPointToPolylineMeters(LatLng point, String encodedPolyline) {
        if (point == null || encodedPolyline == null || encodedPolyline.isBlank()) {
            return Double.MAX_VALUE;
        }
        List<double[]> coords = GeoUtil.decode(encodedPolyline, 5);
        if (coords.size() < 2) {
            return Double.MAX_VALUE;
        }
        double minDistance = Double.MAX_VALUE;
        for (int i = 0; i < coords.size() - 1; i++) {
            double[] start = coords.get(i);
            double[] end = coords.get(i + 1);
            double segmentDistance = distancePointToSegmentMeters(
                    point.latitude(),
                    point.longitude(),
                    start[0],
                    start[1],
                    end[0],
                    end[1]);
            minDistance = Math.min(minDistance, segmentDistance);
        }
        return minDistance;
    }

    private double distancePointToSegmentMeters(double lat, double lng,
            double lat1, double lng1,
            double lat2, double lng2) {
        double refLat = Math.toRadians((lat1 + lat2) / 2.0);
        double metersPerDegLat = 111132.92;
        double metersPerDegLon = 111412.84 * Math.cos(refLat);

        double x = (lng - lng1) * metersPerDegLon;
        double y = (lat - lat1) * metersPerDegLat;
        double x2 = (lng2 - lng1) * metersPerDegLon;
        double y2 = (lat2 - lat1) * metersPerDegLat;

        double segmentLengthSq = x2 * x2 + y2 * y2;
        if (segmentLengthSq == 0) {
            return Math.hypot(x, y);
        }

        double projection = ((x * x2) + (y * y2)) / segmentLengthSq;
        double clamped = Math.max(0, Math.min(1, projection));
        double projX = clamped * x2;
        double projY = clamped * y2;

        double dx = x - projX;
        double dy = y - projY;
        return Math.hypot(dx, dy);
    }

    private record TrackingPolylineResult(String polyline, boolean detoured) {
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
                    .timestamp(latest.timestamp() != null ? latest.timestamp().toInstant() : java.time.Instant.now())
                    .correlationId(java.util.UUID.randomUUID().toString())
                    .build();
            rideEventPublisher.publishDriverLocationUpdate(message);
        } catch (Exception ex) {
            log.warn("Failed to publish driver location update for ride {}: {}", rideId, ex.getMessage(), ex);
        }
    }

    private TrackingResponse handleDriverPoints(SharedRide ride, User user, List<LocationPoint> points) {
        DriverProfile driver = driverRepository.findByUserUserId(user.getUserId())
                .orElseThrow(() -> BaseDomainException.of("user.not-found.driver-profile"));

        if (!ride.getDriver().getDriverId().equals(driver.getDriverId())) {
            throw BaseDomainException.of("ride.unauthorized.not-owner");
        }

        if (ride.getStatus() != SharedRideStatus.ONGOING) {
            throw BaseDomainException.of("ride.validation.invalid-state", "Ride not ongoing");
        }

        publishDriverLocationUpdate(ride.getSharedRideId(), driver, points);

        RideTrack track = trackRepository.findBySharedRideSharedRideId(ride.getSharedRideId())
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
                pointNode.put("timestamp", p.timestamp().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                newArray.add(pointNode);
            }
            existingPoints.addAll(newArray);
            track.setGpsPoints(existingPoints);
            track.setCreatedAt(LocalDateTime.now());
            trackRepository.save(track);

            log.debug("Appended {} driver points to track for ride {}", points.size(), ride.getSharedRideId());

        } catch (Exception e) {
            log.error("Failed to append points for ride {}: {}", ride.getSharedRideId(), e.getMessage());
            throw BaseDomainException.of("tracking.append-failed", "Could not save GPS points");
        }

        TrackingPolylineResult result = determinePolyline(
                ride,
                ride.getSharedRideRequest(),
                getLatestDriverPosition(ride.getSharedRideId(), 5).orElse(null));

        LatLng riderPos = getLatestRiderPosition(ride.getSharedRideId(), 5).orElse(null);
        publishRealTimeTrackingUpdate(ride.getSharedRideId(), result, riderPos, "DRIVER");

        double currentDistanceKm = computeDistanceFromPoints(track.getGpsPoints());
        LatLng latest = getLatestDriverPosition(ride.getSharedRideId(), 5).orElse(null);

        return new TrackingResponse(
                currentDistanceKm,
                result.polyline(),
                "OK",
                latest != null ? latest.latitude() : null,
                latest != null ? latest.longitude() : null,
                result.detoured());
    }

    private TrackingResponse handleRiderPoint(SharedRide ride, User user, LocationPoint point) {
        SharedRideRequest request = ride.getSharedRideRequest();
        if (request == null || request.getRider() == null || request.getRider().getUser() == null) {
            throw BaseDomainException.of("ride.validation.request-invalid-state", "Ride is not linked to a rider");
        }
        if (!request.getRider().getUser().getUserId().equals(user.getUserId())) {
            throw BaseDomainException.of("ride.unauthorized.not-owner");
        }

        if (ride.getStatus() != SharedRideStatus.ONGOING) {
            throw BaseDomainException.of("ride.validation.invalid-state", "Ride not in tracking state");
        }

        RideTrack track = trackRepository.findBySharedRideSharedRideId(ride.getSharedRideId())
                .orElseGet(() -> {
                    RideTrack newTrack = new RideTrack();
                    newTrack.setSharedRide(ride);
                    newTrack.setIsTracking(true);
                    return newTrack;
                });

        track.setRiderLat(point.lat());
        track.setRiderLng(point.lng());
        track.setRiderTimestamp(point.timestamp() != null
                ? point.timestamp().toLocalDateTime()
                : LocalDateTime.now());
        trackRepository.save(track);

        TrackingPolylineResult result = determinePolyline(
                ride,
                ride.getSharedRideRequest(),
                getLatestDriverPosition(ride.getSharedRideId(), 5).orElse(null));

        LatLng riderPos = new LatLng(point.lat(), point.lng());
        publishRealTimeTrackingUpdate(ride.getSharedRideId(), result, riderPos, "RIDER");

        double currentDistanceKm = track.getGpsPoints() != null ? computeDistanceFromPoints(track.getGpsPoints()) : 0.0;
        LatLng latest = getLatestDriverPosition(ride.getSharedRideId(), 5).orElse(null);

        return new TrackingResponse(
                currentDistanceKm,
                result.polyline(),
                "OK",
                latest != null ? latest.latitude() : null,
                latest != null ? latest.longitude() : null,
                result.detoured());
    }
}

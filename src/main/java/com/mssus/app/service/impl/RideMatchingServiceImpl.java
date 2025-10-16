package com.mssus.app.service.impl;

import com.mssus.app.common.enums.SharedRideStatus;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.config.properties.RideConfigurationProperties;
import com.mssus.app.dto.ride.LatLng;
import com.mssus.app.dto.response.RouteResponse;
import com.mssus.app.dto.response.ride.RideMatchProposalResponse;
import com.mssus.app.entity.Location;
import com.mssus.app.entity.SharedRide;
import com.mssus.app.entity.SharedRideRequest;
import com.mssus.app.repository.LocationRepository;
import com.mssus.app.repository.SharedRideRepository;
import com.mssus.app.service.RideMatchingService;
import com.mssus.app.service.RideTrackingService;
import com.mssus.app.service.RoutingService;
import com.mssus.app.util.PolylineDistance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RideMatchingServiceImpl implements RideMatchingService {

    private final SharedRideRepository rideRepository;
    private final LocationRepository locationRepository;
    private final RideConfigurationProperties rideConfig;
    private final RoutingService routingService;
    private final RideTrackingService rideTrackingService;

    @Override
    @Transactional(readOnly = true)
    public List<RideMatchProposalResponse> findMatches(SharedRideRequest request) {
        log.info("Finding matches for request ID: {}", request.getSharedRideRequestId());

        // Step 1: Extract and validate locations
        LocationPair requestLocations = extractRequestLocations(request);

        // Step 2: Find candidate rides within time window
        List<SharedRide> candidateRides = findCandidateRides(request.getPickupTime());

        if (candidateRides.isEmpty()) {
            log.info("No candidate rides found for matching");
            return List.of();
        }

        // Step 3: Score and filter candidates
        List<RideMatchProposalResponse> proposals = evaluateCandidates(
            candidateRides, requestLocations, request);

        // Step 4: Sort and limit results
        return selectTopProposals(proposals);
    }

    private LocationPair extractRequestLocations(SharedRideRequest request) {
        Location pickup = resolveLocation(
            request.getPickupLocationId(),
            request.getPickupLat(),
            request.getPickupLng(),
            "Pickup Location"
        );

        Location dropoff = resolveLocation(
            request.getDropoffLocationId(),
            request.getDropoffLat(),
            request.getDropoffLng(),
            "Dropoff Location"
        );

        return new LocationPair(pickup, dropoff);
    }

    private Location resolveLocation(Integer locationId, Double lat, Double lng, String defaultName) {
        if (locationId != null) {
            return locationRepository.findById(locationId)
                .orElseThrow(() -> BaseDomainException.formatted(
                    "ride.validation.invalid-location",
                    defaultName + " not found: " + locationId));
        }

        if (lat != null && lng != null) {
            Location loc = new Location();
            loc.setLat(lat);
            loc.setLng(lng);
            loc.setName(defaultName);
            return loc;
        }

        throw BaseDomainException.formatted(
            "ride.validation.invalid-location",
            "Neither location ID nor coordinates provided for " + defaultName);
    }

    private List<SharedRide> findCandidateRides(LocalDateTime requestTime) {
        int timeWindowMinutes = rideConfig.getMatching().getTimeWindowMinutes();
        LocalDateTime startTime = requestTime.minusMinutes(timeWindowMinutes);
        LocalDateTime endTime = requestTime.plusMinutes(timeWindowMinutes);

        log.debug("Time window: {} to {}", startTime, endTime);

        List<SharedRide> candidates = rideRepository.findCandidateRidesForMatching(startTime, endTime);
        log.info("Found {} candidate rides in time window", candidates.size());

        return candidates;
    }

    private List<RideMatchProposalResponse> evaluateCandidates(
        List<SharedRide> candidateRides,
        LocationPair requestLocations,
        SharedRideRequest request) {

        List<RideMatchProposalResponse> proposals = new ArrayList<>();
        MatchingMetrics metrics = new MatchingMetrics();

        for (SharedRide ride : candidateRides) {
            metrics.incrementProcessed();

            try {
                evaluateSingleCandidate(ride, requestLocations, request, proposals, metrics);
            } catch (Exception e) {
                metrics.incrementErrors();
                log.error("Error evaluating ride {}: {}", ride.getSharedRideId(), e.getMessage(), e);
            }
        }

        logMatchingMetrics(metrics, proposals.size());
        return proposals;
    }

    private void evaluateSingleCandidate(
        SharedRide ride,
        LocationPair requestLocations,
        SharedRideRequest request,
        List<RideMatchProposalResponse> proposals,
        MatchingMetrics metrics) {

        log.debug("Evaluating ride ID: {}", ride.getSharedRideId());

        // Extract ride locations
        LocationPair rideLocations = extractRideLocations(ride);

        // Check proximity
        ProximityCheck proximityCheck = checkProximity(requestLocations, rideLocations);
        if (!proximityCheck.isValid()) {
            metrics.incrementRejectedProximity();
            log.debug("Ride {} rejected - proximity threshold exceeded", ride.getSharedRideId());
            return;
        }

        // Calculate detour
        DetourCalculation detour = calculateDetour(ride, rideLocations, requestLocations, request.getPickupTime());

        // Check driver's detour preference
        if (!isDetourAcceptable(ride, detour.durationMinutes())) {
            metrics.incrementRejectedDetour();
            log.debug("Ride {} rejected - exceeds driver's max detour", ride.getSharedRideId());
            return;
        }

        // Calculate match score
        float matchScore = calculateMatchScore(
            ride,
            proximityCheck.pickupDistance(),
            proximityCheck.dropoffDistance(),
            request.getPickupTime(),
            detour.distanceKm()
        );

        // Extract features and generate explanation
        MatchFeatures features = extractMatchFeatures(
            ride, requestLocations, request,
            proximityCheck.pickupDistance(), proximityCheck.dropoffDistance(),
            detour, matchScore
        );

        String explanation = explainMatch(features);
        log.info("Match found - Ride {}: {}", ride.getSharedRideId(), explanation);

        // Build and add proposal
        RideMatchProposalResponse proposal = buildProposal(
            ride,
            detour.distanceKm(),
            detour.durationMinutes(),
            matchScore,
            request
        );

        proposals.add(proposal);
    }

    private LocationPair extractRideLocations(SharedRide ride) {
        Location start = resolveRideLocation(
            ride.getStartLocationId(),
            ride.getStartLat(),
            ride.getStartLng(),
            "Ride Start Location"
        );

        Location end = resolveRideLocation(
            ride.getEndLocationId(),
            ride.getEndLat(),
            ride.getEndLng(),
            "Ride End Location"
        );

        return new LocationPair(start, end);
    }

    private Location resolveRideLocation(Integer locationId, Double lat, Double lng, String defaultName) {
        if (locationId != null) {
            return locationRepository.findById(locationId)
                .orElseGet(() -> createLocation(lat, lng, defaultName));
        }
        return createLocation(lat, lng, defaultName);
    }

    private Location createLocation(Double lat, Double lng, String name) {
        Location loc = new Location();
        loc.setLat(lat);
        loc.setLng(lng);
        loc.setName(name);
        return loc;
    }

    private ProximityCheck checkProximity(LocationPair request, LocationPair ride) {
        double pickupDistance = PolylineDistance.haversineMeters(
            request.pickup().getLat(), request.pickup().getLng(),
            ride.pickup().getLat(), ride.pickup().getLng()
        );

        double dropoffDistance = PolylineDistance.haversineMeters(
            request.dropoff().getLat(), request.dropoff().getLng(),
            ride.dropoff().getLat(), ride.dropoff().getLng()
        );

        double maxProximityKm = rideConfig.getMatching().getMaxProximityKm();
        boolean valid = pickupDistance <= maxProximityKm && dropoffDistance <= maxProximityKm;

        return new ProximityCheck(valid, pickupDistance, dropoffDistance);
    }

    private DetourCalculation calculateDetour(
        SharedRide ride,
        LocationPair rideLocations,
        LocationPair requestLocations,
        LocalDateTime requestTime) {

        try {
            return calculateDetourViaRouting(ride, rideLocations, requestLocations, requestTime);
        } catch (Exception e) {
            log.warn("Routing API failed for ride {}: {}. Using fallback.",
                ride.getSharedRideId(), e.getMessage());
            return calculateDetourFallback(rideLocations, requestLocations);
        }
    }

    private DetourCalculation calculateDetourViaRouting(
        SharedRide ride,
        LocationPair rideLocations,
        LocationPair requestLocations,
        LocalDateTime requestTime) {

        LatLng effectiveStart = getEffectiveStartLocation(ride, rideLocations);

        RouteResponse originalRoute = routingService.getRoute(
            effectiveStart.latitude(), effectiveStart.longitude(),
            rideLocations.dropoff().getLat(), rideLocations.dropoff().getLng()
        );

        List<LatLng> waypoints = List.of(
            effectiveStart,
            new LatLng(requestLocations.pickup().getLat(), requestLocations.pickup().getLng()),
            new LatLng(requestLocations.dropoff().getLat(), requestLocations.dropoff().getLng()),
            new LatLng(rideLocations.dropoff().getLat(), rideLocations.dropoff().getLng())
        );

        RouteResponse modifiedRoute = routingService.getMultiStopRoute(waypoints, requestTime);

        double detourKm = Math.max(0, modifiedRoute.distance() * 1000 - originalRoute.distance() * 1000);
        int detourMinutes = (int) Math.max(0, Math.ceil((modifiedRoute.time() - originalRoute.time()) / 60.0));

        return new DetourCalculation(detourKm, detourMinutes);
    }

    private DetourCalculation calculateDetourFallback(LocationPair ride, LocationPair request) {
        double pickupProximity = PolylineDistance.haversineMeters(
            request.pickup().getLat(), request.pickup().getLng(),
            ride.pickup().getLat(), ride.pickup().getLng()
        );

        double dropoffProximity = PolylineDistance.haversineMeters(
            request.dropoff().getLat(), request.dropoff().getLng(),
            ride.dropoff().getLat(), ride.dropoff().getLng()
        );

        double avgProximity = (pickupProximity + dropoffProximity) / 2.0;
        int detourMinutes = (int) Math.ceil(avgProximity / 0.5); // 0.5 km/min avg speed

        return new DetourCalculation(avgProximity, detourMinutes);
    }

    private LatLng getEffectiveStartLocation(SharedRide ride, LocationPair rideLocations) {
        if (ride.getStatus() == SharedRideStatus.ONGOING) {
            return rideTrackingService.getLatestPosition(ride.getSharedRideId(), 5)
                .orElseGet(() -> new LatLng(ride.getStartLat(), ride.getStartLng()));
        }
        return new LatLng(rideLocations.pickup().getLat(), rideLocations.pickup().getLng());
    }

    private boolean isDetourAcceptable(SharedRide ride, int detourMinutes) {
        Integer driverMaxDetour = ride.getDriver().getMaxDetourMinutes();
        return driverMaxDetour == null || detourMinutes <= driverMaxDetour;
    }

    private List<RideMatchProposalResponse> selectTopProposals(List<RideMatchProposalResponse> proposals) {
        int maxProposals = rideConfig.getMatching().getMaxProposals();

        List<RideMatchProposalResponse> topProposals = proposals.stream()
            .sorted(Comparator.comparing(RideMatchProposalResponse::getMatchScore).reversed())
            .limit(maxProposals)
            .toList();

        log.info("Returning {} match proposals", topProposals.size());
        return topProposals;
    }

    private void logMatchingMetrics(MatchingMetrics metrics, int proposalsCount) {
        log.debug("Matching summary: {} processed, {} rejected (proximity: {}, detour: {}), {} errors, {} proposals",
            metrics.processed, metrics.rejectedProximity + metrics.rejectedDetour,
            metrics.rejectedProximity, metrics.rejectedDetour, metrics.errors, proposalsCount);
    }

    // Helper records
    private record LocationPair(Location pickup, Location dropoff) {
    }

    private record ProximityCheck(boolean isValid, double pickupDistance, double dropoffDistance) {
    }

    private record DetourCalculation(double distanceKm, int durationMinutes) {
    }

    public record MatchFeatures(
        double proximityScore,
        double timeAlignmentScore,
        double driverRatingScore,
        double detourScore,
        double pickupDistanceKm,
        double dropoffDistanceKm,
        double detourKm,
        int detourMinutes,
        long timeGapMinutes,
        boolean hasAvailableSeats,
        boolean withinDetourLimit,
        float finalScore
    ) {
    }

    private static class MatchingMetrics {
        int processed = 0;
        int rejectedProximity = 0;
        int rejectedDetour = 0;
        int errors = 0;

        void incrementProcessed() {
            processed++;
        }

        void incrementRejectedProximity() {
            rejectedProximity++;
        }

        void incrementRejectedDetour() {
            rejectedDetour++;
        }

        void incrementErrors() {
            errors++;
        }
    }


    private float calculateMatchScore(SharedRide ride, double pickupDistance, double dropoffDistance,
                                      LocalDateTime requestTime, double detourKm) {
        var scoring = rideConfig.getMatching().getScoring();

        // 1. Proximity score (0-1): closer is better
        double avgProximity = (pickupDistance + dropoffDistance) / 2.0;
        double maxProximityKm = rideConfig.getMatching().getMaxProximityKm();
        double proximityScore = Math.max(0, 1.0 - (avgProximity / maxProximityKm));

        // 2. Time alignment score (0-1): closer to requested time is better
        long timeDiffMinutes = Math.abs(java.time.Duration.between(ride.getScheduledTime(), requestTime).toMinutes());
        int timeWindowMinutes = rideConfig.getMatching().getTimeWindowMinutes();
        double timeScore = Math.max(0, 1.0 - ((double) timeDiffMinutes / timeWindowMinutes));

        // 3. Driver rating score (0-1): higher rating is better
        double ratingScore = ride.getDriver().getRatingAvg() / 5.0;

        // 4. Detour penalty (0-1): less detour is better
        double maxDetourKm = rideConfig.getMatching().getMaxDetourKm();
        double detourScore = Math.max(0, 1.0 - (detourKm / maxDetourKm));

        // Weighted sum
        double totalScore = (proximityScore * scoring.getProximityWeight()) +
            (timeScore * scoring.getTimeWeight()) +
            (ratingScore * scoring.getRatingWeight()) +
            (detourScore * scoring.getDetourWeight());

        // Convert to 0-100 scale
        return (float) (totalScore * 100.0);
    }

    private RideMatchProposalResponse buildProposal(SharedRide ride,
                                                    double detourKm, int detourMinutes, float matchScore,
                                                    SharedRideRequest request) {

        try {
            double estimatedDistanceKm = ride.getEstimatedDistance() != null ?
                ride.getEstimatedDistance() : (detourKm * 2); // Rough estimate
            int estimatedTripMinutes = ride.getEstimatedDuration() != null ?
                ride.getEstimatedDuration() : (int) (estimatedDistanceKm * 2); // ~30 km/h average

            BigDecimal totalFare = request.getTotalFare();
            BigDecimal earnedAmount = totalFare
                .subtract(totalFare.multiply(ride.getPricingConfig().getSystemCommissionRate()));

            // Calculate estimated times
            // Assume detour happens at beginning, then straight to pickup
            LocalDateTime estimatedPickupTime = ride.getScheduledTime().plusMinutes(detourMinutes);
            LocalDateTime estimatedDropoffTime = estimatedPickupTime.plusMinutes(estimatedTripMinutes);

            return RideMatchProposalResponse.builder()
                .sharedRideId(ride.getSharedRideId())
                .driverId(ride.getDriver().getDriverId())
                .driverName(ride.getDriver().getUser().getFullName())
                .driverRating(ride.getDriver().getRatingAvg())
                .vehicleModel(ride.getVehicle() != null ? ride.getVehicle().getModel() : "Unknown")
                .vehiclePlate(ride.getVehicle() != null ? ride.getVehicle().getPlateNumber() : "N/A")
                .scheduledTime(ride.getScheduledTime())
                .availableSeats(ride.getMaxPassengers() - ride.getCurrentPassengers())
                .totalFare(totalFare)
                .earnedAmount(earnedAmount)
                .estimatedDuration(estimatedTripMinutes)
                .estimatedDistance((float) estimatedDistanceKm)
                .detourDistance((float) detourKm)
                .detourDuration(detourMinutes)
                .matchScore(matchScore)
                .estimatedPickupTime(estimatedPickupTime)
                .estimatedDropoffTime(estimatedDropoffTime)
                .build();

        } catch (Exception e) {
            log.error("Error building proposal for ride {}: {}", ride.getSharedRideId(), e.getMessage(), e);
            // Fallback: use ride's base pricing
            double estimatedDistanceKm = ride.getEstimatedDistance() != null ? ride.getEstimatedDistance() : 10.0;
            BigDecimal totalFare = request.getTotalFare();
            BigDecimal earnedAmount = totalFare
                .subtract(totalFare.multiply(ride.getPricingConfig().getSystemCommissionRate()));

            LocalDateTime estimatedPickupTime = ride.getScheduledTime().plusMinutes(detourMinutes);
            int estimatedTripMinutes = ride.getEstimatedDuration() != null ? ride.getEstimatedDuration() : 30;
            LocalDateTime estimatedDropoffTime = estimatedPickupTime.plusMinutes(estimatedTripMinutes);

            return RideMatchProposalResponse.builder()
                .sharedRideId(ride.getSharedRideId())
                .driverId(ride.getDriver().getDriverId())
                .driverName(ride.getDriver().getUser().getFullName())
                .driverRating(ride.getDriver().getRatingAvg())
                .vehicleModel(ride.getVehicle() != null ? ride.getVehicle().getModel() : "Unknown")
                .vehiclePlate(ride.getVehicle() != null ? ride.getVehicle().getPlateNumber() : "N/A")
                .scheduledTime(ride.getScheduledTime())
                .availableSeats(ride.getMaxPassengers() - ride.getCurrentPassengers())
                .totalFare(totalFare)
                .earnedAmount(earnedAmount)
                .estimatedDuration(estimatedTripMinutes)
                .estimatedDistance((float) estimatedDistanceKm)
                .detourDistance((float) detourKm)
                .detourDuration(detourMinutes)
                .matchScore(matchScore)
                .estimatedPickupTime(estimatedPickupTime)
                .estimatedDropoffTime(estimatedDropoffTime)
                .build();
        }
    }

    public MatchFeatures extractMatchFeatures(SharedRide ride, LocationPair requestLocations,
                                              SharedRideRequest request, double pickupDistance,
                                              double dropoffDistance, DetourCalculation detour,
                                              float matchScore) {
        var scoring = rideConfig.getMatching().getScoring();

        double avgProximity = (pickupDistance + dropoffDistance) / 2.0;
        double maxProximityKm = rideConfig.getMatching().getMaxProximityKm();
        double proximityScore = Math.max(0, 1.0 - (avgProximity / maxProximityKm));

        long timeDiffMinutes = Math.abs(java.time.Duration.between(ride.getScheduledTime(), request.getPickupTime()).toMinutes());
        int timeWindowMinutes = rideConfig.getMatching().getTimeWindowMinutes();
        double timeScore = Math.max(0, 1.0 - ((double) timeDiffMinutes / timeWindowMinutes));

        double ratingScore = ride.getDriver().getRatingAvg() / 5.0;

        double maxDetourKm = rideConfig.getMatching().getMaxDetourKm();
        double detourScore = Math.max(0, 1.0 - (detour.distanceKm() / maxDetourKm));

        boolean hasSeats = (ride.getMaxPassengers() - ride.getCurrentPassengers()) > 0;
        boolean withinDetourLimit = isDetourAcceptable(ride, detour.durationMinutes());

        return new MatchFeatures(
            proximityScore,
            timeScore,
            ratingScore,
            detourScore,
            pickupDistance / 1000.0,
            dropoffDistance / 1000.0,
            detour.distanceKm(),
            detour.durationMinutes(),
            timeDiffMinutes,
            hasSeats,
            withinDetourLimit,
            matchScore
        );
    }

    public String explainMatch(MatchFeatures features) {
        List<String> positives = new ArrayList<>();
        List<String> concerns = new ArrayList<>();

        if (features.pickupDistanceKm() < 0.5) {
            positives.add("pickup very close");
        } else if (features.pickupDistanceKm() < 1.0) {
            positives.add("pickup nearby");
        } else if (features.pickupDistanceKm() > 2.0) {
            concerns.add("pickup far (" + String.format("%.1f km", features.pickupDistanceKm()) + ")");
        }

        if (features.dropoffDistanceKm() < 0.5) {
            positives.add("dropoff very close");
        } else if (features.dropoffDistanceKm() < 1.0) {
            positives.add("dropoff nearby");
        } else if (features.dropoffDistanceKm() > 2.0) {
            concerns.add("dropoff far (" + String.format("%.1f km", features.dropoffDistanceKm()) + ")");
        }

        if (features.timeGapMinutes() <= 5) {
            positives.add("perfect timing");
        } else if (features.timeGapMinutes() <= 15) {
            positives.add("good timing");
        } else if (features.timeGapMinutes() > 30) {
            concerns.add("time gap " + features.timeGapMinutes() + " min");
        }

        if (features.detourMinutes() <= 3) {
            positives.add("minimal detour");
        } else if (features.detourMinutes() <= 8) {
            positives.add("low detour");
        } else if (features.detourMinutes() > 15) {
            concerns.add("high detour (" + features.detourMinutes() + " min)");
        }

        if (features.driverRatingScore() >= 0.9) { // 4.5+ stars
            positives.add("highly rated driver");
        } else if (features.driverRatingScore() >= 0.8) { // 4.0+ stars
            positives.add("good driver rating");
        } else if (features.driverRatingScore() < 0.6) { // Below 3.0 stars
            concerns.add("low driver rating");
        }

        if (!features.hasAvailableSeats()) {
            concerns.add("no available seats");
        }

        if (!features.withinDetourLimit()) {
            concerns.add("exceeds driver's detour preference");
        }

        StringBuilder explanation = new StringBuilder();

        if (!positives.isEmpty()) {
            explanation.append("✓ ").append(String.join(", ", positives));
        }

        if (!concerns.isEmpty()) {
            if (!explanation.isEmpty()) {
                explanation.append(" | ");
            }
            explanation.append("⚠ ").append(String.join(", ", concerns));
        }

        if (explanation.isEmpty()) {
            explanation.append("Standard match");
        }

        explanation.append(String.format(" (score: %.1f)", features.finalScore()));

        return explanation.toString();
    }

}


package com.mssus.app.service.impl;

import com.mssus.app.common.enums.SharedRideStatus;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.appconfig.config.properties.AiConfigurationProperties;
import com.mssus.app.appconfig.config.properties.RideConfigurationProperties;
import com.mssus.app.dto.domain.ride.LatLng;
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
import com.mssus.app.service.ai.AiMatchingService;
import com.mssus.app.common.util.GeoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RideMatchingServiceImpl implements RideMatchingService {

    private final SharedRideRepository rideRepository;
    private final LocationRepository locationRepository;
    private final RideConfigurationProperties rideConfig;
    private final RoutingService routingService;
    private final RideTrackingService rideTrackingService;
    private final AiConfigurationProperties aiConfig;
    
    @Autowired(required = false)
    private AiMatchingService aiMatchingService;

    @Override
    @Transactional(readOnly = true)
    public List<RideMatchProposalResponse> findMatches(SharedRideRequest request) {
        log.info(">> findMatches(request={})", request);
        
        // Step 1: Extract and validate locations
        LocationPair requestLocations = extractRequestLocations(request);
        log.debug("Request locations: {}", requestLocations);

        // Step 2: Find candidate rides within time window
        List<SharedRide> candidateRides = findCandidateRides(request.getPickupTime());

        if (candidateRides.isEmpty()) {
            log.info("No candidate rides found for matching");
            log.info("<< findMatches(): []");
            return List.of();
        }

        // Step 3: Score and filter candidates using BASE algorithm
        List<RideMatchProposalResponse> proposals = evaluateCandidates(
            candidateRides, requestLocations, request);
        log.debug("Base algorithm evaluated candidates, found {} proposals.", proposals.size());

        if (proposals.isEmpty()) {
            log.info("<< findMatches(): No proposals after evaluation");
            return List.of();
        }

        // Step 4: Sort by base algorithm scores
        List<RideMatchProposalResponse> sortedProposals = selectTopProposals(proposals);
        
        // Step 5: AI MAKES FINAL RANKING DECISION (replaces weighted-sum as primary algorithm)
        if (aiConfig.isEnabled() && aiMatchingService != null && aiMatchingService.isAvailable()) {
            try {
                log.info("Invoking AI matching algorithm for final ranking decision");
                List<RideMatchProposalResponse> aiRankedProposals = 
                    aiMatchingService.aiRankMatches(request, sortedProposals);
                
                log.info("<< findMatches(): {} proposals (AI-ranked successfully)", aiRankedProposals.size());
                return aiRankedProposals;
                
            } catch (Exception e) {
                log.error("AI matching failed: {}", e.getMessage());
                if (aiConfig.isFallbackToBaseAlgorithm()) {
                    log.warn("Falling back to base algorithm due to AI failure");
                    log.info("<< findMatches(): {} proposals (base algorithm fallback)", sortedProposals.size());
                    return sortedProposals;
                } else {
                    throw e;
                }
            }
        } else {
            log.info("AI matching disabled, using base algorithm only");
            log.info("<< findMatches(): {} proposals (base algorithm)", sortedProposals.size());
            return sortedProposals;
        }
    }

    private LocationPair extractRequestLocations(SharedRideRequest request) {
        log.debug(">> extractRequestLocations(request={})", request);
        Location pickup = request.getPickupLocation();
//        Location pickup = resolveLocation(
//            request.getPickupLocationId(),
//            request.getPickupLat(),
//            request.getPickupLng(),
//            "Pickup Location"
//        );
        Location dropoff = request.getDropoffLocation();

//        Location dropoff = resolveLocation(
//            request.getDropoffLocationId(),
//            request.getDropoffLat(),
//            request.getDropoffLng(),
//            "Dropoff Location"
//        );

        LocationPair result = new LocationPair(pickup, dropoff);
        log.debug("<< extractRequestLocations(): {}", result);
        return result;
    }

    private Location resolveLocation(Integer locationId, Double lat, Double lng, String defaultName) {
        log.debug(">> resolveLocation(locationId={}, lat={}, lng={}, defaultName={})", locationId, lat, lng, defaultName);
        if (locationId != null) {
            log.debug("Resolving location by ID: {}", locationId);
            Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> BaseDomainException.formatted(
                    "ride.validation.invalid-location",
                    defaultName + " not found: " + locationId));
            log.debug("<< resolveLocation(): {}", location);
            return location;
        }

        if (lat != null && lng != null) {
            log.debug("Creating new location from coordinates.");
            Location loc = new Location();
            loc.setLat(lat);
            loc.setLng(lng);
            loc.setName(defaultName);
            log.debug("<< resolveLocation(): {}", loc);
            return loc;
        }

        log.error("Neither location ID nor coordinates provided for {}", defaultName);
        throw BaseDomainException.formatted(
            "ride.validation.invalid-location",
            "Neither location ID nor coordinates provided for " + defaultName);
    }

    private List<SharedRide> findCandidateRides(LocalDateTime requestTime) {
        log.debug(">> findCandidateRides(requestTime={})", requestTime);
        int timeWindowMinutes = rideConfig.getMatching().getTimeWindowMinutes();
        LocalDateTime startTime = requestTime.minusMinutes(timeWindowMinutes);
        LocalDateTime endTime = requestTime.plusMinutes(timeWindowMinutes);

        log.debug("Time window: {} to {}", startTime, endTime);

        List<SharedRide> candidates = rideRepository.findCandidateRidesForMatching(startTime, endTime);
        log.info("Found {} candidate rides in time window", candidates.size());

        log.debug("<< findCandidateRides(): {} candidates", candidates.size());
        return candidates;
    }

    private List<RideMatchProposalResponse> evaluateCandidates(
        List<SharedRide> candidateRides,
        LocationPair requestLocations,
        SharedRideRequest request) {
        log.info(">> evaluateCandidates({} candidates, requestLocations={}, request={})", candidateRides.size(), requestLocations, request);

        List<RideMatchProposalResponse> proposals = new ArrayList<>();
        MatchingMetrics metrics = new MatchingMetrics();

        for (SharedRide ride : candidateRides) {
            metrics.incrementProcessed();

            try {
                log.debug("Evaluating candidate ride: {}", ride.getSharedRideId());
                evaluateSingleCandidate(ride, requestLocations, request, proposals, metrics);
            } catch (Exception e) {
                metrics.incrementErrors();
                log.error("Error evaluating ride {}: {}", ride.getSharedRideId(), e.getMessage(), e);
            }
        }

        logMatchingMetrics(metrics, proposals.size());
        log.info("<< evaluateCandidates(): {} proposals", proposals.size());
        return proposals;
    }

    private void evaluateSingleCandidate(
        SharedRide ride,
        LocationPair requestLocations,
        SharedRideRequest request,
        List<RideMatchProposalResponse> proposals,
        MatchingMetrics metrics) {
        log.debug(">> evaluateSingleCandidate(rideId={}, ...)", ride.getSharedRideId());

        // Extract ride locations
        LocationPair rideLocations = extractRideLocations(ride);
        log.debug("Ride locations: {}", rideLocations);

        // Check proximity
        ProximityCheck proximityCheck = checkProximity(requestLocations, rideLocations);
        log.debug("Proximity check result: {}", proximityCheck);
        if (!proximityCheck.isValid()) {
            metrics.incrementRejectedProximity();
            log.debug("Ride {} rejected - proximity threshold exceeded", ride.getSharedRideId());
            log.debug("<< evaluateSingleCandidate(): proximity rejection");
            return;
        }

        // Calculate detour
        DetourCalculation detour = calculateDetour(ride, rideLocations, requestLocations, request.getPickupTime());
        log.debug("Detour calculation result: {}", detour);

        // Check driver's detour preference
        if (!isDetourAcceptable(ride, detour.durationMinutes())) {
            metrics.incrementRejectedDetour();
            log.debug("Ride {} rejected - exceeds driver's max detour", ride.getSharedRideId());
            log.debug("<< evaluateSingleCandidate(): detour rejection");
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
        log.debug("Calculated match score: {}", matchScore);

        // Extract features and generate explanation
        MatchFeatures features = extractMatchFeatures(
            ride, requestLocations, request,
            proximityCheck.pickupDistance(), proximityCheck.dropoffDistance(),
            detour, matchScore
        );
        log.debug("Extracted match features: {}", features);

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
        log.debug("Built proposal: {}", proposal);

        proposals.add(proposal);
        log.debug("<< evaluateSingleCandidate(): proposal added");
    }

    private LocationPair extractRideLocations(SharedRide ride) {
        log.debug(">> extractRideLocations(rideId={})", ride.getSharedRideId());
        Location start = ride.getStartLocation();
        Location end = ride.getEndLocation();
//        Location start = resolveRideLocation(
//            ride.getStartLocationId(),
//            ride.getStartLat(),
//            ride.getStartLng(),
//            "Ride Start Location"
//        );
//
//        Location end = resolveRideLocation(
//            ride.getEndLocationId(),
//            ride.getEndLat(),
//            ride.getEndLng(),
//            "Ride End Location"
//        );

        LocationPair result = new LocationPair(start, end);
        log.debug("<< extractRideLocations(): {}", result);
        return result;
    }

    private Location resolveRideLocation(Integer locationId, Double lat, Double lng, String defaultName) {
        log.debug(">> resolveRideLocation(locationId={}, lat={}, lng={}, defaultName={})", locationId, lat, lng, defaultName);
        if (locationId != null) {
            log.debug("Resolving ride location by ID: {}", locationId);
            Location location = locationRepository.findById(locationId)
                .orElseGet(() -> {
                    log.warn("Ride location ID {} not found in repository, creating from coordinates.", locationId);
                    return createLocation(lat, lng, defaultName);
                });
            log.debug("<< resolveRideLocation(): {}", location);
            return location;
        }
        log.debug("Creating new ride location from coordinates.");
        Location location = createLocation(lat, lng, defaultName);
        log.debug("<< resolveRideLocation(): {}", location);
        return location;
    }

    private Location createLocation(Double lat, Double lng, String name) {
        log.debug(">> createLocation(lat={}, lng={}, name={})", lat, lng, name);
        Location loc = new Location();
        loc.setLat(lat);
        loc.setLng(lng);
        loc.setName(name);
        log.debug("<< createLocation(): {}", loc);
        return loc;
    }

    private ProximityCheck checkProximity(LocationPair request, LocationPair ride) {
        log.debug(">> checkProximity(request={}, ride={})", request, ride);
        double pickupDistance = GeoUtil.haversineMeters(
            request.pickup().getLat(), request.pickup().getLng(),
            ride.pickup().getLat(), ride.pickup().getLng()
        ) / 1000.0; // convert to km
        log.debug("Pickup distance: {} km", pickupDistance);

        double dropoffDistance = GeoUtil.haversineMeters(
            request.dropoff().getLat(), request.dropoff().getLng(),
            ride.dropoff().getLat(), ride.dropoff().getLng()
        ) / 1000.0;
        log.debug("Dropoff distance: {} km", dropoffDistance);

        double maxProximityKm = rideConfig.getMatching().getMaxProximityKm();
        boolean valid = pickupDistance <= maxProximityKm && dropoffDistance <= maxProximityKm;
        log.debug("Max proximity: {} km. Proximity check valid: {}", maxProximityKm, valid);

        ProximityCheck result = new ProximityCheck(valid, pickupDistance, dropoffDistance);
        log.debug("<< checkProximity(): {}", result);
        return result;
    }

    private DetourCalculation calculateDetour(
        SharedRide ride,
        LocationPair rideLocations,
        LocationPair requestLocations,
        LocalDateTime requestTime) {
        log.debug(">> calculateDetour(rideId={}, ...)", ride.getSharedRideId());

        try {
            log.debug("Attempting to calculate detour via routing API.");
            DetourCalculation result = calculateDetourViaRouting(ride, rideLocations, requestLocations, requestTime);
            log.debug("<< calculateDetour() via routing API: {}", result);
            return result;
        } catch (Exception e) {
            log.warn("Routing API failed for ride {}: {}. Using fallback.",
                ride.getSharedRideId(), e.getMessage());
            DetourCalculation result = calculateDetourFallback(rideLocations, requestLocations);
            log.debug("<< calculateDetour() via fallback: {}", result);
            return result;
        }
    }

    private DetourCalculation calculateDetourViaRouting(
        SharedRide ride,
        LocationPair rideLocations,
        LocationPair requestLocations,
        LocalDateTime requestTime) {
        log.debug(">> calculateDetourViaRouting(rideId={}, ...)", ride.getSharedRideId());

        LatLng effectiveStart = getEffectiveStartLocation(ride, rideLocations);
        log.debug("Effective start location: {}", effectiveStart);

        log.debug("Calculating original route...");
        RouteResponse originalRoute = routingService.getRoute(
            effectiveStart.latitude(), effectiveStart.longitude(),
            rideLocations.dropoff().getLat(), rideLocations.dropoff().getLng()
        );
        log.debug("Original route: distance={}, time={}", originalRoute.distance(), originalRoute.time());

        List<LatLng> waypoints = List.of(
            effectiveStart,
            new LatLng(requestLocations.pickup().getLat(), requestLocations.pickup().getLng()),
            new LatLng(requestLocations.dropoff().getLat(), requestLocations.dropoff().getLng()),
            new LatLng(rideLocations.dropoff().getLat(), rideLocations.dropoff().getLng())
        );
        log.debug("Calculating modified route with waypoints: {}", waypoints);

        RouteResponse modifiedRoute = routingService.getMultiStopRoute(waypoints, requestTime);
        log.debug("Modified route: distance={}, time={}", modifiedRoute.distance(), modifiedRoute.time());

        double detourKm = Math.max(0, modifiedRoute.distance() * 1000 - originalRoute.distance() * 1000);
        int detourMinutes = (int) Math.max(0, Math.ceil((modifiedRoute.time() - originalRoute.time()) / 60.0));

        DetourCalculation result = new DetourCalculation(detourKm, detourMinutes);
        log.debug("<< calculateDetourViaRouting(): {}", result);
        return result;
    }

    private DetourCalculation calculateDetourFallback(LocationPair ride, LocationPair request) {
        log.debug(">> calculateDetourFallback(ride={}, request={})", ride, request);
        double pickupProximity = GeoUtil.haversineMeters(
            request.pickup().getLat(), request.pickup().getLng(),
            ride.pickup().getLat(), ride.pickup().getLng()
        );

        double dropoffProximity = GeoUtil.haversineMeters(
            request.dropoff().getLat(), request.dropoff().getLng(),
            ride.dropoff().getLat(), ride.dropoff().getLng()
        );

        double avgProximity = (pickupProximity + dropoffProximity) / 2.0;
        int detourMinutes = (int) Math.ceil(avgProximity / 0.5); // 0.5 km/min avg speed
        log.debug("Fallback detour calculation: avgProximity={}, detourMinutes={}", avgProximity, detourMinutes);

        DetourCalculation result = new DetourCalculation(avgProximity, detourMinutes);
        log.debug("<< calculateDetourFallback(): {}", result);
        return result;
    }

    private LatLng getEffectiveStartLocation(SharedRide ride, LocationPair rideLocations) {
        log.debug(">> getEffectiveStartLocation(rideId={})", ride.getSharedRideId());
        if (ride.getStatus() == SharedRideStatus.ONGOING) {
            log.debug("Ride is ongoing, getting latest position.");
            LatLng latestPosition = rideTrackingService.getLatestPosition(ride.getSharedRideId(), 5)
                .orElseGet(() -> {
                    log.warn("Could not get latest position for ongoing ride {}, using ride start location.", ride.getSharedRideId());
                    return new LatLng(ride.getStartLocation().getLat(), ride.getStartLocation().getLng());
                });
            log.debug("<< getEffectiveStartLocation(): {}", latestPosition);
            return latestPosition;
        }
        LatLng startLocation = new LatLng(rideLocations.pickup().getLat(), rideLocations.pickup().getLng());
        log.debug("<< getEffectiveStartLocation(): {}", startLocation);
        return startLocation;
    }

    private boolean isDetourAcceptable(SharedRide ride, int detourMinutes) {
        log.debug(">> isDetourAcceptable(rideId={}, detourMinutes={})", ride.getSharedRideId(), detourMinutes);
        Integer driverMaxDetour = ride.getDriver().getMaxDetourMinutes();
        log.debug("Driver's max detour preference: {} minutes", driverMaxDetour);
        boolean acceptable = driverMaxDetour == null || detourMinutes <= driverMaxDetour;
        log.debug("<< isDetourAcceptable(): {}", acceptable);
        return acceptable;
    }

    private List<RideMatchProposalResponse> selectTopProposals(List<RideMatchProposalResponse> proposals) {
        log.debug(">> selectTopProposals({} proposals)", proposals.size());
        int maxProposals = rideConfig.getMatching().getMaxProposals();
        log.debug("Max proposals to return: {}", maxProposals);

        List<RideMatchProposalResponse> topProposals = proposals.stream()
            .sorted(Comparator.comparing(RideMatchProposalResponse::getMatchScore).reversed())
            .limit(maxProposals)
            .toList();

        log.info("Returning {} match proposals", topProposals.size());
        log.debug("<< selectTopProposals()");
        return topProposals;
    }

    private void logMatchingMetrics(MatchingMetrics metrics, int proposalsCount) {
        log.debug(">> logMatchingMetrics(...)");
        log.debug("Matching summary: {} processed, {} rejected (proximity: {}, detour: {}), {} errors, {} proposals",
            metrics.processed, metrics.rejectedProximity + metrics.rejectedDetour,
            metrics.rejectedProximity, metrics.rejectedDetour, metrics.errors, proposalsCount);
        log.debug("<< logMatchingMetrics()");
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
        log.debug(">> calculateMatchScore(rideId={}, pickupDistance={}, dropoffDistance={}, requestTime={}, detourKm={})",
            ride.getSharedRideId(), pickupDistance, dropoffDistance, requestTime, detourKm);

        var scoring = rideConfig.getMatching().getScoring();

        // 1. Proximity score (0-1): closer is better
        double avgProximity = (pickupDistance + dropoffDistance) / 2.0;
        double maxProximityKm = rideConfig.getMatching().getMaxProximityKm();
        double proximityScore = Math.max(0, 1.0 - (avgProximity / maxProximityKm));
        log.debug("Proximity score: {} (avgProximity={}, maxProximityKm={})", proximityScore, avgProximity, maxProximityKm);

        // 2. Time alignment score (0-1): closer to requested time is better
        long timeDiffMinutes = Math.abs(java.time.Duration.between(ride.getScheduledTime(), requestTime).toMinutes());
        int timeWindowMinutes = rideConfig.getMatching().getTimeWindowMinutes();
        double timeScore = Math.max(0, 1.0 - ((double) timeDiffMinutes / timeWindowMinutes));
        log.debug("Time alignment score: {} (timeDiffMinutes={}, timeWindowMinutes={})", timeScore, timeDiffMinutes, timeWindowMinutes);

        // 3. Driver rating score (0-1): higher rating is better
        double ratingScore = ride.getDriver().getRatingAvg() / 5.0;
        log.debug("Driver rating score: {} (rating={})", ratingScore, ride.getDriver().getRatingAvg());

        // 4. Detour penalty (0-1): less detour is better
        double maxDetourKm = rideConfig.getMatching().getMaxDetourKm();
        double detourScore = Math.max(0, 1.0 - (detourKm / maxDetourKm));
        log.debug("Detour score: {} (detourKm={}, maxDetourKm={})", detourScore, detourKm, maxDetourKm);

        // Weighted sum
        double totalScore = (proximityScore * scoring.getProximityWeight()) +
            (timeScore * scoring.getTimeWeight()) +
            (ratingScore * scoring.getRatingWeight()) +
            (detourScore * scoring.getDetourWeight());
        log.debug("Weighted score: {} (weights: proximity={}, time={}, rating={}, detour={})",
            totalScore, scoring.getProximityWeight(), scoring.getTimeWeight(), scoring.getRatingWeight(), scoring.getDetourWeight());

        // Convert to 0-100 scale
        float finalScore = (float) (totalScore * 100.0);
        log.debug("<< calculateMatchScore(): {}", finalScore);
        return finalScore;
    }

    private RideMatchProposalResponse buildProposal(SharedRide ride,
                                                    double detourKm, int detourMinutes, float matchScore,
                                                    SharedRideRequest request) {
        log.debug(">> buildProposal(rideId={}, detourKm={}, detourMinutes={}, matchScore={}, ...)",
            ride.getSharedRideId(), detourKm, detourMinutes, matchScore);

        try {
            double estimatedDistanceKm = ride.getEstimatedDistance() != null ?
                ride.getEstimatedDistance() : (detourKm * 2); // Rough estimate
            int estimatedTripMinutes = ride.getEstimatedDuration() != null ?
                ride.getEstimatedDuration() : (int) (estimatedDistanceKm * 2); // ~30 km/h average
            log.debug("Estimated distance: {} km, estimated duration: {} minutes", estimatedDistanceKm, estimatedTripMinutes);

            BigDecimal totalFare = request.getTotalFare();
            BigDecimal earnedAmount = totalFare
                .subtract(totalFare.multiply(ride.getPricingConfig().getSystemCommissionRate()));
            log.debug("Fare calculation: totalFare={}, commissionRate={}, earnedAmount={}",
                totalFare, ride.getPricingConfig().getSystemCommissionRate(), earnedAmount);

            // Calculate estimated times
            // Assume detour happens at beginning, then straight to pickup
            LocalDateTime estimatedPickupTime = ride.getScheduledTime().plusMinutes(detourMinutes);
            LocalDateTime estimatedDropoffTime = estimatedPickupTime.plusMinutes(estimatedTripMinutes);
            log.debug("Estimated times: pickup={}, dropoff={}", estimatedPickupTime, estimatedDropoffTime);

            RideMatchProposalResponse proposal = RideMatchProposalResponse.builder()
                .sharedRideId(ride.getSharedRideId())
                .driverId(ride.getDriver().getDriverId())
                .driverName(ride.getDriver().getUser().getFullName())
                .driverRating(ride.getDriver().getRatingAvg())
                .vehicleModel(ride.getVehicle() != null ? ride.getVehicle().getModel() : "Unknown")
                .vehiclePlate(ride.getVehicle() != null ? ride.getVehicle().getPlateNumber() : "N/A")
                .scheduledTime(ride.getScheduledTime())
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
            log.debug("<< buildProposal() (success): {}", proposal);
            return proposal;

        } catch (Exception e) {
            log.error("Error building proposal for ride {}: {}", ride.getSharedRideId(), e.getMessage(), e);
            // Fallback: use ride's base pricing
            log.warn("Building proposal with fallback data for ride {}", ride.getSharedRideId());
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
        log.debug(">> extractMatchFeatures(rideId={}, ...)", ride.getSharedRideId());
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

        boolean withinDetourLimit = isDetourAcceptable(ride, detour.durationMinutes());

        MatchFeatures features = new MatchFeatures(
            proximityScore,
            timeScore,
            ratingScore,
            detourScore,
            pickupDistance,
            dropoffDistance,
            detour.distanceKm(),
            detour.durationMinutes(),
            timeDiffMinutes,
            withinDetourLimit,
            matchScore
        );
        log.debug("<< extractMatchFeatures(): {}", features);
        return features;
    }

    public String explainMatch(MatchFeatures features) {
        log.debug(">> explainMatch(features={})", features);
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

        String result = explanation.toString();
        log.debug("<< explainMatch(): {}", result);
        return result;
    }

}

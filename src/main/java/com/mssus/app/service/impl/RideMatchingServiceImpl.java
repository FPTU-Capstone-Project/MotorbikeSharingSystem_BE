package com.mssus.app.service.impl;

import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.config.properties.RideConfigurationProperties;
import com.mssus.app.dto.response.ride.RideMatchProposalResponse;
import com.mssus.app.entity.Location;
import com.mssus.app.entity.SharedRide;
import com.mssus.app.entity.SharedRideRequest;
import com.mssus.app.repository.LocationRepository;
import com.mssus.app.repository.SharedRideRepository;
import com.mssus.app.service.RideMatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final double EARTH_RADIUS_KM = 6371.0;

    @Override
    @Transactional(readOnly = true)
    public List<RideMatchProposalResponse> findMatches(SharedRideRequest request) {
        log.info("Finding matches for request ID: {}, pickup: {}, dropoff: {}, time: {}",
                request.getSharedRideRequestId(),
                request.getPickupLocationId(),
                request.getDropoffLocationId(),
                request.getPickupTime());

        Location pickupLoc = null;
        Location dropoffLoc = null;

        // Step 1: Get request locations
        if (request.getPickupLocationId() != null) {
            pickupLoc = locationRepository.findById(request.getPickupLocationId())
                .orElseThrow(() -> BaseDomainException.formatted("ride.validation.invalid-location",
                    "Pickup location not found: " + request.getPickupLocationId()));
        } else if (request.getPickupLat() != null && request.getPickupLng() != null) {
            pickupLoc = new Location();
            pickupLoc.setLat(request.getPickupLat());
            pickupLoc.setLng(request.getPickupLng());
            pickupLoc.setName("Pickup Location");
        } else {
            throw BaseDomainException.formatted("ride.validation.invalid-location",
                "Neither pickup location ID nor coordinates provided");
        }

        if (request.getDropoffLocationId() != null) {
            dropoffLoc = locationRepository.findById(request.getDropoffLocationId())
                .orElseThrow(() -> BaseDomainException.formatted("ride.validation.invalid-location",
                    "Dropoff location not found: " + request.getDropoffLocationId()));
        } else if (request.getDropoffLat() != null && request.getDropoffLng() != null) {
            dropoffLoc = new Location();
            dropoffLoc.setLat(request.getDropoffLat());
            dropoffLoc.setLng(request.getDropoffLng());
            dropoffLoc.setName("Dropoff Location");
        } else {
            throw BaseDomainException.formatted("ride.validation.invalid-location",
                "Neither dropoff location ID nor coordinates provided");
        }

        // Step 2: Calculate time window
        LocalDateTime requestTime = request.getPickupTime();
        int timeWindowMinutes = rideConfig.getMatching().getTimeWindowMinutes();
        LocalDateTime startTime = requestTime.minusMinutes(timeWindowMinutes);
        LocalDateTime endTime = requestTime.plusMinutes(timeWindowMinutes);

        log.debug("Time window: {} to {}", startTime, endTime);

        // Step 3: Find candidate rides
        List<SharedRide> candidateRides = rideRepository.findCandidateRidesForMatching(startTime, endTime);
        log.info("Found {} candidate rides in time window", candidateRides.size());

        if (candidateRides.isEmpty()) {
            log.info("No candidate rides found for matching");
            return List.of();
        }

        // Step 4: Score and filter candidates
        List<RideMatchProposalResponse> proposals = new ArrayList<>();
        double maxProximityKm = rideConfig.getMatching().getMaxProximityKm();

        for (SharedRide ride : candidateRides) {
            try {
                // Get ride locations
                Location rideStart;
                if (ride.getStartLocationId() != null) {
                    rideStart = locationRepository.findById(ride.getStartLocationId())
                        .orElseGet(() -> {
                            Location loc = new Location();
                            loc.setLat(ride.getStartLat());
                            loc.setLng(ride.getStartLng());
                            loc.setName("Ride Start Location");
                            return loc;
                        });
                } else {
                    rideStart = new Location();
                    rideStart.setLat(ride.getStartLat());
                    rideStart.setLng(ride.getStartLng());
                    rideStart.setName("Ride Start Location");
                }

                Location rideEnd;
                if (ride.getEndLocationId() != null) {
                    rideEnd = locationRepository.findById(ride.getEndLocationId())
                        .orElseGet(() -> {
                            Location loc = new Location();
                            loc.setLat(ride.getEndLat());
                            loc.setLng(ride.getEndLng());
                            loc.setName("Ride End Location");
                            return loc;
                        });
                } else {
                    rideEnd = new Location();
                    rideEnd.setLat(ride.getEndLat());
                    rideEnd.setLng(ride.getEndLng());
                    rideEnd.setName("Ride End Location");
                }


                // Calculate proximity scores
                double pickupToStartDistance = calculateDistance(
                        pickupLoc.getLat(), pickupLoc.getLng(),
                        rideStart.getLat(), rideStart.getLng());
                double dropoffToEndDistance = calculateDistance(
                        dropoffLoc.getLat(), dropoffLoc.getLng(),
                        rideEnd.getLat(), rideEnd.getLng());

                // Filter by proximity threshold
                if (pickupToStartDistance > maxProximityKm || dropoffToEndDistance > maxProximityKm) {
                    log.debug("Ride {} rejected - proximity threshold exceeded (pickup: {}, dropoff: {})",
                            ride.getSharedRideId(), pickupToStartDistance, dropoffToEndDistance);
                    continue;
                }

                // TODO: Validate detour distance using OSRM
                // For MVP, use simple heuristic: average of pickup/dropoff proximity
                double avgProximity = (pickupToStartDistance + dropoffToEndDistance) / 2.0;
                double detourDistanceKm = avgProximity;
                // Assume 30 km/h average speed (0.5 km/min)
                double averageSpeedKmPerMin = 0.5;
                int detourDurationMinutes = (int) Math.ceil(avgProximity / averageSpeedKmPerMin);

                // Check against driver's max detour preference
                Integer driverMaxDetour = ride.getDriver().getMaxDetourMinutes();
                if (driverMaxDetour != null && detourDurationMinutes > driverMaxDetour) {
                    log.debug("Ride {} rejected - exceeds driver's max detour preference ({} > {})",
                            ride.getSharedRideId(), detourDurationMinutes, driverMaxDetour);
                    continue;
                }

                // Calculate match score
                float matchScore = calculateMatchScore(
                        ride, pickupToStartDistance, dropoffToEndDistance,
                        requestTime, detourDistanceKm);

                // Build proposal
                RideMatchProposalResponse proposal = buildProposal(
                        ride, pickupLoc, dropoffLoc,
                        detourDistanceKm, detourDurationMinutes,
                        matchScore, request);

                proposals.add(proposal);

            } catch (Exception e) {
                log.error("Error scoring ride {}: {}", ride.getSharedRideId(), e.getMessage(), e);
                // Continue with next candidate
            }
        }

        // Step 5: Sort by score descending and limit
        proposals.sort(Comparator.comparing(RideMatchProposalResponse::getMatchScore).reversed());
        int maxProposals = rideConfig.getMatching().getMaxProposals();
        List<RideMatchProposalResponse> topProposals = proposals.stream()
                .limit(maxProposals)
                .toList();

        log.info("Returning {} match proposals (scored and ranked)", topProposals.size());
        return topProposals;
    }

    @Override
    public double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        // Haversine formula
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
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

    private RideMatchProposalResponse buildProposal(SharedRide ride, Location pickupLoc, Location dropoffLoc,
                                                    double detourKm, int detourMinutes, float matchScore,
                                                    SharedRideRequest request) {
        // Use QuoteService to generate quote for rider's route (pickup to dropoff)
        com.mssus.app.dto.request.QuoteRequest quoteRequest = new com.mssus.app.dto.request.QuoteRequest(
                new com.mssus.app.dto.LatLng(pickupLoc.getLat(), pickupLoc.getLng()),
                new com.mssus.app.dto.LatLng(dropoffLoc.getLat(), dropoffLoc.getLng())
        );

        try {
            var quote = com.mssus.app.pricing.model.Quote.class.cast(null); // Placeholder for actual quote generation
            // Note: We don't call generateQuote here because it would cache the quote
            // Instead, we calculate inline using PricingService
            
            // Get route from RoutingService
            var route = com.mssus.app.dto.response.RouteResponse.class.cast(null); // Will be injected
            
            // For MVP, use ride's estimates if available, otherwise use haversine distance
            double estimatedDistanceKm = ride.getEstimatedDistance() != null ? 
                    ride.getEstimatedDistance() : (detourKm * 2); // Rough estimate
            int estimatedTripMinutes = ride.getEstimatedDuration() != null ? 
                    ride.getEstimatedDuration() : (int)(estimatedDistanceKm * 2); // ~30 km/h average

            // Calculate fare using ride's pricing
            java.math.BigDecimal estimatedFare = ride.getBaseFare()
                    .add(ride.getPerKmRate().multiply(java.math.BigDecimal.valueOf(estimatedDistanceKm)));

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
                    .estimatedFare(estimatedFare)
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
            java.math.BigDecimal estimatedFare = ride.getBaseFare()
                    .add(ride.getPerKmRate().multiply(java.math.BigDecimal.valueOf(estimatedDistanceKm)));

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
                    .estimatedFare(estimatedFare)
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
}


package com.mssus.app.service.impl;

import com.mssus.app.common.enums.SharedRideStatus;
import com.mssus.app.common.enums.SharedRideRequestStatus;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.request.ride.CompleteRideRequest;
import com.mssus.app.dto.request.ride.CreateRideRequest;
import com.mssus.app.dto.request.ride.StartRideRequest;
import com.mssus.app.dto.request.wallet.WalletCaptureRequest;
import com.mssus.app.dto.request.wallet.WalletReleaseRequest;
import com.mssus.app.dto.response.RouteResponse;
import com.mssus.app.dto.response.ride.RideCompletionResponse;
import com.mssus.app.dto.response.ride.SharedRideResponse;
import com.mssus.app.dto.ride.LatLng;
import com.mssus.app.entity.*;
import com.mssus.app.mapper.SharedRideMapper;
import com.mssus.app.repository.*;
import com.mssus.app.service.*;
import com.mssus.app.util.PolylineDistance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SharedRideServiceImpl implements SharedRideService {

    private final SharedRideRepository rideRepository;
    private final SharedRideRequestRepository requestRepository;
    private final DriverProfileRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final SharedRideMapper rideMapper;
    private final RoutingService routingService;
    private final BookingWalletService bookingWalletService;
    private final PricingConfigRepository pricingConfigRepository;
    private final RideTrackRepository trackRepository;
    private final RideTrackingService rideTrackingService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public SharedRideResponse createRide(CreateRideRequest request, Authentication authentication) {
        String username = authentication.getName();
        log.info("Driver {} creating new shared ride", username);

        // Get authenticated driver and active config
        User user = userRepository.findByEmail(username)
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));
        DriverProfile driver = driverRepository.findByUserUserId(user.getUserId())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.driver-profile"));
        PricingConfig activePricingConfig = pricingConfigRepository.findActive(Instant.now())
            .orElseThrow(() -> BaseDomainException.of("pricing-config.not-found.resource"));

        // Validate vehicle ownership
        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
            .orElseThrow(() -> BaseDomainException.formatted("ride.validation.invalid-location",
                "Vehicle not found with ID: " + request.vehicleId()));

        if (!vehicle.getDriver().getDriverId().equals(driver.getDriverId())) {
            throw BaseDomainException.of("ride.unauthorized.not-owner",
                "You don't own this vehicle");
        }

        // Handle locations: Fetch if IDs provided, else use ad-hoc coords
        Location startLoc = null;
        Location endLoc = null;
        double startLat, startLng, endLat, endLng;

        if (request.startLocationId() != null) {
            startLoc = locationRepository.findById(request.startLocationId())
                .orElseThrow(() -> BaseDomainException.formatted("ride.validation.invalid-location",
                    "Start location not found"));
            startLat = startLoc.getLat();
            startLng = startLoc.getLng();
        } else {
            startLat = request.startLatLng().latitude();
            startLng = request.startLatLng().longitude();
        }

        if (request.endLocationId() != null) {
            endLoc = locationRepository.findById(request.endLocationId())
                .orElseThrow(() -> BaseDomainException.formatted("ride.validation.invalid-location",
                    "End location not found"));
            endLat = endLoc.getLat();
            endLng = endLoc.getLng();
        } else {
            endLat = request.endLatLng().latitude();
            endLng = request.endLatLng().longitude();
        }

        // Validate route via RoutingService (using extracted coords)
        // TODO: For MVP, log validation only. In production, reject invalid routes.
        try {
            RouteResponse routeResponse = routingService.getRoute(startLat, startLng, endLat, endLng);
            log.info("Route validated - distance: {} m, duration: {} s",
                routeResponse.distance(), routeResponse.time());

            // Create and populate ride entity
            SharedRide ride = new SharedRide();
            ride.setDriver(driver);
            ride.setVehicle(vehicle);
            ride.setStartLat(startLat);
            ride.setStartLng(startLng);
            ride.setEndLat(endLat);
            ride.setEndLng(endLng);
            ride.setStartLocationId(request.startLocationId());
            ride.setEndLocationId(request.endLocationId());
            ride.setStatus(SharedRideStatus.SCHEDULED);
            ride.setMaxPassengers(1);
            ride.setCurrentPassengers(0);
            ride.setBaseFare(new BigDecimal(activePricingConfig.getBaseFlagVnd()));
            ride.setPerKmRate(new BigDecimal(activePricingConfig.getPerKmVnd()));
            ride.setScheduledTime(request.scheduledDepartureTime());
            ride.setEstimatedDuration((int) Math.ceil(routeResponse.time() / 60.0)); // in minutes
            ride.setEstimatedDistance((float) routeResponse.distance() / 1000); // in km
            ride.setCreatedAt(LocalDateTime.now());

            SharedRide savedRide = rideRepository.save(ride);
            log.info("Ride created successfully - ID: {}, driver: {}, scheduled: {}",
                savedRide.getSharedRideId(), driver.getDriverId(), savedRide.getScheduledTime());

            return buildRideResponse(savedRide, startLoc, endLoc);

        } catch (Exception e) {
            log.error("Route validation failed for start: {}, end: {}", request.startLocationId(),
                request.endLocationId(), e);
            throw BaseDomainException.of("ride.validation.route-validation-failed",
                "Could not validate route: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SharedRideResponse getRideById(Integer rideId) {
        SharedRide ride = rideRepository.findById(rideId)
            .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource", rideId));

        Location startLoc = locationRepository.findById(ride.getStartLocationId()).orElse(null);
        Location endLoc = locationRepository.findById(ride.getEndLocationId()).orElse(null);

        return buildRideResponse(ride, startLoc, endLoc);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SharedRideResponse> getRidesByDriver(Integer driverId, String status,
                                                     Pageable pageable, Authentication authentication) {
        log.info("Fetching rides for driver: {}, status: {}", driverId, status);

        // TODO: For production, validate user has permission to view driver's rides
        // For MVP, allowing any authenticated user

        Page<SharedRide> ridePage;
        if (status != null && !status.isBlank()) {
            SharedRideStatus rideStatus = SharedRideStatus.valueOf(status.toUpperCase());
            ridePage = rideRepository.findByDriverDriverIdAndStatusOrderByScheduledTimeDesc(
                driverId, rideStatus, pageable);
        } else {
            ridePage = rideRepository.findByDriverDriverIdOrderByScheduledTimeDesc(driverId, pageable);
        }

        return ridePage.map(ride -> {
            Location startLoc = locationRepository.findById(ride.getStartLocationId()).orElse(null);
            Location endLoc = locationRepository.findById(ride.getEndLocationId()).orElse(null);
            return buildRideResponse(ride, startLoc, endLoc);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SharedRideResponse> browseAvailableRides(String startTime, String endTime, Pageable pageable) {
        log.info("Browsing available rides - startTime: {}, endTime: {}", startTime, endTime);

        LocalDateTime start = startTime != null ? LocalDateTime.parse(startTime) : LocalDateTime.now();
        LocalDateTime end = endTime != null ? LocalDateTime.parse(endTime) : start.plusHours(2);

        Page<SharedRide> ridePage = rideRepository.findAvailableRides(
            SharedRideStatus.SCHEDULED, start, end, pageable);

        return ridePage.map(ride -> {
            Location startLoc = locationRepository.findById(ride.getStartLocationId()).orElse(null);
            Location endLoc = locationRepository.findById(ride.getEndLocationId()).orElse(null);
            return buildRideResponse(ride, startLoc, endLoc);
        });
    }

    @Override
    @Transactional
    public SharedRideResponse startRide(StartRideRequest request, Authentication authentication) {
        String username = authentication.getName();
        log.info("Driver {} starting ride {}", username, request.rideId());

        // Get ride with pessimistic lock
        SharedRide ride = rideRepository.findByIdForUpdate(request.rideId())
            .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource", request.rideId()));

        LatLng driverCurrentLoc = rideTrackingService.getLatestPosition(ride.getSharedRideId(), 3)
            .orElse(new LatLng(ride.getStartLat(), ride.getStartLng())); // Fallback to start loc

        // Validate driver ownership
        User user = userRepository.findByEmail(username)
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));
        DriverProfile driver = driverRepository.findByUserUserId(user.getUserId())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.driver-profile"));

        if (!ride.getDriver().getDriverId().equals(driver.getDriverId())) {
            throw BaseDomainException.of("ride.unauthorized.not-owner");
        }

        // Validate state
        if (ride.getStatus() != SharedRideStatus.SCHEDULED) {
            throw BaseDomainException.of("ride.validation.invalid-state",
                Map.of("currentState", ride.getStatus()));
        }

        // Ensure ride has at least one confirmed request
        List<SharedRideRequest> confirmedRequests = requestRepository.findBySharedRideSharedRideIdAndStatus(
            request.rideId(), SharedRideRequestStatus.CONFIRMED);

        if (confirmedRequests.isEmpty()) {
            throw BaseDomainException.of("ride.validation.invalid-state",
                "Cannot start ride without confirmed passengers");
        }

        double distanceFromDriverCurrentLocToRiderPickup = PolylineDistance.haversineMeters(
            driverCurrentLoc.latitude(),
            driverCurrentLoc.longitude(),
            confirmedRequests.stream().findFirst().get().getPickupLat(),
            confirmedRequests.stream().findFirst().get().getPickupLng());

        if (distanceFromDriverCurrentLocToRiderPickup > 0.1) {
            throw BaseDomainException.of("ride.validation.too-far-from-pickup",
                "Driver is too far from the pickup location to start the ride");
        }


        // Update ride status
        ride.setStatus(SharedRideStatus.ONGOING);
        ride.setStartedAt(LocalDateTime.now());
        rideRepository.save(ride);

        // Update all CONFIRMED requests to ONGOING
        for (SharedRideRequest sharedRideRequest : confirmedRequests) {
            sharedRideRequest.setStatus(SharedRideRequestStatus.ONGOING);
            sharedRideRequest.setActualPickupTime(LocalDateTime.now());
            requestRepository.save(sharedRideRequest);
        }

        log.info("Ride {} started successfully with {} passengers", request.rideId(), confirmedRequests.size());

        Location startLoc = locationRepository.findById(ride.getStartLocationId()).orElse(null);
        Location endLoc = locationRepository.findById(ride.getEndLocationId()).orElse(null);
        return buildRideResponse(ride, startLoc, endLoc);
    }

    @Override
    @Transactional
    public RideCompletionResponse completeRide(CompleteRideRequest request,
                                               Authentication authentication) {
        Integer rideId = request.rideId();
        String username = authentication.getName();
        log.info("Driver {} completing ride {}", username, request.rideId());

        // Get ride with pessimistic lock
        SharedRide ride = rideRepository.findByIdForUpdate(rideId)
            .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource", rideId));

        LatLng driverCurrentLoc = rideTrackingService.getLatestPosition(ride.getSharedRideId(), 3)
            .orElse(new LatLng(ride.getStartLat(), ride.getStartLng()));

        // Validate driver ownership
        User user = userRepository.findByEmail(username)
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));
        DriverProfile driver = driverRepository.findByUserUserId(user.getUserId())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.driver-profile"));

        if (!ride.getDriver().getDriverId().equals(driver.getDriverId())) {
            throw BaseDomainException.of("ride.unauthorized.not-owner");
        }

        // Validate state
        if (ride.getStatus() != SharedRideStatus.ONGOING) {
            throw BaseDomainException.of("ride.validation.invalid-state",
                Map.of("currentState", ride.getStatus()));
        }

        if (PolylineDistance.haversineMeters(driverCurrentLoc.latitude(),
            driverCurrentLoc.longitude(),
            ride.getEndLat(), ride.getEndLng()) > 0.1) {
            log.warn("Driver {} is too far from the dropoff location to complete ride {}",
                driver.getDriverId(), rideId);
            // For MVP, just log a warning. In production, consider rejecting completion.
        }

        // Compute actual duration from timestamps
        LocalDateTime now = LocalDateTime.now();
        Integer actualDuration = (int) java.time.Duration.between(ride.getStartedAt(), now).toMinutes();
        log.debug("Computed actual duration for ride {}: {} min (from {} to {})",
            rideId, actualDuration, ride.getStartedAt(), now);

        // Compute actual distance from GPS track (primary) or fallback to routing
        Float actualDistance;
        try {
            RideTrack track = trackRepository.findBySharedRideSharedRideId(rideId).orElse(null);
            if (track != null && track.getGpsPoints() != null && track.getGpsPoints().size() > 1) {
                actualDistance = (float) rideTrackingService.computeDistanceFromPoints(track.getGpsPoints());
                log.debug("Computed actual distance for ride {} from GPS: {} km", rideId, actualDistance);
            } else {
                // Fallback to routing query
                RouteResponse actualRoute = routingService.getRoute(
                    ride.getStartLat(), ride.getStartLng(),
                    ride.getEndLat(), ride.getEndLng());
                actualDistance = (float) (actualRoute.distance() / 1000.0);
                log.debug("Computed actual distance for ride {} via routing fallback: {} km", rideId, actualDistance);
            }
        } catch (Exception e) {
            log.warn("Distance computation failed for ride {}: {}. Falling back to estimated.", rideId, e.getMessage());
            actualDistance = ride.getEstimatedDistance();
        }

        // Get all active (CONFIRMED or ONGOING) requests
        List<SharedRideRequest> ongoingRequests = requestRepository.findBySharedRideSharedRideIdAndStatus(
            rideId,
            SharedRideRequestStatus.ONGOING);

        if (ongoingRequests.isEmpty()) {
            log.warn("Completing ride {} without active requests", rideId);
        }
        BigDecimal totalFareCollected = BigDecimal.ZERO;
        BigDecimal platformCommission = BigDecimal.ZERO;
        List<Integer> completedRequestIds = new ArrayList<>();

        // Capture fares from all ONGOING requests
        if (!ongoingRequests.isEmpty()) {
            for (SharedRideRequest sharedRideRequest : ongoingRequests) {
                try {
                    // Capture fare
                    WalletCaptureRequest captureRequest = new WalletCaptureRequest();
                    captureRequest.setUserId(sharedRideRequest.getRider().getRiderId());
                    captureRequest.setBookingId(sharedRideRequest.getSharedRideRequestId());
                    captureRequest.setAmount(sharedRideRequest.getFareAmount());
                    captureRequest.setDriverId(driver.getDriverId());
                    captureRequest.setNote("Ride completion - Request #" + sharedRideRequest.getSharedRideRequestId());

                    bookingWalletService.captureFunds(captureRequest);

                    totalFareCollected = totalFareCollected.add(sharedRideRequest.getFareAmount());

                    // Get commission rate from active pricing config
                    PricingConfig activePricingConfig = pricingConfigRepository.findActive(Instant.now())
                        .orElseThrow(() -> BaseDomainException.of("pricing-config.not-found.resource"));
                    BigDecimal commissionRate = activePricingConfig.getDefaultCommission();

                    platformCommission = platformCommission.add(sharedRideRequest.getFareAmount().multiply(commissionRate));

                    // Update request status
                    sharedRideRequest.setStatus(SharedRideRequestStatus.COMPLETED);
                    sharedRideRequest.setActualDropoffTime(LocalDateTime.now());
                    requestRepository.save(sharedRideRequest);

                    completedRequestIds.add(sharedRideRequest.getSharedRideRequestId());

                    log.info("Captured fare for request {} - amount: {}",
                        sharedRideRequest.getSharedRideRequestId(), sharedRideRequest.getFareAmount());

                } catch (Exception e) {
                    log.error("Failed to capture fare for request {}: {}",
                        sharedRideRequest.getSharedRideRequestId(), e.getMessage(), e);
                }
            }
        }

        // Update ride status and metrics
        ride.setStatus(SharedRideStatus.COMPLETED);
        ride.setCompletedAt(LocalDateTime.now());
        ride.setActualDistance(actualDistance);
        ride.setActualDuration(actualDuration);
        rideRepository.save(ride);

        // Update driver statistics
        // TODO: Move to separate method or use domain events
        BigDecimal driverEarnings = totalFareCollected.subtract(platformCommission);
        driverRepository.updateRideStats(driver.getDriverId(), driverEarnings);

        log.info("Ride {} completed successfully - total fare: {}, driver earnings: {}, commission: {}",
            rideId, totalFareCollected, driverEarnings, platformCommission);

        return RideCompletionResponse.builder()
            .sharedRideId(rideId)
            .status("COMPLETED")
            .actualDistance(actualDistance)
            .actualDuration(actualDuration)
            .totalFareCollected(totalFareCollected)
            .driverEarnings(driverEarnings)
            .platformCommission(platformCommission)
            .completedRequestsCount(completedRequestIds.size())
            .completedAt(ride.getCompletedAt())
            .completedRequests(completedRequestIds)
            .build();
    }

    @Override
    @Transactional
    public SharedRideResponse cancelRide(Integer rideId, String reason, Authentication authentication) {
        String username = authentication.getName();
        log.info("User {} cancelling ride {} - reason: {}", username, rideId, reason);

        // Get ride with pessimistic lock
        SharedRide ride = rideRepository.findByIdForUpdate(rideId)
            .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource", rideId));

        // Validate ownership or admin role
        User user = userRepository.findByEmail(username)
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));

        boolean isAdmin = authentication.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            DriverProfile driver = driverRepository.findByUserUserId(user.getUserId())
                .orElseThrow(() -> BaseDomainException.of("user.not-found.driver-profile"));

            if (!ride.getDriver().getDriverId().equals(driver.getDriverId())) {
                throw BaseDomainException.of("ride.unauthorized.not-owner");
            }
        }

        // Validate state
        if (ride.getStatus() != SharedRideStatus.SCHEDULED) {
            throw BaseDomainException.of("ride.validation.invalid-state",
                Map.of("currentState", ride.getStatus()));
        }

        // Release holds for all CONFIRMED requests
        List<SharedRideRequest> confirmedRequests = requestRepository.findBySharedRideSharedRideIdAndStatus(
            rideId, SharedRideRequestStatus.CONFIRMED);

        for (SharedRideRequest request : confirmedRequests) {
            try {
                // Release wallet hold
                WalletReleaseRequest releaseRequest = new WalletReleaseRequest();
                releaseRequest.setUserId(request.getRider().getRiderId());
                releaseRequest.setBookingId(request.getSharedRideRequestId());
                releaseRequest.setAmount(request.getFareAmount());
                releaseRequest.setNote("Ride cancelled - Request #" + request.getSharedRideRequestId());

                bookingWalletService.releaseFunds(releaseRequest);

                // Update request status
                request.setStatus(SharedRideRequestStatus.CANCELLED);
                requestRepository.save(request);

                log.info("Released hold for request {} - amount: {}",
                    request.getSharedRideRequestId(), request.getFareAmount());

            } catch (Exception e) {
                log.error("Failed to release hold for request {}: {}",
                    request.getSharedRideRequestId(), e.getMessage(), e);
                // Continue with other requests
            }
        }

        // Update ride status
        ride.setStatus(SharedRideStatus.CANCELLED);
        rideRepository.save(ride);

        log.info("Ride {} cancelled successfully", rideId);

        Location startLoc = locationRepository.findById(ride.getStartLocationId()).orElse(null);
        Location endLoc = locationRepository.findById(ride.getEndLocationId()).orElse(null);
        return buildRideResponse(ride, startLoc, endLoc);
    }

    private SharedRideResponse buildRideResponse(SharedRide ride, Location startLoc, Location endLoc) {
        SharedRideResponse response = rideMapper.toResponse(ride);

        if (startLoc != null) {
            response.setStartLocationName(startLoc.getName());
        }
        if (endLoc != null) {
            response.setEndLocationName(endLoc.getName());
        }

        return response;
    }
}


package com.mssus.app.service.impl;

import com.mssus.app.common.enums.*;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.request.ride.CompleteRideReqRequest;
import com.mssus.app.dto.request.ride.CompleteRideRequest;
import com.mssus.app.dto.request.ride.CreateRideRequest;
import com.mssus.app.dto.request.ride.StartRideRequest;
import com.mssus.app.dto.request.ride.StartRideReqRequest;
import com.mssus.app.dto.request.wallet.RideCompleteSettlementRequest;
import com.mssus.app.dto.request.wallet.RideHoldReleaseRequest;
import com.mssus.app.dto.request.wallet.WalletReleaseRequest;
import com.mssus.app.dto.response.RouteResponse;
import com.mssus.app.dto.response.ride.*;
import com.mssus.app.dto.ride.LatLng;
import com.mssus.app.entity.*;
import com.mssus.app.mapper.SharedRideMapper;
import com.mssus.app.repository.*;
import com.mssus.app.service.*;
import com.mssus.app.service.pricing.PricingService;
import com.mssus.app.service.pricing.model.FareBreakdown;
import com.mssus.app.service.pricing.model.MoneyVnd;
import com.mssus.app.service.pricing.model.SettlementResult;
import com.mssus.app.util.PolylineDistance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
//    private final BookingWalletService bookingWalletService;
    private final PricingConfigRepository pricingConfigRepository;
    private final RideTrackRepository trackRepository;
    private final RideTrackingService rideTrackingService;
    private final NotificationService notificationService;
    private final RideFundCoordinatingService rideFundCoordinatingService;


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

        PricingConfig pricingConfig = pricingConfigRepository.findActive(Instant.now())
            .orElseThrow(() -> BaseDomainException.of("pricing-config.not-found.resource"));

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
            ride.setPricingConfig(pricingConfig);
            ride.setStartLocationId(request.startLocationId());
            ride.setEndLocationId(request.endLocationId());
            ride.setStatus(SharedRideStatus.SCHEDULED);
            ride.setMaxPassengers(1);
            ride.setCurrentPassengers(0);
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

        Page<SharedRide> ridePage = rideRepository.findAvailableRides(start, end, pageable);

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

        ride.setStatus(SharedRideStatus.ONGOING);
        ride.setStartedAt(LocalDateTime.now());
        rideRepository.save(ride);

        try {
            rideTrackingService.startTracking(ride.getSharedRideId());
        } catch (Exception trackingEx) {
            log.warn("Failed to start tracking for ride {}: {}", ride.getSharedRideId(), trackingEx.getMessage());
        }

        try {
            notificationService.sendNotification(
                driver.getUser(),
                NotificationType.RIDE_STARTED,
                "Ride Started",
                "Your ride has been started successfully with " + confirmedRequests.size() + " confirmed passengers.",
                null,
                Priority.MEDIUM,
                DeliveryMethod.PUSH,
                "/queue/ride-offers"
            );
            log.info("Ride start notification sent to driver {}", driver.getDriverId());
        } catch (Exception ex) {
            log.warn("Failed to notify driver for ride start {}: {}", ride.getSharedRideId(), ex.getMessage());
        }

        log.info("Ride {} started successfully with {} confirmed passengers awaiting pickup",
            request.rideId(), confirmedRequests.size());

        Location startLoc = locationRepository.findById(ride.getStartLocationId()).orElse(null);
        Location endLoc = locationRepository.findById(ride.getEndLocationId()).orElse(null);
        return buildRideResponse(ride, startLoc, endLoc);
    }

    @Override
    @Transactional
    public SharedRideRequestResponse startRideRequestOfRide(StartRideReqRequest request,
                                                            Authentication authentication) {
        String username = authentication.getName();
        log.info("Driver {} starting ride request {} of ride {}",
            username, request.rideRequestId(), request.rideId());

        SharedRide ride = rideRepository.findByIdForUpdate(request.rideId())
            .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource", request.rideId()));

        SharedRideRequest rideRequest = requestRepository.findById(request.rideRequestId())
            .orElseThrow(() -> BaseDomainException.formatted(
                "ride-request.not-found.resource", request.rideRequestId()));

        // Validate association
        if (rideRequest.getSharedRide() == null
            || !rideRequest.getSharedRide().getSharedRideId().equals(ride.getSharedRideId())) {
            throw BaseDomainException.of("ride.validation.invalid-state",
                "Ride request does not belong to the specified ride");
        }

        // Validate driver ownership
        User user = userRepository.findByEmail(username)
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));
        DriverProfile driver = driverRepository.findByUserUserId(user.getUserId())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.driver-profile"));

        if (!ride.getDriver().getDriverId().equals(driver.getDriverId())) {
            throw BaseDomainException.of("ride.unauthorized.not-owner");
        }

        if (ride.getStatus() != SharedRideStatus.ONGOING) {
            throw BaseDomainException.of("ride.validation.invalid-state",
                Map.of("currentState", ride.getStatus()));
        }

        if (rideRequest.getStatus() != SharedRideRequestStatus.CONFIRMED) {
            throw BaseDomainException.of("ride-request.validation.invalid-state",
                Map.of("currentState", rideRequest.getStatus()));
        }

        LatLng driverCurrentLoc = rideTrackingService.getLatestPosition(ride.getSharedRideId(), 3)
            .orElse(new LatLng(ride.getStartLat(), ride.getStartLng()));

        double distanceToPickup = PolylineDistance.haversineMeters(
            driverCurrentLoc.latitude(),
            driverCurrentLoc.longitude(),
            rideRequest.getPickupLat(),
            rideRequest.getPickupLng());

        if (distanceToPickup > 100) {
            throw BaseDomainException.of("ride.validation.too-far-from-pickup",
                "Driver is too far from the pickup location to start this request");
        }

        rideRequest.setStatus(SharedRideRequestStatus.ONGOING);
        rideRequest.setActualPickupTime(LocalDateTime.now());
        requestRepository.save(rideRequest);

        // Send notification to driver
        try {
            notificationService.sendNotification(
                driver.getUser(),
                NotificationType.REQUEST_STARTED,
                "Passenger Pickup Started",
                "You have started picking up passenger " + rideRequest.getRider().getUser().getFullName(),
                null,
                Priority.MEDIUM,
                DeliveryMethod.PUSH,
                "/queue/ride-offers"
            );
        } catch (Exception ex) {
            log.warn("Failed to notify driver for pickup start (request {}): {}", request.rideRequestId(), ex.getMessage());
        }

        try {
            notificationService.sendNotification(
                rideRequest.getRider().getUser(),
                NotificationType.REQUEST_STARTED,
                "Pickup Started",
                "Your driver has started the pickup process. Please be ready at the pickup location.",
                null,
                Priority.HIGH,
                DeliveryMethod.PUSH,
                "/queue/ride-matching"
            );
        } catch (Exception ex) {
            log.warn("Failed to notify rider for pickup start (request {}): {}", request.rideRequestId(), ex.getMessage());
        }

        log.info("Ride request {} for ride {} is now ONGOING", request.rideRequestId(), request.rideId());

        Location pickupLoc = rideRequest.getPickupLocationId() != null
            ? locationRepository.findById(rideRequest.getPickupLocationId()).orElse(null)
            : null;
        Location dropoffLoc = rideRequest.getDropoffLocationId() != null
            ? locationRepository.findById(rideRequest.getDropoffLocationId()).orElse(null)
            : null;

        return SharedRideRequestResponse.builder()
            .sharedRideRequestId(rideRequest.getSharedRideRequestId())
            .sharedRideId(ride.getSharedRideId())
            .requestKind(rideRequest.getRequestKind().name())
            .riderId(rideRequest.getRider().getRiderId())
            .riderName(rideRequest.getRider().getUser().getFullName())
            .pickupLocationId(rideRequest.getPickupLocationId())
            .pickupLocationName(pickupLoc != null ? pickupLoc.getName() : null)
            .dropoffLocationId(rideRequest.getDropoffLocationId())
            .dropoffLocationName(dropoffLoc != null ? dropoffLoc.getName() : null)
            .pickupLat(rideRequest.getPickupLat())
            .pickupLng(rideRequest.getPickupLng())
            .dropoffLat(rideRequest.getDropoffLat())
            .dropoffLng(rideRequest.getDropoffLng())
            .status(rideRequest.getStatus().name())
            .fareAmount(rideRequest.getTotalFare())
            .originalFare(rideRequest.getSubtotalFare())
            .discountAmount(rideRequest.getDiscountAmount())
            .pickupTime(rideRequest.getPickupTime())
            .estimatedPickupTime(rideRequest.getEstimatedPickupTime())
            .actualPickupTime(rideRequest.getActualPickupTime())
            .estimatedDropoffTime(rideRequest.getEstimatedDropoffTime())
            .specialRequests(rideRequest.getSpecialRequests())
            .createdAt(rideRequest.getCreatedAt())
            .build();
    }

    @Override
    @Transactional
    public RideRequestCompletionResponse completeRideRequestOfRide(CompleteRideReqRequest request, Authentication authentication) {
        Integer rideId = request.rideId();
        String username = authentication.getName();
        log.info("Driver {} completing ride request {}", username, request.rideRequestId());

        // Get ride with pessimistic lock
        SharedRide ride = rideRepository.findByIdForUpdate(rideId)
            .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource", rideId));
        SharedRideRequest rideRequest = requestRepository.findById(request.rideRequestId())
            .orElseThrow(() -> BaseDomainException.formatted("ride-request.not-found.resource", request.rideRequestId()));

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

        if (ride.getStatus() != SharedRideStatus.ONGOING) {
            throw BaseDomainException.of("ride.validation.invalid-state",
                Map.of("currentState", ride.getStatus()));
        }

        if (rideRequest.getStatus() != SharedRideRequestStatus.ONGOING) {
            throw BaseDomainException.of("ride-request.validation.invalid-state",
                Map.of("currentState", rideRequest.getStatus()));
        }

        if (PolylineDistance.haversineMeters(driverCurrentLoc.latitude(),
            driverCurrentLoc.longitude(),
            rideRequest.getDropoffLat(), rideRequest.getDropoffLng()) > 100) {
            log.warn("Driver {} is too far from the request's dropoff location to complete request{}",
                driver.getDriverId(), rideRequest.getSharedRideRequestId());

            throw BaseDomainException.of("ride.validation.too-far-from-dropoff");
        }

        LocalDateTime now = LocalDateTime.now();
        Integer actualDuration = (int) java.time.Duration.between(ride.getStartedAt(), now).toMinutes();
        log.debug("Computed actual duration for ride {}: {} min (from {} to {})",
            rideId, actualDuration, ride.getStartedAt(), now);

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

        PricingConfig activePricingConfig = pricingConfigRepository.findActive(Instant.now())
            .orElseThrow(() -> BaseDomainException.of("pricing-config.not-found.resource"));
        FareBreakdown fareBreakdown = new FareBreakdown(
            activePricingConfig.getVersion(),
            rideRequest.getDistanceMeters(),
            MoneyVnd.VND(activePricingConfig.getBase2KmVnd()),
            MoneyVnd.VND(activePricingConfig.getAfter2KmPerKmVnd()),
            MoneyVnd.VND(rideRequest.getDiscountAmount()),
            MoneyVnd.VND(rideRequest.getSubtotalFare()),
            MoneyVnd.VND(rideRequest.getTotalFare()),
            activePricingConfig.getSystemCommissionRate()
            );
        RideRequestSettledResponse requestSettledResponse = null;

        try {
            RideCompleteSettlementRequest captureRequest = new RideCompleteSettlementRequest();
            captureRequest.setRiderId(rideRequest.getRider().getRiderId());
            captureRequest.setRideRequestId(rideRequest.getSharedRideRequestId());
            captureRequest.setDriverId(driver.getDriverId());
            captureRequest.setNote("Ride completion - Request #" + rideRequest.getSharedRideRequestId());

            requestSettledResponse = rideFundCoordinatingService.settleRideFunds(captureRequest, fareBreakdown);

            rideRequest.setStatus(SharedRideRequestStatus.COMPLETED);
            rideRequest.setActualDropoffTime(LocalDateTime.now());
            requestRepository.save(rideRequest);

            log.info("Captured fare for request {} - amount: {}",
                rideRequest.getSharedRideRequestId(), rideRequest.getTotalFare());

        } catch (Exception e) {
            log.error("Failed to capture fare for request {}: {}",
                rideRequest.getSharedRideRequestId(), e.getMessage(), e);
        }

        BigDecimal driverEarnings = requestSettledResponse.driverEarnings() == null
            ? BigDecimal.ZERO : requestSettledResponse.driverEarnings();

        try {
            notificationService.sendNotification(
                driver.getUser(),
                NotificationType.REQUEST_COMPLETED,
                "Passenger Dropped Off",
                "You have successfully completed the ride for passenger " + rideRequest.getRider().getUser().getFullName(),
                null,
                Priority.MEDIUM,
                DeliveryMethod.PUSH,
                "/queue/ride-offers"
            );
        } catch (Exception ex) {
            log.warn("Failed to notify driver for request completion {}: {}", request.rideRequestId(), ex.getMessage());
        }

        try {
            notificationService.sendNotification(
                rideRequest.getRider().getUser(),
                NotificationType.REQUEST_COMPLETED,
                "Ride Request Completed",
                "Your ride has been completed successfully. Thank you for using our service!",
                null,
                Priority.HIGH,
                DeliveryMethod.PUSH,
                "/queue/ride-matching"
            );
        } catch (Exception ex) {
            log.warn("Failed to notify rider for request completion {}: {}", request.rideRequestId(), ex.getMessage());
        }

        return RideRequestCompletionResponse.builder()
            .sharedRideRequestId(rideRequest.getSharedRideRequestId())
            .sharedRideId(rideId)
            .driverEarningsOfRequest(driverEarnings)
            .platformCommission(requestSettledResponse.systemCommission())
            .requestActualDistance(actualDistance)
            .requestActualDuration(actualDuration)
            .build();
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

        List<SharedRideRequest> activeRequests = requestRepository.findActiveRequestsByRide(
            rideId, SharedRideRequestStatus.CONFIRMED, SharedRideRequestStatus.ONGOING);
        if (!activeRequests.isEmpty()) {
            throw BaseDomainException.of("ride.validation.active-requests",
                "Cannot complete ride while requests are still awaiting pickup/dropoff");
        }

        if (PolylineDistance.haversineMeters(driverCurrentLoc.latitude(),
            driverCurrentLoc.longitude(),
            ride.getEndLat(), ride.getEndLng()) > 100) {
            log.warn("Driver {} is too far from the end location to complete ride {}",
                driver.getDriverId(), rideId);
            // TODO: consider rejecting completion.
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
        List<SharedRideRequest> rideRequests = requestRepository.findBySharedRideSharedRideIdAndStatus(
            rideId,
            SharedRideRequestStatus.COMPLETED);

        List<SharedRideRequest> ongoingRequests = requestRepository.findBySharedRideSharedRideIdAndStatus(
            rideId, SharedRideRequestStatus.ONGOING);

        if (!ongoingRequests.isEmpty()) {
            throw BaseDomainException.of("ride.validation.incomplete-requests");
        }

        BigDecimal totalFareCollected = BigDecimal.ZERO;
        BigDecimal platformCommission = BigDecimal.ZERO;
        List<Integer> completedRequestIds = new ArrayList<>();

        PricingConfig activePricingConfig = pricingConfigRepository.findActive(Instant.now())
            .orElseThrow(() -> BaseDomainException.of("pricing-config.not-found.resource"));

        for (SharedRideRequest completedRequest : rideRequests) {
            totalFareCollected = totalFareCollected.add(completedRequest.getTotalFare());

            BigDecimal commissionRate = activePricingConfig.getSystemCommissionRate();
            BigDecimal requestCommission = completedRequest.getTotalFare().multiply(commissionRate);
            platformCommission = platformCommission.add(requestCommission);

            completedRequestIds.add(completedRequest.getSharedRideRequestId());
        }

        ride.setStatus(SharedRideStatus.COMPLETED);
        ride.setCompletedAt(LocalDateTime.now());
        ride.setActualDistance(actualDistance);
        ride.setActualDuration(actualDuration);
        ride.setPricingConfig(activePricingConfig);
        ride.setDriverEarnedAmount(totalFareCollected.subtract(platformCommission));
        rideRepository.save(ride);


        // TODO: Move to separate method or use domain events
        BigDecimal driverEarnings = totalFareCollected.subtract(platformCommission);
        driverRepository.updateRideStats(driver.getDriverId(), driverEarnings);

        try {
            rideTrackingService.stopTracking(ride.getSharedRideId());
        } catch (Exception trackingEx) {
            log.warn("Failed to stop tracking for completed ride {}: {}", ride.getSharedRideId(), trackingEx.getMessage());
        }

        // Send notification to driver
        try {
            notificationService.sendNotification(
                driver.getUser(),
                NotificationType.RIDE_COMPLETED,
                "Ride Completed",
                String.format("Your ride has been completed. Total earnings: %s VND from %d passenger(s).",
                    driverEarnings.toString(), completedRequestIds.size()),
                null,
                Priority.MEDIUM,
                DeliveryMethod.PUSH,
                "/queue/ride-offers"
            );
            log.info("Ride completion notification sent to driver {}", driver.getDriverId());
        } catch (Exception ex) {
            log.warn("Failed to notify driver for ride completion {}: {}", ride.getSharedRideId(), ex.getMessage());
        }

        log.info("Ride {} completed successfully - total fare: {}, driver earnings: {}, commission: {}",
            rideId, totalFareCollected, driverEarnings, platformCommission);

        return RideCompletionResponse.builder()
            .sharedRideId(rideId)
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
                RideHoldReleaseRequest releaseRequest = RideHoldReleaseRequest.builder()
                    .riderId(request.getRider().getRiderId())
                    .rideRequestId(request.getSharedRideRequestId())
                    .note("Ride cancelled - Request #" + request.getSharedRideRequestId())
                    .build();

                rideFundCoordinatingService.releaseRideFunds(releaseRequest);

                // Update request status
                request.setStatus(SharedRideRequestStatus.CANCELLED);
                requestRepository.save(request);

                log.info("Released hold for request {} - amount: {}",
                    request.getSharedRideRequestId(), request.getTotalFare());

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


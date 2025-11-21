package com.mssus.app.service.impl;

import com.mssus.app.common.enums.*;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.appconfig.config.properties.RideConfigurationProperties;
import com.mssus.app.dto.request.ride.CompleteRideReqRequest;
import com.mssus.app.dto.request.ride.CreateRideRequest;
import com.mssus.app.dto.request.ride.StartRideRequest;
import com.mssus.app.dto.request.ride.StartRideReqRequest;
import com.mssus.app.dto.request.wallet.RideCompleteSettlementRequest;
import com.mssus.app.dto.request.wallet.RideHoldReleaseRequest;
import com.mssus.app.dto.response.LocationResponse;
import com.mssus.app.dto.response.RouteResponse;
import com.mssus.app.dto.response.ride.*;
import com.mssus.app.dto.domain.ride.LatLng;
import com.mssus.app.entity.*;
import com.mssus.app.mapper.LocationMapper;
import com.mssus.app.mapper.SharedRideMapper;
import com.mssus.app.repository.*;
import com.mssus.app.service.*;
import com.mssus.app.service.domain.matching.MatchingResponseAssembler;
import com.mssus.app.service.domain.pricing.model.FareBreakdown;
import com.mssus.app.service.domain.pricing.model.MoneyVnd;
import com.mssus.app.common.util.GeoUtil;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private final PricingConfigRepository pricingConfigRepository;
    private final RideTrackRepository trackRepository;
    private final RideTrackingService rideTrackingService;
    private final NotificationService notificationService;
    private final RideFundCoordinatingService rideFundCoordinatingService;
    private final LocationMapper locationMapper;
    private final RideConfigurationProperties rideConfig;
    private final RouteAssignmentService routeAssignmentService;
    private final RealTimeNotificationService realTimeNotificationService;
    private final MatchingResponseAssembler responseAssembler;

    @Override
    @Transactional
    public SharedRideResponse createRide(CreateRideRequest request, Authentication authentication) {
        String username = authentication.getName();
        log.info("Driver {} creating new shared ride", username);

        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));
        DriverProfile driver = driverRepository.findByUserUserId(user.getUserId())
                .orElseThrow(() -> BaseDomainException.of("user.not-found.driver-profile"));

        Vehicle vehicle = vehicleRepository.findByDriver_DriverId(driver.getDriverId())
                .orElseThrow(() -> BaseDomainException.formatted("ride.validation.vehicle-not-found"));

        if (!vehicle.getDriver().getDriverId().equals(driver.getDriverId())) {
            throw BaseDomainException.of("ride.unauthorized.not-owner",
                    "You don't own this vehicle");
        }

        Optional<SharedRide> latestRide = rideRepository
                .findLatestScheduledRideByDriverId(driver.getDriverId());
        if (latestRide.isPresent()) {
            SharedRide lastRide = latestRide.get();
            LocalDateTime referenceTime = lastRide.getScheduledTime();

            if (lastRide.getStatus() == SharedRideStatus.COMPLETED && lastRide.getCompletedAt() != null) {
                referenceTime = lastRide.getCompletedAt();
            }

            LocalDateTime minAllowedTimeForNewRide = referenceTime
                    .plus(rideConfig.getRideConstraints().getMinIntervalBetweenRides());

            LocalDateTime newRideStartTime = request.scheduledDepartureTime() != null
                    ? request.scheduledDepartureTime()
                    : LocalDateTime.now();

            boolean isLastRideActive = lastRide.getStatus() != SharedRideStatus.COMPLETED
                    && lastRide.getStatus() != SharedRideStatus.CANCELLED;

            if (newRideStartTime.isBefore(minAllowedTimeForNewRide) && isLastRideActive) {
                String formattedTime = minAllowedTimeForNewRide
                        .format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
                throw BaseDomainException.withContext(
                        "ride.validation.invalid-scheduled-time",
                        Map.of("reason", "Chuyến xe tiếp theo phải sau " + formattedTime));
            }
        }

        Route templateRoute = null;
        if (request.routeId() != null) {
            templateRoute = routeAssignmentService.getRoute(request.routeId());
            log.info("Using predefined route {} with locations {} -> {}",
                    templateRoute.getRouteId(),
                    templateRoute.getFromLocation() != null ? templateRoute.getFromLocation().getLocationId() : null,
                    templateRoute.getToLocation() != null ? templateRoute.getToLocation().getLocationId() : null);
        }

        Location startLoc;
        Location endLoc;

        if (templateRoute != null) {
            startLoc = templateRoute.getFromLocation();
            endLoc = templateRoute.getToLocation();
        } else {
            startLoc = findOrCreateLocation(request.startLocationId(), request.startLatLng(), "Start");
            endLoc = findOrCreateLocation(request.endLocationId(), request.endLatLng(), "End");
        }

        PricingConfig pricingConfig = pricingConfigRepository.findActive(Instant.now())
                .orElseThrow(() -> BaseDomainException.of("pricing-config.not-found.resource"));

        try {
            RouteResponse routeResponse = null;
            if (templateRoute == null) {
                routeResponse = routingService.getRoute(
                        startLoc.getLat(), startLoc.getLng(),
                        endLoc.getLat(), endLoc.getLng());
                log.info("Route validated - distance: {} m, duration: {} s",
                        routeResponse.distance(), routeResponse.time());
            }

            Route resolvedRoute = routeAssignmentService.resolveRoute(
                    request.routeId(), startLoc, endLoc,
                    templateRoute != null && templateRoute.getPolyline() != null
                            ? templateRoute.getPolyline()
                            : routeResponse != null ? routeResponse.polyline() : null);

            SharedRide ride = new SharedRide();
            ride.setDriver(driver);
            ride.setVehicle(vehicle);
            ride.setStartLocation(startLoc);
            ride.setEndLocation(endLoc);
            ride.setRoute(resolvedRoute);
            ride.setPricingConfig(pricingConfig);
            ride.setStatus(request.scheduledDepartureTime() != null
                    ? SharedRideStatus.SCHEDULED
                    : SharedRideStatus.ONGOING);
            ride.setScheduledTime(request.scheduledDepartureTime() != null
                    ? request.scheduledDepartureTime()
                    : LocalDateTime.now());
            if (templateRoute != null) {
                if (templateRoute.getDurationSeconds() != null) {
                    ride.setEstimatedDuration((int) Math.ceil(templateRoute.getDurationSeconds() / 60.0));
                }
                if (templateRoute.getDistanceMeters() != null) {
                    ride.setEstimatedDistance(templateRoute.getDistanceMeters() / 1000F);
                }
            } else if (routeResponse != null) {
                ride.setEstimatedDuration((int) Math.ceil(routeResponse.time() / 60.0)); // in minutes
                ride.setEstimatedDistance((float) routeResponse.distance() / 1000); // in km
            }
            ride.setCreatedAt(LocalDateTime.now());
            ride.setStartedAt(request.scheduledDepartureTime() == null
                    ? LocalDateTime.now()
                    : null);

            SharedRide savedRide = rideRepository.save(ride);
            log.info("Ride created successfully - ID: {}, driver: {}, scheduled: {}",
                    savedRide.getSharedRideId(), driver.getDriverId(),
                    savedRide.getScheduledTime());

            if (request.scheduledDepartureTime() == null) {
                rideTrackingService.startTracking(savedRide.getSharedRideId());
            }

            return buildRideResponse(savedRide/* , startLoc, endLoc */);

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

        // Location startLoc =
        // locationRepository.findById(ride.getStartLocationId()).orElse(null);
        // Location endLoc =
        // locationRepository.findById(ride.getEndLocationId()).orElse(null);
        //
        // return buildRideResponse(ride, startLoc, endLoc);

        return buildRideResponse(ride);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SharedRideResponse> getRidesByDriver(Integer driverId, String status,
            Pageable pageable, Authentication authentication) {
        log.info("Fetching rides for driver: {}, status: {}", driverId, status);

        Page<SharedRide> ridePage;
        if (status != null && !status.isBlank()) {
            SharedRideStatus rideStatus = SharedRideStatus.valueOf(status.toUpperCase());
            ridePage = rideRepository.findByDriverDriverIdAndStatusOrderByScheduledTimeDesc(
                    driverId, rideStatus, pageable);
        } else {
            ridePage = rideRepository.findByDriverDriverIdOrderByScheduledTimeDesc(driverId, pageable);
        }

        // Location startLoc =
        // locationRepository.findById(ride.getStartLocationId()).orElse(null);
        // Location endLoc =
        // locationRepository.findById(ride.getEndLocationId()).orElse(null);
        // return buildRideResponse(ride, startLoc, endLoc);
        return ridePage.map(this::buildRideResponse);
    }

    @Override
    public Page<SharedRideResponse> getRidesByDriver(String status, Pageable pageable,
            Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));

        DriverProfile driver = driverRepository.findByUserUserId(user.getUserId())
                .orElseThrow(() -> BaseDomainException.of("user.not-found.driver-profile"));

        Integer driverId = driver.getDriverId();

        Page<SharedRide> ridePage;
        if (status != null && !status.isBlank()) {
            SharedRideStatus rideStatus = SharedRideStatus.valueOf(status.toUpperCase());
            ridePage = rideRepository.findByDriverDriverIdAndStatusOrderByScheduledTimeDesc(
                    driverId, rideStatus, pageable);
        } else {
            ridePage = rideRepository.findByDriverDriverIdOrderByScheduledTimeDesc(driverId, pageable);
        }

        // Location startLoc =
        // locationRepository.findById(ride.getStartLocationId()).orElse(null);
        // Location endLoc =
        // locationRepository.findById(ride.getEndLocationId()).orElse(null);
        // return buildRideResponse(ride, startLoc, endLoc);
        return ridePage.map(this::buildRideResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SharedRideResponse> browseAvailableRides(String startTime, String endTime, Pageable pageable) {
        log.info("Browsing available rides - startTime: {}, endTime: {}", startTime, endTime);

        LocalDateTime start = startTime != null ? LocalDateTime.parse(startTime) : LocalDateTime.now();
        LocalDateTime end = endTime != null ? LocalDateTime.parse(endTime) : start.plusHours(2);

        Page<SharedRide> ridePage = rideRepository.findAvailableRides(start, end, pageable);

        // Location startLoc =
        // locationRepository.findById(ride.getStartLocationId()).orElse(null);
        // Location endLoc =
        // locationRepository.findById(ride.getEndLocationId()).orElse(null);
        // return buildRideResponse(ride, startLoc, endLoc);
        return ridePage.map(this::buildRideResponse);
    }

    @Override
    @Transactional
    public SharedRideResponse startRide(StartRideRequest request, Authentication authentication) {
        String username = authentication.getName();
        log.info("Driver {} starting ride {}", username, request.rideId());

        SharedRide ride = rideRepository.findByIdForUpdate(request.rideId())
                .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource",
                        request.rideId()));

        // LatLng driverCurrentLoc =
        // rideTrackingService.getLatestPosition(ride.getSharedRideId(), 3)
        // .orElse(new LatLng(ride.getStartLocation().getLat(),
        // ride.getStartLocation().getLng())); // Fallback to start loc

        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));
        DriverProfile driver = driverRepository.findByUserUserId(user.getUserId())
                .orElseThrow(() -> BaseDomainException.of("user.not-found.driver-profile"));

        if (!ride.getDriver().getDriverId().equals(driver.getDriverId())) {
            throw BaseDomainException.of("ride.unauthorized.not-owner");
        }

        if (ride.getStatus() != SharedRideStatus.SCHEDULED) {
            throw BaseDomainException.of("ride.validation.invalid-state",
                    Map.of("currentState", ride.getStatus()));
        }

        // Ensure ride has at least one confirmed request
        // List<SharedRideRequest> confirmedRequests =
        // requestRepository.findBySharedRideSharedRideIdAndStatus(
        // request.rideId(), SharedRideRequestStatus.CONFIRMED);
        //
        // if (confirmedRequests.isEmpty()) {
        // throw BaseDomainException.of("ride.validation.invalid-state",
        // "Cannot start ride without confirmed passengers");
        // }

        // double distanceFromDriverCurrentLocToRiderPickup =
        // PolylineDistance.haversineMeters(
        // driverCurrentLoc.latitude(),
        // driverCurrentLoc.longitude(),
        // confirmedRequests.stream().findFirst().get().getPickupLat(),
        // confirmedRequests.stream().findFirst().get().getPickupLng());
        //
        // if (distanceFromDriverCurrentLocToRiderPickup > 0.1) {
        // throw BaseDomainException.of("ride.validation.too-far-from-pickup",
        // "Driver is too far from the pickup location to start the ride");
        // }

        ride.setStatus(SharedRideStatus.ONGOING);
        ride.setStartedAt(LocalDateTime.now());
        rideRepository.save(ride);

        try {
            rideTrackingService.startTracking(ride.getSharedRideId());
        } catch (Exception trackingEx) {
            log.warn("Failed to start tracking for ride {}: {}", ride.getSharedRideId(),
                    trackingEx.getMessage());
        }

        try {
            notificationService.sendNotification(
                    driver.getUser(),
                    NotificationType.RIDE_STARTED,
                    "Ride Started",
                    "Your ride has been started successfully.",
                    null,
                    Priority.MEDIUM,
                    DeliveryMethod.PUSH,
                    "/queue/ride-offers");
            log.info("Ride start notification sent to driver {}", driver.getDriverId());
        } catch (Exception ex) {
            log.warn("Failed to notify driver for ride start {}: {}", ride.getSharedRideId(),
                    ex.getMessage());
        }

        // log.info("Ride {} started successfully with {} confirmed passengers awaiting
        // pickup",
        // request.rideId(), confirmedRequests.size());

        // Location startLoc =
        // locationRepository.findById(ride.getStartLocationId()).orElse(null);
        // Location endLoc =
        // locationRepository.findById(ride.getEndLocationId()).orElse(null);
        // return buildRideResponse(ride, startLoc, endLoc);
        return buildRideResponse(ride);
    }

    @Override
    @Transactional
    public SharedRideRequestResponse startRideRequestOfRide(StartRideReqRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        log.info("Driver {} starting ride request {} of ride {}",
                username, request.rideRequestId(), request.rideId());

        SharedRide ride = rideRepository.findByIdForUpdate(request.rideId())
                .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource",
                        request.rideId()));

        SharedRideRequest rideRequest = requestRepository.findById(request.rideRequestId())
                .orElseThrow(() -> BaseDomainException.formatted(
                        "ride-request.not-found.resource", request.rideRequestId()));

        if (rideRequest.getSharedRide() == null
                || !rideRequest.getSharedRide().getSharedRideId().equals(ride.getSharedRideId())) {
            throw BaseDomainException.of("ride.validation.invalid-state",
                    "Ride request does not belong to the specified ride");
        }

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
                .orElse(new LatLng(ride.getStartLocation().getLat(), ride.getStartLocation().getLng()));

        double distanceToPickup = GeoUtil.haversineMeters(
                driverCurrentLoc.latitude(),
                driverCurrentLoc.longitude(),
                rideRequest.getPickupLocation().getLat(),
                rideRequest.getPickupLocation().getLng());

        if (distanceToPickup > 100) {
            throw BaseDomainException.of("ride.validation.too-far-from-pickup",
                    "Driver is too far from the pickup location to start this request");
        }

        rideRequest.setStatus(SharedRideRequestStatus.ONGOING);
        rideRequest.setActualPickupTime(LocalDateTime.now());
        requestRepository.save(rideRequest);

        try {
            notificationService.sendNotification(
                    driver.getUser(),
                    NotificationType.REQUEST_STARTED,
                    "Passenger Pickup Started",
                    "You have started picking up passenger "
                            + rideRequest.getRider().getUser().getFullName(),
                    null,
                    Priority.MEDIUM,
                    DeliveryMethod.IN_APP,
                    null);
        } catch (Exception ex) {
            log.warn("Failed to notify driver for pickup start (request {}): {}", request.rideRequestId(),
                    ex.getMessage());
        }

        try {

            notificationService.sendNotification(
                    rideRequest.getRider().getUser(),
                    NotificationType.REQUEST_STARTED,
                    "Pickup Started",
                    "Your driver has started the pickup process. Please be ready at the pickup location.",
                    null,
                    Priority.HIGH,
                    DeliveryMethod.IN_APP,
                    null);
        } catch (Exception ex) {
            log.warn("Failed to notify rider for pickup start (request {}): {}", request.rideRequestId(),
                    ex.getMessage());
        }

        log.info("Ride request {} for ride {} is now ONGOING", request.rideRequestId(), request.rideId());

        // Location pickupLoc = rideRequest.getPickupLocationId() != null
        // ? locationRepository.findById(rideRequest.getPickupLocationId()).orElse(null)
        // : null;
        // Location dropoffLoc = rideRequest.getDropoffLocationId() != null
        // ?
        // locationRepository.findById(rideRequest.getDropoffLocationId()).orElse(null)
        // : null;

        LocationResponse pickupLoc = locationMapper.toResponse(rideRequest.getPickupLocation());
        LocationResponse dropoffLoc = locationMapper.toResponse(rideRequest.getDropoffLocation());

        return SharedRideRequestResponse.builder()
                .sharedRideRequestId(rideRequest.getSharedRideRequestId())
                .sharedRideId(ride.getSharedRideId())
                .requestKind(rideRequest.getRequestKind().name())
                .riderId(rideRequest.getRider().getRiderId())
                .riderName(rideRequest.getRider().getUser().getFullName())
                .pickupLocation(pickupLoc)
                .dropoffLocation(dropoffLoc)
                // .pickupLocationId(rideRequest.getPickupLocationId())
                // .pickupLocationName(pickupLoc != null ? pickupLoc.getName() : null)
                // .dropoffLocationId(rideRequest.getDropoffLocationId())
                // .dropoffLocationName(dropoffLoc != null ? dropoffLoc.getName() : null)
                // .pickupLat(rideRequest.getPickupLat())
                // .pickupLng(rideRequest.getPickupLng())
                // .dropoffLat(rideRequest.getDropoffLat())
                // .dropoffLng(rideRequest.getDropoffLng())
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
    public RideCompletionResponse completeRide(CompleteRideReqRequest request, Authentication authentication) {
        Integer rideId = request.rideId();
        Integer resolvedRideRequestId = request.rideRequestId();
        String username = authentication.getName();
        log.info("Driver {} completing ride {} request {}", username, rideId, resolvedRideRequestId);

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
            throw BaseDomainException.of("ride.validation.invalid-state",
                    Map.of("currentState", ride.getStatus()));
        }

        SharedRideRequest rideRequest = null;
        if (resolvedRideRequestId != null) {
            final Integer requestIdLookup = resolvedRideRequestId;
            rideRequest = requestRepository.findById(requestIdLookup)
                    .orElseThrow(() -> BaseDomainException
                            .formatted("ride-request.not-found.resource", requestIdLookup));
        } else {
            rideRequest = requestRepository
                    .findBySharedRideSharedRideIdAndStatus(rideId, SharedRideRequestStatus.ONGOING)
                    .stream()
                    .findFirst()
                    .orElse(null);

            if (rideRequest != null) {
                resolvedRideRequestId = rideRequest.getSharedRideRequestId();
                log.info("Auto-selected ride request {} for ride {}", resolvedRideRequestId, rideId);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Integer actualDuration = (int) Duration.between(ride.getStartedAt(), now).toMinutes();
        Float actualDistance = resolveActualDistance(rideId, ride);

        PricingConfig activePricingConfig = pricingConfigRepository.findActive(Instant.now())
                .orElseThrow(() -> BaseDomainException.of("pricing-config.not-found.resource"));

        if (rideRequest == null) {
            boolean hasActiveRequests = requestRepository.existsBySharedRideSharedRideIdAndStatusIn(
                    rideId,
                    List.of(SharedRideRequestStatus.CONFIRMED, SharedRideRequestStatus.ONGOING));

            if (hasActiveRequests) {
                throw BaseDomainException.of("ride-request.validation.invalid-state",
                        Map.of("currentState", SharedRideRequestStatus.ONGOING));
            }

            boolean rideCompleted = finalizeRideCompletion(
                    ride, driver, activePricingConfig, actualDistance, actualDuration, now);

            BigDecimal driverEarnings = rideCompleted && ride.getDriverEarnedAmount() != null
                    ? ride.getDriverEarnedAmount()
                    : BigDecimal.ZERO;

            LocalDateTime completedAt = rideCompleted && ride.getCompletedAt() != null
                    ? ride.getCompletedAt()
                    : now;

            return RideCompletionResponse.builder()
                    .sharedRideId(rideId)
                    .sharedRideRequestId(null)
                    .actualDistance(actualDistance)
                    .actualDuration(actualDuration)
                    .driverEarnings(driverEarnings)
                    .completedAt(completedAt)
                    .build();
        }

        if (!rideRequest.getSharedRide().getSharedRideId().equals(rideId)) {
            throw BaseDomainException.of("ride-request.validation.invalid-parent");
        }

        if (rideRequest.getStatus() != SharedRideRequestStatus.ONGOING) {
            throw BaseDomainException.of("ride-request.validation.invalid-state",
                    Map.of("currentState", rideRequest.getStatus()));
        }

        LatLng driverCurrentLoc = rideTrackingService.getLatestPosition(ride.getSharedRideId(), 3)
                .orElse(new LatLng(ride.getStartLocation().getLat(), ride.getStartLocation().getLng()));

        double distanceToDropoff = GeoUtil.haversineMeters(
                driverCurrentLoc.latitude(),
                driverCurrentLoc.longitude(),
                rideRequest.getDropoffLocation().getLat(),
                rideRequest.getDropoffLocation().getLng());

        if (distanceToDropoff > 100d) {
            log.warn("Driver {} is too far from the request {} dropoff ({}m)",
                    driver.getDriverId(), rideRequest.getSharedRideRequestId(), distanceToDropoff);
            throw BaseDomainException.of("ride.validation.too-far-from-dropoff");
        }

        RideRequestSettledResponse requestSettledResponse;
        try {
            RideCompleteSettlementRequest settlementRequest = new RideCompleteSettlementRequest();
            settlementRequest.setRiderId(rideRequest.getRider().getRiderId());
            settlementRequest.setRideRequestId(rideRequest.getSharedRideRequestId());
            settlementRequest.setDriverId(driver.getDriverId());
            settlementRequest.setRideId(ride.getSharedRideId());
            settlementRequest.setNote("Ride completion - Request #" + rideRequest.getSharedRideRequestId());

            FareBreakdown fareBreakdown = new FareBreakdown(
                    activePricingConfig.getVersion(),
                    rideRequest.getDistanceMeters(),
                    MoneyVnd.VND(rideRequest.getDiscountAmount()),
                    MoneyVnd.VND(rideRequest.getSubtotalFare()),
                    MoneyVnd.VND(rideRequest.getTotalFare()),
                    activePricingConfig.getSystemCommissionRate());

            requestSettledResponse = rideFundCoordinatingService.settleRideFunds(settlementRequest,
                    fareBreakdown);

            rideRequest.setStatus(SharedRideRequestStatus.COMPLETED);
            rideRequest.setActualDropoffTime(now);
            requestRepository.save(rideRequest);

            log.info("Captured fare for request {} - amount: {}",
                    rideRequest.getSharedRideRequestId(), rideRequest.getTotalFare());
        } catch (Exception e) {
            log.error("Failed to capture fare for request {}: {}",
                    rideRequest.getSharedRideRequestId(), e.getMessage(), e);
            throw BaseDomainException.of("ride-request.settlement.failed");
        }

        try {
            notificationService.sendNotification(
                    driver.getUser(),
                    NotificationType.REQUEST_COMPLETED,
                    "Passenger Dropped Off",
                    "You have successfully completed the ride for passenger "
                            + rideRequest.getRider().getUser().getFullName(),
                    null,
                    Priority.MEDIUM,
                    DeliveryMethod.IN_APP,
                    null);
        } catch (Exception ex) {
            log.warn("Failed to notify driver for request completion {}: {}", resolvedRideRequestId,
                    ex.getMessage());
        }

        try {
            notificationService.sendNotification(
                    rideRequest.getRider().getUser(),
                    NotificationType.REQUEST_COMPLETED,
                    "Ride Request Completed",
                    "Your ride has been completed successfully. Thank you for using our service!",
                    null,
                    Priority.HIGH,
                    DeliveryMethod.IN_APP,
                    null);
        } catch (Exception ex) {
            log.warn("Failed to notify rider for request completion {}: {}", resolvedRideRequestId,
                    ex.getMessage());
        }

        try {
            if (realTimeNotificationService != null && responseAssembler != null) {
                var riderStatusUpdate = responseAssembler.toRiderCompleted(rideRequest);
                realTimeNotificationService.notifyRiderStatus(
                        rideRequest.getRider().getUser(),
                        riderStatusUpdate);
                log.info("WebSocket ride matching update sent to rider {} for completed request {}",
                        rideRequest.getRider().getUser().getUserId(), resolvedRideRequestId);
            }
        } catch (Exception ex) {
            log.warn("Failed to send WebSocket ride matching update for request completion {}: {}",
                    resolvedRideRequestId,
                    ex.getMessage());
        }

        boolean rideCompleted = finalizeRideCompletion(
                ride, driver, activePricingConfig, actualDistance, actualDuration, now);

        BigDecimal driverEarnings = requestSettledResponse.driverEarnings() == null
                ? BigDecimal.ZERO
                : requestSettledResponse.driverEarnings();
        LocalDateTime completedAt = rideRequest.getActualDropoffTime();

        if (rideCompleted) {
            driverEarnings = ride.getDriverEarnedAmount();
            completedAt = ride.getCompletedAt();
        }

        return RideCompletionResponse.builder()
                .sharedRideId(rideId)
                .sharedRideRequestId(resolvedRideRequestId)
                .actualDistance(actualDistance)
                .actualDuration(actualDuration)
                .driverEarnings(driverEarnings)
                .completedAt(completedAt)
                .build();
    }

    private Float resolveActualDistance(Integer rideId, SharedRide ride) {
        try {
            RideTrack track = trackRepository.findBySharedRideSharedRideId(rideId).orElse(null);
            if (track != null && track.getGpsPoints() != null && track.getGpsPoints().size() > 1) {
                Float distance = (float) rideTrackingService
                        .computeDistanceFromPoints(track.getGpsPoints());
                log.debug("Computed actual distance for ride {} from GPS: {} km", rideId, distance);
                return distance;
            }

            RouteResponse actualRoute = routingService.getRoute(
                    ride.getStartLocation().getLat(),
                    ride.getStartLocation().getLng(),
                    ride.getEndLocation().getLat(),
                    ride.getEndLocation().getLng());
            Float fallback = (float) (actualRoute.distance() / 1000.0);
            log.debug("Computed actual distance for ride {} via routing fallback: {} km", rideId, fallback);
            return fallback;
        } catch (Exception e) {
            log.warn("Distance computation failed for ride {}: {}. Falling back to estimated.", rideId,
                    e.getMessage());
            return ride.getEstimatedDistance();
        }
    }

    private boolean finalizeRideCompletion(SharedRide ride,
            DriverProfile driver,
            PricingConfig pricingConfig,
            Float actualDistance,
            Integer actualDuration,
            LocalDateTime completionTime) {
        List<SharedRideRequest> activeRequests = requestRepository.findActiveRequestsByRide(
                ride.getSharedRideId(), SharedRideRequestStatus.CONFIRMED,
                SharedRideRequestStatus.ONGOING);
        if (!activeRequests.isEmpty()) {
            return false;
        }

        List<SharedRideRequest> completedRequests = requestRepository.findBySharedRideSharedRideIdAndStatus(
                ride.getSharedRideId(), SharedRideRequestStatus.COMPLETED);

        BigDecimal totalFareCollected = BigDecimal.ZERO;
        BigDecimal platformCommission = BigDecimal.ZERO;
        BigDecimal commissionRate = pricingConfig.getSystemCommissionRate();

        for (SharedRideRequest completedRequest : completedRequests) {
            totalFareCollected = totalFareCollected.add(completedRequest.getTotalFare());
            platformCommission = platformCommission
                    .add(completedRequest.getTotalFare().multiply(commissionRate));
        }

        BigDecimal driverEarnings = totalFareCollected.subtract(platformCommission);

        ride.setStatus(SharedRideStatus.COMPLETED);
        ride.setCompletedAt(completionTime);
        ride.setActualDistance(actualDistance);
        ride.setActualDuration(actualDuration);
        ride.setPricingConfig(pricingConfig);
        ride.setDriverEarnedAmount(driverEarnings);
        rideRepository.save(ride);

        driverRepository.updateRideStats(driver.getDriverId(), driverEarnings);

        try {
            rideTrackingService.stopTracking(ride.getSharedRideId());
        } catch (Exception trackingEx) {
            log.warn("Failed to stop tracking for completed ride {}: {}", ride.getSharedRideId(),
                    trackingEx.getMessage());
        }

        int completedCount = completedRequests.size();
        try {
            notificationService.sendNotification(
                    driver.getUser(),
                    NotificationType.RIDE_COMPLETED,
                    "Ride Completed",
                    String.format("Your ride has been completed. Total earnings: %s VND from %d passenger(s).",
                            driverEarnings, completedCount),
                    null,
                    Priority.MEDIUM,
                    DeliveryMethod.PUSH,
                    "/queue/ride-offers");
            log.info("Ride completion notification sent to driver {}", driver.getDriverId());
        } catch (Exception ex) {
            log.warn("Failed to notify driver for ride completion {}: {}", ride.getSharedRideId(),
                    ex.getMessage());
        }

        log.info("Ride {} completed successfully - total fare: {}, driver earnings: {}, commission: {}",
                ride.getSharedRideId(), totalFareCollected, driverEarnings, platformCommission);
        return true;
    }

    @Override
    @Transactional
    public SharedRideResponse cancelRide(Integer rideId, String reason, Authentication authentication) {
        String username = authentication.getName();
        log.info("User {} cancelling ride {} - reason: {}", username, rideId, reason);

        SharedRide ride = rideRepository.findByIdForUpdate(rideId)
                .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource", rideId));

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

        if (ride.getStatus() != SharedRideStatus.SCHEDULED && ride.getStatus() != SharedRideStatus.ONGOING) {
            throw BaseDomainException.of("ride.validation.invalid-state",
                    Map.of("currentState", ride.getStatus()));
        }

        // Release holds for all CONFIRMED requests
        List<SharedRideRequest> confirmedRequests = requestRepository.findBySharedRideSharedRideIdAndStatus(
                rideId, SharedRideRequestStatus.CONFIRMED);

        if (!confirmedRequests.isEmpty()) {
            for (SharedRideRequest request : confirmedRequests) {
                try {
                    RideHoldReleaseRequest releaseRequest = RideHoldReleaseRequest.builder()
                            .riderId(request.getRider().getRiderId())
                            .rideRequestId(request.getSharedRideRequestId())
                            .note("Ride cancelled - Request #"
                                    + request.getSharedRideRequestId())
                            .build();

                    rideFundCoordinatingService.releaseRideFunds(releaseRequest);

                    request.setStatus(SharedRideRequestStatus.CANCELLED);
                    requestRepository.save(request);

                    log.info("Released hold for request {} - amount: {}",
                            request.getSharedRideRequestId(), request.getTotalFare());

                } catch (Exception e) {
                    log.error("Failed to release hold for request {}: {}",
                            request.getSharedRideRequestId(), e.getMessage(), e);
                }
            }
        }

        ride.setStatus(SharedRideStatus.CANCELLED);
        rideRepository.save(ride);

        log.info("Ride {} cancelled successfully", rideId);

        // Location startLoc =
        // locationRepository.findById(ride.getStartLocationId()).orElse(null);
        // Location endLoc =
        // locationRepository.findById(ride.getEndLocationId()).orElse(null);
        // return buildRideResponse(ride, startLoc, endLoc);
        return buildRideResponse(ride);
    }

    // @Override
    // @Transactional
    // public RideRequestCompletionResponse
    // forceCompleteRideRequestOfRide(CompleteRideReqRequest request, Authentication
    // authentication) {
    // Integer rideId = request.rideId();
    // String username = authentication.getName();
    // log.info("System auto-completing ride request {} for ride {}",
    // request.rideRequestId(), rideId);
    //
    // SharedRide ride = rideRepository.findByIdForUpdate(rideId)
    // .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource",
    // rideId));
    // SharedRideRequest rideRequest =
    // requestRepository.findById(request.rideRequestId())
    // .orElseThrow(() ->
    // BaseDomainException.formatted("ride-request.not-found.resource",
    // request.rideRequestId()));
    //
    // User user = userRepository.findByEmail(username)
    // .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));
    // DriverProfile driver = driverRepository.findByUserUserId(user.getUserId())
    // .orElseThrow(() -> BaseDomainException.of("user.not-found.driver-profile"));
    //
    // if (!ride.getDriver().getDriverId().equals(driver.getDriverId())) {
    // throw BaseDomainException.of("ride.unauthorized.not-owner", "Automation task
    // running for wrong driver.");
    // }
    //
    // if (ride.getStatus() != SharedRideStatus.ONGOING) {
    // log.warn("Force-complete skipped for request {}: ride {} is not ONGOING
    // (status: {})",
    // rideRequest.getSharedRideRequestId(), ride.getSharedRideId(),
    // ride.getStatus());
    // return null;
    // }
    //
    // if (rideRequest.getStatus() != SharedRideRequestStatus.ONGOING) {
    // log.warn("Force-complete skipped for request {}: request is not ONGOING
    // (status: {})",
    // rideRequest.getSharedRideRequestId(), rideRequest.getStatus());
    // return null;
    // }
    //
    // LocalDateTime now = LocalDateTime.now();
    // Integer actualDuration = (int)
    // java.time.Duration.between(ride.getStartedAt(), now).toMinutes();
    //
    // Float actualDistance;
    // try {
    // RideTrack track =
    // trackRepository.findBySharedRideSharedRideId(rideId).orElse(null);
    // if (track != null && track.getGpsPoints() != null &&
    // track.getGpsPoints().size() > 1) {
    // actualDistance = (float)
    // rideTrackingService.computeDistanceFromPoints(track.getGpsPoints());
    // } else {
    // actualDistance = ride.getEstimatedDistance();
    // }
    // } catch (Exception e) {
    // log.warn("Distance computation failed for ride {}: {}. Falling back to
    // estimated.", rideId, e.getMessage());
    // actualDistance = ride.getEstimatedDistance();
    // }
    //
    // PricingConfig activePricingConfig =
    // pricingConfigRepository.findActive(Instant.now())
    // .orElseThrow(() ->
    // BaseDomainException.of("pricing-config.not-found.resource"));
    //
    // FareBreakdown fareBreakdown = new FareBreakdown(
    // activePricingConfig.getVersion(),
    // rideRequest.getDistanceMeters(),
    // MoneyVnd.VND(rideRequest.getDiscountAmount()),
    // MoneyVnd.VND(rideRequest.getSubtotalFare()),
    // MoneyVnd.VND(rideRequest.getTotalFare()),
    // activePricingConfig.getSystemCommissionRate()
    // );
    //
    // RideRequestSettledResponse requestSettledResponse;
    // try {
    // RideCompleteSettlementRequest captureRequest = new
    // RideCompleteSettlementRequest();
    // captureRequest.setRiderId(rideRequest.getRider().getRiderId());
    // captureRequest.setRideRequestId(rideRequest.getSharedRideRequestId());
    // captureRequest.setDriverId(driver.getDriverId());
    // captureRequest.setNote("Ride auto-completion - Request #" +
    // rideRequest.getSharedRideRequestId());
    // captureRequest.setRideId(ride.getSharedRideId());
    // requestSettledResponse =
    // rideFundCoordinatingService.settleRideFunds(captureRequest, fareBreakdown);
    //
    // rideRequest.setStatus(SharedRideRequestStatus.COMPLETED);
    // rideRequest.setActualDropoffTime(LocalDateTime.now());
    // requestRepository.save(rideRequest);
    //
    // log.info("Auto-captured fare for request {} - amount: {}",
    // rideRequest.getSharedRideRequestId(), rideRequest.getTotalFare());
    //
    // } catch (Exception e) {
    // log.error("Failed to auto-capture fare for request {}: {}",
    // rideRequest.getSharedRideRequestId(), e.getMessage(), e);
    // throw BaseDomainException.of("ride-request.settlement.failed");
    // }
    //
    // BigDecimal driverEarnings = requestSettledResponse.driverEarnings() == null
    // ? BigDecimal.ZERO : requestSettledResponse.driverEarnings();
    //
    // return RideRequestCompletionResponse.builder()
    // .sharedRideRequestId(rideRequest.getSharedRideRequestId())
    // .sharedRideId(rideId)
    // .driverEarningsOfRequest(driverEarnings)
    // .platformCommission(requestSettledResponse.systemCommission())
    // .requestActualDistance(actualDistance)
    // .requestActualDuration(actualDuration)
    // .build();
    // }

    @Override
    public Page<SharedRideResponse> getOngoingRidesOfRider(Integer riderId, Pageable pageable) {
        Page<SharedRide> ridesPage = rideRepository.findOngoingRidesOfRider(riderId, pageable);

        return ridesPage.map(this::buildRideResponse);
    }

    private SharedRideResponse buildRideResponse(SharedRide ride/* , Location startLoc, Location endLoc */) {

        // if (startLoc != null) {
        // response.setStartLocationName(startLoc.getName());
        // }
        // if (endLoc != null) {
        // response.setEndLocationName(endLoc.getName());
        // }

        SharedRideResponse response = rideMapper.toResponse(ride);

        if (ride.getStartLocation() != null) {
            response.setStartLocation(locationMapper.toResponse(ride.getStartLocation()));
        }
        if (ride.getEndLocation() != null) {
            response.setEndLocation(locationMapper.toResponse(ride.getEndLocation()));
        }
        response.setRoute(toRouteSummary(ride.getRoute()));

        return response;
    }

    private Location findOrCreateLocation(Integer locationId, LatLng latLng, String pointType) {
        boolean hasId = locationId != null;
        boolean hasCoords = latLng != null && latLng.latitude() != null && latLng.longitude() != null;

        if (hasId && !hasCoords) {
            return locationRepository.findById(locationId)
                    .orElseThrow(() -> BaseDomainException.formatted(
                            "ride.validation.invalid-location",
                            pointType + " location not found with ID: " + locationId));
        }

        if (!hasId && hasCoords) {
            return locationRepository.findByLatAndLng(latLng.latitude(), latLng.longitude())
                    .orElseGet(() -> {
                        Location newLocation = new Location();
                        newLocation.setName(null);
                        newLocation.setLat(latLng.latitude());
                        newLocation.setLng(latLng.longitude());
                        newLocation.setAddress(
                                routingService.getAddressFromCoordinates(
                                        latLng.latitude(), latLng.longitude()));
                        newLocation.setCreatedAt(LocalDateTime.now());
                        newLocation.setIsPoi(false);
                        return locationRepository.save(newLocation);
                    });
        }

        if (hasId) {
            throw BaseDomainException.of("ride.validation.invalid-location",
                    "Provide either " + pointType.toLowerCase() + "LocationId or "
                            + pointType.toLowerCase()
                            + "LatLng, not both");
        }

        throw BaseDomainException.of("ride.validation.invalid-location", "Either " + pointType.toLowerCase()
                + "LocationId or " + pointType.toLowerCase() + "LatLng must be provided");
    }

    private RouteSummaryResponse toRouteSummary(Route route) {
        if (route == null) {
            return null;
        }
        return RouteSummaryResponse.builder()
                .routeId(route.getRouteId())
                .name(route.getName())
                .routeType(route.getRouteType() != null ? route.getRouteType().name() : null)
                .defaultPrice(route.getDefaultPrice())
                .polyline(route.getPolyline())
                .validFrom(route.getValidFrom())
                .validUntil(route.getValidUntil())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SharedRideResponse> getMyCompletedRides(Pageable pageable, Authentication authentication) {
        log.info("Fetching completed rides for authenticated driver: {}", authentication.getName());
        return getRidesByDriver("COMPLETED", pageable, authentication);
    }
}

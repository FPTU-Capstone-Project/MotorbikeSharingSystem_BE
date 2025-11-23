package com.mssus.app.service.impl;

import com.mssus.app.common.enums.DeliveryMethod;
import com.mssus.app.common.enums.NotificationType;
import com.mssus.app.common.enums.DriverProfileStatus;
import com.mssus.app.common.enums.Priority;
import com.mssus.app.common.enums.RequestKind;
import com.mssus.app.common.enums.SharedRideRequestStatus;
import com.mssus.app.common.enums.SharedRideStatus;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.appconfig.config.properties.RideConfigurationProperties;
import com.mssus.app.appconfig.config.properties.RideMessagingProperties;
import com.mssus.app.dto.request.wallet.RideHoldReleaseRequest;
import com.mssus.app.dto.domain.ride.AcceptRequestDto;
import com.mssus.app.dto.domain.ride.BroadcastAcceptRequest;
import com.mssus.app.dto.domain.ride.CreateRideRequestDto;
import com.mssus.app.dto.request.ride.JoinRideRequest;
import com.mssus.app.dto.request.wallet.RideConfirmHoldRequest;
import com.mssus.app.dto.response.RouteResponse;
import com.mssus.app.dto.response.ride.BroadcastingRideRequestResponse;
import com.mssus.app.dto.response.ride.RideMatchProposalResponse;
import com.mssus.app.dto.response.ride.RouteSummaryResponse;
import com.mssus.app.dto.response.ride.SharedRideRequestResponse;
import com.mssus.app.dto.domain.ride.LatLng;
import com.mssus.app.entity.*;
import com.mssus.app.mapper.SharedRideRequestMapper;
import com.mssus.app.service.domain.pricing.model.Quote;
import com.mssus.app.repository.*;
import com.mssus.app.service.*;
import com.mssus.app.service.domain.matching.QueueRideMatchingOrchestrator;
import com.mssus.app.service.domain.matching.RideMatchingCoordinator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SharedRideRequestServiceImpl implements SharedRideRequestService {
    @Value("${app.timezone:Asia/Ho_Chi_Minh}")
    private String appTimezone;

    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final SharedRideRequestRepository requestRepository;
    private final SharedRideRepository rideRepository;
    private final VehicleRepository vehicleRepository;
    private final RiderProfileRepository riderRepository;
    private final DriverProfileRepository driverRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final SharedRideRequestMapper requestMapper;
    private final QuoteService quoteService;
    private final RideMatchingService matchingService;
    private final RideConfigurationProperties rideConfig;
    private final RideMessagingProperties rideMessagingProperties;
    private final RideMatchingCoordinator matchingCoordinator;
    private final ObjectProvider<QueueRideMatchingOrchestrator> queueOrchestratorProvider;
    private final ApplicationEventPublisherService eventPublisherService;
    private final PricingConfigRepository pricingConfigRepository;
    private final NotificationService notificationService;
    private final RideFundCoordinatingService rideFundCoordinatingService;
    private final RoutingService routingService;
    private final RideTrackingService rideTrackingService;
    private final RouteAssignmentService routeAssignmentService;

    @Override
    @Transactional
    public SharedRideRequestResponse createAIBookingRequest(CreateRideRequestDto request,
            Authentication authentication) {
        String username = authentication.getName();
        log.info("Rider {} creating AI booking request with quote {}", username, request.quoteId());

        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));
        RiderProfile rider = riderRepository.findByUserUserId(user.getUserId())
                .orElseThrow(() -> BaseDomainException.of("user.not-found.rider-profile"));

        // String activeProfile =
        // Optional.ofNullable(AuthServiceImpl.userContext.get(user.getUserId().toString()))
        // .filter(Map.class::isInstance)
        // .map(obj -> (Map<String, Object>) obj)
        // .map(claims -> (String) claims.get("active_profile"))
        // .orElse(null);
        //
        // System.out.println(activeProfile);
        //
        // if (activeProfile == null || !activeProfile.equals("rider")) {
        // throw BaseDomainException.of("ride.unauthorized.invalid-profile",
        // "Active profile is not rider");
        // }

        if (requestRepository
                .findFirstByRiderRiderIdAndStatusOrderByCreatedAtDesc(rider.getRiderId(),
                        SharedRideRequestStatus.PENDING)
                .isPresent()
                || requestRepository.findFirstByRiderRiderIdAndStatusOrderByCreatedAtDesc(rider.getRiderId(),
                        SharedRideRequestStatus.BROADCASTING).isPresent()) {
            throw BaseDomainException.of("ride.validation.request-already-exists",
                    "You already have a pending booking request");
        }

        Quote quote = quoteService.getQuote(request.quoteId());

        if (quote.riderId() != rider.getRiderId()) {
            throw BaseDomainException.of("ride.unauthorized.request-not-owner",
                    "Quote belongs to different user");
        }

        BigDecimal fareAmount = quote.fare().total().amount();
        BigDecimal subtotalFare = quote.fare().subtotal().amount();
        PricingConfig pricingConfig = pricingConfigRepository.findByVersion(quote.fare().pricingVersion())
                .orElseThrow(() -> BaseDomainException.of("pricing-config.not-found.resource"));
        String polyline = quote.polyline();

        log.info("Creating AI booking - fare from quote: {} VND", fareAmount);

        LocalDateTime desiredPickupTime = request.desiredPickupTime() == null
                ? LocalDateTime.now(ZoneId.of(appTimezone))
                : request.desiredPickupTime();

        SharedRideRequest rideRequest = SharedRideRequest.builder()
                .requestKind(RequestKind.BOOKING)
                .sharedRide(null)
                .rider(rider)
                .pickupLocation(quote.pickupLocation())
                .dropoffLocation(quote.dropoffLocation())
                .status(SharedRideRequestStatus.PENDING)
                .totalFare(fareAmount)
                .pricingConfig(pricingConfig)
                .subtotalFare(subtotalFare)
                .distanceMeters((int) quote.distanceM())
                .durationSeconds(quote.durationS())
                .promotion(null)
                .discountAmount(BigDecimal.ZERO)
                .pickupTime(desiredPickupTime)
                .specialRequests(request.notes() == null ? "N/A" : request.notes())
                .initiatedBy("rider")
                .polyline(polyline)
                .createdAt(LocalDateTime.now())
                .build();

        SharedRideRequest savedRequest = requestRepository.save(rideRequest);

        try {
            RideConfirmHoldRequest holdRequest = new RideConfirmHoldRequest();
            holdRequest.setRiderId(rider.getRiderId());
            holdRequest.setRideRequestId(savedRequest.getSharedRideRequestId());
            holdRequest.setAmount(fareAmount);
            holdRequest.setNote("Hold for booking request #" + savedRequest.getSharedRideRequestId());

            rideFundCoordinatingService.holdRideFunds(holdRequest);

            log.info("Wallet hold placed for booking request {} - amount: {}",
                    savedRequest.getSharedRideRequestId(), fareAmount);

        } catch (Exception e) {
            log.error("Failed to place wallet hold for request {}: {}",
                    savedRequest.getSharedRideRequestId(), e.getMessage(), e);
            requestRepository.delete(savedRequest);
            throw BaseDomainException.of("ride.operation.wallet-hold-failed",
                    "Failed to reserve funds: " + e.getMessage());
        }

        log.info("AI booking request created - ID: {}, rider: {}, fare: {}, status: {}",
                savedRequest.getSharedRideRequestId(), rider.getRiderId(),
                fareAmount, savedRequest.getStatus());

        eventPublisherService.publishRideRequestCreatedEvent(savedRequest.getSharedRideRequestId());
        notificationService.sendNotification(user,
                NotificationType.BOOKING_REQUEST_CREATED,
                "Booking Request Created",
                "Your booking request has been created successfully.",
                null,
                Priority.MEDIUM,
                DeliveryMethod.IN_APP,
                null);

        return buildRequestResponse(savedRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BroadcastingRideRequestResponse> getBroadcastingRideRequests(Authentication authentication) {
        String username = authentication.getName();
        log.info("Driver {} fetching broadcasting ride requests", username);

        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));

        driverRepository.findByUserUserId(user.getUserId())
                .orElseThrow(() -> BaseDomainException.of("user.not-found.driver-profile"));

        List<SharedRideRequest> broadcastingRequests = requestRepository
                .findByStatus(SharedRideRequestStatus.BROADCASTING);

        log.debug("Broadcast marketplace returning {} requests", broadcastingRequests.size());

        return broadcastingRequests.stream()
                .map(this::toBroadcastingResponse)
                .toList();
    }

    @Override
    @Transactional
    public SharedRideRequestResponse requestToJoinRide(Integer rideId, JoinRideRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        log.info("Rider {} requesting to join ride {} with quote {}", username, rideId, request.quoteId());

        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));
        RiderProfile rider = riderRepository.findByUserUserId(user.getUserId())
                .orElseThrow(() -> BaseDomainException.of("user.not-found.rider-profile"));

        Quote quote = quoteService.getQuote(request.quoteId());

        if (quote.riderId() != rider.getRiderId()) {
            throw BaseDomainException.of("ride.unauthorized.request-not-owner",
                    "Quote belongs to different user");
        }

        SharedRide ride = rideRepository.findByIdForUpdate(rideId)
                .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource", rideId));

        if (ride.getStatus() != SharedRideStatus.SCHEDULED && ride.getStatus() != SharedRideStatus.ONGOING) {
            throw BaseDomainException.of("ride.validation.invalid-state",
                    Map.of("currentState", ride.getStatus()));
        }

        BigDecimal fareAmount = quote.fare().total().amount();
        BigDecimal subtotalFare = quote.fare().subtotal().amount();
        PricingConfig pricingConfig = pricingConfigRepository.findByVersion(quote.fare().pricingVersion())
                .orElseThrow(() -> BaseDomainException.of("pricing-config.not-found.resource"));
        String polyline = quote.polyline();

        LocalDateTime desiredPickupTime = request.desiredPickupTime() == null
                ? LocalDateTime.now(ZoneId.of(appTimezone))
                : request.desiredPickupTime();

        SharedRideRequest rideRequest = SharedRideRequest.builder()
                .requestKind(RequestKind.JOIN_RIDE)
                .sharedRide(ride)
                .rider(rider)
                .pickupLocation(quote.pickupLocation())
                .dropoffLocation(quote.dropoffLocation())
                .status(SharedRideRequestStatus.PENDING)
                .totalFare(fareAmount)
                .pricingConfig(pricingConfig)
                .subtotalFare(subtotalFare)
                .distanceMeters((int) quote.distanceM())
                .durationSeconds(quote.durationS())
                .promotion(null)
                .discountAmount(BigDecimal.ZERO)
                .pickupTime(desiredPickupTime)
                .specialRequests(request.notes() == null ? "N/A" : request.notes())
                .initiatedBy("rider")
                .polyline(polyline)
                .createdAt(LocalDateTime.now())
                .build();

        SharedRideRequest savedRequest = requestRepository.save(rideRequest);

        try {
            RideConfirmHoldRequest holdRequest = new RideConfirmHoldRequest();
            holdRequest.setRiderId(rider.getRiderId());
            holdRequest.setRideRequestId(savedRequest.getSharedRideRequestId());
            holdRequest.setAmount(fareAmount);
            holdRequest.setNote("Hold for join ride request #" + savedRequest.getSharedRideRequestId());

            rideFundCoordinatingService.holdRideFunds(holdRequest);

            log.info("Wallet hold placed for request {} - amount: {}",
                    savedRequest.getSharedRideRequestId(), fareAmount);

        } catch (Exception e) {
            log.error("Failed to place wallet hold for request {}: {}",
                    savedRequest.getSharedRideRequestId(), e.getMessage(), e);
            // Rollback: delete the request
            requestRepository.delete(savedRequest);
            throw BaseDomainException.of("ride.operation.wallet-hold-failed",
                    "Failed to reserve funds: " + e.getMessage());
        }

        log.info("Join ride request created - ID: {}, rider: {}, ride: {}, fare: {}, status: {}",
                savedRequest.getSharedRideRequestId(), rider.getRiderId(), rideId, fareAmount,
                savedRequest.getStatus());

        // Publish event to MQ (if enabled) or use legacy coordinator
        eventPublisherService.publishRideRequestCreatedEvent(savedRequest.getSharedRideRequestId());

        notificationService.sendNotification(user,
                NotificationType.JOIN_RIDE_REQUEST_CREATED,
                "Join Ride Request Created",
                "Your request to join the ride has been created successfully.",
                null,
                Priority.MEDIUM,
                DeliveryMethod.IN_APP,
                null);

        return buildRequestResponse(savedRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public SharedRideRequestResponse getRequestById(Integer requestId) {
        SharedRideRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.request", requestId));

        SharedRide ride = request.getSharedRide();
        if (ride == null) {
            return buildRequestResponse(request);
        }

        String polylineFromDriverToPickup = ride.getDriverApproachPolyline();

        if (polylineFromDriverToPickup == null) {
            Optional<LatLng> latestPosition = rideTrackingService.getLatestPosition(ride.getSharedRideId(), 3);
            if (latestPosition.isPresent()) {
                try {
                    polylineFromDriverToPickup = routingService.getRoute(
                            latestPosition.get().latitude(),
                            latestPosition.get().longitude(),
                            request.getPickupLocation().getLat(),
                            request.getPickupLocation().getLng()).polyline();
                    ride.setDriverApproachPolyline(polylineFromDriverToPickup);
                    rideRepository.save(ride);
                } catch (Exception ex) {
                    log.warn("Failed to refresh driver approach polyline for request {}: {}", requestId,
                            ex.getMessage());
                }
            }
        }

        if (polylineFromDriverToPickup != null) {
            return buildRequestResponse(request, polylineFromDriverToPickup);
        }
        return buildRequestResponse(request);
    }

    @Override
    @Transactional
    public SharedRideRequestResponse acceptBroadcast(Integer requestId,
            BroadcastAcceptRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        log.info("Driver {} accepting broadcast request {}", username, requestId);

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

        if (driver.getStatus() != DriverProfileStatus.ACTIVE) {
            throw BaseDomainException.of("ride.validation.invalid-state",
                    "Driver profile is not active");
        }

        boolean hasCoords = request.startLatLng() != null &&
                request.startLatLng().latitude() != null &&
                request.startLatLng().longitude() != null;

        if (!hasCoords && request.startLocationId() == null) {
            throw BaseDomainException.of("ride.validation.missing-current-location");
        }

        SharedRideRequest rideRequest = requestRepository.findById(requestId)
                .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.request", requestId));

        if (rideRequest.getRequestKind() != RequestKind.BOOKING) {
            throw BaseDomainException.of("ride.validation.request-invalid-state",
                    "Broadcast acceptance is only available for booking requests");
        }

        if (rideRequest.getStatus() != SharedRideRequestStatus.BROADCASTING) {
            throw BaseDomainException.of("ride.validation.request-invalid-state",
                    Map.of("currentState", rideRequest.getStatus()));
        }

        boolean usingMqMode = rideMessagingProperties != null &&
                rideMessagingProperties.isEnabled() &&
                rideMessagingProperties.isMatchingEnabled();

        if (!usingMqMode) {
            boolean locked = matchingCoordinator.beginBroadcastAcceptance(requestId, driver.getDriverId());
            if (!locked) {
                throw BaseDomainException.of("ride.validation.request-invalid-state",
                        "Broadcast offer is no longer available or already processed");
            }
        }

        try {

            if (rideRepository.existsByDriverDriverIdAndStatus(driver.getDriverId(), SharedRideStatus.ONGOING)) {
                throw BaseDomainException.of("ride.validation.invalid-state",
                        "Driver currently has an ongoing ride");
            }

            LocalDateTime now = LocalDateTime.now(ZoneId.of(appTimezone));
            LocalDateTime pickupTime = rideRequest.getPickupTime() == null ? now : rideRequest.getPickupTime();
            LocalDateTime scheduledTime = pickupTime.isAfter(now) ? pickupTime : now;

            Location startLocation = rideRequest.getPickupLocation();
            Location endLocation = rideRequest.getDropoffLocation();
            Location driverStartLocation = null;
            boolean hasDriverStart = (request.startLocationId() != null)
                    || (request.startLatLng() != null && request.startLatLng().latitude() != null
                            && request.startLatLng().longitude() != null);
            if (hasDriverStart) {
                driverStartLocation = findOrCreateLocation(request.startLocationId(), request.startLatLng(),
                        "driver start");
            }

            RouteResponse rideRoute = null;
            try {
                rideRoute = routingService.getRoute(
                        startLocation.getLat(),
                        startLocation.getLng(),
                        endLocation.getLat(),
                        endLocation.getLng());
            } catch (Exception routeEx) {
                log.warn("Failed to fetch start->end route for request {}: {}", requestId, routeEx.getMessage());
            }

            SharedRide newRide = new SharedRide();
            newRide.setDriver(driver);
            newRide.setVehicle(vehicle);
            newRide.setStatus(SharedRideStatus.ONGOING);
            int capacity = vehicle.getCapacity() != null
                    ? vehicle.getCapacity()
                    : Optional.ofNullable(driver.getMaxPassengers()).orElse(1);
            if (capacity <= 0) {
                capacity = 1;
            }
            newRide.setPricingConfig(rideRequest.getPricingConfig());
            newRide.setScheduledTime(scheduledTime);
            newRide.setStartLocation(startLocation);
            newRide.setEndLocation(endLocation);
            newRide.setRoute(routeAssignmentService.resolveRoute(
                    null,
                    startLocation,
                    endLocation,
                    rideRoute != null ? rideRoute.polyline() : null));
            if (rideRoute != null) {
                newRide.setEstimatedDuration((int) Math.ceil(rideRoute.time() / 60.0));
                newRide.setEstimatedDistance((float) rideRoute.distance() / 1000);
            }
            newRide.setCreatedAt(LocalDateTime.now());
            newRide.setStartedAt(LocalDateTime.now());

            RouteResponse driverApproachRoute = null;
            if (driverStartLocation != null) {
                try {
                    driverApproachRoute = routingService.getRoute(
                            driverStartLocation.getLat(),
                            driverStartLocation.getLng(),
                            startLocation.getLat(),
                            startLocation.getLng());
                } catch (Exception approachEx) {
                    log.warn("Failed to fetch driver approach route for request {}: {}", requestId,
                            approachEx.getMessage());
                }
            }

            LocalDateTime estimatedPickupTime;
            if (driverApproachRoute != null) {
                long approachSeconds = Math.max(driverApproachRoute.time(), 0);
                estimatedPickupTime = now.plusSeconds(approachSeconds);
                newRide.setDriverApproachPolyline(driverApproachRoute.polyline());
                try {
                    newRide.setDriverApproachDistanceMeters(Math.toIntExact(driverApproachRoute.distance()));
                } catch (ArithmeticException ex) {
                    log.warn("Driver approach distance overflow for request {}: {}", requestId, ex.getMessage());
                }
                try {
                    newRide.setDriverApproachDurationSeconds(Math.toIntExact(driverApproachRoute.time()));
                } catch (ArithmeticException ex) {
                    log.warn("Driver approach duration overflow for request {}: {}", requestId, ex.getMessage());
                }
                newRide.setDriverApproachEta(estimatedPickupTime);
            } else {
                estimatedPickupTime = scheduledTime;
            }
            LocalDateTime estimatedDropoffTime = estimatedPickupTime.plusSeconds(rideRequest.getDurationSeconds());

            SharedRide savedRide = rideRepository.save(newRide);

            rideRequest.setSharedRide(savedRide);
            rideRequest.setStatus(SharedRideRequestStatus.CONFIRMED);
            rideRequest.setEstimatedPickupTime(estimatedPickupTime);
            rideRequest.setEstimatedDropoffTime(estimatedDropoffTime);
            requestRepository.save(rideRequest);

            newRide.setSharedRideRequest(rideRequest);
            rideRepository.save(newRide);

            RideMatchProposalResponse proposal = RideMatchProposalResponse.builder()
                    .sharedRideId(savedRide.getSharedRideId())
                    .driverId(driver.getDriverId())
                    .driverName(driver.getUser().getFullName())
                    .driverRating(driver.getRatingAvg())
                    .vehicleModel(vehicle.getModel())
                    .vehiclePlate(vehicle.getPlateNumber())
                    .scheduledTime(savedRide.getScheduledTime())
                    .totalFare(rideRequest.getTotalFare())
                    .estimatedPickupTime(estimatedPickupTime)
                    .estimatedDropoffTime(estimatedDropoffTime)
                    .build();

            if (usingMqMode) {
                QueueRideMatchingOrchestrator orchestrator = queueOrchestratorProvider != null
                        ? queueOrchestratorProvider.getIfAvailable()
                        : null;
                if (orchestrator == null) {
                    throw BaseDomainException.of("ride.validation.request-invalid-state",
                            "Matching orchestrator unavailable");
                }
                boolean registered = orchestrator.registerBroadcastInterest(requestId, driver.getDriverId());
                if (!registered) {
                    throw BaseDomainException.of("ride.validation.request-invalid-state",
                            "Broadcast offer is no longer available or already processed");
                }
                orchestrator.publishDriverResponse(
                        requestId,
                        driver.getDriverId(),
                        savedRide.getSharedRideId(),
                        true);
            } else {
                matchingCoordinator.completeBroadcastAcceptance(requestId, proposal);
            }

            log.info("Broadcast request {} accepted by driver {} - new ride {}",
                    requestId, driver.getDriverId(), savedRide.getSharedRideId());

            rideTrackingService.startTracking(savedRide.getSharedRideId());

            String polylineFromDriverToPickup = driverApproachRoute != null ? driverApproachRoute.polyline() : null;

            return buildRequestResponse(rideRequest, polylineFromDriverToPickup);
        } catch (RuntimeException ex) {
            if (!usingMqMode) {
                // Legacy mode: notify coordinator of failure
                matchingCoordinator.failBroadcastAcceptance(requestId, ex.getMessage());
            }
            // MQ mode: failure is handled by transaction rollback and timeout
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<RideMatchProposalResponse> getMatchProposals(Integer requestId, Authentication authentication) {
        String username = authentication.getName();
        log.info("Rider {} getting match proposals for request {}", username, requestId);

        // Get request
        SharedRideRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.request", requestId));

        // Validate ownership
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));
        RiderProfile rider = riderRepository.findByUserUserId(user.getUserId())
                .orElseThrow(() -> BaseDomainException.of("user.not-found.rider-profile"));

        if (!request.getRider().getRiderId().equals(rider.getRiderId())) {
            throw BaseDomainException.of("ride.unauthorized.request-not-owner");
        }

        // Validate request kind and status
        if (request.getRequestKind() != RequestKind.BOOKING) {
            throw BaseDomainException.of("ride.validation.request-invalid-state",
                    "Match proposals only available for AI_BOOKING requests");
        }

        if (request.getStatus() != SharedRideRequestStatus.PENDING) {
            throw BaseDomainException.of("ride.validation.request-invalid-state",
                    Map.of("currentState", request.getStatus()));
        }

        // Run matching algorithm
        try {
            List<RideMatchProposalResponse> proposals = matchingService.findMatches(request);

            if (proposals.isEmpty()) {
                log.info("No matches found for request {}", requestId);
            } else {
                log.info("Found {} match proposals for request {}", proposals.size(), requestId);
            }

            return proposals;

        } catch (Exception e) {
            log.error("Matching algorithm failed for request {}: {}", requestId, e.getMessage(), e);
            throw BaseDomainException.of("ride.operation.matching-failed",
                    "Matching failed: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SharedRideRequestResponse> getRequestsByRider(Integer riderId, String status,
            Pageable pageable, Authentication authentication) {
        log.info("Fetching requests for rider: {}, status: {}", riderId, status);

        Page<SharedRideRequest> requestPage;
        if (status != null && !status.isBlank()) {
            SharedRideRequestStatus requestStatus = SharedRideRequestStatus.valueOf(status.toUpperCase());
            requestPage = requestRepository.findByRiderRiderIdAndStatusOrderByCreatedAtDesc(
                    riderId, requestStatus, pageable);
        } else {
            requestPage = requestRepository.findByRiderRiderIdOrderByCreatedAtDesc(riderId, pageable);
        }

        return requestPage.map(this::buildRequestResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SharedRideRequestResponse> getRequestsByRide(Integer rideId, String status,
            Pageable pageable, Authentication authentication) {
        log.info("Fetching requests for ride: {}, status: {}", rideId, status);

        // Validate ride exists
        rideRepository.findById(rideId)
                .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource", rideId));

        Page<SharedRideRequest> requestPage;
        if (status != null && !status.isBlank()) {
            SharedRideRequestStatus requestStatus = SharedRideRequestStatus.valueOf(status.toUpperCase());
            List<SharedRideRequest> requests = requestRepository.findBySharedRideSharedRideIdAndStatus(
                    rideId, requestStatus);
            requestPage = new PageImpl<>(requests, pageable, requests.size());
        } else {
            List<SharedRideRequest> requests = requestRepository.findBySharedRideSharedRideId(rideId);
            requestPage = new PageImpl<>(requests, pageable, requests.size());
        }

        return requestPage.map(this::buildRequestResponse);
    }

    @Override
    @Transactional
    public SharedRideRequestResponse acceptRequest(Integer requestId, AcceptRequestDto acceptDto,
            Authentication authentication) {
        String username = authentication.getName();
        log.info("Driver {} accepting request {} for ride {}", username, requestId, acceptDto.rideId());

        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));
        DriverProfile driver = driverRepository.findByUserUserId(user.getUserId())
                .orElseThrow(() -> BaseDomainException.of("user.not-found.driver-profile"));

        SharedRideRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.request", requestId));

        SharedRide ride = rideRepository.findByIdForUpdate(acceptDto.rideId())
                .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource", acceptDto.rideId()));

        if (!ride.getDriver().getDriverId().equals(driver.getDriverId())) {
            throw BaseDomainException.of("ride.unauthorized.not-owner");
        }

        if (request.getStatus() != SharedRideRequestStatus.PENDING) {
            throw BaseDomainException.of("ride.validation.request-invalid-state",
                    Map.of("currentState", request.getStatus()));
        }

        boolean trackingAcceptance = false;
        boolean usingMqMode = rideMessagingProperties != null &&
                rideMessagingProperties.isEnabled() &&
                rideMessagingProperties.isMatchingEnabled();

        if (!usingMqMode) {
            // Legacy mode: validate via in-memory coordinator
            if (request.getRequestKind() == RequestKind.BOOKING) {
                boolean accepted = matchingCoordinator.beginDriverAcceptance(
                        requestId,
                        acceptDto.rideId(),
                        driver.getDriverId());

                if (!accepted) {
                    throw BaseDomainException.of("ride.validation.request-invalid-state",
                            "Ride offer is no longer available or already processed");
                }
                trackingAcceptance = true;
            } else if (request.getRequestKind() == RequestKind.JOIN_RIDE) {
                boolean accepted = matchingCoordinator.beginJoinAcceptance(
                        requestId,
                        acceptDto.rideId(),
                        driver.getDriverId());

                if (!accepted) {
                    throw BaseDomainException.of("ride.validation.request-invalid-state",
                            "Ride offer is no longer available or already processed");
                }
                trackingAcceptance = true;
            }
        } else {
            // MQ mode: validation happens in queue orchestrator
            trackingAcceptance = true;
        }

        RouteResponse driverApproachRoute = null;
        try {
            if (request.getRequestKind() == RequestKind.BOOKING) {
                request.setSharedRide(ride);

            } else if (request.getRequestKind() == RequestKind.JOIN_RIDE) {
                if (request.getSharedRide() == null ||
                        !request.getSharedRide().getSharedRideId().equals(acceptDto.rideId())) {
                    throw BaseDomainException.of("ride.validation.invalid-state",
                            "Request is for a different ride");
                }
            }

            LatLng driverCurrentLocation = acceptDto.currentDriverLocation();
            if (driverCurrentLocation != null && driverCurrentLocation.latitude() != null
                    && driverCurrentLocation.longitude() != null) {
                try {
                    driverApproachRoute = routingService.getRoute(
                            driverCurrentLocation.latitude(),
                            driverCurrentLocation.longitude(),
                            request.getPickupLocation().getLat(),
                            request.getPickupLocation().getLng());
                } catch (Exception ex) {
                    log.warn("Failed to fetch driver approach route for request {}: {}", requestId, ex.getMessage());
                }
            }

            LocalDateTime now = LocalDateTime.now(ZoneId.of(appTimezone));
            LocalDateTime estimatedPickupTime;
            if (driverApproachRoute != null) {
                long approachSeconds = Math.max(driverApproachRoute.time(), 0);
                estimatedPickupTime = now.plusSeconds(approachSeconds);
                try {
                    ride.setDriverApproachDistanceMeters(Math.toIntExact(driverApproachRoute.distance()));
                } catch (ArithmeticException distEx) {
                    log.warn("Driver approach distance overflow for request {}: {}", requestId, distEx.getMessage());
                }
                try {
                    ride.setDriverApproachDurationSeconds(Math.toIntExact(driverApproachRoute.time()));
                } catch (ArithmeticException durEx) {
                    log.warn("Driver approach duration overflow for request {}: {}", requestId, durEx.getMessage());
                }
                ride.setDriverApproachPolyline(driverApproachRoute.polyline());
            } else {
                int estimatedTravelTimeFromCurrentDriverPosToPickup = routingService.getEstimatedTravelTimeMinutes(
                        ride.getStartLocation().getLat(),
                        ride.getStartLocation().getLng(),
                        request.getPickupLocation().getLat(),
                        request.getPickupLocation().getLng());
                int safeTravelMinutes = Math.max(estimatedTravelTimeFromCurrentDriverPosToPickup, 0);
                estimatedPickupTime = now.plusMinutes(safeTravelMinutes);
            }
            ride.setDriverApproachEta(estimatedPickupTime);

            LocalDateTime estimatedDropoffTime = estimatedPickupTime.plusSeconds(request.getDurationSeconds());

            request.setStatus(SharedRideRequestStatus.CONFIRMED);
            request.setEstimatedPickupTime(estimatedPickupTime);
            request.setEstimatedDropoffTime(estimatedDropoffTime);
            requestRepository.save(request);

            log.info("Request {} accepted successfully for ride {}", requestId, acceptDto.rideId());

            if (trackingAcceptance) {
                if (usingMqMode) {
                    // MQ mode: only publish to queue orchestrator
                    queueOrchestratorProvider.ifAvailable(orchestrator -> orchestrator.publishDriverResponse(
                            requestId,
                            driver.getDriverId(),
                            acceptDto.rideId(),
                            false));
                } else {
                    // Legacy mode: use in-memory coordinator
                    matchingCoordinator.completeDriverAcceptance(requestId);
                }
            }

            ride.setSharedRideRequest(request);
            rideRepository.save(ride);

        } catch (RuntimeException e) {
            if (trackingAcceptance && !usingMqMode) {
                // Legacy mode: notify coordinator of failure
                matchingCoordinator.failDriverAcceptance(requestId, e.getMessage());
            }
            // MQ mode: failure is handled by transaction rollback and timeout
            throw e;
        }

        String polylineFromDriverToPickup = null;
        if (driverApproachRoute != null) {
            polylineFromDriverToPickup = driverApproachRoute.polyline();
        }
        if (polylineFromDriverToPickup == null) {
            try {
                RouteResponse fallbackRoute = routingService.getRoute(
                        acceptDto.currentDriverLocation().latitude(),
                        acceptDto.currentDriverLocation().longitude(),
                        request.getPickupLocation().getLat(),
                        request.getPickupLocation().getLng());
                polylineFromDriverToPickup = fallbackRoute.polyline();
            } catch (Exception ex) {
                log.warn("Failed to compute polyline from driver to pickup for request {}: {}", requestId,
                        ex.getMessage());
            }
        }

        return buildRequestResponse(request, polylineFromDriverToPickup);
    }

    @Override
    @Transactional
    public SharedRideRequestResponse rejectRequest(Integer requestId, String reason, Authentication authentication) {
        String username = authentication.getName();
        log.info("Driver {} rejecting request {} - reason: {}", username, requestId, reason);

        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));
        DriverProfile driver = driverRepository.findByUserUserId(user.getUserId())
                .orElseThrow(() -> BaseDomainException.of("user.not-found.driver-profile"));

        SharedRideRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.request", requestId));

        if (request.getSharedRide() == null) {
            throw BaseDomainException.of("ride.validation.invalid-state",
                    "Cannot reject AI_BOOKING request without ride assignment");
        }

        if (!request.getSharedRide().getDriver().getDriverId().equals(driver.getDriverId())) {
            throw BaseDomainException.of("ride.unauthorized.not-owner");
        }

        if (request.getStatus() != SharedRideRequestStatus.PENDING) {
            throw BaseDomainException.of("ride.validation.request-invalid-state",
                    Map.of("currentState", request.getStatus()));
        }

        matchingCoordinator.rejectJoinRequest(requestId, reason);

        log.info("Request {} rejected successfully", requestId);

        // The coordinator will handle notifying the rider.

        return buildRequestResponse(request);
    }

    @Override
    @Transactional
    public SharedRideRequestResponse cancelRequest(Integer requestId, Authentication authentication) {
        String username = authentication.getName();
        log.info("User {} cancelling request {}", username, requestId);

        // Get authenticated user
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));

        // Get request
        SharedRideRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.request", requestId));

        // Validate ownership or admin role
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            RiderProfile rider = riderRepository.findByUserUserId(user.getUserId())
                    .orElseThrow(() -> BaseDomainException.of("user.not-found.rider-profile"));

            if (!request.getRider().getRiderId().equals(rider.getRiderId())) {
                throw BaseDomainException.of("ride.unauthorized.request-not-owner");
            }
        }

        // Validate request status
        if (request.getStatus() != SharedRideRequestStatus.PENDING &&
                request.getStatus() != SharedRideRequestStatus.CONFIRMED &&
                request.getStatus() != SharedRideRequestStatus.BROADCASTING) {
            throw BaseDomainException.of("ride.validation.request-invalid-state",
                    Map.of("currentState", request.getStatus()));
        }

        if (request.getRequestKind() == RequestKind.BOOKING &&
                request.getStatus() == SharedRideRequestStatus.PENDING) {
            matchingCoordinator.cancelMatching(requestId);
            queueOrchestratorProvider.ifAvailable(orchestrator -> orchestrator.publishCancellation(requestId));
        }

        // Calculate cancellation fee if CONFIRMED
        BigDecimal cancellationFee = BigDecimal.ZERO;
        boolean withinGracePeriod = true;

        if (request.getStatus() == SharedRideRequestStatus.CONFIRMED) {
            // Check grace period
            Duration timeSinceConfirmation = Duration.between(request.getCreatedAt(), LocalDateTime.now());
            int gracePeriodMinutes = rideConfig.getCancellation().getGracePeriodMinutes();
            withinGracePeriod = timeSinceConfirmation.toMinutes() <= gracePeriodMinutes;

            if (!withinGracePeriod) {
                // Apply cancellation fee
                BigDecimal feePercentage = rideConfig.getCancellation().getFeePercentage();
                cancellationFee = request.getTotalFare().multiply(feePercentage);

                log.info("Cancellation fee applied for request {} - fee: {} ({}%)",
                        requestId, cancellationFee, feePercentage.multiply(BigDecimal.valueOf(100)));
            }
        }

        try {
            if (withinGracePeriod || request.getStatus() == SharedRideRequestStatus.PENDING) {
                RideHoldReleaseRequest releaseRequest = RideHoldReleaseRequest.builder()
                        .riderId(request.getRider().getRiderId())
                        .rideRequestId(request.getSharedRideRequestId())
                        .note("Ride cancelled - Request #" + request.getSharedRideRequestId())
                        .build();

                rideFundCoordinatingService.releaseRideFunds(releaseRequest);

                log.info("Full wallet hold released for cancelled request {} - amount: {}",
                        requestId, request.getTotalFare());

            } else {
                // TODO: Implement partial release with cancellation fee capture
                // For MVP, do full release and log warning
                RideHoldReleaseRequest releaseRequest = RideHoldReleaseRequest.builder()
                        .riderId(request.getRider().getRiderId())
                        .rideRequestId(request.getSharedRideRequestId())
                        .note("Ride cancelled - Request #" + request.getSharedRideRequestId())
                        .build();

                rideFundCoordinatingService.releaseRideFunds(releaseRequest);

                log.warn("TODO: Implement cancellation fee capture for request {} - fee: {}",
                        requestId, cancellationFee);
            }

        } catch (Exception e) {
            log.error("Failed to release wallet hold for request {}: {}",
                    requestId, e.getMessage(), e);
            // Continue with cancellation even if release fails
        }

        // if (request.getStatus() == SharedRideRequestStatus.CONFIRMED &&
        // request.getSharedRide() != null) {
        // rideRepository.decrementPassengerCount(request.getSharedRide().getSharedRideId());
        // }

        // Update request status
        request.setStatus(SharedRideRequestStatus.CANCELLED);
        requestRepository.save(request);

        log.info("Request {} cancelled successfully", requestId);

        // TODO: Notify driver if request was CONFIRMED (placeholder for MVP)
        // if (request.getSharedRide() != null) {
        // notificationService.notifyDriverOfCancellation(request.getSharedRide().getDriver(),
        // request);
        // }

        return buildRequestResponse(request);
    }

    private SharedRideRequestResponse buildRequestResponse(SharedRideRequest request) {
        SharedRideRequestResponse response = requestMapper.toResponse(request);
        applyDriverApproachData(request, response);
        applyRouteSummary(request, response);
        if (request.getRider().getUser().getPhone() != null) {
            response.setRiderPhone(request.getRider().getUser().getPhone());
        }
        return response;
    }

    private SharedRideRequestResponse buildRequestResponse(SharedRideRequest request,
            String polylineFromDriverToPickup) {
        SharedRideRequestResponse response = buildRequestResponse(request);
        if (polylineFromDriverToPickup != null) {
            response.setPolylineFromDriverToPickup(polylineFromDriverToPickup);
        }
        return response;
    }

    private BroadcastingRideRequestResponse toBroadcastingResponse(SharedRideRequest request) {
        return new BroadcastingRideRequestResponse(
                request.getSharedRideRequestId(),
                request.getTotalFare(),
                request.getPickupLocation(),
                request.getDropoffLocation(),
                request.getPickupTime() != null ? request.getPickupTime().format(ISO_DATE_TIME) : null);
    }

    private void applyRouteSummary(SharedRideRequest request, SharedRideRequestResponse response) {
        if (response == null || request == null) {
            return;
        }
        SharedRide ride = request.getSharedRide();
        if (ride != null) {
            response.setRoute(toRouteSummary(ride.getRoute()));
        }
    }

    private void applyDriverApproachData(SharedRideRequest request, SharedRideRequestResponse response) {
        if (request == null || response == null) {
            return;
        }
        SharedRide ride = request.getSharedRide();
        if (ride == null) {
            return;
        }
        if (response.getPolylineFromDriverToPickup() == null) {
            response.setPolylineFromDriverToPickup(ride.getDriverApproachPolyline());
        }
        response.setDriverApproachDistanceMeters(ride.getDriverApproachDistanceMeters());
        response.setDriverApproachDurationSeconds(ride.getDriverApproachDurationSeconds());
        response.setDriverApproachEta(ride.getDriverApproachEta());
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

    private Location findOrCreateLocation(Integer locationId, LatLng latLng, String pointType) {
        boolean hasId = locationId != null;
        boolean hasCoords = latLng != null && latLng.latitude() != null && latLng.longitude() != null;

        if (hasId && !hasCoords) {
            return locationRepository.findById(locationId)
                    .orElseThrow(() -> BaseDomainException.formatted("ride.validation.invalid-location",
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
                                routingService.getAddressFromCoordinates(latLng.latitude(), latLng.longitude()));
                        newLocation.setCreatedAt(LocalDateTime.now());
                        newLocation.setIsPoi(false);
                        return locationRepository.save(newLocation);
                    });
        }

        if (hasId) {
            throw BaseDomainException.of("ride.validation.invalid-location",
                    "Provide either " + pointType.toLowerCase() + "LocationId or " + pointType.toLowerCase()
                            + "LatLng, not both");
        }

        throw BaseDomainException.of("ride.validation.invalid-location", "Either " + pointType.toLowerCase()
                + "LocationId or " + pointType.toLowerCase() + "LatLng must be provided");
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SharedRideRequestResponse> getMyCompletedRideRequests(Pageable pageable, Authentication authentication) {
        log.info("Fetching completed ride requests for authenticated rider: {}", authentication.getName());
        
        User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));

        RiderProfile rider = riderRepository.findByUserUserId(user.getUserId())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.rider-profile"));

        Integer riderId = rider.getRiderId();
        
        Page<SharedRideRequest> requestPage = requestRepository.findByRiderRiderIdAndStatusOrderByCreatedAtDesc(
            riderId, SharedRideRequestStatus.COMPLETED, pageable);

        return requestPage.map(this::buildRequestResponse);
    }

}

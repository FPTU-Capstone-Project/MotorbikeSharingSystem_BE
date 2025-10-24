package com.mssus.app.service.impl;

import com.mssus.app.common.enums.DeliveryMethod;
import com.mssus.app.common.enums.NotificationType;
import com.mssus.app.common.enums.DriverProfileStatus;
import com.mssus.app.common.enums.Priority;
import com.mssus.app.common.enums.RequestKind;
import com.mssus.app.common.enums.RiderProfileStatus;
import com.mssus.app.common.enums.SharedRideRequestStatus;
import com.mssus.app.common.enums.SharedRideStatus;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.config.properties.RideConfigurationProperties;
import com.mssus.app.dto.request.wallet.RideHoldReleaseRequest;
import com.mssus.app.dto.ride.AcceptRequestDto;
import com.mssus.app.dto.ride.BroadcastAcceptRequest;
import com.mssus.app.dto.ride.CreateRideRequestDto;
import com.mssus.app.dto.request.ride.JoinRideRequest;
import com.mssus.app.dto.request.wallet.RideConfirmHoldRequest;
import com.mssus.app.dto.request.wallet.WalletReleaseRequest;
import com.mssus.app.dto.response.ride.BroadcastingRideRequestResponse;
import com.mssus.app.dto.response.ride.RideMatchProposalResponse;
import com.mssus.app.dto.response.ride.SharedRideRequestResponse;
import com.mssus.app.dto.ride.LatLng;
import com.mssus.app.entity.*;
import com.mssus.app.mapper.SharedRideRequestMapper;
import com.mssus.app.service.pricing.model.Quote;
import com.mssus.app.repository.*;
import com.mssus.app.service.*;
import com.mssus.app.service.matching.RideMatchingCoordinator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final RideMatchingCoordinator matchingCoordinator;
    private final ApplicationEventPublisherService eventPublisherService;
    private final PricingConfigRepository pricingConfigRepository;
    private final NotificationService notificationService;
    private final RideFundCoordinatingService rideFundCoordinatingService;
    private final RoutingService routingService;

    @Override
    @Transactional
    public SharedRideRequestResponse createAIBookingRequest(CreateRideRequestDto request, Authentication authentication) {
        String username = authentication.getName();
        log.info("Rider {} creating AI booking request with quote {}", username, request.quoteId());

        User user = userRepository.findByEmail(username)
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));
        RiderProfile rider = riderRepository.findByUserUserId(user.getUserId())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.rider-profile"));

//        String activeProfile = Optional.ofNullable(AuthServiceImpl.userContext.get(user.getUserId().toString()))
//            .filter(Map.class::isInstance)
//            .map(obj -> (Map<String, Object>) obj)
//            .map(claims -> (String) claims.get("active_profile"))
//            .orElse(null);
//
//        System.out.println(activeProfile);
//
//        if (activeProfile == null || !activeProfile.equals("rider")) {
//            throw BaseDomainException.of("ride.unauthorized.invalid-profile",
//                "Active profile is not rider");
//        }

        Quote quote = quoteService.getQuote(request.quoteId());

        if (quote.riderId() != rider.getRiderId()) {
            throw BaseDomainException.of("ride.unauthorized.request-not-owner",
                "Quote belongs to different user");
        }

        BigDecimal fareAmount = quote.fare().total().amount();
        BigDecimal subtotalFare = quote.fare().subtotal().amount();
        PricingConfig pricingConfig = pricingConfigRepository.findByVersion(quote.fare().pricingVersion())
            .orElseThrow(() -> BaseDomainException.of("pricing-config.not-found.resource"));

        //TODO: Refactor to use fare rules from pricing service

        log.info("Creating AI booking - fare from quote: {} VND", fareAmount);

        LocalDateTime desiredPickupTime = request.desiredPickupTime() == null ?
            LocalDateTime.now(ZoneId.of(appTimezone)) :
            request.desiredPickupTime();

        SharedRideRequest rideRequest = SharedRideRequest.builder()
            .requestKind(RequestKind.BOOKING)
            .sharedRide(null)
            .rider(rider)
            .pickupLocation(quote.pickupLocation())
            .dropoffLocation(quote.dropoffLocation())
//            .pickupLocationId(quote.pickupLocationId())
//            .dropoffLocationId(quote.dropoffLocationId())
//            .pickupLat(quote.pickupLat())
//            .pickupLng(quote.pickupLng())
//            .dropoffLat(quote.dropoffLat())
//            .dropoffLng(quote.dropoffLng())
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

        List<SharedRideRequest> broadcastingRequests =
            requestRepository.findByStatus(SharedRideRequestStatus.BROADCASTING);

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

        if (ride.getCurrentPassengers() >= ride.getMaxPassengers()) {
            throw BaseDomainException.of("ride.validation.no-seats-available");
        }

        BigDecimal fareAmount = quote.fare().total().amount();
        BigDecimal subtotalFare = quote.fare().subtotal().amount();
        PricingConfig pricingConfig = pricingConfigRepository.findByVersion(quote.fare().pricingVersion())
            .orElseThrow(() -> BaseDomainException.of("pricing-config.not-found.resource"));

        LocalDateTime desiredPickupTime = request.desiredPickupTime() == null ?
            LocalDateTime.now(ZoneId.of(appTimezone)) :
            request.desiredPickupTime();


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
            savedRequest.getSharedRideRequestId(), rider.getRiderId(), rideId, fareAmount, savedRequest.getStatus());

        matchingCoordinator.initiateRideJoining(savedRequest.getSharedRideRequestId());
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

        return buildRequestResponse(request);
    }

//    @Override
//    @Transactional
//    public SharedRideRequestResponse acceptBroadcast(Integer requestId,
//                                                     BroadcastAcceptRequest request,
//                                                     Authentication authentication) {
//        String username = authentication.getName();
//        log.info("Driver {} accepting broadcast request {} with vehicle {}", username, requestId, request.vehicleId());
//
//        User user = userRepository.findByEmail(username)
//            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));
//        DriverProfile driver = driverRepository.findByUserUserId(user.getUserId())
//            .orElseThrow(() -> BaseDomainException.of("user.not-found.driver-profile"));
//
//        if (driver.getStatus() != DriverProfileStatus.ACTIVE) {
//            throw BaseDomainException.of("ride.validation.invalid-state",
//                "Driver profile is not active");
//        }
//
//        SharedRideRequest rideRequest = requestRepository.findById(requestId)
//            .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.request", requestId));
//
//        if (rideRequest.getRequestKind() != RequestKind.BOOKING) {
//            throw BaseDomainException.of("ride.validation.request-invalid-state",
//                "Broadcast acceptance is only available for booking requests");
//        }
//
//        if (rideRequest.getStatus() != SharedRideRequestStatus.BROADCASTING) {
//            throw BaseDomainException.of("ride.validation.request-invalid-state",
//                Map.of("currentState", rideRequest.getStatus()));
//        }
//
//        boolean locked = matchingCoordinator.beginBroadcastAcceptance(requestId, driver.getDriverId());
//        if (!locked) {
//            throw BaseDomainException.of("ride.validation.request-invalid-state",
//                "Broadcast offer is no longer available or already processed");
//        }
//
//        try {
//            Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
//                .orElseThrow(() -> BaseDomainException.formatted("ride.validation.vehicle-not-found",
//                    "Vehicle not found with ID: " + request.vehicleId()));
//
//            if (!vehicle.getDriver().getDriverId().equals(driver.getDriverId())) {
//                throw BaseDomainException.of("ride.unauthorized.not-owner",
//                    "You don't own this vehicle");
//            }
//
//            if (rideRepository.existsByDriverDriverIdAndStatus(driver.getDriverId(), SharedRideStatus.ONGOING)) {
//                throw BaseDomainException.of("ride.validation.invalid-state",
//                    "Driver currently has an ongoing ride");
//            }
//
//            LocalDateTime now = LocalDateTime.now(ZoneId.of(appTimezone));
//            LocalDateTime pickupTime = rideRequest.getPickupTime() == null ? now : rideRequest.getPickupTime();
//            LocalDateTime scheduledTime = pickupTime.isAfter(now) ? pickupTime : now;
//            Location startLocation = rideRequest.getPickupLocation();
//            Location endLocation = rideRequest.getDropoffLocation();
//
//            SharedRide newRide = new SharedRide();
//            newRide.setDriver(driver);
//            newRide.setVehicle(vehicle);
//            newRide.setStatus(SharedRideStatus.ONGOING);
//            int capacity = vehicle.getCapacity() != null
//                ? vehicle.getCapacity()
//                : Optional.ofNullable(driver.getMaxPassengers()).orElse(1);
//            if (capacity <= 0) {
//                capacity = 1;
//            }
//            newRide.setMaxPassengers(capacity);
//            newRide.setCurrentPassengers(1);
//            newRide.setPricingConfig(rideRequest.getPricingConfig());
//            newRide.setScheduledTime(scheduledTime);
//            newRide.setStartLocation(startLocation);
//            newRide.setEndLocation(endLocation);
//            newRide.setCreatedAt(LocalDateTime.now());
//
//            SharedRide savedRide = rideRepository.save(newRide);
//
//            int estimatedTravelTimeFromCurrentDriverPosToPickup = routingService.getEstimatedTravelTimeMinutes(
//                startLocation.getLat(),
//                startLocation.getLng(),
//                endLocation.getLat(),
//                endLocation.getLng()
//            );
//
//            rideRequest.setSharedRide(savedRide);
//            rideRequest.setStatus(SharedRideRequestStatus.CONFIRMED);
//            rideRequest.setEstimatedPickupTime(scheduledTime);
//            requestRepository.save(rideRequest);
//
//            RideMatchProposalResponse proposal = RideMatchProposalResponse.builder()
//                .sharedRideId(savedRide.getSharedRideId())
//                .driverId(driver.getDriverId())
//                .driverName(driver.getUser().getFullName())
//                .driverRating(driver.getRatingAvg())
//                .vehicleModel(vehicle.getModel())
//                .vehiclePlate(vehicle.getPlateNumber())
//                .scheduledTime(savedRide.getScheduledTime())
//                .availableSeats(Math.max(0,
//                    (savedRide.getMaxPassengers() == null ? 0 : savedRide.getMaxPassengers())
//                        - savedRide.getCurrentPassengers()))
//                .totalFare(rideRequest.getTotalFare())
//                .estimatedPickupTime(LocalDateTime.now().plusMinutes(estimatedTravelTimeFromCurrentDriverPosToPickup))
//                .estimatedDropoffTime()
//                .build();
//
//            matchingCoordinator.completeBroadcastAcceptance(requestId, proposal);
//
//            log.info("Broadcast request {} accepted by driver {} - new ride {}",
//                requestId, driver.getDriverId(), savedRide.getSharedRideId());
//
//            return buildRequestResponse(rideRequest);
//        } catch (RuntimeException ex) {
//            matchingCoordinator.failBroadcastAcceptance(requestId, ex.getMessage());
//            throw ex;
//        }
//    }

    @Override
    @Transactional
    public SharedRideRequestResponse acceptBroadcast(Integer requestId, BroadcastAcceptRequest request, Authentication authentication) {
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

        boolean locked = matchingCoordinator.beginBroadcastAcceptance(requestId, driver.getDriverId());
        if (!locked) {
            throw BaseDomainException.of("ride.validation.request-invalid-state",
                "Broadcast offer is no longer available or already processed");
        }

        try {

            if (rideRepository.existsByDriverDriverIdAndStatus(driver.getDriverId(), SharedRideStatus.ONGOING)) {
                throw BaseDomainException.of("ride.validation.invalid-state",
                    "Driver currently has an ongoing ride");
            }

            LocalDateTime now = LocalDateTime.now(ZoneId.of(appTimezone));
            LocalDateTime pickupTime = rideRequest.getPickupTime() == null ? now : rideRequest.getPickupTime();
            LocalDateTime scheduledTime = pickupTime.isAfter(now) ? pickupTime : now;

            Location startLocation = findOrCreateLocation(request.startLocationId(), request.startLatLng(), "Start");
            Location endLocation = rideRequest.getDropoffLocation();

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
            newRide.setMaxPassengers(capacity);
            newRide.setCurrentPassengers(1);
            newRide.setPricingConfig(rideRequest.getPricingConfig());
            newRide.setScheduledTime(scheduledTime);
            newRide.setStartLocation(startLocation);
            newRide.setEndLocation(endLocation);
            newRide.setCreatedAt(LocalDateTime.now());
            newRide.setStartedAt(LocalDateTime.now());

            SharedRide savedRide = rideRepository.save(newRide);

            int estimatedTravelTimeFromCurrentDriverPosToPickup = routingService.getEstimatedTravelTimeMinutes(
                startLocation.getLat(),
                startLocation.getLng(),
                rideRequest.getPickupLocation().getLat(),
                rideRequest.getPickupLocation().getLng()
            );
            LocalDateTime estimatedPickupTime = LocalDateTime.now().plusMinutes(estimatedTravelTimeFromCurrentDriverPosToPickup);
            LocalDateTime estimatedDropoffTime = estimatedPickupTime.plusMinutes(rideRequest.getDurationSeconds() / 60);

            rideRequest.setSharedRide(savedRide);
            rideRequest.setStatus(SharedRideRequestStatus.CONFIRMED);
            rideRequest.setEstimatedPickupTime(estimatedPickupTime);
            rideRequest.setEstimatedDropoffTime(estimatedDropoffTime);
            requestRepository.save(rideRequest);

            RideMatchProposalResponse proposal = RideMatchProposalResponse.builder()
                .sharedRideId(savedRide.getSharedRideId())
                .driverId(driver.getDriverId())
                .driverName(driver.getUser().getFullName())
                .driverRating(driver.getRatingAvg())
                .vehicleModel(vehicle.getModel())
                .vehiclePlate(vehicle.getPlateNumber())
                .scheduledTime(savedRide.getScheduledTime())
                .availableSeats(Math.max(0,
                    (savedRide.getMaxPassengers() == null ? 0 : savedRide.getMaxPassengers())
                        - savedRide.getCurrentPassengers()))
                .totalFare(rideRequest.getTotalFare())
                .estimatedPickupTime(estimatedPickupTime)
                .estimatedDropoffTime(estimatedDropoffTime)
                .build();

            matchingCoordinator.completeBroadcastAcceptance(requestId, proposal);

            log.info("Broadcast request {} accepted by driver {} - new ride {}",
                requestId, driver.getDriverId(), savedRide.getSharedRideId());

            return buildRequestResponse(rideRequest);
        } catch (RuntimeException ex) {
            matchingCoordinator.failBroadcastAcceptance(requestId, ex.getMessage());
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

        if (ride.getCurrentPassengers() >= ride.getMaxPassengers()) {
            throw BaseDomainException.of("ride.validation.no-seats-available");
        }

        boolean trackingAcceptance = false;
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

            int estimatedTravelTimeFromCurrentDriverPosToPickup = routingService.getEstimatedTravelTimeMinutes(
                ride.getStartLocation().getLat(),
                ride.getStartLocation().getLng(),
                request.getPickupLocation().getLat(),
                request.getPickupLocation().getLng()
            );
            LocalDateTime estimatedPickupTime = LocalDateTime.now().plusMinutes(estimatedTravelTimeFromCurrentDriverPosToPickup);
            LocalDateTime estimatedDropoffTime = estimatedPickupTime.plusMinutes(request.getDurationSeconds());

            request.setStatus(SharedRideRequestStatus.CONFIRMED);
            request.setEstimatedPickupTime(estimatedPickupTime);
            request.setEstimatedDropoffTime(estimatedDropoffTime);
            requestRepository.save(request);

            rideRepository.incrementPassengerCount(ride.getSharedRideId());

            log.info("Request {} accepted successfully for ride {}", requestId, acceptDto.rideId());

            if (trackingAcceptance) {
                matchingCoordinator.completeDriverAcceptance(requestId);
            }

        } catch (RuntimeException e) {
            if (trackingAcceptance) {
                matchingCoordinator.failDriverAcceptance(requestId, e.getMessage());
            }
            throw e;
        }

        return buildRequestResponse(request);
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
            request.getStatus() != SharedRideRequestStatus.CONFIRMED) {
            throw BaseDomainException.of("ride.validation.request-invalid-state",
                Map.of("currentState", request.getStatus()));
        }

        if (request.getRequestKind() == RequestKind.BOOKING &&
            request.getStatus() == SharedRideRequestStatus.PENDING) {
            matchingCoordinator.cancelMatching(requestId);
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

        // Release or partially release wallet hold
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

        if (request.getStatus() == SharedRideRequestStatus.CONFIRMED && request.getSharedRide() != null) {
            rideRepository.decrementPassengerCount(request.getSharedRide().getSharedRideId());
        }

        // Update request status
        request.setStatus(SharedRideRequestStatus.CANCELLED);
        requestRepository.save(request);

        log.info("Request {} cancelled successfully", requestId);

        // TODO: Notify driver if request was CONFIRMED (placeholder for MVP)
        // if (request.getSharedRide() != null) {
        //     notificationService.notifyDriverOfCancellation(request.getSharedRide().getDriver(), request);
        // }

        return buildRequestResponse(request);
    }

    private Integer ensureLocationExists(Integer locationId, Double lat, Double lng, String label) {
        if (locationId != null) {
            return locationId;
        }
        if (lat == null || lng == null) {
            throw BaseDomainException.of("ride.validation.invalid-location",
                label + " coordinates are missing");
        }

        return locationRepository.findByLatAndLng(lat, lng)
            .map(Location::getLocationId)
            .orElseGet(() -> {
                Location newLocation = new Location();
                newLocation.setName(label);
                newLocation.setLat(lat);
                newLocation.setLng(lng);
                return locationRepository.save(newLocation).getLocationId();
            });
    }

    private SharedRideRequestResponse buildRequestResponse(SharedRideRequest request) {

//        if (request.getPickupLocationId() != null) {
//            Location pickupLoc = locationRepository.findById(request.getPickupLocationId()).orElse(null);
//            if (pickupLoc != null) {
//                response.setPickupLocationName(pickupLoc.getName());
//                response.setPickupLat(pickupLoc.getLat());
//                response.setPickupLng(pickupLoc.getLng());
//            } else {
//                response.setPickupLocationName("Unknown Location");
//                response.setPickupLat(request.getPickupLat());
//                response.setPickupLng(request.getPickupLng());
//            }
//        } else {
//            response.setPickupLocationName("Custom Pickup Location");
//            response.setPickupLat(request.getPickupLat());
//            response.setPickupLng(request.getPickupLng());
//        }
//
//        if (request.getDropoffLocationId() != null) {
//            Location dropoffLoc = locationRepository.findById(request.getDropoffLocationId()).orElse(null);
//            if (dropoffLoc != null) {
//                response.setDropoffLocationName(dropoffLoc.getName());
//                response.setDropoffLat(dropoffLoc.getLat());
//                response.setDropoffLng(dropoffLoc.getLng());
//            } else {
//                response.setDropoffLocationName("Unknown Location");
//                response.setDropoffLat(request.getDropoffLat());
//                response.setDropoffLng(request.getDropoffLng());
//            }
//        } else {
//            response.setDropoffLocationName("Custom Dropoff Location");
//            response.setDropoffLat(request.getDropoffLat());
//            response.setDropoffLng(request.getDropoffLng());
//        }

        return requestMapper.toResponse(request);
    }

    private BroadcastingRideRequestResponse toBroadcastingResponse(SharedRideRequest request) {
//        String pickupName = resolveLocationName(
//            request.getPickupLocationId(),
//            request.getPickupLat(),
//            request.getPickupLng(),
//            "Custom Pickup Location");
//
//        String dropoffName = resolveLocationName(
//            request.getDropoffLocationId(),
//            request.getDropoffLat(),
//            request.getDropoffLng(),
//            "Custom Dropoff Location");
//
//        LatLng pickupCoordinates = (request.getPickupLat() != null && request.getPickupLng() != null)
//            ? new LatLng(request.getPickupLat(), request.getPickupLng())
//            : null;
//
//        LatLng dropoffCoordinates = (request.getDropoffLat() != null && request.getDropoffLng() != null)
//            ? new LatLng(request.getDropoffLat(), request.getDropoffLng())
//            : null;

        return new BroadcastingRideRequestResponse(
            request.getSharedRideRequestId(),
            request.getTotalFare(),
            request.getPickupLocation(),
            request.getDropoffLocation(),
//            pickupName,
//            dropoffName,
//            pickupCoordinates,
//            dropoffCoordinates,
            request.getPickupTime() != null ? request.getPickupTime().format(ISO_DATE_TIME) : null
        );
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

        throw BaseDomainException.of("ride.validation.invalid-location", "Either " + pointType.toLowerCase() + "LocationId or " + pointType.toLowerCase() + "LatLng must be provided");
    }


}

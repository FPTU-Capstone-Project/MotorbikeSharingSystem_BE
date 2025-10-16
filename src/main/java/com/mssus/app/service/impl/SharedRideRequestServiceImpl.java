package com.mssus.app.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mssus.app.common.enums.NotificationType;
import com.mssus.app.common.enums.Priority;
import com.mssus.app.common.enums.RequestKind;
import com.mssus.app.common.enums.SharedRideRequestStatus;
import com.mssus.app.common.enums.SharedRideStatus;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.config.properties.RideConfigurationProperties;
import com.mssus.app.dto.ride.AcceptRequestDto;
import com.mssus.app.dto.ride.CreateRideRequestDto;
import com.mssus.app.dto.request.ride.JoinRideRequest;
import com.mssus.app.dto.request.wallet.WalletHoldRequest;
import com.mssus.app.dto.request.wallet.WalletReleaseRequest;
import com.mssus.app.dto.response.ride.RideMatchProposalResponse;
import com.mssus.app.dto.response.ride.SharedRideRequestResponse;
import com.mssus.app.entity.*;
import com.mssus.app.mapper.SharedRideRequestMapper;
import com.mssus.app.pricing.model.Quote;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SharedRideRequestServiceImpl implements SharedRideRequestService {
    @Value("${app.timezone:Asia/Ho_Chi_Minh}")
    private String appTimezone;

    private final SharedRideRequestRepository requestRepository;
    private final SharedRideRepository rideRepository;
    private final RiderProfileRepository riderRepository;
    private final DriverProfileRepository driverRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final SharedRideRequestMapper requestMapper;
    private final QuoteService quoteService;
    private final BookingWalletService bookingWalletService;
    private final RideMatchingService matchingService;
    private final RideConfigurationProperties rideConfig;
    private final RideMatchingCoordinator matchingCoordinator;
    private final ApplicationEventPublisherService eventPublisherService;

    @Override
    @Transactional
    public SharedRideRequestResponse createAIBookingRequest(CreateRideRequestDto request, Authentication authentication) {
        String username = authentication.getName();
        log.info("Rider {} creating AI booking request with quote {}", username, request.quoteId());

        // Get authenticated rider
        User user = userRepository.findByEmail(username)
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));
        RiderProfile rider = riderRepository.findByUserUserId(user.getUserId())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.rider-profile"));

        String activeProfile = Optional.ofNullable(AuthServiceImpl.userContext.get(user.getUserId().toString()))
            .filter(Map.class::isInstance)
            .map(obj -> (Map<String, Object>) obj)
            .map(claims -> (String) claims.get("active_profile"))
            .orElse(null);

        if (activeProfile == null || !activeProfile.equals("rider")) {
            throw BaseDomainException.of("ride.unauthorized.invalid-profile",
                "Active profile is not rider");
        }

        // Get and validate quote
        Quote quote = quoteService.getQuote(request.quoteId());

        // Validate rider owns the quote
        if (quote.riderId() != rider.getRiderId()) {
            throw BaseDomainException.of("ride.unauthorized.request-not-owner",
                "Quote belongs to different user");
        }

        // Extract fare from quote
        BigDecimal fareAmount = BigDecimal.valueOf(quote.fare().total().amount());

        log.info("Creating AI booking - fare from quote: {} VND", fareAmount);

        LocalDateTime desiredPickupTime = request.desiredPickupTime() == null ?
            LocalDateTime.now(ZoneId.of(appTimezone)) :
            request.desiredPickupTime();

        SharedRideRequest rideRequest = SharedRideRequest.builder()
            .requestKind(RequestKind.BOOKING)
            .sharedRide(null) // NULL for AI_BOOKING until driver accepts
            .rider(rider)
            .pickupLocationId(quote.pickupLocationId())
            .dropoffLocationId(quote.dropoffLocationId())
            .pickupLat(quote.pickupLat())
            .pickupLng(quote.pickupLng())
            .dropoffLat(quote.dropoffLat())
            .dropoffLng(quote.dropoffLng())
            .status(SharedRideRequestStatus.PENDING)
            .fareAmount(fareAmount)
            .originalFare(fareAmount)
            .discountAmount(BigDecimal.ZERO)
            .coverageTimeStep(rideConfig.getMatching().getCoverageTimeStep())
            .pickupTime(desiredPickupTime)
            .estimatedPickupTime(LocalDateTime.now())
            .estimatedDropoffTime(LocalDateTime.now().plusMinutes(quote.durationS() / 60 + 5)) // +5 min buffer
            .actualPickupTime(null)
            .actualDropoffTime(null)
            .specialRequests(request.notes())
            .initiatedBy("rider")
            .createdAt(LocalDateTime.now())
            .build();

        SharedRideRequest savedRequest = requestRepository.save(rideRequest);

        try {
            WalletHoldRequest holdRequest = new WalletHoldRequest();
            holdRequest.setUserId(rider.getRiderId());
            holdRequest.setBookingId(savedRequest.getSharedRideRequestId());
            holdRequest.setAmount(fareAmount);
            holdRequest.setNote("Hold for join ride request #" + savedRequest.getSharedRideRequestId());

            bookingWalletService.holdFunds(holdRequest);

            log.info("Wallet hold placed for booking request {} - amount: {}",
                savedRequest.getSharedRideRequestId(), fareAmount);

        } catch (Exception e) {
            log.error("Failed to place wallet hold for request {}: {}",
                savedRequest.getSharedRideRequestId(), e.getMessage(), e);
            // Rollback: delete the request
            requestRepository.delete(savedRequest);
            throw BaseDomainException.of("ride.operation.wallet-hold-failed",
                "Failed to reserve funds: " + e.getMessage());
        }

        log.info("AI booking request created - ID: {}, rider: {}, fare: {}, status: {}",
            savedRequest.getSharedRideRequestId(), rider.getRiderId(),
            fareAmount, savedRequest.getStatus());
        
        // Publish an event to trigger matching after the transaction commits
        eventPublisherService.publishRideRequestCreatedEvent(savedRequest.getSharedRideRequestId());

        return buildRequestResponse(savedRequest);
    }

    @Override
    @Transactional
    public SharedRideRequestResponse requestToJoinRide(Integer rideId, JoinRideRequest request,
                                                       Authentication authentication) {
        String username = authentication.getName();
        log.info("Rider {} requesting to join ride {} with quote {}", username, rideId, request.quoteId());

        // Get authenticated rider
        User user = userRepository.findByEmail(username)
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));
        RiderProfile rider = riderRepository.findByUserUserId(user.getUserId())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.rider-profile"));

        // Get quote and validate
        Quote quote = quoteService.getQuote(request.quoteId());

        if (quote.riderId() != rider.getRiderId()) {
            throw BaseDomainException.of("ride.unauthorized.request-not-owner",
                "Quote belongs to different user");
        }

        // Get ride with pessimistic lock
        SharedRide ride = rideRepository.findByIdForUpdate(rideId)
            .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource", rideId));

        // Validate ride state
        if (ride.getStatus() != SharedRideStatus.SCHEDULED) {
            throw BaseDomainException.of("ride.validation.invalid-state",
                Map.of("currentState", ride.getStatus()));
        }

        // Validate available seats
        if (ride.getCurrentPassengers() >= ride.getMaxPassengers()) {
            throw BaseDomainException.of("ride.validation.no-seats-available");
        }

        // Extract fare from quote
        BigDecimal fareAmount = BigDecimal.valueOf(quote.fare().total().amount());

        // Create request entity
        SharedRideRequest rideRequest = new SharedRideRequest();
        rideRequest.setRequestKind(RequestKind.JOIN_RIDE);
        rideRequest.setSharedRide(ride);
        rideRequest.setRider(rider);
        rideRequest.setPickupLocationId(quote.pickupLocationId());
        rideRequest.setDropoffLocationId(quote.dropoffLocationId());
        rideRequest.setPickupLat(quote.pickupLat());
        rideRequest.setPickupLng(quote.pickupLng());
        rideRequest.setDropoffLat(quote.dropoffLat());
        rideRequest.setDropoffLng(quote.dropoffLng());
        rideRequest.setStatus(SharedRideRequestStatus.PENDING);
        rideRequest.setFareAmount(fareAmount);
        rideRequest.setOriginalFare(fareAmount);
        rideRequest.setDiscountAmount(BigDecimal.ZERO);
        rideRequest.setPickupTime(request.desiredPickupTime());
        rideRequest.setSpecialRequests(request.notes());
        rideRequest.setInitiatedBy("rider");
        rideRequest.setCreatedAt(LocalDateTime.now());

        SharedRideRequest savedRequest = requestRepository.save(rideRequest);

        // Place wallet hold (for JOIN_RIDE, hold on creation)
        try {
            WalletHoldRequest holdRequest = new WalletHoldRequest();
            holdRequest.setUserId(rider.getRiderId());
            holdRequest.setBookingId(savedRequest.getSharedRideRequestId());
            holdRequest.setAmount(fareAmount);
            holdRequest.setNote("Hold for join ride request #" + savedRequest.getSharedRideRequestId());

            bookingWalletService.holdFunds(holdRequest);

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

        return buildRequestResponse(savedRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public SharedRideRequestResponse getRequestById(Integer requestId) {
        SharedRideRequest request = requestRepository.findById(requestId)
            .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.request", requestId));

        return buildRequestResponse(request);
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

        // Get authenticated driver
        User user = userRepository.findByEmail(username)
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));
        DriverProfile driver = driverRepository.findByUserUserId(user.getUserId())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.driver-profile"));

        // Get request
        SharedRideRequest request = requestRepository.findById(requestId)
            .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.request", requestId));

        // Get ride with pessimistic lock
        SharedRide ride = rideRepository.findByIdForUpdate(acceptDto.rideId())
            .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.resource", acceptDto.rideId()));

        // Validate driver owns the ride
        if (!ride.getDriver().getDriverId().equals(driver.getDriverId())) {
            throw BaseDomainException.of("ride.unauthorized.not-owner");
        }

        // Validate request status
        if (request.getStatus() != SharedRideRequestStatus.PENDING) {
            throw BaseDomainException.of("ride.validation.request-invalid-state",
                Map.of("currentState", request.getStatus()));
        }

        // Validate ride has available seats
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
        }

        try {
            // Handle based on request kind
            if (request.getRequestKind() == RequestKind.BOOKING) {
                // For AI_BOOKING: assign ride and place wallet hold
                request.setSharedRide(ride);

                // Place wallet hold
//                WalletHoldRequest holdRequest = new WalletHoldRequest();
//                holdRequest.setUserId(request.getRider().getRiderId());
//                holdRequest.setBookingId(requestId);
//                holdRequest.setAmount(request.getFareAmount());
//                holdRequest.setNote("Hold for AI booking acceptance #" + requestId);
//
//                try {
//                    bookingWalletService.holdFunds(holdRequest);
//                    log.info("Wallet hold placed for AI booking {} - amount: {}", requestId, request.getFareAmount());
//                } catch (Exception e) {
//                    log.error("Failed to place wallet hold for AI booking {}: {}", requestId, e.getMessage(), e);
//                    throw BaseDomainException.of("ride.operation.wallet-hold-failed",
//                        "Failed to reserve funds: " + e.getMessage());
//                }

            } else if (request.getRequestKind() == RequestKind.JOIN_RIDE) {
                // For JOIN_RIDE: validate ride ID matches
                if (request.getSharedRide() == null ||
                    !request.getSharedRide().getSharedRideId().equals(acceptDto.rideId())) {
                    throw BaseDomainException.of("ride.validation.invalid-state",
                        "Request is for a different ride");
                }
            }

            // Update request status
            request.setStatus(SharedRideRequestStatus.CONFIRMED);

            requestRepository.save(request);

            // Increment ride passenger count
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

        // Get authenticated driver
        User user = userRepository.findByEmail(username)
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));
        DriverProfile driver = driverRepository.findByUserUserId(user.getUserId())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.driver-profile"));

        // Get request
        SharedRideRequest request = requestRepository.findById(requestId)
            .orElseThrow(() -> BaseDomainException.formatted("ride.not-found.request", requestId));

        // Validate driver owns the associated ride
        if (request.getSharedRide() == null) {
            throw BaseDomainException.of("ride.validation.invalid-state",
                "Cannot reject AI_BOOKING request without ride assignment");
        }

        if (!request.getSharedRide().getDriver().getDriverId().equals(driver.getDriverId())) {
            throw BaseDomainException.of("ride.unauthorized.not-owner");
        }

        // Validate request status
        if (request.getStatus() != SharedRideRequestStatus.PENDING) {
            throw BaseDomainException.of("ride.validation.request-invalid-state",
                Map.of("currentState", request.getStatus()));
        }

        // Release wallet hold (for JOIN_RIDE)
        if (request.getRequestKind() == RequestKind.JOIN_RIDE) {
            try {
                WalletReleaseRequest releaseRequest = new WalletReleaseRequest();
                releaseRequest.setUserId(request.getRider().getRiderId());
                releaseRequest.setBookingId(requestId);
                releaseRequest.setAmount(request.getFareAmount());
                releaseRequest.setNote("Request rejected - #" + requestId);

                bookingWalletService.releaseFunds(releaseRequest);

                log.info("Wallet hold released for rejected request {} - amount: {}",
                    requestId, request.getFareAmount());

            } catch (Exception e) {
                log.error("Failed to release wallet hold for request {}: {}",
                    requestId, e.getMessage(), e);
                // Continue with rejection even if release fails
            }
        }

        // Update request status
        request.setStatus(SharedRideRequestStatus.CANCELLED);
        requestRepository.save(request);

        log.info("Request {} rejected successfully", requestId);

        // TODO: Notify rider of rejection (placeholder for MVP)
        // notificationService.notifyRiderOfRejection(request.getRider(), request, reason);

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
                cancellationFee = request.getFareAmount().multiply(feePercentage);

                log.info("Cancellation fee applied for request {} - fee: {} ({}%)",
                    requestId, cancellationFee, feePercentage.multiply(BigDecimal.valueOf(100)));
            }
        }

        // Release or partially release wallet hold
        try {
            if (withinGracePeriod || request.getStatus() == SharedRideRequestStatus.PENDING) {
                // Full release
                WalletReleaseRequest releaseRequest = new WalletReleaseRequest();
                releaseRequest.setUserId(request.getRider().getRiderId());
                releaseRequest.setBookingId(requestId);
                releaseRequest.setAmount(request.getFareAmount());
                releaseRequest.setNote("Request cancelled - #" + requestId);

                bookingWalletService.releaseFunds(releaseRequest);

                log.info("Full wallet hold released for cancelled request {} - amount: {}",
                    requestId, request.getFareAmount());

            } else {
                // TODO: Implement partial release with cancellation fee capture
                // For MVP, do full release and log warning
                WalletReleaseRequest releaseRequest = new WalletReleaseRequest();
                releaseRequest.setUserId(request.getRider().getRiderId());
                releaseRequest.setBookingId(requestId);
                releaseRequest.setAmount(request.getFareAmount());
                releaseRequest.setNote("Request cancelled with fee - #" + requestId);

                bookingWalletService.releaseFunds(releaseRequest);

                log.warn("TODO: Implement cancellation fee capture for request {} - fee: {}",
                    requestId, cancellationFee);
            }

        } catch (Exception e) {
            log.error("Failed to release wallet hold for request {}: {}",
                requestId, e.getMessage(), e);
            // Continue with cancellation even if release fails
        }

        // Decrement ride passenger count if CONFIRMED
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

    /**
     * Validate location coordinates match quote (within tolerance).
     * Tolerance: 100 meters (~0.001 degrees)
     */
    private void validateLocationMatchesQuote(Location location, double quoteLat, double quoteLng, String locationType) {
        double latDiff = Math.abs(location.getLat() - quoteLat);
        double lngDiff = Math.abs(location.getLng() - quoteLng);
        double tolerance = 0.001; // ~100 meters

        if (latDiff > tolerance || lngDiff > tolerance) {
            throw BaseDomainException.of("ride.validation.invalid-location",
                String.format("%s location coordinates don't match quote (lat: %.6f vs %.6f, lng: %.6f vs %.6f)",
                    locationType, location.getLat(), quoteLat, location.getLng(), quoteLng));
        }
    }

    private SharedRideRequestResponse buildRequestResponse(SharedRideRequest request) {
        SharedRideRequestResponse response = requestMapper.toResponse(request);

        if (request.getPickupLocationId() != null) {
            Location pickupLoc = locationRepository.findById(request.getPickupLocationId()).orElse(null);
            if (pickupLoc != null) {
                response.setPickupLocationName(pickupLoc.getName());
                response.setPickupLat(pickupLoc.getLat());
                response.setPickupLng(pickupLoc.getLng());
            } else {
                response.setPickupLocationName("Unknown Location");
                response.setPickupLat(request.getPickupLat());
                response.setPickupLng(request.getPickupLng());
            }
        } else {
            response.setPickupLocationName("Custom Pickup Location");
            response.setPickupLat(request.getPickupLat());
            response.setPickupLng(request.getPickupLng());
        }

        if (request.getDropoffLocationId() != null) {
            Location dropoffLoc = locationRepository.findById(request.getDropoffLocationId()).orElse(null);
            if (dropoffLoc != null) {
                response.setDropoffLocationName(dropoffLoc.getName());
                response.setDropoffLat(dropoffLoc.getLat());
                response.setDropoffLng(dropoffLoc.getLng());
            } else {
                response.setDropoffLocationName("Unknown Location");
                response.setDropoffLat(request.getDropoffLat());
                response.setDropoffLng(request.getDropoffLng());
            }
        } else {
            response.setDropoffLocationName("Custom Dropoff Location");
            response.setDropoffLat(request.getDropoffLat());
            response.setDropoffLng(request.getDropoffLng());
        }

        return response;
    }

}

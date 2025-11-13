package com.mssus.app.service.domain.matching;

import com.mssus.app.common.enums.RequestKind;
import com.mssus.app.common.enums.SharedRideRequestStatus;
import com.mssus.app.infrastructure.config.properties.RideConfigurationProperties;
import com.mssus.app.dto.domain.notification.DriverRideOfferNotification;
import com.mssus.app.dto.domain.notification.RiderMatchStatusNotification;
import com.mssus.app.dto.response.ride.RideMatchProposalResponse;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.Location;
import com.mssus.app.entity.SharedRideRequest;
import com.mssus.app.infrastructure.config.properties.RideMessagingProperties;
import com.mssus.app.messaging.RideMatchingCommandPublisher;
import com.mssus.app.messaging.RideNotificationEventPublisher;
import com.mssus.app.messaging.dto.MatchingCommandMessage;
import com.mssus.app.messaging.dto.MatchingCommandType;
import com.mssus.app.messaging.dto.RideRequestCreatedMessage;
import com.mssus.app.repository.DriverProfileRepository;
import com.mssus.app.repository.SharedRideRequestRepository;
import com.mssus.app.service.RealTimeNotificationService;
import com.mssus.app.service.RideMatchingService;
import com.mssus.app.service.domain.matching.session.ActiveOfferState;
import com.mssus.app.service.domain.matching.session.MatchingSessionPhase;
import com.mssus.app.service.domain.matching.session.MatchingSessionRepository;
import com.mssus.app.service.domain.matching.session.MatchingSessionState;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.messaging.ride", name = {"enabled", "matching-enabled"}, havingValue = "true")
public class QueueRideMatchingOrchestrator {

    private static final Duration STALE_SESSION_SKEW = Duration.ofSeconds(5);

    private final SharedRideRequestRepository requestRepository;
    private final DriverProfileRepository driverRepository;
    private final RideMatchingService rideMatchingService;
    private final MatchingResponseAssembler responseAssembler;
    private final RealTimeNotificationService notificationService;
    private final DriverDecisionGateway decisionGateway;
    private final MatchingSessionRepository sessionRepository;
    private final RideMatchingCommandPublisher commandPublisher;
    private final RideMessagingProperties properties;
    private final RideConfigurationProperties rideConfig;
    private final ObjectProvider<RideNotificationEventPublisher> notificationPublisherProvider;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    @RabbitListener(queues = "${app.messaging.ride.ride-request-created-queue}", autoStartup = "true")
    public void onRideRequestCreated(@Payload RideRequestCreatedMessage message) {
        if (message == null || message.getRequestId() == null) {
            return;
        }

        Integer requestId = message.getRequestId();

        SharedRideRequest request = requestRepository.findById(requestId).orElse(null);
        if (request == null) {
            log.warn("Ride request {} not found when handling created event", requestId);
            return;
        }

        Optional<MatchingSessionState> existingSession = sessionRepository.find(requestId);
        if (existingSession.isPresent()) {
            MatchingSessionState state = existingSession.get();
            if (!isStaleSession(state, request)) {
                log.debug("Matching session already exists for request {}, skipping duplicate create event", requestId);
                return;
            }

            log.info("Detected stale matching session for request {} (session created {}, request created {}). Purging.",
                requestId, state.getRequestCreatedAt(), request.getCreatedAt());
            sessionRepository.delete(requestId);
        }

        if (request.getStatus() != SharedRideRequestStatus.PENDING) {
            log.debug("Request {} not eligible for queue-based matching (status={})",
                requestId, request.getStatus());
            return;
        }

        // Handle based on request kind
        if (request.getRequestKind() == RequestKind.JOIN_RIDE) {
            handleJoinRequest(request);
        } else if (request.getRequestKind() == RequestKind.BOOKING) {
            handleBookingRequest(request);
        } else {
            log.debug("Unknown request kind {} for request {}", 
                request.getRequestKind(), requestId);
        }
    }

    private void handleBookingRequest(SharedRideRequest request) {
        Integer requestId = request.getSharedRideRequestId();
        log.info("Handling booking request {} - calling matching service", requestId);
        
        List<RideMatchProposalResponse> proposals = rideMatchingService.findMatches(request);
        log.info("Matching service returned {} proposals for request {}", proposals.size(), requestId);

        Instant requestCreatedAt = Optional.ofNullable(toInstant(request.getCreatedAt()))
            .orElse(Instant.now());
        
        MatchingSessionState session = MatchingSessionState.initialize(
            requestId,
            Instant.now().plus(sessionTtl()),
            proposals,
            requestCreatedAt);
        
        log.info("Initialized session for request {} - saving to Redis", requestId);
        sessionRepository.save(session, sessionTtl());
        log.info("Session saved to Redis for request {}", requestId);

        recordMetric("booking.created", proposals.isEmpty() ? "no_candidates" : "success");
        
        if (proposals.isEmpty()) {
            log.info("No candidates found for request {} – entering broadcast mode immediately", requestId);
            // When 0 candidates found, immediately broadcast to all eligible drivers
            if (!tryEnterBroadcast(session, request)) {
                // Broadcast failed (no eligible drivers or disabled)
                dispatchRiderStatus(request, responseAssembler.toRiderNoMatch(request));
            }
            return;
        }

        log.info("Publishing SEND_NEXT_OFFER command for request {}", requestId);
        commandPublisher.publish(MatchingCommandMessage.sendNext(requestId, 0));
        log.info("SEND_NEXT_OFFER command published for request {}", requestId);
    }

    private void handleJoinRequest(SharedRideRequest request) {
        Integer requestId = request.getSharedRideRequestId();
        
        if (request.getSharedRide() == null) {
            log.warn("Join request {} has no associated ride", requestId);
            dispatchRiderStatus(request, responseAssembler.toRiderJoinRequestFailed(
                request, "No ride specified"));
            return;
        }

        Integer rideId = request.getSharedRide().getSharedRideId();
        Integer driverId = request.getSharedRide().getDriver().getDriverId();

        // Create a simple session for join request (no proposals needed)
        Instant requestCreatedAt = Optional.ofNullable(toInstant(request.getCreatedAt()))
            .orElse(Instant.now());

        MatchingSessionState session = MatchingSessionState.builder()
            .requestId(requestId)
            .requestKind(RequestKind.JOIN_RIDE) // Explicitly mark as JOIN_RIDE
            .requestCreatedAt(requestCreatedAt)
            .phase(MatchingSessionPhase.MATCHING)
            .proposals(List.of()) // Empty proposals for join requests
            .nextProposalIndex(0)
            .requestDeadline(Instant.now().plus(sessionTtl()))
            .notifiedDrivers(new HashSet<>())
            .build();
        sessionRepository.save(session, sessionTtl());

        log.info("Initialized JOIN_RIDE session for request {} targeting driver {} on ride {}", 
            requestId, driverId, rideId);

        // Send notification to the specific driver
        sendJoinRequestNotification(session, request, rideId, driverId);
    }

    private void sendJoinRequestNotification(MatchingSessionState session, 
                                             SharedRideRequest request,
                                             Integer rideId,
                                             Integer driverId) {
        DriverProfile driver = driverRepository.findById(driverId).orElse(null);
        if (driver == null || driver.getUser() == null) {
            log.warn("Cannot send join request - driver {} not found", driverId);
            session.markExpired();
            sessionRepository.save(session, sessionTtl());
            dispatchRiderStatus(request, responseAssembler.toRiderJoinRequestFailed(
                request, "Driver not found"));
            return;
        }

        Location pickup = request.getPickupLocation();
        Location dropoff = request.getDropoffLocation();
        Duration responseWindow = driverResponseWindow();
        Instant offerDeadline = Instant.now().plus(responseWindow);

        DriverRideOfferNotification driverPayload = responseAssembler.toDriverJoinRequest(
            request,
            driver,
            pickup,
            dropoff,
            offerDeadline);

        dispatchDriverOffer(request, driver, driverPayload);

        // Set active offer for join request validation
        session.setActiveOffer(ActiveOfferState.builder()
            .driverId(driverId)
            .rideId(rideId)
            .expiresAt(offerDeadline)
            .build());
        session.recordNotifiedDriver(driverId);
        session.setPhase(MatchingSessionPhase.AWAITING_CONFIRMATION);
        sessionRepository.save(session, sessionTtl());

        // Register timeout
        decisionGateway.registerOffer(
            request.getSharedRideRequestId(),
            rideId,
            driver.getDriverId(),
            responseWindow,
            () -> commandPublisher.publish(MatchingCommandMessage.driverTimeout(
                request.getSharedRideRequestId(),
                driver.getDriverId(),
                rideId)));

        log.info("Sent join request to driver {} for request {} (ride {})",
            driver.getDriverId(), request.getSharedRideRequestId(), rideId);
    }

    @RabbitListener(queues = "${app.messaging.ride.matching-command-queue}", autoStartup = "true", concurrency = "1")
    public void onMatchingCommand(@Payload MatchingCommandMessage command) {
        if (command == null || command.getRequestId() == null || command.getCommandType() == null) {
            log.warn("Received invalid matching command - command: {}", command);
            return;
        }
        
        log.info("Received matching command {} for request {} (correlationId: {})", 
            command.getCommandType(), command.getRequestId(), command.getCorrelationId());
        
        MatchingSessionState state = sessionRepository.find(command.getRequestId()).orElse(null);
        if (state == null) {
            log.warn("No session found for request {} when handling command {} - session may not be saved yet or Redis error", 
                command.getRequestId(), command.getCommandType());
            return;
        }

        // Idempotency check using correlation ID
        if (!state.shouldProcess(command.getCorrelationId())) {
            log.debug("Skipping duplicate message {} for request {}", 
                command.getCorrelationId(), command.getRequestId());
            return;
        }

        MatchingCommandType type = command.getCommandType();
        try {
            switch (type) {
                case SEND_NEXT_OFFER -> handleSendNext(state);
                case DRIVER_TIMEOUT -> handleDriverTimeout(state, command);
                case DRIVER_RESPONSE -> handleDriverResponse(state, command);
                case BROADCAST_TIMEOUT -> handleBroadcastTimeout(state);
                case CANCEL_MATCHING -> handleCancel(state);
                default -> log.warn("Unhandled matching command type {}", type);
            }
            // Save state after processing to persist idempotency info
            sessionRepository.save(state, sessionTtl());
        } catch (Exception e) {
            log.error("Error processing matching command {} for request {}", 
                command.getCommandType(), command.getRequestId(), e);
            // Don't rethrow - let message be acked and potentially go to DLQ on retry
        }
    }

    private Duration sessionTtl() {
        return Optional.ofNullable(properties.getMatchingRequestTimeout())
            .orElse(Duration.ofMinutes(15)); // Default 15 minutes total matching window
    }

    private Duration driverResponseWindow() {
        return Optional.ofNullable(properties.getDriverResponseWindow())
            .orElse(Duration.ofSeconds(90));
    }

    private void handleSendNext(MatchingSessionState state) {
        if (state.isTerminal()) {
            return;
        }

        RideMatchProposalResponse proposal = state.consumeNextProposal();
        if (proposal == null) {
            handleNoMoreCandidates(state);
            return;
        }

        state.recordNotifiedDriver(proposal.getDriverId());
        sessionRepository.save(state, sessionTtl());

        DriverProfile driver = driverRepository.findById(proposal.getDriverId()).orElse(null);
        if (driver == null) {
            log.warn("Driver {} not found for proposal on request {}", proposal.getDriverId(), state.getRequestId());
            commandPublisher.publish(MatchingCommandMessage.sendNext(state.getRequestId(), state.getNextProposalIndex()));
            return;
        }

        SharedRideRequest request = requestRepository.findById(state.getRequestId()).orElse(null);
        if (request == null) {
            log.warn("Request {} not found when dispatching offer", state.getRequestId());
            return;
        }

        Location pickup = request.getPickupLocation();
        Location dropoff = request.getDropoffLocation();
        DriverRideOfferNotification payload = responseAssembler.toDriverOffer(
            request,
            driver,
            pickup,
            dropoff,
            proposal,
            state.getNextProposalIndex(),
            Instant.now().plus(driverResponseWindow()),
            (int) driverResponseWindow().toSeconds());

        dispatchDriverOffer(request, driver, payload);

        decisionGateway.registerOffer(
            state.getRequestId(),
            proposal.getSharedRideId(),
            driver.getDriverId(),
            driverResponseWindow(),
            () -> commandPublisher.publish(MatchingCommandMessage.driverTimeout(
                state.getRequestId(),
                driver.getDriverId(),
                proposal.getSharedRideId())));

        state.setActiveOffer(ActiveOfferState.builder()
            .driverId(driver.getDriverId())
            .rideId(proposal.getSharedRideId())
            .expiresAt(Instant.now().plus(driverResponseWindow()))
            .build());
        state.setPhase(MatchingSessionPhase.AWAITING_CONFIRMATION);
        sessionRepository.save(state, sessionTtl());

        commandPublisher.publishDriverTimeout(
            MatchingCommandMessage.driverTimeout(state.getRequestId(), driver.getDriverId(), proposal.getSharedRideId()),
            driverResponseWindow());
    }

    private void handleDriverTimeout(MatchingSessionState state, MatchingCommandMessage command) {
        ActiveOfferState active = state.getActiveOffer();
        if (active == null || !active.matches(command.getRideId(), command.getDriverId())) {
            log.debug("Ignoring timeout for request {} driver {} ride {} - state no longer matches",
                state.getRequestId(), command.getDriverId(), command.getRideId());
            return;
        }
        
        state.setActiveOffer(null);
        
        // JOIN_RIDE requests should fail immediately on timeout (no retry, no broadcast)
        if (state.isJoinRequest()) {
            log.info("JOIN_RIDE request {} timed out - driver {} did not respond. Marking as expired.",
                state.getRequestId(), command.getDriverId());
            state.markExpired();
            sessionRepository.save(state, sessionTtl());
            
            SharedRideRequest request = requestRepository.findById(state.getRequestId()).orElse(null);
            if (request != null) {
                markRequestExpired(request);
                dispatchRiderStatus(request, responseAssembler.toRiderJoinRequestFailed(
                    request, "Driver did not respond in time"));
            }
            return;
        }
        
        // BOOKING requests continue to next candidate
        state.setPhase(MatchingSessionPhase.MATCHING);
        sessionRepository.save(state, sessionTtl());
        commandPublisher.publish(MatchingCommandMessage.sendNext(state.getRequestId(), state.getNextProposalIndex()));
    }

    private void handleDriverResponse(MatchingSessionState state, MatchingCommandMessage command) {
        boolean isBroadcast = Boolean.TRUE.equals(command.getBroadcast());

        if (!isBroadcast) {
            ActiveOfferState active = state.getActiveOffer();
            if (active == null || !active.matches(command.getRideId(), command.getDriverId())) {
                log.debug("Sequential driver response for request {} driver {} ignored - no matching active offer",
                    state.getRequestId(), command.getDriverId());
                recordMetric("driver.response", "ignored");
                return;
            }
        } else {
            if (!state.wasDriverNotified(command.getDriverId())) {
                log.debug("Broadcast driver response for request {} driver {} ignored - driver was not notified",
                    state.getRequestId(), command.getDriverId());
                recordMetric("driver.response", "ignored");
                return;
            }
        }

        log.info("Processing driver response for request {} from driver {} (broadcast: {})", 
            state.getRequestId(), command.getDriverId(), command.getBroadcast());

        SharedRideRequest request = requestRepository.findById(state.getRequestId()).orElse(null);
        RideMatchProposalResponse acceptedProposal = null;
        if (state.getProposals() != null) {
            acceptedProposal = state.getProposals().stream()
                .filter(p -> p.getSharedRideId() != null && p.getSharedRideId().equals(command.getRideId()))
                .findFirst()
                .orElse(null);
        }

        state.markCompleted();
        sessionRepository.save(state, sessionTtl());
        
        recordMetric("driver.response", "accepted");
        recordMetricWithTags("match.completed", 
            "type", isBroadcast ? "broadcast" : "sequential");

        if (request != null && request.getRider() != null && request.getRider().getUser() != null) {
            RiderMatchStatusNotification payload;
            boolean isJoinRequest = request.getRequestKind() == com.mssus.app.common.enums.RequestKind.JOIN_RIDE;
            
            if (isJoinRequest) {
                // JOIN request acceptance
                payload = responseAssembler.toRiderJoinRequestSuccess(request);
                log.info("Sending JOIN request success notification to rider {}", request.getRider().getUser().getUserId());
            } else if (acceptedProposal != null) {
                // BOOKING with matched proposal (sequential mode)
                payload = responseAssembler.toRiderMatchSuccess(request, acceptedProposal);
                log.info("Sending match success notification to rider {} with proposal for ride {}", 
                    request.getRider().getUser().getUserId(), acceptedProposal.getSharedRideId());
            } else {
                // Broadcast acceptance - generic notification
                payload = responseAssembler.toRiderLifecycleUpdate(
                    request,
                    "ACCEPTED",
                    "Driver accepted your ride request.");
                log.info("Sending broadcast acceptance notification to rider {}", request.getRider().getUser().getUserId());
            }
            dispatchRiderStatus(request, payload);
        } else {
            log.warn("Cannot send rider notification for request {} - missing request or rider data", 
                state.getRequestId());
        }
    }

    private void handleBroadcastTimeout(MatchingSessionState state) {
        if (!state.isBroadcasting()) {
            return;
        }
        state.markExpired();
        sessionRepository.save(state, sessionTtl());
        requestRepository.findById(state.getRequestId()).ifPresent(request -> {
            markRequestExpired(request);
            dispatchRiderStatus(request, responseAssembler.toRiderNoMatch(request));
        });
    }

    private void handleCancel(MatchingSessionState state) {
        state.markCancelled();
        sessionRepository.save(state, sessionTtl());
    }

    private void handleNoMoreCandidates(MatchingSessionState state) {
        SharedRideRequest request = requestRepository.findById(state.getRequestId()).orElse(null);
        if (request == null) {
            log.warn("Request {} not found when handling no more candidates", state.getRequestId());
            state.markExpired();
            sessionRepository.save(state, sessionTtl());
            return;
        }

        // JOIN_RIDE requests should never enter broadcast mode
        if (state.isJoinRequest()) {
            log.info("JOIN_RIDE request {} has no more candidates (should never happen for direct join). Marking as expired.",
                state.getRequestId());
            state.markExpired();
            sessionRepository.save(state, sessionTtl());
            markRequestExpired(request);
            dispatchRiderStatus(request, responseAssembler.toRiderJoinRequestFailed(
                request, "Unable to process join request"));
            return;
        }

        // Only BOOKING requests can enter broadcast mode
        if (tryEnterBroadcast(state, request)) {
            log.info("Entered broadcast mode for BOOKING request {}", state.getRequestId());
            return;
        }

        // No broadcast or broadcast not configured - mark as expired
        state.markExpired();
        sessionRepository.save(state, sessionTtl());
        markRequestExpired(request);
        dispatchRiderStatus(request, responseAssembler.toRiderNoMatch(request));
    }

    private boolean tryEnterBroadcast(MatchingSessionState state, SharedRideRequest request) {
        // JOIN_RIDE requests should NEVER enter broadcast mode
        if (state.isJoinRequest()) {
            log.warn("Attempted to enter broadcast mode for JOIN_RIDE request {} - this should never happen", 
                state.getRequestId());
            return false;
        }
        
        if (state.isBroadcasting() || state.isTerminal()) {
            log.debug("Cannot enter broadcast - state is {} for request {}", 
                state.getPhase(), state.getRequestId());
            return false;
        }

        if (request.getStatus() != SharedRideRequestStatus.PENDING) {
            log.debug("Cannot enter broadcast - request status is {} for request {}", 
                request.getStatus(), state.getRequestId());
            return false;
        }

        // Calculate remaining time from the 15-minute total matching timeout
        Duration remainingTime = Duration.between(Instant.now(), state.getRequestDeadline());
        if (remainingTime.isNegative() || remainingTime.isZero()) {
            log.info("Cannot enter broadcast - matching session expired for request {}", 
                state.getRequestId());
            return false;
        }

        // Find ALL eligible drivers (excluding already notified ones from sequential phase)
        List<Integer> excludedDriverIds = state.getNotifiedDrivers() == null ? 
            List.of() : List.copyOf(state.getNotifiedDrivers());
        List<DriverProfile> candidates = Optional
            .ofNullable(driverRepository.findBroadcastEligibleDrivers(excludedDriverIds))
            .orElse(List.of());
        boolean hasPushCandidates = !candidates.isEmpty();

        // Broadcast window = remaining time from 15-minute total
        Instant deadline = state.getRequestDeadline();
        long broadcastWindowSeconds = remainingTime.getSeconds();
        
        state.enterBroadcast(deadline);
        request.setStatus(SharedRideRequestStatus.BROADCASTING);
        requestRepository.save(request);
        sessionRepository.save(state, sessionTtl());

        // Send broadcast offers to all eligible drivers when push is enabled
        if (hasPushCandidates && properties.isBroadcastPushEnabled()) {
            sendBroadcastOffers(state, request, candidates, deadline, (int) broadcastWindowSeconds);
        } else if (hasPushCandidates) {
            log.info("Broadcast push disabled - request {} will be discoverable via marketplace only", state.getRequestId());
        } else {
            log.info("No eligible drivers to push - request {} stays discoverable via marketplace feed only",
                state.getRequestId());
        }

        // Schedule broadcast timeout using remaining time
        commandPublisher.publishBroadcastTimeout(
            MatchingCommandMessage.broadcastTimeout(state.getRequestId()),
            remainingTime);

        log.info("Broadcasting ride request {} to {} drivers with {} seconds remaining (total 15 min timeout)",
            state.getRequestId(), candidates.size(), broadcastWindowSeconds);

        return true;
    }

    private void sendBroadcastOffers(MatchingSessionState state,
                                     SharedRideRequest request,
                                     List<DriverProfile> candidates,
                                     Instant deadline,
                                     int responseWindowSeconds) {
        Location pickup = request.getPickupLocation();
        Location dropoff = request.getDropoffLocation();

        for (DriverProfile driver : candidates) {
            if (driver.getUser() == null) {
                log.warn("Skipping driver {} - no user associated", driver.getDriverId());
                continue;
            }

            DriverRideOfferNotification payload = responseAssembler.toDriverBroadcastOffer(
                request,
                driver,
                pickup,
                dropoff,
                deadline,
                responseWindowSeconds);

            dispatchDriverOffer(request, driver, payload);
            state.recordNotifiedDriver(driver.getDriverId());
        }

        sessionRepository.save(state, sessionTtl());
    }

    private void dispatchDriverOffer(SharedRideRequest request,
                                     DriverProfile driver,
                                     DriverRideOfferNotification payload) {
        if (request == null || driver == null || payload == null) {
            return;
        }
        if (properties.isNotificationsEnabled()) {
            RideNotificationEventPublisher publisher = notificationPublisherProvider.getIfAvailable();
            if (publisher != null) {
                publisher.publishDriverOffer(request.getSharedRideRequestId(), driver, payload);
                return;
            }
        }
        notificationService.notifyDriverOffer(driver, payload);
    }

    private void dispatchRiderStatus(SharedRideRequest request,
                                     RiderMatchStatusNotification payload) {
        if (request == null || request.getRider() == null || request.getRider().getUser() == null || payload == null) {
            log.warn("Cannot dispatch rider status - missing request, rider, or payload data");
            return;
        }
        if (properties.isNotificationsEnabled()) {
            RideNotificationEventPublisher publisher = notificationPublisherProvider.getIfAvailable();
            if (publisher != null) {
                log.info("Using MQ notification publisher for request {} rider {}", 
                    request.getSharedRideRequestId(), request.getRider().getUser().getUserId());
                publisher.publishRiderStatus(
                    request.getSharedRideRequestId(),
                    request.getRider().getUser().getUserId(),
                    payload);
                return;
            } else {
                log.warn("MQ notifications enabled but publisher not available - falling back to direct notification");
            }
        }
        log.info("Using direct notification service for request {} rider {}", 
            request.getSharedRideRequestId(), request.getRider().getUser().getUserId());
        notificationService.notifyRiderStatus(request.getRider().getUser(), payload);
    }

    public void publishDriverResponse(Integer requestId, Integer driverId, Integer rideId, boolean broadcast) {
        commandPublisher.publish(MatchingCommandMessage.driverResponse(
            requestId,
            driverId,
            rideId,
            broadcast,
            Map.of()));
    }

    /**
     * Registers that a driver has proactively claimed a broadcast request via the marketplace feed.
     * This allows the subsequent driver response to pass the {@code wasDriverNotified} validation.
     *
     * @return {@code true} when the session is in broadcasting state and the driver is recorded.
     */
    public boolean registerBroadcastInterest(Integer requestId, Integer driverId) {
        if (requestId == null || driverId == null) {
            return false;
        }

        MatchingSessionState state = sessionRepository.find(requestId).orElse(null);
        if (state == null) {
            log.warn("Cannot register broadcast interest - no session for request {}", requestId);
            return false;
        }

        if (!state.isBroadcasting() || state.isTerminal()) {
            log.info("Cannot register broadcast interest for request {} - state {}", requestId, state.getPhase());
            return false;
        }

        if (state.wasDriverNotified(driverId)) {
            return true;
        }

        state.recordNotifiedDriver(driverId);
        sessionRepository.save(state, sessionTtl());
        log.debug("Registered driver {} for broadcast request {}", driverId, requestId);
        return true;
    }

    public void publishCancellation(Integer requestId) {
        commandPublisher.publish(MatchingCommandMessage.cancel(requestId));
    }

    private void markRequestExpired(SharedRideRequest request) {
        if (request == null) {
            return;
        }
        if (request.getStatus() == SharedRideRequestStatus.EXPIRED) {
            return;
        }
        request.setStatus(SharedRideRequestStatus.EXPIRED);
        requestRepository.save(request);
    }

    private boolean isStaleSession(MatchingSessionState state, SharedRideRequest request) {
        Instant requestCreated = toInstant(request.getCreatedAt());
        Instant sessionCreated = state.getRequestCreatedAt();

        if (sessionCreated == null) {
            Instant lastProcessed = state.getLastProcessedAt();
            if (lastProcessed == null || requestCreated == null) {
                return true;
            }
            return requestCreated.isAfter(lastProcessed.plus(STALE_SESSION_SKEW));
        }

        if (requestCreated == null) {
            return false;
        }

        return requestCreated.isAfter(sessionCreated.plus(STALE_SESSION_SKEW));
    }

    private Instant toInstant(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }

    // Metrics recording methods
    private void recordMetric(String metricName, String outcome) {
        meterRegistryProvider.ifAvailable(registry -> 
            registry.counter("ride.matching." + metricName, "outcome", outcome).increment());
    }

    private void recordMetricWithTags(String metricName, String... tags) {
        meterRegistryProvider.ifAvailable(registry -> 
            registry.counter("ride.matching." + metricName, tags).increment());
    }
}

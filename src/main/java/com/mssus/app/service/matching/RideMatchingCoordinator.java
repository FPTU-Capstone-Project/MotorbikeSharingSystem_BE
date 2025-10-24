package com.mssus.app.service.matching;

import com.mssus.app.common.enums.RequestKind;
import com.mssus.app.common.enums.SharedRideRequestStatus;
import com.mssus.app.config.properties.RideConfigurationProperties;
import com.mssus.app.dto.notification.DriverRideOfferNotification;
import com.mssus.app.dto.response.ride.RideMatchProposalResponse;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.Location;
import com.mssus.app.entity.SharedRideRequest;
import com.mssus.app.repository.DriverProfileRepository;
import com.mssus.app.repository.LocationRepository;
import com.mssus.app.repository.SharedRideRequestRepository;
import com.mssus.app.service.RealTimeNotificationService;
import com.mssus.app.dto.request.wallet.RideHoldReleaseRequest;
import com.mssus.app.service.RideFundCoordinatingService;
import com.mssus.app.service.RideMatchingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.time.Instant;

import com.mssus.app.service.RideRequestCreatedEvent;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Set;

import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Coordinates the ride matching workflow after a rider submits an AI booking request.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Fetch ranked ride candidates via {@link RideMatchingService}</li>
 *   <li>Send offers sequentially to drivers and enforce response deadlines</li>
 *   <li>Resume matching when a driver declines / times out / acceptance fails</li>
 *   <li>Notify riders when a match succeeds or when no drivers are available</li>
 * </ul>
 */
@Service
@Slf4j
public class RideMatchingCoordinator {

    private final SharedRideRequestRepository requestRepository;
    private final DriverProfileRepository driverRepository;
    private final LocationRepository locationRepository;
    private final RideMatchingService rideMatchingService;
    private final RideConfigurationProperties rideConfig;
    private final RealTimeNotificationService notificationService;
    private final DriverDecisionGateway decisionGateway;
    private final MatchingResponseAssembler responseAssembler;
    private final ThreadPoolTaskExecutor matchingExecutor;
    private final RideFundCoordinatingService rideFundCoordinatingService;
    private final ScheduledExecutorService matchingScheduler;

    public RideMatchingCoordinator(
        SharedRideRequestRepository requestRepository,
        DriverProfileRepository driverRepository,
        LocationRepository locationRepository,
        RideMatchingService rideMatchingService,
        RideConfigurationProperties rideConfig,
        RealTimeNotificationService notificationService,
        DriverDecisionGateway decisionGateway,
        MatchingResponseAssembler responseAssembler,
        @Qualifier("matchingTaskExecutor") ThreadPoolTaskExecutor matchingExecutor,
        @Qualifier("matchingScheduler") ScheduledExecutorService matchingScheduler,
        RideFundCoordinatingService rideFundCoordinatingService) {

        this.requestRepository = requestRepository;
        this.driverRepository = driverRepository;
        this.locationRepository = locationRepository;
        this.rideMatchingService = rideMatchingService;
        this.rideConfig = rideConfig;
        this.notificationService = notificationService;
        this.decisionGateway = decisionGateway;
        this.responseAssembler = responseAssembler;
        this.matchingExecutor = matchingExecutor;
        this.rideFundCoordinatingService = rideFundCoordinatingService;
        this.matchingScheduler = matchingScheduler;
    }

    @Autowired
    @Lazy
    private RideMatchingCoordinator self;

    private final ConcurrentHashMap<Integer, MatchingSession> sessions = new ConcurrentHashMap<>();

    @TransactionalEventListener(fallbackExecution = true)
    public void onRideRequestCreated(RideRequestCreatedEvent event) {
        log.info("Received RideRequestCreatedEvent for request ID: {}", event.getRequestId());
        self.initiateMatching(event.getRequestId());
    }

    @Async("matchingTaskExecutor")
    public void initiateMatching(Integer requestId) {
        if (requestId == null) {
            return;
        }

        if (sessions.containsKey(requestId)) {
            log.debug("Matching already in progress for request {}", requestId);
            return;
        }

        Optional<SharedRideRequest> maybeRequest = requestRepository.findById(requestId);
        if (maybeRequest.isEmpty()) {
            log.warn("Cannot start matching. Request {} not found.", requestId);
            return;
        }

        SharedRideRequest request = maybeRequest.get();
        if (request.getRequestKind() != RequestKind.BOOKING ||
            request.getStatus() != SharedRideRequestStatus.PENDING) {
            log.debug("Skipping matching for request {} - kind={} status={}",
                requestId, request.getRequestKind(), request.getStatus());
            return;
        }

        List<RideMatchProposalResponse> proposals = rideMatchingService.findMatches(request);
        CandidateSelector selector = new CandidateSelector(proposals);
        Location pickup = request.getPickupLocation();
        Location dropoff = request.getDropoffLocation();

        MatchingSession session = new MatchingSession(
            request.getSharedRideRequestId(),
            selector,
            pickup,
            dropoff,
            Instant.now().plus(rideConfig.getRequestAcceptTimeout()));

        sessions.put(requestId, session);

        if (!selector.hasNext()) {
            handleNoCandidates(session, request);
            return;
        }

        scheduleNext(session);
    }

    @Async("matchingTaskExecutor")
    public void initiateRideJoining(Integer requestId) {
        if (requestId == null) {
            return;
        }

        if (sessions.containsKey(requestId)) {
            log.debug("Join request already in progress for request {}", requestId);
            return;
        }

        Optional<SharedRideRequest> maybeRequest = requestRepository.findById(requestId);
        if (maybeRequest.isEmpty()) {
            log.warn("Cannot start join request. Request {} not found.", requestId);
            return;
        }

        SharedRideRequest request = maybeRequest.get();
        if (request.getRequestKind() != RequestKind.JOIN_RIDE ||
            request.getStatus() != SharedRideRequestStatus.PENDING ||
            request.getSharedRide() == null) {
            log.debug("Skipping join request for request {} - kind={} status={} ride={}",
                requestId, request.getRequestKind(), request.getStatus(),
                request.getSharedRide() != null ? request.getSharedRide().getSharedRideId() : "null");
            return;
        }

        // Get locations for notification
        Location pickup = request.getPickupLocation();
        Location dropoff = request.getDropoffLocation();
//        Location pickup = request.getPickupLocationId() != null
//            ? locationRepository.findById(request.getPickupLocationId()).orElse(null)
//            : null;
//        Location dropoff = request.getDropoffLocationId() != null
//            ? locationRepository.findById(request.getDropoffLocationId()).orElse(null)
//            : null;

        JoinRequestSession session = new JoinRequestSession(
            request.getSharedRideRequestId(),
            request.getSharedRide().getSharedRideId(),
            request.getSharedRide().getDriver().getDriverId(),
            pickup,
            dropoff,
            Instant.now().plus(rideConfig.getRequestAcceptTimeout()));

        sessions.put(requestId, MatchingSession.forJoinRequest(session));

        // Send notification to the specific driver
        sendJoinRequestNotification(session, request);
    }

    private void sendJoinRequestNotification(JoinRequestSession session, SharedRideRequest request) {
        Optional<DriverProfile> maybeDriver = driverRepository.findById(session.driverId());
        if (maybeDriver.isEmpty() || maybeDriver.get().getUser() == null) {
            log.warn("Cannot send join request - driver {} not found", session.driverId());
            handleJoinRequestFailure(session, request, "Driver not found");
            return;
        }

        DriverProfile driver = maybeDriver.get();

        Duration responseWindow = Duration.ofSeconds(
            rideConfig.getMatching().getDriverResponseSeconds());
        Instant offerDeadline = Instant.now().plus(responseWindow);

        // Create a join-specific notification payload
        DriverRideOfferNotification driverPayload = responseAssembler.toDriverJoinRequest(
            request,
            driver,
            session.pickupLocation(),
            session.dropoffLocation(),
            offerDeadline);

        notificationService.notifyDriverJoinRequest(driver, driverPayload);

        // Register with decision gateway
        decisionGateway.registerOffer(
            request.getSharedRideRequestId(),
            session.rideId(),
            driver.getDriverId(),
            responseWindow,
            () -> handleJoinRequestTimeout(session, request));

        log.info("Sent join request to driver {} for request {} (ride {})",
            driver.getDriverId(),
            request.getSharedRideRequestId(),
            session.rideId());
    }

    @Async("matchingTaskExecutor")
    public void rejectJoinRequest(Integer requestId, String reason) {
        Optional<SharedRideRequest> maybeRequest = requestRepository.findById(requestId);
        if (maybeRequest.isEmpty()) {
            log.warn("Cannot reject join request. Request {} not found.", requestId);
            return;
        }
        SharedRideRequest request = maybeRequest.get();

        // The session might be null if the rejection happens after a timeout, which is fine.
        MatchingSession session = sessions.get(requestId);
        JoinRequestSession joinSession = session != null ? session.joinSession : null;

        handleJoinRequestFailure(joinSession, request, reason);
    }

    private void handleJoinRequestTimeout(JoinRequestSession session, SharedRideRequest request) {
        log.info("Driver {} timed out for join request {}", session.driverId(), session.requestId());
        handleJoinRequestFailure(session, request, "Driver response timeout");
    }

    private void handleJoinRequestFailure(JoinRequestSession session, SharedRideRequest request, String reason) {
        // Clean up session and gateway offer if they exist
        if (session != null) {
            sessions.remove(session.requestId());
            decisionGateway.cancelOffer(session.requestId());
        } else {
            // If session is null (e.g., explicit rejection), still cancel any lingering gateway offer
            decisionGateway.cancelOffer(request.getSharedRideRequestId());
        }

        SharedRideRequest latest = requestRepository.findById(request.getSharedRideRequestId())
            .orElse(request);

        if (latest.getStatus() != SharedRideRequestStatus.PENDING) {
            log.info("Join request {} failure ignored - status {}", latest.getSharedRideRequestId(), latest.getStatus());
            return;
        }

        // Use CANCELLED for explicit rejections, EXPIRED for timeouts.
        latest.setStatus("Driver response timeout".equals(reason) ? SharedRideRequestStatus.EXPIRED : SharedRideRequestStatus.CANCELLED);
        requestRepository.save(latest);

        // Release wallet hold
        releaseRequestHold(latest, reason);

        // Notify rider of failure
        try {
            notificationService.notifyRiderStatus(
                latest.getRider().getUser(),
                responseAssembler.toRiderJoinRequestFailed(latest, reason));
        } catch (Exception e) {
            log.error("Failed to send join request failure notification", e);
        }

        log.info("Join request {} failed - {}", latest.getSharedRideRequestId(), reason);
    }

    private record JoinRequestSession(int requestId, int rideId, int driverId, Location pickupLocation,
                                      Location dropoffLocation, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }


    public boolean beginDriverAcceptance(Integer requestId, Integer rideId, Integer driverId) {
        MatchingSession session = sessions.get(requestId);
        if (session == null) {
            return false;
        }

        boolean locked;
        synchronized (session) {
            locked = session.markAwaitingConfirmation(rideId, driverId);
        }

        if (!locked) {
            return false;
        }

        boolean allowed = decisionGateway.beginAcceptance(requestId, rideId, driverId);
        if (!allowed) {
            synchronized (session) {
                session.resumeMatching();
            }
        }

        return allowed;
    }

    public boolean beginJoinAcceptance(Integer requestId, Integer rideId, Integer driverId) {
        MatchingSession session = sessions.get(requestId);
        if (session == null || !session.isJoinRequest()) {
            return false;
        }

        JoinRequestSession joinSession;
        boolean locked;
        synchronized (session) {
            joinSession = session.joinSession();
            if (joinSession == null || joinSession.rideId() != rideId || joinSession.driverId() != driverId) {
                return false;
            }

            if (joinSession.isExpired()) {
                session.markExpired();
                sessions.remove(requestId, session);
                return false;
            }

            locked = session.markJoinAwaitingConfirmation(driverId);
        }

        if (!locked) {
            return false;
        }

        boolean allowed = decisionGateway.beginAcceptance(requestId, rideId, driverId);
        if (!allowed) {
            synchronized (session) {
                session.resumeMatching();
            }
        }

        return allowed;
    }

    public void completeDriverAcceptance(Integer requestId) {
        MatchingSession session = sessions.remove(requestId);
        decisionGateway.completeAcceptance(requestId);
        if (session == null) {
            return;
        }

        session.markCompleted();
        requestRepository.findById(requestId).ifPresent(req -> {
            if (req.getStatus() != SharedRideRequestStatus.CONFIRMED) {
                return;
            }

            if (session.isJoinRequest()) {
                notificationService.notifyRiderStatus(
                    req.getRider().getUser(),
                    responseAssembler.toRiderJoinRequestSuccess(req));
                return;
            }

            if (session.currentCandidate() != null) {
                notificationService.notifyRiderStatus(
                    req.getRider().getUser(),
                    responseAssembler.toRiderMatchSuccess(req, session.currentCandidate()));
            }
        });
    }

    public void failDriverAcceptance(Integer requestId, String reason) {
        MatchingSession session = sessions.get(requestId);
        decisionGateway.failAcceptance(requestId);
        if (session == null) {
            return;
        }

        synchronized (session) {
            session.resumeMatching();
        }

        log.warn("Driver acceptance failed for request {} - {}", requestId, reason);
        scheduleNext(session);
    }

    public boolean beginBroadcastAcceptance(Integer requestId, Integer driverId) {
        MatchingSession session = sessions.get(requestId);
        if (session == null) {
            return false;
        }

        synchronized (session) {
            return session.markAwaitingBroadcastConfirmation(driverId);
        }
    }

    public void completeBroadcastAcceptance(Integer requestId, RideMatchProposalResponse proposal) {
        MatchingSession session = sessions.remove(requestId);
        if (session != null) {
            session.markBroadcastCompleted();
        }

        requestRepository.findById(requestId).ifPresent(req -> {
            if (req.getStatus() == SharedRideRequestStatus.CONFIRMED && proposal != null) {
                notificationService.notifyRiderStatus(
                    req.getRider().getUser(),
                    responseAssembler.toRiderMatchSuccess(req, proposal));
            }
        });
    }

    public void failBroadcastAcceptance(Integer requestId, String reason) {
        MatchingSession session = sessions.get(requestId);
        if (session == null) {
            return;
        }

        synchronized (session) {
            session.markBroadcastFailed();
        }
        log.warn("Broadcast acceptance failed for request {} - {}", requestId, reason);
    }

    public void cancelMatching(Integer requestId) {
        MatchingSession session = sessions.remove(requestId);
        decisionGateway.cancelOffer(requestId);
        if (session != null) {
            session.markCancelled();
            session.cancelBroadcast();
        }
    }

    private void scheduleNext(MatchingSession session) {
        matchingExecutor.execute(() -> offerNextCandidate(session));
    }

    private void offerNextCandidate(MatchingSession session) {
        if (session.isTerminal()) {
            return;
        }

        Optional<SharedRideRequest> activeRequest = fetchActiveRequest(session.requestId());
        if (activeRequest.isEmpty()) {
            cancelSession(session);
            return;
        }

        SharedRideRequest request = activeRequest.get();

        if (session.isExpired()) {
            handleNoCandidates(session, request);
            return;
        }

        Optional<RideMatchProposalResponse> nextCandidate;
        synchronized (session) {
            if (!session.isMatchingPhase()) {
                return;
            }
            nextCandidate = session.advance();
        }

        if (nextCandidate.isEmpty()) {
            handleNoCandidates(session, request);
            return;
        }

        RideMatchProposalResponse proposal = nextCandidate.get();
        Optional<DriverProfile> maybeDriver = driverRepository.findById(proposal.getDriverId());
        if (maybeDriver.isEmpty() || maybeDriver.get().getUser() == null) {
            log.warn("Skipping candidate for request {} - driver {} not found",
                request.getSharedRideRequestId(), proposal.getDriverId());
            synchronized (session) {
                session.resumeMatching();
            }
            scheduleNext(session);
            return;
        }

        DriverProfile driver = maybeDriver.get();

        Duration responseWindow = Duration.ofSeconds(
            rideConfig.getMatching().getDriverResponseSeconds());
        Instant offerDeadline = Instant.now().plus(responseWindow);

        DriverRideOfferNotification driverPayload = responseAssembler.toDriverOffer(
            request,
            driver,
            session.pickupLocation(),
            session.dropoffLocation(),
            proposal,
            session.currentRank(),
            offerDeadline,
            (int) responseWindow.toSeconds());

        notificationService.notifyDriverOffer(driver, driverPayload);
        session.recordNotifiedDriver(driver.getDriverId());

        decisionGateway.registerOffer(
            request.getSharedRideRequestId(),
            proposal.getSharedRideId(),
            driver.getDriverId(),
            responseWindow,
            () -> handleOfferTimeout(session, proposal));

        log.info("Sent ride offer #{}/{} to driver {} for request {}",
            session.currentRank(),
            session.totalCandidates(),
            driver.getDriverId(),
            request.getSharedRideRequestId());
    }

    private void handleOfferTimeout(MatchingSession session, RideMatchProposalResponse proposal) {
        log.info("Driver {} timed out for request {}", proposal.getDriverId(), session.requestId());
        synchronized (session) {
            session.resumeMatching();
        }
        scheduleNext(session);
    }

    private void handleNoCandidates(MatchingSession session, SharedRideRequest request) {
        if (session.isBroadcasting()) {
            log.debug("Broadcast already active for request {}", request.getSharedRideRequestId());
            return;
        }
        boolean broadcastStarted = tryStartBroadcast(session, request);
        if (broadcastStarted) {
            return;
        }
        if (session.markExpired()) {
            handleMatchingFailure(session, request, "No available drivers found within the time limit.");
        }
    }

    private void handleMatchingFailure(MatchingSession session, SharedRideRequest request, String reason) {
        // Clean up any active session state
        if (session != null) {
            decisionGateway.cancelOffer(request.getSharedRideRequestId());
            sessions.remove(request.getSharedRideRequestId());
            session.cancelBroadcast();
        }

        // Only update status and notify if the request is still in a pending state
        if (request.getStatus() == SharedRideRequestStatus.PENDING ||
            request.getStatus() == SharedRideRequestStatus.BROADCASTING) {
            request.setStatus(SharedRideRequestStatus.EXPIRED);
            requestRepository.save(request);

            // Release the wallet hold placed when the request was created
            releaseRequestHold(request, "Request expired: " + reason);

            // Notify the rider that no match was found
            try {
                notificationService.notifyRiderStatus(
                    request.getRider().getUser(),
                    responseAssembler.toRiderNoMatch(request));
                log.info("No-match notification sent successfully for request {}", request.getSharedRideRequestId());
            } catch (Exception e) {
                log.error("Failed to send no-match notification for request {}", request.getSharedRideRequestId(), e);
            }
        }

        log.info("Request {} failed to match and has expired. Reason: {}", request.getSharedRideRequestId(), reason);
    }

    private void releaseRequestHold(SharedRideRequest request, String reason) {
        try {
            RideHoldReleaseRequest releaseRequest = RideHoldReleaseRequest.builder()
                .riderId(request.getRider().getRiderId())
                .rideRequestId(request.getSharedRideRequestId())
                .note(reason)
                .build();

            rideFundCoordinatingService.releaseRideFunds(releaseRequest);
            log.info("Wallet hold released for request {} - amount: {}", request.getSharedRideRequestId(), request.getTotalFare());
        } catch (Exception e) {
            log.error("Failed to release wallet hold for request {}: {}", request.getSharedRideRequestId(), e.getMessage(), e);
        }
    }

    private boolean tryStartBroadcast(MatchingSession session, SharedRideRequest request) {
        Integer windowSeconds = rideConfig.getBroadcast().getResponseWindowSeconds();
        if (windowSeconds == null || windowSeconds <= 0) {
            return false;
        }
        if (session.isBroadcasting() || session.isTerminal()) {
            return false;
        }
        if (request.getStatus() != SharedRideRequestStatus.PENDING) {
            return false;
        }

        Duration remaining = session.remainingMatchingTime();
        if (remaining.isNegative() || remaining.isZero()) {
            return false;
        }

        Set<Integer> excludedDrivers = session.notifiedDriversSnapshot();
        List<DriverProfile> candidates = findBroadcastCandidates(excludedDrivers);
        if (candidates.isEmpty()) {
            log.info("Broadcast fallback skipped - no eligible drivers for request {}", request.getSharedRideRequestId());
            return false;
        }

        Instant deadline = Instant.now().plus(remaining);
        boolean entered;
        synchronized (session) {
            entered = session.enterBroadcast(deadline);
        }
        if (!entered) {
            return session.isBroadcasting();
        }

        request.setStatus(SharedRideRequestStatus.BROADCASTING);
        requestRepository.save(request);

        sendBroadcastOffers(session, request, candidates, deadline, (int) remaining.toSeconds());
        return true;
    }

    private List<DriverProfile> findBroadcastCandidates(Set<Integer> excludedDrivers) {
        List<Integer> excluded = excludedDrivers == null || excludedDrivers.isEmpty()
            ? null
            : new ArrayList<>(excludedDrivers);
        return driverRepository.findBroadcastEligibleDrivers(excluded);
    }

    private void sendBroadcastOffers(MatchingSession session,
                                     SharedRideRequest request,
                                     List<DriverProfile> candidates,
                                     Instant deadline,
                                     int responseWindowSeconds) {
        log.info("Broadcasting ride request {} to {} drivers", request.getSharedRideRequestId(), candidates.size());
        for (DriverProfile driver : candidates) {
            DriverRideOfferNotification payload = responseAssembler.toDriverBroadcastOffer(
                request,
                driver,
                session.pickupLocation(),
                session.dropoffLocation(),
                deadline,
                responseWindowSeconds);
            notificationService.notifyDriverOffer(driver, payload);
            session.recordNotifiedDriver(driver.getDriverId());
        }

        ScheduledFuture<?> timeoutFuture = matchingScheduler.schedule(
            () -> handleBroadcastTimeout(request.getSharedRideRequestId()),
            responseWindowSeconds,
            TimeUnit.SECONDS);
        session.attachBroadcastTimeout(timeoutFuture);
    }

    private void handleBroadcastTimeout(int requestId) {
        matchingExecutor.execute(() -> {
            MatchingSession session = sessions.get(requestId);
            if (session == null) {
                return;
            }

            Optional<SharedRideRequest> maybeRequest = requestRepository.findById(requestId);
            if (maybeRequest.isEmpty()) {
                cancelSession(session);
                return;
            }
            SharedRideRequest request = maybeRequest.get();

            boolean expired;
            synchronized (session) {
                expired = session.markExpiredFromBroadcast();
            }

            if (!expired) {
                return;
            }

            log.info("Broadcast timeout for request {}", requestId);
            handleMatchingFailure(session, request, "Broadcast window timed out.");
        });
    }

    private Optional<SharedRideRequest> fetchActiveRequest(int requestId) {
        return requestRepository.findById(requestId)
            .filter(req -> req.getStatus() == SharedRideRequestStatus.PENDING
                || req.getStatus() == SharedRideRequestStatus.BROADCASTING);
    }

    private void cancelSession(MatchingSession session) {
        session.cancelBroadcast();
        session.markCancelled();
        sessions.remove(session.requestId());
        decisionGateway.cancelOffer(session.requestId());
    }

    private static final class MatchingSession {
        private final int requestId;
        private final CandidateSelector selector;
        private final Location pickupLocation;
        private final Location dropoffLocation;
        private final Instant expiresAt;
        private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.MATCHING);
        private final AtomicInteger rankCounter = new AtomicInteger(0);
        private final Set<Integer> notifiedDrivers = ConcurrentHashMap.newKeySet();
        private RideMatchProposalResponse currentCandidate;
        private int currentRank;
        private final JoinRequestSession joinSession;
        private BroadcastContext broadcastContext;

        private MatchingSession(int requestId,
                                CandidateSelector selector,
                                Location pickupLocation,
                                Location dropoffLocation,
                                Instant expiresAt) {
            this.requestId = requestId;
            this.selector = selector;
            this.pickupLocation = pickupLocation;
            this.dropoffLocation = dropoffLocation;
            this.expiresAt = expiresAt;
            this.joinSession = null;
        }

        private MatchingSession(JoinRequestSession joinSession) {
            this.requestId = joinSession.requestId();
            this.selector = null;
            this.pickupLocation = joinSession.pickupLocation();
            this.dropoffLocation = joinSession.dropoffLocation();
            this.expiresAt = joinSession.expiresAt();
            this.joinSession = joinSession;
        }

        static MatchingSession forJoinRequest(JoinRequestSession joinSession) {
            return new MatchingSession(joinSession);
        }

        boolean isJoinRequest() {
            return joinSession != null;
        }

        JoinRequestSession joinSession() {
            return joinSession;
        }

        synchronized boolean markJoinAwaitingConfirmation(int driverId) {
            if (!isJoinRequest()) {
                return false;
            }
            if (phase.get() != Phase.MATCHING) {
                return false;
            }
            if (joinSession == null || joinSession.driverId() != driverId) {
                return false;
            }
            phase.set(Phase.AWAITING_CONFIRMATION);
            return true;
        }

        synchronized Optional<RideMatchProposalResponse> advance() {
            Optional<RideMatchProposalResponse> next = selector.next();
            next.ifPresent(candidate -> {
                currentCandidate = candidate;
                currentRank = rankCounter.incrementAndGet();
            });
            return next;
        }

        synchronized boolean markAwaitingConfirmation(int rideId, int driverId) {
            if (phase.get() != Phase.MATCHING || currentCandidate == null) {
                return false;
            }
            if (!currentCandidate.getSharedRideId().equals(rideId) ||
                !currentCandidate.getDriverId().equals(driverId)) {
                return false;
            }
            phase.set(Phase.AWAITING_CONFIRMATION);
            return true;
        }

        synchronized boolean markAwaitingBroadcastConfirmation(int driverId) {
            if (phase.get() != Phase.BROADCASTING || broadcastContext == null) {
                return false;
            }
            boolean claimed = broadcastContext.acceptedDriverId.compareAndSet(null, driverId);
            if (!claimed) {
                return false;
            }
            phase.set(Phase.AWAITING_CONFIRMATION);
            return true;
        }

        synchronized void releaseBroadcastLock() {
            if (broadcastContext != null) {
                broadcastContext.acceptedDriverId.set(null);
            }
            if (phase.get() == Phase.AWAITING_CONFIRMATION) {
                phase.set(Phase.BROADCASTING);
            }
        }

        synchronized void resumeMatching() {
            if (phase.get() != Phase.CANCELLED && phase.get() != Phase.EXPIRED && phase.get() != Phase.COMPLETED) {
                phase.set(Phase.MATCHING);
            }
        }

        synchronized void markCompleted() {
            phase.set(Phase.COMPLETED);
        }

        synchronized void markCancelled() {
            phase.set(Phase.CANCELLED);
        }

        boolean isTerminal() {
            Phase current = phase.get();
            return current == Phase.CANCELLED || current == Phase.EXPIRED || current == Phase.COMPLETED;
        }

        boolean isBroadcasting() {
            return phase.get() == Phase.BROADCASTING;
        }

        boolean isMatchingPhase() {
            return phase.get() == Phase.MATCHING;
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        Duration remainingMatchingTime() {
            return Duration.between(Instant.now(), expiresAt);
        }

        int requestId() {
            return requestId;
        }

        Location pickupLocation() {
            return pickupLocation;
        }

        Location dropoffLocation() {
            return dropoffLocation;
        }

        RideMatchProposalResponse currentCandidate() {
            return currentCandidate;
        }

        int currentRank() {
            return currentRank;
        }

        int totalCandidates() {
            return selector.size();
        }

        synchronized boolean enterBroadcast(Instant deadline) {
            if (broadcastContext != null) {
                return false;
            }
            broadcastContext = new BroadcastContext(deadline);
            phase.set(Phase.BROADCASTING);
            return true;
        }

        synchronized void attachBroadcastTimeout(ScheduledFuture<?> timeoutFuture) {
            if (broadcastContext != null) {
                broadcastContext.setTimeoutFuture(timeoutFuture);
            }
        }

        synchronized void cancelBroadcast() {
            if (broadcastContext != null) {
                broadcastContext.cancelTimeout();
                broadcastContext = null;
            }
            if (phase.get() == Phase.BROADCASTING) {
                phase.set(Phase.MATCHING);
            }
        }

        synchronized Instant broadcastDeadline() {
            return broadcastContext != null ? broadcastContext.deadline() : null;
        }

        synchronized void markBroadcastCompleted() {
            if (broadcastContext != null) {
                broadcastContext.cancelTimeout();
            }
            phase.set(Phase.COMPLETED);
        }

        synchronized void clearBroadcast() {
            if (broadcastContext != null) {
                broadcastContext.cancelTimeout();
            }
            broadcastContext = null;
            if (phase.get() == Phase.BROADCASTING) {
                phase.set(Phase.MATCHING);
            }
        }

        synchronized boolean markExpired() {
            boolean transitioned = false;
            if (phase.compareAndSet(Phase.MATCHING, Phase.EXPIRED)) {
                transitioned = true;
            } else if (phase.compareAndSet(Phase.AWAITING_CONFIRMATION, Phase.EXPIRED)) {
                transitioned = true;
            } else if (phase.compareAndSet(Phase.BROADCASTING, Phase.EXPIRED)) {
                transitioned = true;
            }
            if (transitioned && broadcastContext != null) {
                broadcastContext.cancelTimeout();
            }
            return transitioned;
        }

        synchronized boolean markExpiredFromBroadcast() {
            if (phase.compareAndSet(Phase.BROADCASTING, Phase.EXPIRED) ||
                phase.compareAndSet(Phase.AWAITING_CONFIRMATION, Phase.EXPIRED)) {
                if (broadcastContext != null) {
                    broadcastContext.cancelTimeout();
                }
                return true;
            }
            return false;
        }

        synchronized void markBroadcastFailed() {
            if (broadcastContext != null) {
                broadcastContext.acceptedDriverId.set(null);
            }
            if (phase.get() == Phase.AWAITING_CONFIRMATION) {
                phase.set(Phase.BROADCASTING);
            }
        }

        void recordNotifiedDriver(int driverId) {
            notifiedDrivers.add(driverId);
        }

        Set<Integer> notifiedDriversSnapshot() {
            return Set.copyOf(notifiedDrivers);
        }

        private static final class BroadcastContext {
            private final Instant deadline;
            private final AtomicReference<Integer> acceptedDriverId = new AtomicReference<>(null);
            private ScheduledFuture<?> timeoutFuture;

            BroadcastContext(Instant deadline) {
                this.deadline = deadline;
            }

            Instant deadline() {
                return deadline;
            }

            void setTimeoutFuture(ScheduledFuture<?> timeoutFuture) {
                this.timeoutFuture = timeoutFuture;
            }

            void cancelTimeout() {
                if (timeoutFuture != null) {
                    timeoutFuture.cancel(false);
                }
            }

            AtomicReference<Integer> acceptedDriverId() {
                return acceptedDriverId;
            }
        }

        private enum Phase {
            MATCHING,
            AWAITING_CONFIRMATION,
            COMPLETED,
            EXPIRED,
            CANCELLED,
            BROADCASTING
        }
    }
}

package com.mssus.app.service.matching;

import com.mssus.app.common.enums.RequestKind;
import com.mssus.app.common.enums.SharedRideRequestStatus;
import com.mssus.app.config.properties.RideConfigurationProperties;
import com.mssus.app.dto.notification.DriverRideOfferNotification;
import com.mssus.app.dto.request.wallet.WalletReleaseRequest;
import com.mssus.app.dto.response.ride.RideMatchProposalResponse;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.Location;
import com.mssus.app.entity.SharedRideRequest;
import com.mssus.app.repository.DriverProfileRepository;
import com.mssus.app.repository.LocationRepository;
import com.mssus.app.repository.SharedRideRequestRepository;
import com.mssus.app.service.BookingWalletService;
import com.mssus.app.service.RealTimeNotificationService;
import com.mssus.app.service.RideMatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
    private final BookingWalletService bookingWalletService;

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
        BookingWalletService bookingWalletService) {

        this.requestRepository = requestRepository;
        this.driverRepository = driverRepository;
        this.locationRepository = locationRepository;
        this.rideMatchingService = rideMatchingService;
        this.rideConfig = rideConfig;
        this.notificationService = notificationService;
        this.decisionGateway = decisionGateway;
        this.responseAssembler = responseAssembler;
        this.matchingExecutor = matchingExecutor;
        this.bookingWalletService = bookingWalletService;
    }

    private final ConcurrentHashMap<Integer, MatchingSession> sessions = new ConcurrentHashMap<>();

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

        Location pickup = request.getPickupLocationId() != null
            ? locationRepository.findById(request.getPickupLocationId()).orElse(null)
            : null;
        Location dropoff = request.getDropoffLocationId() != null
            ? locationRepository.findById(request.getDropoffLocationId()).orElse(null)
            : null;

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
        Location pickup = request.getPickupLocationId() != null
            ? locationRepository.findById(request.getPickupLocationId()).orElse(null)
            : null;
        Location dropoff = request.getDropoffLocationId() != null
            ? locationRepository.findById(request.getDropoffLocationId()).orElse(null)
            : null;

        JoinRequestSession session = new JoinRequestSession(
            request.getSharedRideRequestId(),
            request.getSharedRide().getSharedRideId(),
            request.getSharedRide().getDriver().getDriverId(),
            pickup,
            dropoff,
            Instant.now().plus(rideConfig.getRequestAcceptTimeout()));

        sessions.put(requestId, new MatchingSession(session));

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

    private void handleJoinRequestTimeout(JoinRequestSession session, SharedRideRequest request) {
        log.info("Driver {} timed out for join request {}", session.driverId(), session.requestId());
        handleJoinRequestFailure(session, request, "Driver response timeout");
    }

    private void handleJoinRequestFailure(JoinRequestSession session, SharedRideRequest request, String reason) {
        sessions.remove(session.requestId());
        decisionGateway.cancelOffer(session.requestId());

        if (request.getStatus() == SharedRideRequestStatus.PENDING) {
            request.setStatus(SharedRideRequestStatus.EXPIRED);
            requestRepository.save(request);

            // Release wallet hold
            try {
                WalletReleaseRequest releaseRequest = new WalletReleaseRequest();
                releaseRequest.setUserId(request.getRider().getRiderId());
                releaseRequest.setBookingId(request.getSharedRideRequestId());
                releaseRequest.setAmount(request.getFareAmount());
                releaseRequest.setNote("Join request failed - #" + request.getSharedRideRequestId());

                bookingWalletService.releaseFunds(releaseRequest);
                log.info("Wallet hold released for failed join request {} - amount: {}",
                    request.getSharedRideRequestId(), request.getFareAmount());
            } catch (Exception e) {
                log.error("Failed to release wallet hold for join request {}: {}",
                    request.getSharedRideRequestId(), e.getMessage(), e);
            }

            // Notify rider of failure
            try {
                notificationService.notifyRiderStatus(
                    request.getRider().getUser(),
                    responseAssembler.toRiderJoinRequestFailed(request, reason));
            } catch (Exception e) {
                log.error("Failed to send join request failure notification", e);
            }
        }

        log.info("Join request {} failed - {}", request.getSharedRideRequestId(), reason);
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

    public void completeDriverAcceptance(Integer requestId) {
        MatchingSession session = sessions.remove(requestId);
        decisionGateway.completeAcceptance(requestId);
        if (session == null) {
            return;
        }

        session.markCompleted();
        requestRepository.findById(requestId).ifPresent(req -> {
            if (req.getStatus() == SharedRideRequestStatus.CONFIRMED &&
                session.currentCandidate() != null) {
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

    public void cancelMatching(Integer requestId) {
        MatchingSession session = sessions.remove(requestId);
        decisionGateway.cancelOffer(requestId);
        if (session != null) {
            session.markCancelled();
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
            offerDeadline);

        notificationService.notifyDriverOffer(driver, driverPayload);

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
        if (session.markExpired()) {
            decisionGateway.cancelOffer(request.getSharedRideRequestId());
            sessions.remove(request.getSharedRideRequestId());

            if (request.getStatus() == SharedRideRequestStatus.PENDING) {
                request.setStatus(SharedRideRequestStatus.EXPIRED);
                requestRepository.save(request);

                // Add debug logging
                log.info("Sending no-match notification to rider {} for request {}",
                    request.getRider().getUser().getUserId(), request.getSharedRideRequestId());

                int requestId = request.getSharedRideRequestId();

                try {
                    WalletReleaseRequest releaseRequest = new WalletReleaseRequest();
                    releaseRequest.setUserId(request.getRider().getRiderId());
                    releaseRequest.setBookingId(requestId);
                    releaseRequest.setAmount(request.getFareAmount());
                    releaseRequest.setNote("Request matching timeout - #" + requestId);

                    bookingWalletService.releaseFunds(releaseRequest);

                    log.info("Wallet hold released for rejected request {} - amount: {}",
                        requestId, request.getFareAmount());

                } catch (Exception e) {
                    log.error("Failed to release wallet hold for request {}: {}",
                        requestId, e.getMessage(), e);
                    // Continue with rejection even if release fails
                }

                try {
                    notificationService.notifyRiderStatus(
                        request.getRider().getUser(),
                        responseAssembler.toRiderNoMatch(request));
                    log.info("No-match notification sent successfully");
                } catch (Exception e) {
                    log.error("Failed to send no-match notification", e);
                }
            }

            log.info("Request {} expired - no available drivers", request.getSharedRideRequestId());
        }
    }

    private Optional<SharedRideRequest> fetchActiveRequest(int requestId) {
        return requestRepository.findById(requestId)
            .filter(req -> req.getStatus() == SharedRideRequestStatus.PENDING);
    }

    private void cancelSession(MatchingSession session) {
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
        private RideMatchProposalResponse currentCandidate;
        private int currentRank;
        private final JoinRequestSession joinSession;

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

        synchronized void resumeMatching() {
            if (phase.get() != Phase.CANCELLED && phase.get() != Phase.EXPIRED && phase.get() != Phase.COMPLETED) {
                phase.set(Phase.MATCHING);
            }
        }

        synchronized void markCompleted() {
            phase.set(Phase.COMPLETED);
        }

        synchronized boolean markExpired() {
            if (phase.compareAndSet(Phase.MATCHING, Phase.EXPIRED)
                || phase.compareAndSet(Phase.AWAITING_CONFIRMATION, Phase.EXPIRED)) {
                return true;
            }
            return false;
        }

        synchronized void markCancelled() {
            phase.set(Phase.CANCELLED);
        }

        boolean isTerminal() {
            Phase current = phase.get();
            return current == Phase.CANCELLED || current == Phase.EXPIRED || current == Phase.COMPLETED;
        }

        boolean isMatchingPhase() {
            return phase.get() == Phase.MATCHING;
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
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

        private enum Phase {
            MATCHING,
            AWAITING_CONFIRMATION,
            COMPLETED,
            EXPIRED,
            CANCELLED
        }
    }
}

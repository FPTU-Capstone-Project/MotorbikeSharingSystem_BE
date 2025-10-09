package com.mssus.app.service.matching;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks outstanding ride offers awaiting driver decisions.
 *
 * <p>The gateway is responsible for enforcing per-offer response windows and
 * signalling the coordinator when a driver did not respond in time. Business
 * logic (e.g. what to do on timeout) stays in {@link RideMatchingCoordinator}.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DriverDecisionGateway {

    private final ScheduledExecutorService matchingScheduler;

    private final Map<Integer, DriverOffer> activeOffers = new ConcurrentHashMap<>();

    public void registerOffer(int requestId,
                              int rideId,
                              int driverId,
                              Duration responseWindow,
                              Runnable onTimeout) {
        Objects.requireNonNull(onTimeout, "onTimeout callback is required");

        cancelOffer(requestId);

        DriverOffer offer = new DriverOffer(requestId, rideId, driverId, onTimeout);
        ScheduledFuture<?> timeoutFuture = matchingScheduler.schedule(() -> handleTimeout(offer),
                responseWindow.toMillis(), TimeUnit.MILLISECONDS);
        offer.setTimeoutFuture(timeoutFuture);

        activeOffers.put(requestId, offer);
        log.debug("Registered driver offer - requestId={}, driverId={}, rideId={}, window={}s",
                requestId, driverId, rideId, responseWindow.toSeconds());
    }

    public boolean hasActiveOffer(int requestId, int rideId, int driverId) {
        DriverOffer offer = activeOffers.get(requestId);
        return offer != null
                && offer.matches(rideId, driverId)
                && offer.state() == OfferState.PENDING;
    }

    /**
     * Marks that the driver has responded and cancels the timeout clock.
     *
     * @return true if acceptance can proceed, false if the offer is no longer valid
     */
    public boolean beginAcceptance(int requestId, int rideId, int driverId) {
        DriverOffer offer = activeOffers.get(requestId);
        if (offer == null || !offer.matches(rideId, driverId)) {
            return false;
        }

        boolean transitioned = offer.compareAndSetState(OfferState.PENDING, OfferState.ACCEPTING);
        if (transitioned) {
            offer.cancelTimeout();
            log.debug("Driver began accepting offer - requestId={}, driverId={}, rideId={}",
                    requestId, driverId, rideId);
        }
        return transitioned;
    }

    public void completeAcceptance(int requestId) {
        Optional.ofNullable(activeOffers.remove(requestId))
                .ifPresent(DriverOffer::cancelTimeout);
    }

    public void failAcceptance(int requestId) {
        Optional.ofNullable(activeOffers.remove(requestId))
                .ifPresent(offer -> {
                    offer.cancelTimeout();
                    offer.setState(OfferState.FAILED);
                });
    }

    public void cancelOffer(int requestId) {
        Optional.ofNullable(activeOffers.remove(requestId))
                .ifPresent(offer -> {
                    offer.cancelTimeout();
                    offer.setState(OfferState.CANCELLED);
                });
    }

    private void handleTimeout(DriverOffer offer) {
        if (!offer.compareAndSetState(OfferState.PENDING, OfferState.TIMED_OUT)) {
            return;
        }
        activeOffers.remove(offer.requestId(), offer);
        log.debug("Driver offer timed out - requestId={}, driverId={}, rideId={}",
                offer.requestId(), offer.driverId(), offer.rideId());
        offer.onTimeout().run();
    }

    private static final class DriverOffer {
        private final int requestId;
        private final int rideId;
        private final int driverId;
        private final Runnable onTimeout;
        private final AtomicReference<OfferState> state = new AtomicReference<>(OfferState.PENDING);
        private volatile ScheduledFuture<?> timeoutFuture;

        private DriverOffer(int requestId, int rideId, int driverId, Runnable onTimeout) {
            this.requestId = requestId;
            this.rideId = rideId;
            this.driverId = driverId;
            this.onTimeout = onTimeout;
        }

        void setTimeoutFuture(ScheduledFuture<?> timeoutFuture) {
            this.timeoutFuture = timeoutFuture;
        }

        Runnable onTimeout() {
            return onTimeout;
        }

        int requestId() {
            return requestId;
        }

        int rideId() {
            return rideId;
        }

        int driverId() {
            return driverId;
        }

        OfferState state() {
            return state.get();
        }

        void setState(OfferState newState) {
            state.set(newState);
        }

        boolean compareAndSetState(OfferState expected, OfferState updated) {
            return state.compareAndSet(expected, updated);
        }

        void cancelTimeout() {
            ScheduledFuture<?> future = this.timeoutFuture;
            if (future != null) {
                future.cancel(false);
            }
        }

        boolean matches(int rideId, int driverId) {
            return this.rideId == rideId && this.driverId == driverId;
        }
    }

    private enum OfferState {
        PENDING,
        ACCEPTING,
        TIMED_OUT,
        FAILED,
        CANCELLED
    }
}

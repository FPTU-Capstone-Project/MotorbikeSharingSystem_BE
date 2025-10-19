package com.mssus.app.worker;

import com.mssus.app.common.enums.SharedRideRequestStatus;
import com.mssus.app.common.enums.SharedRideStatus;
import com.mssus.app.config.properties.RideConfigurationProperties;
import com.mssus.app.common.enums.DeliveryMethod;
import com.mssus.app.common.enums.NotificationType;
import com.mssus.app.common.enums.Priority;
import com.mssus.app.dto.notification.DriverRideOfferNotification;
import com.mssus.app.dto.notification.RiderMatchStatusNotification;
import com.mssus.app.dto.request.ride.CompleteRideReqRequest;
import com.mssus.app.dto.request.ride.CompleteRideRequest;
import com.mssus.app.entity.SharedRide;
import com.mssus.app.entity.SharedRideRequest;
import com.mssus.app.entity.User;
import com.mssus.app.repository.SharedRideRepository;
import com.mssus.app.repository.SharedRideRequestRepository;
import com.mssus.app.service.NotificationService;
import com.mssus.app.service.RealTimeNotificationService;
import com.mssus.app.service.SharedRideService;
import com.mssus.app.service.RideTrackingService;
import com.mssus.app.service.matching.MatchingResponseAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class RideLifecycleWorker {

    private static final String SYSTEM_TRIGGER = "ROLE_SYSTEM_AUTOMATION";

    private final RideConfigurationProperties rideConfig;
    private final SharedRideRepository rideRepository;
    private final SharedRideRequestRepository requestRepository;
    private final SharedRideService sharedRideService;
    private final RideTrackingService rideTrackingService;
    private final RealTimeNotificationService realTimeNotificationService;
    private final NotificationService notificationService;
    private final MatchingResponseAssembler responseAssembler;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${app.ride.auto-lifecycle.scan-interval-ms:60000}")
    public void enforceLifecycle() {
        var autoLifecycle = rideConfig.getAutoLifecycle();
        if (autoLifecycle == null || !autoLifecycle.isEnabled()) {
            return;
        }

        if (!running.compareAndSet(false, true)) {
            log.debug("Skipping lifecycle enforcement run because previous execution is still in progress");
            return;
        }

        try {
            LocalDateTime now = LocalDateTime.now();
            autoStartRides(now);
            autoStartRideRequests(now);
            autoCompleteRideRequests(now);
            autoCompleteRides(now);
        } catch (Exception ex) {
            log.error("Ride lifecycle worker encountered an unexpected error", ex);
        } finally {
            running.set(false);
        }
    }

    private void autoStartRides(LocalDateTime now) {
        Duration leeway = rideConfig.getAutoLifecycle().getRideAutoStartLeeway();
        if (leeway == null || leeway.isNegative() || leeway.isZero()) {
            return;
        }

        LocalDateTime cutoff = now.minus(leeway);
        List<SharedRide> candidates = rideRepository.findScheduledForAutoStart(cutoff);
        if (candidates.isEmpty()) {
            return;
        }

        for (SharedRide candidate : candidates) {
            rideRepository.findByIdForUpdate(candidate.getSharedRideId()).ifPresent(ride -> {
                if (ride.getStatus() != SharedRideStatus.SCHEDULED) {
                    return;
                }

                List<SharedRideRequest> confirmed = requestRepository.findBySharedRideSharedRideIdAndStatus(
                    ride.getSharedRideId(), SharedRideRequestStatus.CONFIRMED);
                if (confirmed.isEmpty()) {
                    log.debug("Auto-start skipped for ride {} because there are no confirmed passengers",
                        ride.getSharedRideId());
                    return;
                }

                ride.setStatus(SharedRideStatus.ONGOING);
                if (ride.getStartedAt() == null) {
                    ride.setStartedAt(now);
                }
                rideRepository.save(ride);

                try {
                    rideTrackingService.startTracking(ride.getSharedRideId());
                } catch (Exception trackingEx) {
                    log.warn("Failed to start tracking for auto-started ride {}: {}",
                        ride.getSharedRideId(), trackingEx.getMessage());
                }

                notifyDriverRideTransition(ride, "ride.auto.started",
                    "Ride auto-started because pickup window elapsed.");
                log.info("Auto-started ride {} (scheduled at {}, {} confirmed passengers)",
                    ride.getSharedRideId(), ride.getScheduledTime(), confirmed.size());
            });
        }
    }

    private void autoStartRideRequests(LocalDateTime now) {
        Duration timeout = rideConfig.getAutoLifecycle().getRequestPickupTimeout();
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            return;
        }

        LocalDateTime cutoff = now.minus(timeout);
        List<SharedRideRequest> requests = requestRepository.findConfirmedForAutoStart(cutoff);
        for (SharedRideRequest request : requests) {
            SharedRide ride = request.getSharedRide();
            if (ride == null || ride.getStatus() != SharedRideStatus.ONGOING) {
                continue;
            }

            request.setStatus(SharedRideRequestStatus.ONGOING);
            if (request.getActualPickupTime() == null) {
                request.setActualPickupTime(now);
            }
            requestRepository.save(request);

            notifyRideRequestTransition(request, "REQUEST_AUTO_STARTED",
                "Ride request auto-marked as ongoing due to pickup timeout.");
            log.info("Auto-started ride request {} (ride {}, scheduled pickup {})",
                request.getSharedRideRequestId(), ride.getSharedRideId(), request.getPickupTime());
        }
    }

    private void autoCompleteRideRequests(LocalDateTime now) {
        Duration timeout = rideConfig.getAutoLifecycle().getRequestDropoffTimeout();
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            return;
        }

        LocalDateTime cutoff = now.minus(timeout);
        List<SharedRideRequest> requests = requestRepository.findOngoingForAutoCompletion(cutoff);
        for (SharedRideRequest request : requests) {
            SharedRide ride = request.getSharedRide();
            if (ride == null || ride.getStatus() != SharedRideStatus.ONGOING) {
                continue;
            }

            try {
                Authentication automationAuth = buildDriverAuthentication(ride.getDriver().getUser());
                sharedRideService.completeRideRequestOfRide(
                    new CompleteRideReqRequest(ride.getSharedRideId(), request.getSharedRideRequestId()),
                    automationAuth);
                notifyRideRequestTransition(request, "REQUEST_AUTO_COMPLETED",
                    "Ride request auto-completed due to extended dropoff duration.");
                log.info("Auto-completed ride request {} for ride {} after pickup at {}",
                    request.getSharedRideRequestId(), ride.getSharedRideId(), request.getActualPickupTime());
            } catch (Exception ex) {
                log.warn("Auto-complete failed for ride request {} (ride {}): {}",
                    request.getSharedRideRequestId(), ride.getSharedRideId(), ex.getMessage());
                log.debug("Auto-complete error details", ex);
            }
        }
    }

    private void autoCompleteRides(LocalDateTime now) {
        Duration leeway = rideConfig.getAutoLifecycle().getRideAutoCompleteLeeway();
        if (leeway == null || leeway.isNegative() || leeway.isZero()) {
            return;
        }

        LocalDateTime cutoff = now.minus(leeway);
        List<SharedRide> rides = rideRepository.findOngoingForAutoCompletion(cutoff);
        if (rides.isEmpty()) {
            return;
        }

        List<SharedRideRequestStatus> activeStatuses = List.of(
            SharedRideRequestStatus.CONFIRMED, SharedRideRequestStatus.ONGOING);

        for (SharedRide ride : rides) {
            boolean hasActiveRequests = requestRepository.existsBySharedRideSharedRideIdAndStatusIn(
                ride.getSharedRideId(), activeStatuses);
            if (hasActiveRequests) {
                log.debug("Ride {} still has active requests; auto-completion skipped", ride.getSharedRideId());
                continue;
            }

            try {
                Authentication automationAuth = buildDriverAuthentication(ride.getDriver().getUser());
                sharedRideService.completeRide(new CompleteRideRequest(ride.getSharedRideId()), automationAuth);
                notifyDriverRideTransition(ride, "ride.auto.completed",
                    "Ride auto-completed because all passengers finished and driver was inactive.");
                log.info("Auto-completed ride {} (started at {})", ride.getSharedRideId(), ride.getStartedAt());
            } catch (Exception ex) {
                log.warn("Auto-complete failed for ride {}: {}", ride.getSharedRideId(), ex.getMessage());
                log.debug("Auto-complete ride error details", ex);
            }
        }
    }

    private Authentication buildDriverAuthentication(User user) {
        return new UsernamePasswordAuthenticationToken(
            user.getEmail(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_DRIVER"),
                new SimpleGrantedAuthority(SYSTEM_TRIGGER)));
    }

    private void notifyRideRequestTransition(SharedRideRequest request, String status, String message) {
        try {
            var riderUser = request.getRider().getUser();
            if (riderUser != null) {
                RiderMatchStatusNotification riderPayload = responseAssembler.toRiderLifecycleUpdate(
                    request, status, message);
                realTimeNotificationService.notifyRiderStatus(riderUser, riderPayload);
                notificationService.sendNotification(
                    riderUser,
                    mapStatusToNotificationType(status),
                    "Ride request update",
                    message,
                    null,
                    Priority.MEDIUM,
                    DeliveryMethod.PUSH,
                    "/queue/ride-matching");
            }
        } catch (Exception ex) {
            log.warn("Failed to notify rider for auto lifecycle update (request {}): {}",
                request.getSharedRideRequestId(), ex.getMessage());
            log.debug("Rider notification error details", ex);
        }

        try {
            var driver = request.getSharedRide() != null ? request.getSharedRide().getDriver() : null;
            if (driver != null && driver.getUser() != null) {
                realTimeNotificationService.notifyDriverOffer(
                    driver,
                    DriverRideOfferNotification.builder()
                        .requestId(request.getSharedRideRequestId())
                        .rideId(request.getSharedRide().getSharedRideId())
                        .driverId(driver.getDriverId())
                        .driverName(driver.getUser().getFullName())
                        .riderId(request.getRider().getRiderId())
                        .riderName(request.getRider().getUser().getFullName())
                        .pickupLat(request.getPickupLat())
                        .pickupLng(request.getPickupLng())
                        .dropoffLat(request.getDropoffLat())
                        .dropoffLng(request.getDropoffLng())
                        .pickupTime(request.getPickupTime())
                        .totalFare(request.getTotalFare())
                        .broadcast(Boolean.TRUE)
                        .responseWindowSeconds(null)
                        .offerExpiresAt(ZonedDateTime.now(ZoneId.systemDefault()))
                        .build());
                notificationService.sendNotification(
                    driver.getUser(),
                    mapStatusToNotificationType(status),
                    "Ride request update",
                    message,
                    null,
                    Priority.MEDIUM,
                    DeliveryMethod.PUSH,
                    "/queue/ride-offers");
            }
        } catch (Exception ex) {
            log.warn("Failed to notify driver for auto lifecycle update (request {}): {}",
                request.getSharedRideRequestId(), ex.getMessage());
            log.debug("Driver notification error details", ex);
        }
    }

    private void notifyDriverRideTransition(SharedRide ride, String logKey, String message) {
        try {
            var driver = ride.getDriver();
            if (driver == null || driver.getUser() == null) {
                return;
            }
            notificationService.sendNotification(
                driver.getUser(),
                logKey.contains("completed") ? NotificationType.RIDE_AUTO_COMPLETED : NotificationType.RIDE_AUTO_STARTED,
                "Ride update",
                message,
                null,
                Priority.MEDIUM,
                DeliveryMethod.PUSH,
                "/queue/ride-offers");
            log.info("Lifecycle notification sent to driver {} for ride {} ({})",
                driver.getDriverId(), ride.getSharedRideId(), logKey);
        } catch (Exception ex) {
            log.warn("Failed to notify driver for ride {} lifecycle event {}: {}",
                ride.getSharedRideId(), logKey, ex.getMessage());
            log.debug("Driver lifecycle notification error details", ex);
        }
    }

    private NotificationType mapStatusToNotificationType(String status) {
        return switch (status) {
            case "REQUEST_AUTO_STARTED" -> NotificationType.REQUEST_AUTO_STARTED;
            case "REQUEST_AUTO_COMPLETED" -> NotificationType.REQUEST_AUTO_COMPLETED;
            case "ride.auto.started" -> NotificationType.RIDE_AUTO_STARTED;
            case "ride.auto.completed" -> NotificationType.RIDE_AUTO_COMPLETED;
            default -> NotificationType.SYSTEM;
        };
    }
}

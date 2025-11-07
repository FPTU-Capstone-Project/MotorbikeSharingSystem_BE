package com.mssus.app.messaging;

import com.mssus.app.messaging.dto.DriverLocationUpdateMessage;
import com.mssus.app.service.RideRequestCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

@RequiredArgsConstructor
@Slf4j
public class SynchronousRideEventPublisher implements RideEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publishRideRequestCreated(Integer requestId) {
        if (requestId == null) {
            log.warn("Skipping RideRequestCreatedEvent publish - requestId is null");
            return;
        }
        log.debug("Publishing RideRequestCreatedEvent via synchronous publisher for request {}", requestId);
        applicationEventPublisher.publishEvent(new RideRequestCreatedEvent(this, requestId));
    }

    @Override
    public void publishDriverLocationUpdate(DriverLocationUpdateMessage message) {
        if (message == null) {
            log.warn("Skipping synchronous driver location publish - message is null");
            return;
        }
        log.trace("Synchronous driver location publish is a no-op (driverId={}, location=({},{}))",
            message.getDriverId(),
            message.getLatitude(),
            message.getLongitude());
    }
}

package com.mssus.app.service;

import com.mssus.app.messaging.RideEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationEventPublisherService {

    private final RideEventPublisher rideEventPublisher;

    public void publishRideRequestCreatedEvent(Integer requestId) {
        log.info("Dispatching ride.request.created for request ID: {}", requestId);
        rideEventPublisher.publishRideRequestCreated(requestId);
    }
}

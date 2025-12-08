package com.mssus.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationEventPublisherService {

    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Publishes Spring application event which will be handled by TransactionalEventListener
     * AFTER transaction commits, preventing race conditions on AWS.
     */
    public void publishRideRequestCreatedEvent(Integer requestId) {
        log.info("Publishing RideRequestCreatedEvent for request ID: {} (will be sent to MQ after TX commit)", requestId);
        applicationEventPublisher.publishEvent(new RideRequestCreatedEvent(this, requestId));
    }
}

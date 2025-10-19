package com.mssus.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationEventPublisherService {

    private final ApplicationEventPublisher eventPublisher;

    public void publishRideRequestCreatedEvent(Integer requestId) {
        log.info("Publishing RideRequestCreatedEvent for request ID: {}", requestId);
        RideRequestCreatedEvent event = new RideRequestCreatedEvent(this, requestId);
        eventPublisher.publishEvent(event);
    }
}
package com.mssus.app.messaging;

import com.mssus.app.service.RideRequestCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to application events and publishes to RabbitMQ AFTER transaction commits.
 * This prevents race conditions where RabbitMQ consumers receive messages before
 * database transaction is committed (especially important on AWS with network latency).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionalRideEventListener {

    private final RideEventPublisher rideEventPublisher;

    /**
     * Publishes ride request created event to RabbitMQ AFTER transaction commits successfully.
     * This ensures database changes are visible to consumers before they process the message.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRideRequestCreated(RideRequestCreatedEvent event) {
        Integer requestId = event.getRequestId();
        log.info("Transaction committed - publishing ride.request.created for request ID: {}", requestId);
        rideEventPublisher.publishRideRequestCreated(requestId);
    }
}

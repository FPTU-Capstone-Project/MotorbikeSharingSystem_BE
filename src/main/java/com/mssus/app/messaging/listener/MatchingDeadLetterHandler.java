package com.mssus.app.messaging.listener;

import com.mssus.app.messaging.dto.MatchingCommandMessage;
import com.mssus.app.service.domain.matching.session.MatchingSessionRepository;
import com.mssus.app.service.domain.matching.session.MatchingSessionState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Handler for messages that failed processing and landed in the Dead Letter Queue.
 * Logs errors, attempts recovery where possible, and alerts operators to critical failures.
 * 
 * NOTE: Currently configured for manual monitoring only. Automatic DLQ routing is disabled
 * to avoid conflicts with existing queue declarations. Messages must be manually moved to
 * the DLQ for processing.
 * 
 * Future enhancement: Enable automatic DLQ routing after queue cleanup/migration.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.messaging.ride", name = "enabled", havingValue = "true")
public class MatchingDeadLetterHandler {

    private final MatchingSessionRepository sessionRepository;
    private final MessageConverter rideEventMessageConverter;

    @RabbitListener(queues = "ride.matching.dlq", autoStartup = "true")
    public void handleDeadLetter(Message failedMessage) {
        try {
            // Extract original message details
            String messageId = failedMessage.getMessageProperties().getMessageId();
            Integer retryCount = (Integer) failedMessage.getMessageProperties()
                .getHeaders().get("x-delivery-count");
            String failureReason = (String) failedMessage.getMessageProperties()
                .getHeaders().get("x-first-death-reason");

            log.error("Message landed in DLQ - messageId: {}, retries: {}, reason: {}",
                messageId, retryCount, failureReason);

            // Try to deserialize the message
            Object payload = rideEventMessageConverter.fromMessage(failedMessage);
            if (payload instanceof MatchingCommandMessage command) {
                handleFailedMatchingCommand(command, failedMessage);
            } else {
                log.warn("Unknown message type in DLQ: {}", 
                    payload != null ? payload.getClass().getName() : "null");
            }

        } catch (Exception e) {
            log.error("Error processing dead letter message", e);
        }
    }

    private void handleFailedMatchingCommand(MatchingCommandMessage command, Message failedMessage) {
        log.error("Matching command failed - type: {}, requestId: {}, correlationId: {}",
            command.getCommandType(), command.getRequestId(), command.getCorrelationId());

        // Attempt recovery based on command type
        if (command.getRequestId() != null) {
            MatchingSessionState session = sessionRepository.find(command.getRequestId())
                .orElse(null);

            if (session != null) {
                log.error("Session state at failure - phase: {}, requestId: {}", 
                    session.getPhase(), session.getRequestId());

                // For critical failures, mark session as expired to prevent hanging requests
                if (isPermanentFailure(failedMessage)) {
                    log.warn("Marking session {} as expired due to permanent failure", 
                        command.getRequestId());
                    session.markExpired();
                    sessionRepository.save(session, java.time.Duration.ofMinutes(5));
                }
            } else {
                log.warn("No session found for failed command - requestId: {}", 
                    command.getRequestId());
            }
        }

        // Future enhancements:
        // 1. Send alert to operations team for manual intervention
        // 2. Write to dedicated error table for later analysis
        // 3. Attempt to notify affected users (rider/driver)
        // 4. Trigger compensating transactions (e.g., release wallet holds)
    }

    private boolean isPermanentFailure(Message message) {
        Integer retryCount = (Integer) message.getMessageProperties()
            .getHeaders().get("x-delivery-count");
        // If message has been retried more than threshold, consider it permanent
        return retryCount != null && retryCount > 3;
    }
}


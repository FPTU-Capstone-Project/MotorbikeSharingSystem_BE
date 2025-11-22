package com.mssus.app.messaging;

import com.mssus.app.appconfig.config.properties.RideMessagingProperties;
import com.mssus.app.messaging.dto.MatchingCommandMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "app.messaging.ride", name = "enabled", havingValue = "true")
public class RideMatchingCommandPublisher {

    @Qualifier("rideEventRabbitTemplate")
    private final RabbitTemplate rabbitTemplate;
    private final RideMessagingProperties properties;

    public void publish(MatchingCommandMessage command) {
        if (command == null) {
            return;
        }
        rabbitTemplate.convertAndSend(
            properties.getExchange(),
            properties.getMatchingCommandRoutingKey(),
            command);
        if (log.isTraceEnabled()) {
            log.trace("Published matching command {} for request {}", command.getCommandType(), command.getRequestId());
        }
    }

    public void publishDriverTimeout(MatchingCommandMessage command, Duration delay) {
        publishToDelayQueue(command, delay, properties.getDriverTimeoutDelayQueue());
    }

    public void publishBroadcastTimeout(MatchingCommandMessage command, Duration delay) {
        publishToDelayQueue(command, delay, properties.getBroadcastTimeoutDelayQueue());
    }

    private void publishToDelayQueue(MatchingCommandMessage command, Duration delay, String queue) {
        if (command == null || delay == null || queue == null) {
            return;
        }
        MessagePostProcessor processor = message -> {
            message.getMessageProperties().setExpiration(String.valueOf(delay.toMillis()));
            return message;
        };
        rabbitTemplate.convertAndSend(queue, command, processor);
        if (log.isTraceEnabled()) {
            log.trace("Scheduled matching command {} for request {} -> queue {} delay {}",
                command.getCommandType(), command.getRequestId(), queue, delay);
        }
    }
}

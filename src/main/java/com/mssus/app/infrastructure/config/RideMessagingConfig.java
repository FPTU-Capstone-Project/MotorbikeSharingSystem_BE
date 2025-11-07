package com.mssus.app.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mssus.app.infrastructure.config.properties.RideMessagingProperties;
import com.mssus.app.messaging.RabbitRideEventPublisher;
import com.mssus.app.messaging.RideEventPublisher;
import com.mssus.app.messaging.SynchronousRideEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RideMessagingProperties.class)
@RequiredArgsConstructor
public class RideMessagingConfig {

    private final RideMessagingProperties rideMessagingProperties;

    @Bean
    @ConditionalOnProperty(prefix = "app.messaging.ride", name = "enabled", havingValue = "true")
    public Declarables rideMessagingTopology() {
        TopicExchange exchange = new TopicExchange(rideMessagingProperties.getExchange(), true, false);

        Queue matchingDlq = new Queue("ride.matching.dlq", true);

        Queue requestQueue = new Queue(rideMessagingProperties.getRideRequestCreatedQueue(), true);
        Queue locationQueue = new Queue(rideMessagingProperties.getDriverLocationQueue(), true);
        Queue commandQueue = new Queue(rideMessagingProperties.getMatchingCommandQueue(), true);
        Queue notificationQueue = new Queue(rideMessagingProperties.getNotificationQueue(), true);

        Queue driverTimeoutDelayQueue = QueueBuilder.durable(rideMessagingProperties.getDriverTimeoutDelayQueue())
            .withArgument("x-dead-letter-exchange", rideMessagingProperties.getExchange())
            .withArgument("x-dead-letter-routing-key", rideMessagingProperties.getMatchingCommandRoutingKey())
            .build();
        Queue broadcastTimeoutDelayQueue = QueueBuilder.durable(rideMessagingProperties.getBroadcastTimeoutDelayQueue())
            .withArgument("x-dead-letter-exchange", rideMessagingProperties.getExchange())
            .withArgument("x-dead-letter-routing-key", rideMessagingProperties.getMatchingCommandRoutingKey())
            .build();

        Binding requestBinding = BindingBuilder.bind(requestQueue)
            .to(exchange)
            .with(rideMessagingProperties.getRideRequestCreatedRoutingKey());
        Binding locationBinding = BindingBuilder.bind(locationQueue)
            .to(exchange)
            .with(rideMessagingProperties.getDriverLocationRoutingKey());
        Binding commandBinding = BindingBuilder.bind(commandQueue)
            .to(exchange)
            .with(rideMessagingProperties.getMatchingCommandRoutingKey());
        Binding notificationBinding = BindingBuilder.bind(notificationQueue)
            .to(exchange)
            .with(rideMessagingProperties.getNotificationRoutingKey());

        return new Declarables(
            exchange,
            requestQueue,
            locationQueue,
            commandQueue,
            notificationQueue,
            matchingDlq,
            driverTimeoutDelayQueue,
            broadcastTimeoutDelayQueue,
            requestBinding,
            locationBinding,
            commandBinding,
            notificationBinding);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.messaging.ride", name = "enabled", havingValue = "true")
    public MessageConverter rideEventMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setCreateMessageIds(true);
        return converter;
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.messaging.ride", name = "enabled", havingValue = "true")
    public RabbitTemplate rideEventRabbitTemplate(ConnectionFactory connectionFactory,
                                                  MessageConverter rideEventMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(rideEventMessageConverter);
        template.setExchange(rideMessagingProperties.getExchange());
        return template;
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.messaging.ride", name = "enabled", havingValue = "true")
    public RideEventPublisher rabbitRideEventPublisher(RabbitTemplate rideEventRabbitTemplate) {
        return new RabbitRideEventPublisher(rideEventRabbitTemplate, rideMessagingProperties);
    }

    @Bean
    @ConditionalOnMissingBean(RideEventPublisher.class)
    public RideEventPublisher synchronousRideEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        return new SynchronousRideEventPublisher(applicationEventPublisher);
    }
}

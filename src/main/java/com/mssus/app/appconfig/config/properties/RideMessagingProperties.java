package com.mssus.app.appconfig.config.properties;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Messaging configuration for ride coordination events.
 *
 * <p>Phase 1 introduces RabbitMQ as an optional transport. We keep it
 * disabled by default and flip {@code enabled} via configuration when the
 * broker is available.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.messaging.ride")
public class RideMessagingProperties {

    /**
     * Feature flag controlling whether Rabbit-backed publishers are used.
     */
    private boolean enabled = false;

    /**
     * Enables the queue-driven matching orchestrator. When disabled, the legacy
     * in-memory coordinator runs instead.
     */
    private boolean matchingEnabled = false;

    /**
     * Enables publishing ride notifications via RabbitMQ instead of direct service calls.
     */
    private boolean notificationsEnabled = false;

    /**
     * Controls whether broadcast offers are pushed to drivers or only exposed in the marketplace feed.
     */
    private boolean broadcastPushEnabled = false;

    /**
     * Exchange that receives ride events (topic exchange works for routing by type).
     */
    private String exchange = "ride.events";

    /**
     * Routing key for AI booking request creation.
     */
    private String rideRequestCreatedRoutingKey = "ride.request.created";

    /**
     * Queue name used when auto-declaring topology (optional but keeps defaults together).
     */
    private String rideRequestCreatedQueue = "ride.request.created.queue";

    /**
     * Routing key for general matching commands.
     */
    private String matchingCommandRoutingKey = "ride.matching.command";

    /**
     * Queue that matching commands are delivered to.
     */
    private String matchingCommandQueue = "ride.matching.command.queue";

    /**
     * Delayed queue for driver offer timeouts (dead-lettered back to command queue).
     */
    private String driverTimeoutDelayQueue = "ride.matching.delay.driver-timeout";

    /**
     * Delayed queue for broadcast mode timeouts.
     */
    private String broadcastTimeoutDelayQueue = "ride.matching.delay.broadcast-timeout";

    /**
     * Routing key for driver location updates.
     */
    private String driverLocationRoutingKey = "ride.location.driver";

    /**
     * Queue used to consume driver location updates.
     */
    private String driverLocationQueue = "ride.location.driver.queue";

    private String notificationRoutingKey = "ride.notifications";

    private String notificationQueue = "ride.notifications.queue";

    /**
     * Optional override for full matching timeout (defaults to 5 minutes when null).
     */
    private Duration matchingRequestTimeout = Duration.ofMinutes(5);

    /**
     * Optional override for driver response window (defaults to 90 seconds when null).
     */
    private Duration driverResponseWindow = Duration.ofSeconds(90);

}

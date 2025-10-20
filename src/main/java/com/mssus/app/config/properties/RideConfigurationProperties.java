package com.mssus.app.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Configuration properties for ride module.
 * 
 * <p>Properties are loaded from application.yml under 'app.ride' prefix.</p>
 * 
 * @since 1.0.0 (Ride Module MVP)
 */
@ConfigurationProperties(prefix = "app.ride")
@Data
public class RideConfigurationProperties {
    
    /**
     * Request acceptance timeout (T_ACCEPT).
     * Duration after which pending AI_BOOKING requests expire if no driver accepts.
     * Default: 5 minutes
     */
    private Duration requestAcceptTimeout = Duration.ofMinutes(5);
    
    /**
     * Matching algorithm parameters.
     */
    private Matching matching = new Matching();
    
    /**
     * Cancellation fee configuration.
     */
    private Cancellation cancellation = new Cancellation();

    /**
     * Broadcast fallback configuration.
     */
    private Broadcast broadcast = new Broadcast();

    /**
     * Automatic lifecycle enforcement configuration.
     */
    private AutoLifecycle autoLifecycle = new AutoLifecycle();
    
    /**
     * Matching algorithm configuration.
     */
    @Data
    public static class Matching {
        /**
         * Maximum proximity distance (km) for AI matching.
         * Candidate rides must have pickup/dropoff within this radius.
         * Default: 2.0 km (as per BR-25)
         */
        private Double maxProximityKm = 2.0;
        
        /**
         * Time window (minutes) for matching rides.
         * Candidate rides must be scheduled within ±window of requested pickup time.
         * Default: 15 minutes (as per BR-26)
         */
        private Integer timeWindowMinutes = 15;
        
        /**
         * Maximum detour distance (km) for pickup.
         * Default: 1.5 km (can be overridden by driver profile setting)
         */
        private Double maxDetourKm = 1.5;
        
        /**
         * Maximum detour time (minutes) for pickup.
         * Default: 8 minutes (as per BR-26, BR-27)
         */
        private Integer maxDetourMinutes = 8;
        
        /**
         * Maximum number of match proposals to return.
         * Default: 10
         */
        private Integer maxProposals = 10;

        /**
         * Time step (seconds) for increasing coverage radius during matching.
         * Each step increases search radius to find more candidates.
         * Default: 30
         */
        private Integer coverageTimeStep = 30;

        /**
         * Maximum time (seconds) a driver has to respond to a ride offer.
         * Default: 90 seconds.
         */
        private Integer driverResponseSeconds = 90;
        
        /**
         * Scoring weights for matching algorithm.
         */
        private Scoring scoring = new Scoring();
    }
    
    /**
     * Scoring weights for AI matching algorithm.
     * Total must sum to 1.0 for balanced scoring.
     */
    @Data
    public static class Scoring {
        /**
         * Weight for proximity score (0.0-1.0).
         * Default: 0.4 (40%)
         */
        private Double proximityWeight = 0.4;
        
        /**
         * Weight for time alignment score (0.0-1.0).
         * Default: 0.3 (30%)
         */
        private Double timeWeight = 0.3;
        
        /**
         * Weight for driver rating score (0.0-1.0).
         * Default: 0.2 (20%)
         */
        private Double ratingWeight = 0.2;
        
        /**
         * Weight for detour penalty score (0.0-1.0).
         * Default: 0.1 (10%)
         */
        private Double detourWeight = 0.1;
    }
    
    /**
     * Cancellation fee configuration.
     */
    @Data
    public static class Cancellation {
        /**
         * Cancellation fee percentage (0.0-1.0).
         * Applied to fare amount if rider cancels after confirmation.
         * Default: 0.2 (20%)
         */
        private BigDecimal feePercentage = new BigDecimal("0.20");
        
        /**
         * Grace period (minutes) for free cancellation after confirmation.
         * Cancellations within this period are free.
         * Default: 2 minutes
         */
        private Integer gracePeriodMinutes = 2;
    }

    /**
     * Broadcast fallback configuration.
     */
    @Data
    public static class Broadcast {
        /**
         * Response window (seconds) for drivers during broadcast fallback.
         * Default: 30 seconds.
         */
        private Integer responseWindowSeconds = 30;
    }

    /**
     * Automatic lifecycle enforcement settings.
     */
    @Data
    public static class AutoLifecycle {
        /**
         * Enable/disable lifecycle automation.
         * Default: true.
         */
        private boolean enabled = true;

        /**
         * How often the lifecycle worker scans the system (milliseconds).
         * Default: 60_000 (1 minute).
         */
        private Long scanIntervalMs = 60_000L;

        /**
         * Grace period after scheduledTime before a ride is auto-started (minutes).
         * Default: 5 minutes.
         */
        private Duration rideAutoStartLeeway = Duration.ofMinutes(5);

        /**
         * Grace period after ride startedAt before auto-completing the ride (minutes).
         * Default: 15 minutes.
         */
        private Duration rideAutoCompleteLeeway = Duration.ofMinutes(15);

        /**
         * Maximum time a confirmed request can wait for pickup before auto-start (minutes).
         * Default: 5 minutes.
         */
        private Duration requestPickupTimeout = Duration.ofMinutes(15);

        /**
         * Maximum time an ongoing request can remain active before auto-completion (minutes).
         * Default: 15 minutes.
         */
        private Duration requestDropoffTimeout = Duration.ofMinutes(15);
    }
}


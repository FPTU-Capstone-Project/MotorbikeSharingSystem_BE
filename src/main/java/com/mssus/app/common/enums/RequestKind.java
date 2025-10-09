package com.mssus.app.common.enums;

/**
 * Type of shared ride request flow.
 * 
 * <p>This enum distinguishes between two ride request flows:</p>
 * <ul>
 *   <li><b>AI_BOOKING</b>: Rider requests a ride without specifying a driver.
 *       The system uses AI matching to propose candidate rides. The request
 *       starts with {@code shared_ride_id = NULL} and gets assigned when a
 *       driver accepts it.</li>
 *   <li><b>JOIN_RIDE</b>: Rider requests to join a specific shared ride.
 *       The request is created with {@code shared_ride_id} already set.</li>
 * </ul>
 * 
 * @since 1.0.0 (Ride Module MVP)
 * @see com.mssus.app.entity.SharedRideRequest
 */
public enum RequestKind {
    /**
     * AI-powered matching flow.
     * Request starts without a ride, system proposes matches, driver accepts.
     */
    BOOKING,
    
    /**
     * Direct join flow.
     * Rider selects a specific ride from browse results and requests to join.
     */
    JOIN_RIDE
}


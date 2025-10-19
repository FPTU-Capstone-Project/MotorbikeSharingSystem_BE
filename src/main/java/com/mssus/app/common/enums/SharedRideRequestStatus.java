package com.mssus.app.common.enums;

/**
 * Status of a shared ride request.
 * 
 * <p><b>Status Flow:</b></p>
 * <pre>
 * PENDING → CONFIRMED → ONGOING → COMPLETED
 *    ↓
 * EXPIRED (timeout)
 * CANCELLED (explicit cancellation)
 * </pre>
 * 
 * <p><b>Note:</b> IN_PROGRESS is aliased to ONGOING for consistency.
 * Both are kept for backward compatibility.</p>
 * 
 * @since 1.0.0
 */
public enum SharedRideRequestStatus {
    /**
     * Request is awaiting driver acceptance (AI_BOOKING) or approval (JOIN_RIDE).
     */
    PENDING,

    /**
     * Request is being broadcasted to potential drivers (AI_BOOKING).
     * NEW in Ride Module MVP.
     */
    //TODO: Implement logic around this status
    BROADCASTING,
    
    /**
     * Request accepted by driver, awaiting ride start.
     */
    CONFIRMED,
    
    /**
     * @deprecated Use ONGOING instead. Kept for backward compatibility.
     */
    @Deprecated
    IN_PROGRESS,
    
    /**
     * Request is active (rider picked up, ride in progress).
     * NEW in Ride Module MVP.
     */
    ONGOING,
    
    /**
     * Request completed successfully (rider dropped off, fare captured).
     */
    COMPLETED,
    
    /**
     * Request cancelled explicitly by rider, driver, or admin.
     */
    CANCELLED,
    
    /**
     * Request expired due to timeout (no driver accepted within T_ACCEPT window).
     * NEW in Ride Module MVP.
     */
    EXPIRED
}

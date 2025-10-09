package com.mssus.app.common.enums;

/**
 * Status of a shared ride.
 * 
 * <p><b>Status Flow:</b></p>
 * <pre>
 * SCHEDULED → ONGOING → COMPLETED
 *     ↓
 * CANCELLED
 * </pre>
 * 
 * <p><b>Note:</b> ACTIVE and PENDING are deprecated in favor of SCHEDULED and ONGOING.
 * Migration V3 handles the data migration. Kept for backward compatibility.</p>
 * 
 * @since 1.0.0
 */
public enum SharedRideStatus {
    /**
     * Ride is created and awaiting start time.
     * Replaces PENDING/ACTIVE for consistency.
     */
    SCHEDULED,
    
    /**
     * @deprecated Use SCHEDULED instead. Kept for backward compatibility.
     */
    @Deprecated
    ACTIVE,
    
    /**
     * Ride is currently in progress (driver started the trip).
     * NEW in Ride Module MVP.
     */
    ONGOING,
    
    /**
     * Ride finished successfully (all riders dropped off).
     */
    COMPLETED,
    
    /**
     * Ride was cancelled by driver or admin before starting.
     */
    CANCELLED,
    
    /**
     * @deprecated Use SCHEDULED instead. Kept for backward compatibility.
     */
    @Deprecated
    PENDING
}

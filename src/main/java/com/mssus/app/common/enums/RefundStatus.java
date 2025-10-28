package com.mssus.app.common.enums;

/**
 * Enum representing the lifecycle status of a refund request.
 * Tracks the progression from initial request through approval/rejection to completion.
 */
public enum RefundStatus {
    /**
     * Initial status when refund request is created and pending staff review
     */
    PENDING,

    /**
     * Refund has been approved by staff/admin and is awaiting processing
     */
    APPROVED,

    /**
     * Refund has been rejected by staff/admin with a reason
     */
    REJECTED,

    /**
     * Refund has been successfully processed and completed
     */
    COMPLETED,

    /**
     * Refund processing failed (e.g., PSP rejection)
     */
    FAILED,

    /**
     * Refund has been cancelled (e.g., user cancelled the request)
     */
    CANCELLED
}


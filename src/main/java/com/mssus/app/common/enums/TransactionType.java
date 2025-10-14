package com.mssus.app.common.enums;

public enum TransactionType {
    /**
     * Records funds deposited by the user into the in-app wallet via the Payment Service Provider (PSP).
     * Increases the user's available balance (shadow_balance).
     * Involves a mirror row for SYSTEM.MASTER (IN) and a credit row for USER (IN).
     */
    TOPUP,

    /**
     * Records placing a financial hold (authorization) on the rider's wallet for the quoted fare.
     * This is an INTERNAL transaction.
     * Decreases shadow_balance and increases pending_balance.
     * Used upon booking or confirming a shared ride request.
     */
    HOLD_CREATE,

    /**
     * Records the release of a financial hold back to the user's available balance.
     * This is an INTERNAL transaction.
     * Decreases pending_balance and increases shadow_balance.
     * Occurs when a ride is canceled or expires.
     */
    HOLD_RELEASE,

    /**
     * Records the final payment deduction and allocation upon ride completion.
     * Converts the held funds (pending_balance) to spent.
     * Creates multiple rows (Rider OUT, Driver IN, SYSTEM.COMMISSION IN) tied by group_id.
     * Reduces the Rider's pending_balance and credits the Driver's shadow_balance.
     */
    CAPTURE_FARE,

    /**
     * Records the successful withdrawal of funds (payout) from the user's available balance to an external bank or PSP account.
     * Decreases the user's shadow_balance and is mirrored by an outflow from SYSTEM.MASTER .
     */
    PAYOUT,

    /**
     * Used for corrections, compensation, or technical reversals of failed transactions (e.g., reversing a failed payout debit).
     * Affects the user's shadow_balance (increase or decrease).
     */
    ADJUSTMENT,

    REFUND
}

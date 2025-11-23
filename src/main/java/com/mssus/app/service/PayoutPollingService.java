package com.mssus.app.service;

import com.mssus.app.entity.Transaction;

/**
 * Service to poll PayOS API for payout status when webhook is not received.
 */
public interface PayoutPollingService {
    /**
     * Poll PayOS API to check payout status by referenceId.
     * Updates transaction status if status has changed.
     * 
     * @param transaction The payout transaction to check
     * @return true if status was updated, false otherwise
     */
    boolean pollPayoutStatus(Transaction transaction);
    
    /**
     * Check if transaction should be polled (based on age and status).
     * 
     * @param transaction The transaction to check
     * @return true if should poll, false otherwise
     */
    boolean shouldPoll(Transaction transaction);
}


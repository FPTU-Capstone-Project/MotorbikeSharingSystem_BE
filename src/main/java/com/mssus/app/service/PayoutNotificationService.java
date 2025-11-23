package com.mssus.app.service;

import com.mssus.app.entity.User;

import java.math.BigDecimal;

/**
 * Service to send notifications for payout events.
 */
public interface PayoutNotificationService {
    /**
     * Notify user when payout is initiated.
     */
    void notifyPayoutInitiated(User user, String payoutRef, BigDecimal amount);
    
    /**
     * Notify user when payout is processing.
     */
    void notifyPayoutProcessing(User user, String payoutRef, BigDecimal amount);
    
    /**
     * Notify user when payout is successful.
     */
    void notifyPayoutSuccess(User user, String payoutRef, BigDecimal amount);
    
    /**
     * Notify user when payout fails.
     */
    void notifyPayoutFailed(User user, String payoutRef, BigDecimal amount, String reason);
}


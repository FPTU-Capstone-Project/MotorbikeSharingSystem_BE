package com.mssus.app.service;

import com.mssus.app.dto.request.wallet.*;
import com.mssus.app.dto.response.wallet.BalanceCheckResponse;
import com.mssus.app.dto.response.wallet.WalletOperationResponse;

import java.math.BigDecimal;

/**
 * Service for internal booking-related wallet operations.
 * This service is used by the booking service to manage wallet holds, captures, and refunds.
 */
public interface BookingWalletService {

    /**
     * Hold funds from user wallet for a booking.
     * This creates a pending transaction that reserves the amount.
     *
     * @param request Hold request containing userId, bookingId, amount
     * @return Operation response with transaction details
     */
    WalletOperationResponse holdFunds(WalletHoldRequest request);

    /**
     * Capture previously held funds and transfer to driver.
     * This finalizes the payment for a completed booking.
     *
     * @param request Capture request containing userId, bookingId, amount, driverId
     * @return Operation response with transaction details
     */
    WalletOperationResponse captureFunds(WalletCaptureRequest request);

    /**
     * Release previously held funds back to user.
     * Used when a booking is cancelled before completion.
     *
     * @param request Release request containing userId, bookingId, amount
     * @return Operation response with transaction details
     */
    WalletOperationResponse releaseFunds(WalletReleaseRequest request);

    /**
     * Refund amount to user wallet.
     * Used for service issues or cancellations after payment.
     *
     * @param request Refund request containing userId, bookingId, amount, reason
     * @return Operation response with transaction details
     */
    WalletOperationResponse refundToUser(WalletRefundRequest request);

    /**
     * Check if user has sufficient balance for a booking amount.
     *
     * @param userId User ID to check
     * @param amount Required amount
     * @return Balance check response with sufficiency status
     */
    BalanceCheckResponse checkBalance(Integer userId, BigDecimal amount);
}

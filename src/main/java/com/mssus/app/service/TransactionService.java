package com.mssus.app.service;

import com.mssus.app.entity.Transaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface TransactionService {
    // Existing methods
//    Transaction createPendingTopUpTransaction(Integer userId, BigDecimal amount, String pspRef, String description);
//    void completeTopUpTransaction(String pspRef);
//    void failTransaction(String pspRef, String reason);
//    Transaction getTransactionByPspRef(String pspRef);

    // RIDER_TOPUP flows
    List<Transaction> initTopup(Integer userId, BigDecimal amount, String pspRef, String description);
    void handleTopupSuccess(String pspRef);
    void handleTopupFailed(String pspRef, String reason);

    // RIDE_HOLD flows
    List<Transaction> createHold(Integer riderId, BigDecimal amount, Integer bookingId, String description);

    // RIDE_CAPTURE flows
    List<Transaction> captureFare(UUID groupId, Integer riderId, Integer driverId, BigDecimal totalFare,
                                 BigDecimal commissionRate, String description);

    // RIDE_CANCEL flows
    List<Transaction> releaseHold(UUID groupId, String description);

    // DRIVER_PAYOUT flows
    List<Transaction> initPayout(Integer driverId, BigDecimal amount, String pspRef, String description);
    void handlePayoutSuccess(String pspRef);
    void handlePayoutFailed(String pspRef, String reason);

    // RIDE_REFUND flows
    List<Transaction> refundRide(UUID originalGroupId, Integer riderId, Integer driverId,
                                BigDecimal refundAmount, String description);

    // PROMO_CREDIT flows
//    Transaction creditPromo(Integer userId, BigDecimal amount, String promoCode, String description);

    // ADMIN_ADJUSTMENT flows
    Transaction adjustment(Integer userId, BigDecimal amount, Integer adminUserId, String reason);

    // Utility methods
    List<Transaction> getTransactionsByGroupId(UUID groupId);
    BigDecimal calculateCommission(BigDecimal amount, BigDecimal commissionRate);
}

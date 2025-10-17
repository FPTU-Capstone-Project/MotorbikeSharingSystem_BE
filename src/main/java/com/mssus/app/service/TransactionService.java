package com.mssus.app.service;

import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.wallet.TransactionResponse;
import com.mssus.app.entity.Transaction;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface TransactionService {
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

    // Utility methods
    List<Transaction> getTransactionsByGroupId(UUID groupId);
    BigDecimal calculateCommission(BigDecimal amount, BigDecimal commissionRate);

    PageResponse<TransactionResponse> getAllTransactions(Pageable pageable);

    PageResponse<TransactionResponse> getUserHistoryTransactions(Authentication authentication,
                                                                Pageable pageable,
                                                                String type,
                                                                String status);
}

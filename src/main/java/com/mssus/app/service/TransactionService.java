package com.mssus.app.service;

import com.mssus.app.dto.request.CreateTransactionRequest;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.wallet.TransactionResponse;
import com.mssus.app.entity.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface TransactionService {
    /**
     * ✅ FIX: Thêm parameter idempotencyKey để support client-side idempotency
     * Nếu idempotencyKey = null, sẽ tự generate dựa trên pspRef (backward compatibility)
     */
    List<Transaction> initTopup(Integer userId, BigDecimal amount, String pspRef, String description, String idempotencyKey);

    void handleTopupSuccess(String pspRef);

    void handleTopupFailed(String pspRef, String reason);

    Transaction createTransaction(CreateTransactionRequest request);


//    // RIDE_HOLD flows
//    List<Transaction> createHold(Integer riderId, BigDecimal amount, Integer bookingId, String description);
//
//    // RIDE_CAPTURE flows
//    List<Transaction> captureFare(SettlementResult settlementResult, Integer riderId, Integer driverId, String description);
//
//    // RIDE_CANCEL flows
//    List<Transaction> releaseHold(UUID groupId, String description);

    // DRIVER_PAYOUT flows
    List<Transaction> initPayout(Integer driverId, BigDecimal amount, String pspRef, String description);

    void handlePayoutSuccess(String pspRef);

    void handlePayoutFailed(String pspRef, String reason);

    List<Transaction> refundRide(UUID originalGroupId, Integer riderId, Integer driverId,
                                BigDecimal refundAmount, String description);

    // Additional refund methods for different scenarios
    List<Transaction> refundTopup(Integer userId, BigDecimal refundAmount, String pspRef, String description);
    
    List<Transaction> refundPayout(Integer driverId, BigDecimal refundAmount, String pspRef, String description);
    
    List<Transaction> refundAdjustment(Integer userId, BigDecimal refundAmount, Integer adminUserId, String reason);
    
    List<Transaction> refundPromoCredit(Integer userId, BigDecimal refundAmount, String promoCode, String description);
    
    List<Transaction> processRefund(Integer refundId, String pspRef, String description);
    
    void handleRefundSuccess(String pspRef);
    
    void handleRefundFailed(String pspRef, String reason);

    List<Transaction> getTransactionsByGroupId(UUID groupId);

    BigDecimal calculateCommission(BigDecimal amount, BigDecimal commissionRate);

    PageResponse<TransactionResponse> getAllTransactions(Pageable pageable,
                                                         String type,
                                                         String status,
                                                         String direction,
                                                         String actorKind,
                                                         String dateFrom,
                                                         String dateTo);

    PageResponse<TransactionResponse> getUserHistoryTransactions(Authentication authentication,
                                                                 Pageable pageable,
                                                                 String type,
                                                                 String status);
}

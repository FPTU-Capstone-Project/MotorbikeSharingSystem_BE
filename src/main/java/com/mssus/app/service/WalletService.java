package com.mssus.app.service;

import com.mssus.app.common.enums.TransactionStatus;
import com.mssus.app.dto.request.wallet.PayoutInitRequest;
import com.mssus.app.dto.response.wallet.*;
import com.mssus.app.entity.Transaction;
import com.mssus.app.entity.Wallet;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.UUID;

public interface WalletService {
//    void updateWalletBalanceOnTopUp(Integer userId, BigDecimal amount);
//
//    void increasePendingBalance(Integer userId, BigDecimal amount);
//
//    void decreasePendingBalance(Integer userId, BigDecimal amount);
//
//    void increaseShadowBalance(Integer userId, BigDecimal amount);
//
//    void decreaseShadowBalance(Integer userId, BigDecimal amount);
//
//    void transferPendingToAvailable(Integer userId, BigDecimal amount);

    WalletResponse getBalance(Authentication authentication);

    Wallet getWalletByUserId(Integer userId);

    // ✅ initiateTopUp đã được move sang TopUpService để tách PayOS integration

    PayoutInitResponse initiatePayout(PayoutInitRequest request, Authentication authentication);

    DriverEarningsResponse getDriverEarnings(Authentication authentication);

    // Admin payout processing methods
    java.util.List<PendingPayoutResponse> getPendingPayouts();
    PayoutProcessResponse processPayout(String payoutRef, Authentication authentication);
    PayoutProcessResponse completePayout(String payoutRef, MultipartFile evidenceFile, String notes, Authentication authentication);
    PayoutProcessResponse failPayout(String payoutRef, String reason, Authentication authentication);

    Wallet createWalletForUser(Integer userId);

    boolean hasSufficientBalance(Integer userId, BigDecimal amount);

    void reconcileWalletBalance(Integer userId);

    // ========== SSOT Methods ==========
    
    /**
     * Tạo top-up transaction (SSOT)
     * @param userId User ID
     * @param amount Amount to top-up
     * @param pspRef Payment service provider reference (orderCode)
     * @param idempotencyKey Idempotency key để prevent duplicates
     * @param status Transaction status (PENDING or SUCCESS)
     * @return Created transaction
     */
    com.mssus.app.entity.Transaction createTopUpTransaction(
        Integer userId, 
        BigDecimal amount, 
        String pspRef, 
        String idempotencyKey, 
        TransactionStatus status
    );
    
    /**
     * Complete top-up transaction (update status từ PENDING -> SUCCESS)
     */
    void completeTopUpTransaction(Integer txnId);
    
    /**
     * Fail top-up transaction (update status từ PENDING -> FAILED)
     */
    void failTopUpTransaction(Integer txnId, String reason);
    
    /**
     * Find transaction by idempotency key
     */
    java.util.Optional<Transaction> findTransactionByIdempotencyKey(String idempotencyKey);
    
    /**
     * Hold amount: Create HOLD_CREATE transaction
     * @param sharedRideRequestId Required for HOLD_CREATE (database constraint)
     */
    Transaction holdAmount(
        Integer walletId, 
        BigDecimal amount, 
        UUID groupId,
        String reason,
        Integer sharedRideRequestId  // ✅ FIX: Required for database constraint
    );
    
    /**
     * Release hold: Create HOLD_RELEASE transaction
     */
    Transaction releaseHold(UUID groupId, String reason);
}

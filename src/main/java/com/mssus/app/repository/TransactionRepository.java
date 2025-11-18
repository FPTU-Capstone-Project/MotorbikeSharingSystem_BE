package com.mssus.app.repository;

import com.mssus.app.common.enums.TransactionStatus;
import com.mssus.app.common.enums.TransactionType;
import com.mssus.app.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Integer> {

    Optional<Transaction> findByPspRef(String pspRef);

    List<Transaction> findByPspRefAndStatus(String pspRef, TransactionStatus status);

    @Query("SELECT t FROM Transaction t WHERE t.actorUser.userId = :userId AND t.type = :type")
    List<Transaction> findByActorUserIdAndType(Integer userId, TransactionType type);

    @Query("SELECT t FROM Transaction t WHERE t.actorUser.userId = :userId ORDER BY t.createdAt DESC")
    List<Transaction> findByActorUserIdOrderByCreatedAtDesc(Integer userId);

    @Query("SELECT t FROM Transaction t WHERE t.actorUser.userId = :userId AND t.status = :status")
    List<Transaction> findByUserIdAndStatus(@Param("userId") Integer userId, @Param("status") TransactionStatus status);

    @Query("SELECT t FROM Transaction t WHERE t.pspRef = :pspRef AND t.status = 'PENDING'")
    List<Transaction> findPendingTransactionByPspRef(@Param("pspRef") String pspRef);

    List<Transaction> findByGroupId(java.util.UUID groupId);

    List<Transaction> findByGroupIdAndStatus(java.util.UUID groupId, TransactionStatus status);

    @Query("SELECT t FROM Transaction t WHERE (t.actorUser.userId = :userId)"
            + "AND (:type IS NULL OR t.type = :type) "
            + "AND (:status IS NULL OR t.status = :status)")
    Page<Transaction> findUserHistory(@Param("userId") Integer userId,
                                      @Param("type") TransactionType type,
                                      @Param("status") TransactionStatus status,
                                      Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.type = :type AND t.status = :status AND t.actorKind = com.mssus.app.common.enums.ActorKind.USER")
    List<Transaction> findByTypeAndStatusAndActorKindUser(@Param("type") TransactionType type,
                                                           @Param("status") TransactionStatus status);


    /**
     * Tính số dư khả dụng từ ledger
     * SSOT - Single Source of Truth
     */
    @Query(value = """
        SELECT COALESCE(SUM(
            CASE 
                WHEN direction = 'IN' AND type IN ('TOPUP', 'REFUND', 'CAPTURE_FARE') THEN amount
                WHEN direction = 'OUT' AND type IN ('PAYOUT', 'CAPTURE_FARE') THEN -amount
                WHEN direction = 'INTERNAL' AND type = 'HOLD_CREATE' THEN -amount
                WHEN direction = 'INTERNAL' AND type = 'HOLD_RELEASE' THEN amount
                ELSE 0
            END
        ), 0)
        FROM transactions
        WHERE wallet_id = :walletId
          AND status = 'SUCCESS'
        """, nativeQuery = true)
    BigDecimal calculateAvailableBalance(@Param("walletId") Integer walletId);

    /**
     * Tính số dư đang hold
     */
    @Query(value = """
        SELECT COALESCE(SUM(
            CASE 
                WHEN type = 'HOLD_CREATE' AND status = 'SUCCESS' THEN amount
                WHEN type = 'HOLD_RELEASE' AND status = 'SUCCESS' THEN -amount
                WHEN type = 'CAPTURE_FARE' AND direction = 'OUT' AND status = 'SUCCESS' THEN -amount
                ELSE 0
            END
        ), 0)
        FROM transactions
        WHERE wallet_id = :walletId
        """, nativeQuery = true)
    BigDecimal calculatePendingBalance(@Param("walletId") Integer walletId);

    /**
     * Tìm transaction theo idempotency key
     */
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Tìm transaction theo groupId và type
     */
    @Query("SELECT t FROM Transaction t WHERE t.groupId = :groupId AND t.type = :type")
    Optional<Transaction> findByGroupIdAndType(@Param("groupId") java.util.UUID groupId, 
                                               @Param("type") TransactionType type);
}

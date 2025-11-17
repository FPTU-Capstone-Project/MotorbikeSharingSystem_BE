package com.mssus.app.repository;

import com.mssus.app.common.enums.ActorKind;
import com.mssus.app.common.enums.SystemWallet;
import com.mssus.app.common.enums.TransactionDirection;
import com.mssus.app.common.enums.TransactionStatus;
import com.mssus.app.common.enums.TransactionType;
import com.mssus.app.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Integer>, JpaSpecificationExecutor<Transaction> {

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

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.type = :type AND t.status = :status AND t.createdAt BETWEEN :start AND :end")
    BigDecimal sumAmountByTypeAndStatusBetween(@Param("type") TransactionType type,
                                               @Param("status") TransactionStatus status,
                                               @Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.type = :type AND t.status = :status AND t.direction = :direction " +
            "AND t.actorKind = :actorKind AND t.createdAt BETWEEN :start AND :end")
    BigDecimal sumAmountByTypeStatusDirectionAndActorBetween(@Param("type") TransactionType type,
                                                             @Param("status") TransactionStatus status,
                                                             @Param("direction") TransactionDirection direction,
                                                             @Param("actorKind") ActorKind actorKind,
                                                             @Param("start") LocalDateTime start,
                                                             @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.systemWallet = :systemWallet AND t.status = :status")
    BigDecimal sumAmountBySystemWalletAndStatus(@Param("systemWallet") SystemWallet systemWallet,
                                                @Param("status") TransactionStatus status);

    @Query("SELECT COALESCE(SUM(CASE WHEN t.direction = com.mssus.app.common.enums.TransactionDirection.IN THEN t.amount ELSE -t.amount END), 0) "
            + "FROM Transaction t WHERE t.actorKind = com.mssus.app.common.enums.ActorKind.SYSTEM "
            + "AND t.systemWallet = :systemWallet AND t.status = :status")
    BigDecimal netAmountBySystemWalletAndStatus(@Param("systemWallet") SystemWallet systemWallet,
                                                @Param("status") TransactionStatus status);

    long countByStatus(TransactionStatus status);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.createdAt BETWEEN :start AND :end")
    long countTransactionsBetween(@Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end);

    List<Transaction> findByTypeAndStatusAndCreatedAtBetween(TransactionType type,
                                                             TransactionStatus status,
                                                             LocalDateTime start,
                                                             LocalDateTime end);

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
}

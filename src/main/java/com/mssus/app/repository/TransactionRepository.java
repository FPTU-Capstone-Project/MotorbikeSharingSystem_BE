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

    @Query("SELECT t FROM Transaction t WHERE (t.actorUser.userId = :userId OR t.riderUser.userId = :userId OR t.driverUser.userId = :userId) "
            + "AND (:type IS NULL OR t.type = :type) "
            + "AND (:status IS NULL OR t.status = :status)")
    Page<Transaction> findUserHistory(@Param("userId") Integer userId,
                                      @Param("type") TransactionType type,
                                      @Param("status") TransactionStatus status,
                                      Pageable pageable);
}

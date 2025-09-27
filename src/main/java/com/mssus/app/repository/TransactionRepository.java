package com.mssus.app.repository;

import com.mssus.app.entity.Transactions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transactions, Long> {

    Optional<Transactions> findByPspRef(String pspRef);

    List<Transactions> findByActorUserIdAndType(Integer userId, String type);

    List<Transactions> findByActorUserIdOrderByCreatedAtDesc(Integer userId);

    @Query("SELECT t FROM Transactions t WHERE t.actorUserId = :userId AND t.status = :status")
    List<Transactions> findByUserIdAndStatus(@Param("userId") Integer userId, @Param("status") String status);

    @Query("SELECT t FROM Transactions t WHERE t.pspRef = :pspRef AND t.status = 'PENDING'")
    Optional<Transactions> findPendingTransactionByPspRef(@Param("pspRef") String pspRef);
}

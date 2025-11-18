package com.mssus.app.repository;

import com.mssus.app.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Integer> {

    @Query("SELECT COUNT(w) > 0 FROM Wallet w WHERE w.user.userId = :userId")
    boolean existsByUserId(@Param("userId") Integer userId);
    Optional<Wallet> findByUser_UserId(Integer userId);

    /**
     * ✅ FIX P2-8: Find wallet with pessimistic lock for concurrency safety
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.walletId = :walletId")
    Optional<Wallet> findByIdWithLock(@Param("walletId") Integer walletId);

//    /**
//     * @deprecated ❌ SSOT: Không nên update balance trực tiếp. Sử dụng Transaction ledger thay thế.
//     * Balance phải được tính từ transactions table, không phải từ Wallet entity.
//     * @see com.mssus.app.service.BalanceCalculationService
//     */
//    @Deprecated(since = "SSOT refactor", forRemoval = false)
//    @Modifying
//    @Query("UPDATE Wallet w SET w.pendingBalance = w.pendingBalance + :amount WHERE w.user.userId = :userId")
//    int increasePendingBalance(@Param("userId") Integer userId, @Param("amount") BigDecimal amount);
//
//    /**
//     * @deprecated ❌ SSOT: Không nên update balance trực tiếp. Sử dụng Transaction ledger thay thế.
//     * Balance phải được tính từ transactions table, không phải từ Wallet entity.
//     * @see com.mssus.app.service.BalanceCalculationService
//     */
//    @Deprecated(since = "SSOT refactor", forRemoval = false)
//    @Modifying
//    @Query("UPDATE Wallet w SET w.pendingBalance = w.pendingBalance - :amount WHERE w.user.userId = :userId AND w.pendingBalance >= :amount")
//    int decreasePendingBalance(@Param("userId") Integer userId, @Param("amount") BigDecimal amount);
//
//    /**
//     * @deprecated ❌ SSOT: Không nên update balance trực tiếp. Sử dụng Transaction ledger thay thế.
//     * Balance phải được tính từ transactions table, không phải từ Wallet entity.
//     * @see com.mssus.app.service.BalanceCalculationService
//     */
//    @Deprecated(since = "SSOT refactor", forRemoval = false)
//    @Modifying
//    @Query("UPDATE Wallet w SET w.shadowBalance = w.shadowBalance + :amount WHERE w.user.userId = :userId")
//    int increaseShadowBalance(@Param("userId") Integer userId, @Param("amount") BigDecimal amount);
//
//    /**
//     * @deprecated ❌ SSOT: Không nên update balance trực tiếp. Sử dụng Transaction ledger thay thế.
//     * Balance phải được tính từ transactions table, không phải từ Wallet entity.
//     * @see com.mssus.app.service.BalanceCalculationService
//     */
//    @Deprecated(since = "SSOT refactor", forRemoval = false)
//    @Modifying
//    @Query("UPDATE Wallet w SET w.shadowBalance = w.shadowBalance - :amount WHERE w.user.userId = :userId AND w.shadowBalance >= :amount")
//    int decreaseShadowBalance(@Param("userId") Integer userId, @Param("amount") BigDecimal amount);
//
//
//    /**
//     * @deprecated ❌ SSOT: Không nên update balance trực tiếp. Sử dụng Transaction ledger thay thế.
//     * Balance phải được tính từ transactions table, không phải từ Wallet entity.
//     * @see com.mssus.app.service.BalanceCalculationService
//     */
//    @Deprecated(since = "SSOT refactor", forRemoval = false)
//    @Modifying
//    @Query("UPDATE Wallet w SET w.shadowBalance = w.shadowBalance + :delta, w.updatedAt = CURRENT_TIMESTAMP WHERE w.user.userId = :userId")
//    int addToAvailable(@Param("userId") Integer userId, @Param("delta") BigDecimal delta);
//
//    /**
//     * @deprecated ❌ SSOT: Không nên update balance trực tiếp. Sử dụng Transaction ledger thay thế.
//     * Balance phải được tính từ transactions table, không phải từ Wallet entity.
//     * @see com.mssus.app.service.BalanceCalculationService
//     */
//    @Deprecated(since = "SSOT refactor", forRemoval = false)
//    @Modifying
//    @Query("UPDATE Wallet w SET w.pendingBalance = w.pendingBalance + :delta, w.updatedAt = CURRENT_TIMESTAMP WHERE w.user.userId = :userId")
//    int addToPending(@Param("userId") Integer userId, @Param("delta") BigDecimal delta);

}

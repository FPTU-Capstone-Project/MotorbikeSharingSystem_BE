package com.mssus.app.repository;

import com.mssus.app.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
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

    Optional<Wallet> findByUserId(Integer userId);

    @Query("SELECT COUNT(w) > 0 FROM Wallet w WHERE w.user.userId = :userId")
    boolean existsByUserId(@Param("userId") Integer userId);

    @Query("SELECT w FROM Wallet w WHERE w.isActive = true")
    List<Wallet> findAllActiveWallets();

    @Query("SELECT w FROM Wallet w WHERE w.isActive = :isActive")
    List<Wallet> findByIsActive(@Param("isActive") Boolean isActive);

    @Query("SELECT w.cachedBalance FROM Wallet w WHERE w.user.userId = :userId")
    Optional<BigDecimal> findBalanceByUserId(@Param("userId") Integer userId);

    @Query("SELECT w.pendingBalance FROM Wallet w WHERE w.user.userId = :userId")
    Optional<BigDecimal> findPendingBalanceByUserId(@Param("userId") Integer userId);

    @Modifying
    @Query("UPDATE Wallet w SET w.cachedBalance = :balance, w.updatedAt = :updateTime WHERE w.user.userId = :userId")
    int updateBalanceByUserId(@Param("userId") Integer userId, @Param("balance") BigDecimal balance, @Param("updateTime") LocalDateTime updateTime);

    @Modifying
    @Query("UPDATE Wallet w SET w.pendingBalance = :pendingBalance, w.updatedAt = :updateTime WHERE w.user.userId = :userId")
    int updatePendingBalanceByUserId(@Param("userId") Integer userId, @Param("pendingBalance") BigDecimal pendingBalance, @Param("updateTime") LocalDateTime updateTime);

    @Modifying
    @Query("UPDATE Wallet w SET w.isActive = :isActive, w.updatedAt = :updateTime WHERE w.walletId = :walletId")
    int updateWalletStatus(@Param("walletId") Integer walletId, @Param("isActive") Boolean isActive, @Param("updateTime") LocalDateTime updateTime);

    @Modifying
    @Query("UPDATE Wallet w SET w.totalToppedUp = w.totalToppedUp + :amount, w.updatedAt = :updateTime WHERE w.user.userId = :userId")
    int updateTotalToppedUp(@Param("userId") Integer userId, @Param("amount") BigDecimal amount, @Param("updateTime") LocalDateTime updateTime);

    @Modifying
    @Query("UPDATE Wallet w SET w.totalSpent = w.totalSpent + :amount, w.updatedAt = :updateTime WHERE w.user.userId = :userId")
    int updateTotalSpent(@Param("userId") Integer userId, @Param("amount") BigDecimal amount, @Param("updateTime") LocalDateTime updateTime);

    @Modifying
    @Query("UPDATE Wallet w SET w.lastSyncedAt = :syncTime, w.updatedAt = :updateTime WHERE w.walletId = :walletId")
    int updateLastSyncedAt(@Param("walletId") Integer walletId, @Param("syncTime") LocalDateTime syncTime, @Param("updateTime") LocalDateTime updateTime);

    @Query("SELECT COUNT(w) FROM Wallet w WHERE w.isActive = true")
    long countActiveWallets();

    @Query("SELECT w FROM Wallet w WHERE w.pspAccountId = :pspAccountId")
    Optional<Wallet> findByPspAccountId(@Param("pspAccountId") String pspAccountId);
}
